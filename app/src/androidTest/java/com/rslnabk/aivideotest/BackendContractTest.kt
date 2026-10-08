package com.rslnabk.aivideotest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rslnabk.aivideotest.data.backend.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BackendContractTest {
    private val user = "11111111-2222-3333-4444-555555555555"
    private val clock = 100000L
    private fun token(access: String = "access", refresh: String = "refresh", id: String = user) =
        JSONObject().put("userId", id).put("deviceId", "installation").put("accessToken", access).put("refreshToken", refresh)
            .put("tokenType", "Bearer").put("expiresIn", 3600).put("refreshExpiresIn", 7200).toString()
    private inner class Store : BackendAuthStore {
        @Volatile var value: AuthSession? = null
        override fun deviceId() = "installation"
        override fun load() = value
        override fun save(session: AuthSession) { value = session }
    }
    @Test fun registrationPersistsIdentityAndDoesNotRegisterTwice() {
        val store = Store(); val requests = mutableListOf<String>()
        val client = BackendClient(BackendTransport { method, path, bearer, body ->
            requests += method + " " + path
            if (method == "POST") { assertNull(bearer); assertEquals("installation", JSONObject(body!!).getString("deviceId")); BackendResponse(200, token()) }
            else { assertEquals("access", bearer); BackendResponse(200, "{\"balance\":37}") }
        }, store) { clock }
        client.initialize(); client.initialize(); assertEquals(37, client.get("/v1/wallet").getInt("balance"))
        assertEquals(listOf("POST /v1/auth/register", "GET /v1/wallet"), requests)
        assertEquals(user, store.value!!.userId); assertFalse(store.value.toString().contains("refreshToken"))
    }
    @Test fun expiredAccessRotatesBothTokensBeforeNextRequest() {
        val store = Store().apply { value = AuthSession(user, "installation", "old", "old-refresh", clock, clock + 999999) }
        var refreshes = 0
        val client = BackendClient(BackendTransport { method, path, bearer, body ->
            if (path == "/v1/auth/refresh") {
                refreshes++; assertEquals("old-refresh", JSONObject(body!!).getString("refreshToken")); BackendResponse(200, token("new", "new-refresh"))
            } else { assertEquals("GET", method); assertEquals("new", bearer); assertEquals("new-refresh", store.value!!.refreshToken); BackendResponse(200, "{}") }
        }, store) { clock }
        client.initialize(); client.get("/v1/profile"); client.initialize()
        assertEquals(1, refreshes); assertEquals("new-refresh", store.value!!.refreshToken)
    }
    @Test fun concurrentUnauthorizedReadsShareOneRefresh() {
        val store = Store().apply { value = BackendJson.session(JSONObject(token("old", "old-refresh")), clock) }
        val barrier = CyclicBarrier(2); val refreshes = AtomicInteger()
        val client = BackendClient(BackendTransport { method, path, bearer, _ ->
            if (path == "/v1/auth/refresh") { refreshes.incrementAndGet(); BackendResponse(200, token("new", "new-refresh")) }
            else if (bearer == "old") { assertEquals("GET", method); barrier.await(5, TimeUnit.SECONDS); BackendResponse(401, "{}") }
            else { assertEquals("new", bearer); BackendResponse(200, "{}") }
        }, store) { clock }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map { executor.submit<JSONObject> { client.get("/v1/wallet") } }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, refreshes.get()); assertEquals("new-refresh", store.value!!.refreshToken)
        } finally { executor.shutdownNow() }
    }
    @Test fun rejectedRefreshKeepsStoredIdentityAndDoesNotRegisterAgain() {
        val store = Store().apply { value = AuthSession(user, "installation", "old", "refresh", clock, clock + 999999) }
        val requests = mutableListOf<String>()
        val client = BackendClient(BackendTransport { _, path, _, _ -> requests += path; BackendResponse(401, "{\"error\":{\"code\":\"unauthorized\"}}") }, store) { clock }
        try { client.initialize(); fail("Expected expired session") } catch (failure: BackendFailure) { assertEquals(401, failure.status) }
        assertEquals(listOf("/v1/auth/refresh"), requests); assertEquals("refresh", store.value!!.refreshToken)
    }
    @Test fun refreshCannotMoveCredentialsToAnotherAccount() {
        val store = Store().apply { value = AuthSession(user, "installation", "old", "refresh", clock, clock + 999999) }
        val client = BackendClient(BackendTransport { _, _, _, _ -> BackendResponse(200, token(id = "22222222-2222-3333-4444-555555555555")) }, store) { clock }
        try { client.initialize(); fail("Expected identity rejection") } catch (failure: BackendFailure) { assertEquals("identity_mismatch", failure.code) }
        assertEquals(user, store.value!!.userId); assertEquals("old", store.value!!.accessToken)
    }
    @Test fun nullableCatalogFieldsAndUnknownJobStatusAreSafe() {
        val catalog = JSONObject("""{"templates":[{"id":"effect","title":"Remote name","coverMediaType":"video/mp4","coverUrl":"https://example.com/video","coverPosterUrl":null,"tokens":17,"requiredInputImages":3,"pipeline":"image_video","categories":["new"]}]}""")
        var data = BackendJson.update(BackendData(), BackendSection.VIDEOS, catalog)
        assertNull(data.videos.single().cover); assertEquals(3, data.videos.single().requiredImages)
        data = BackendJson.update(data, BackendSection.JOBS, JSONObject("""{"jobs":[{"jobId":"job","kind":"image","prompt":"","status":"future_status","assets":[]}],"nextCursor":null}"""))
        assertEquals("future_status", data.jobs.single().status); assertNull(data.jobs.single().thumbnail); assertNull(data.nextCursor)
        assertNull(data.wallet); assertNull(data.policy)
    }
    @Test fun bothValidationFormatsAndBusinessBlockRemainFailures() {
        for (body in listOf("{\"detail\":[{\"msg\":\"bad value\"}]}", "{\"error\":{\"code\":\"invalid_input\",\"message\":\"private message\"}}")) {
            try { BackendJson.response(BackendResponse(422, body)); fail("Expected validation error") }
            catch (failure: BackendFailure) { assertEquals(422, failure.status); assertFalse(failure.toString().contains("private message")) }
        }
        try { BackendJson.response(BackendResponse(200, "{\"status\":\"blocked\",\"blockReason\":\"policy\"}")); fail("Expected block") }
        catch (failure: BackendFailure) { assertEquals("blocked", failure.code) }
    }
    @Test fun encryptedSessionSurvivesStoreRecreationAndCacheStaysAccountScoped() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("b1_auth_test", Context.MODE_PRIVATE); prefs.edit().clear().commit()
        try {
            val first = EncryptedBackendAuthStore(context, "b1_auth_test"); val id = first.deviceId()
            val session = AuthSession(user, id, "secret-access", "secret-refresh", clock, clock + 999999)
            first.save(session)
            val restored = EncryptedBackendAuthStore(context, "b1_auth_test")
            assertEquals(id, restored.deviceId()); assertEquals(session, restored.load())
            assertFalse(prefs.all.toString().contains("secret-access")); assertFalse(prefs.all.toString().contains("secret-refresh"))
            val cache = BackendReadCache(context); cache.write(user, BackendSection.WALLET, JSONObject("{\"balance\":37}"))
            assertEquals(37, cache.read(user).getJSONObject("WALLET").getInt("balance"))
            assertFalse(cache.read("33333333-2222-3333-4444-555555555555").has("WALLET"))
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun sectionFailureKeepsCatalogAvailableAndModeSwitchCanReconnect() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("backend_source_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val store = Store(); val transport = BackendTransport { method, path, _, _ ->
            if (method == "POST") BackendResponse(200, token())
            else if (path == BackendSection.PHOTOS.path || path == BackendSection.VIDEOS.path) BackendResponse(200, "{\"templates\":[]}")
            else BackendResponse(503, "{\"error\":{\"code\":\"not_configured\"}}")
        }
        lateinit var controller: BackendController
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { controller = BackendController(context, transport, store); controller.connect() }
        try {
            fun waitLoaded() {
                val deadline = System.currentTimeMillis() + 8000
                while (System.currentTimeMillis() < deadline && controller.state.value!!.loading.isNotEmpty()) Thread.sleep(30)
                // Allow the first posted authentication/cache state to become visible.
                while (System.currentTimeMillis() < deadline && (controller.state.value!!.connecting || BackendSection.VIDEOS !in controller.state.value!!.loaded)) Thread.sleep(30)
                assertTrue(BackendSection.VIDEOS in controller.state.value!!.loaded)
            }
            waitLoaded(); assertNull(controller.state.value!!.authError); assertTrue(BackendSection.MODELS in controller.state.value!!.errors)
            instrumentation.runOnMainSync { controller.useDemo(); controller.connect() }
            waitLoaded(); assertEquals(DataSource.SERVER, controller.source.value)
        } finally { instrumentation.runOnMainSync { controller.useDemo(); controller.close() }; context.getSharedPreferences("backend_source_v1", Context.MODE_PRIVATE).edit().clear().commit() }
    }
}
