package com.rslnabk.aivideotest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.rslnabk.aivideotest.data.backend.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BackendStartupTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val user = "b5000000-1111-4111-8111-111111111111"
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun waitFor(predicate: () -> Boolean) {
        val until = System.currentTimeMillis() + 10000
        while (!predicate() && System.currentTimeMillis() < until) Thread.sleep(25)
        assertTrue("Startup state did not settle", predicate())
    }
    private class Store(initial: AuthSession?) : BackendAuthStore {
        @Volatile var value = initial
        override fun deviceId() = "startup-device"
        override fun load() = value
        override fun save(session: AuthSession) { value = session }
    }
    private fun session(expired: Boolean = false) = AuthSession(user, "startup-device", "old-access", "old-refresh",
        if (expired) 0 else Long.MAX_VALUE, Long.MAX_VALUE)
    private fun token() = JSONObject().put("userId", user).put("deviceId", "startup-device")
        .put("tokenType", "Bearer").put("accessToken", "new-access").put("refreshToken", "new-refresh").put("expiresIn", 3600).put("refreshExpiresIn", 7200).toString()
    private fun read(path: String): BackendResponse = BackendResponse(200, when (path) {
        BackendSection.PHOTOS.path, BackendSection.VIDEOS.path -> "{\"templates\":[]}"
        BackendSection.MODELS.path -> "{\"models\":[]}"
        BackendSection.JOBS.path -> "{\"jobs\":[],\"nextCursor\":null}"
        BackendSection.PROFILE.path -> "{\"accountId\":\"startup-account\",\"name\":null}"
        BackendSection.POLICY.path -> "{\"isSubscribed\":true,\"creditsBalance\":44,\"trialRemaining\":0,\"canGenerateCreditsMode\":true}"
        BackendSection.WALLET.path -> "{\"balance\":44}"
        else -> "{\"products\":[]}"
    })
    private fun settled(backend: BackendController) = !backend.state.value!!.connecting && backend.state.value!!.loading.isEmpty()
    private fun prefs() = "startup-test-" + UUID.randomUUID()
    private fun close(backend: BackendController, name: String) {
        main { backend.close() }; context.getSharedPreferences(name, 0).edit().clear().commit()
    }
    @Test fun freshInstallationOpensServerAndRegistersOnlyOnceAcrossControllers() {
        val name = prefs(); val store = Store(null); val registrations = AtomicInteger()
        val transport = BackendTransport { method, path, _, _ ->
            if (method == "POST") { assertEquals("/v1/auth/register", path); registrations.incrementAndGet(); BackendResponse(200, token()) }
            else read(path)
        }
        lateinit var first: BackendController
        main { first = BackendController(context, transport, store, prefsName = name) }
        try {
            waitFor { settled(first) }; assertEquals(DataSource.SERVER, first.source.value); assertEquals(44, first.state.value!!.data.wallet)
        } finally { close(first, name) }
        lateinit var second: BackendController
        main { second = BackendController(context, transport, store, prefsName = name) }
        try { waitFor { settled(second) }; assertEquals(user, second.state.value!!.userId); assertEquals(1, registrations.get()) }
        finally { close(second, name) }
    }
    @Test fun releaseIgnoresSavedDemoAndCannotSwitchToDemo() {
        val name = prefs(); context.getSharedPreferences(name, 0).edit().putString("source", "DEMO").commit()
        lateinit var backend: BackendController
        main { backend = BackendController(context, BackendTransport { _, path, _, _ -> read(path) }, Store(session()), allowDemo = false, prefsName = name) }
        try {
            waitFor { settled(backend) }; main { backend.useDemo() }
            assertEquals(DataSource.SERVER, backend.source.value); assertEquals(user, backend.state.value!!.userId)
        } finally { close(backend, name) }
    }
    @Test fun explicitDebugDemoMakesNoNetworkRequests() {
        val name = prefs(); context.getSharedPreferences(name, 0).edit().putString("source", "DEMO").commit()
        val requests = AtomicInteger(); lateinit var backend: BackendController
        main { backend = BackendController(context, BackendTransport { _, path, _, _ -> requests.incrementAndGet(); read(path) }, Store(session()), prefsName = name) }
        try {
            main { backend.onForeground() }; assertEquals(DataSource.DEMO, backend.source.value); assertEquals(0, requests.get())
            main { backend.connect() }; waitFor { settled(backend) }; assertEquals(DataSource.SERVER, backend.source.value)
        } finally { close(backend, name) }
    }
    @Test fun expiredOfflineSessionKeepsCacheAndReconnectsSameIdentityWithoutReplayingRefresh() {
        val name = prefs(); val store = Store(session(true)); val refreshes = AtomicInteger(); val tokens = AtomicInteger()
        BackendReadCache(context).write(user, BackendSection.WALLET, JSONObject().put("balance", 37))
        val transport = BackendTransport { method, path, _, _ -> when (path) {
            "/v1/auth/refresh" -> { refreshes.incrementAndGet(); throw BackendFailure() }
            "/v1/auth/token" -> { tokens.incrementAndGet(); BackendResponse(200, token()) }
            else -> { assertEquals("GET", method); read(path) }
        } }
        lateinit var first: BackendController
        main { first = BackendController(context, transport, store, prefsName = name) }
        try {
            waitFor { first.state.value!!.authError != null }
            assertEquals(37, first.state.value!!.data.wallet); assertTrue(BackendSection.WALLET in first.state.value!!.cached)
            assertEquals(user, first.state.value!!.userId); assertEquals(DataSource.SERVER, first.source.value)
        } finally { close(first, name) }
        lateinit var second: BackendController
        main { second = BackendController(context, transport, store, prefsName = name) }
        try {
            waitFor { second.state.value!!.authError != null }; assertEquals(1, refreshes.get()); assertEquals(37, second.state.value!!.data.wallet)
            main { second.connect(true) }; waitFor { settled(second) && second.state.value!!.authError == null }
            assertEquals(1, refreshes.get()); assertEquals(1, tokens.get()); assertEquals(user, second.state.value!!.userId)
            assertEquals(44, second.state.value!!.data.wallet); assertTrue(second.state.value!!.cached.isEmpty()); assertFalse(store.value!!.refreshPending)
        } finally { close(second, name) }
    }
    @Test fun pendingRefreshSurvivesEncryptedStoreRecreation() {
        val name = prefs(); val preferences = context.getSharedPreferences(name, 0)
        try {
            val first = EncryptedBackendAuthStore(context, name); val id = first.deviceId()
            first.save(session().copy(deviceId = id, refreshPending = true))
            val second = EncryptedBackendAuthStore(context, name)
            val client = BackendClient(BackendTransport { _, _, _, _ -> fail("Pending refresh must not be sent again"); BackendResponse(500, "{}") }, second)
            try { client.initialize(); fail("Expected explicit reconnect") } catch (failure: BackendFailure) { assertEquals("refresh_uncertain", failure.code) }
            assertEquals(user, second.load()!!.userId); assertEquals(id, second.deviceId())
        } finally { preferences.edit().clear().commit() }
    }
    @Test fun refreshJournalWriteFailurePreventsAnyExchange() {
        val store = object : BackendAuthStore {
            override fun deviceId() = "startup-device"
            override fun load() = session(true)
            override fun save(session: AuthSession) { throw BackendFailure(code = "session_storage") }
        }
        val client = BackendClient(BackendTransport { _, _, _, _ -> fail("No exchange without durable marker"); BackendResponse(500, "{}") }, store)
        try { client.initialize(); fail("Expected storage failure") } catch (failure: BackendFailure) { assertEquals("session_storage", failure.code) }
    }
    @Test fun unreadableIdentityNeverCreatesAnotherAccountOrLoadsArbitraryCache() {
        val name = prefs(); val store = object : BackendAuthStore {
            override fun deviceId(): String = throw BackendFailure(code = "session_storage")
            override fun load(): AuthSession? = throw BackendFailure(code = "session_storage")
            override fun save(session: AuthSession) = Unit
        }
        lateinit var backend: BackendController
        main { backend = BackendController(context, BackendTransport { _, _, _, _ -> fail("No replacement identity"); BackendResponse(500, "{}") }, store, prefsName = name) }
        try { waitFor { backend.state.value!!.authError != null }; assertNull(backend.state.value!!.userId); assertNull(backend.state.value!!.data.wallet) }
        finally { close(backend, name) }
    }
    @Test fun foregroundRefreshIsThrottledAndReadsOnly() {
        val name = prefs(); var clock = 100000L; val reads = AtomicInteger()
        lateinit var backend: BackendController
        main { backend = BackendController(context, BackendTransport { method, path, _, _ -> assertEquals("GET", method); reads.incrementAndGet(); read(path) }, Store(session()), prefsName = name, now = { clock }) }
        try {
            waitFor { settled(backend) }; val initial = reads.get()
            main { backend.onForeground(); backend.onForeground() }; assertEquals(initial, reads.get())
            clock += 60001; main { backend.onForeground() }; waitFor { settled(backend) && reads.get() > initial }
            assertEquals(initial * 2, reads.get()); assertEquals(44, backend.state.value!!.data.wallet)
        } finally { close(backend, name) }
    }
    @Test fun mutation401NeverReplaysAfterExplicitSessionReconnect() {
        val store = Store(session()); val posts = AtomicInteger(); val tokens = AtomicInteger()
        val client = BackendClient(BackendTransport { method, path, _, _ -> when (path) {
            "/v1/media/images" -> { posts.incrementAndGet(); BackendResponse(401, "{}") }
            "/v1/auth/token" -> { tokens.incrementAndGet(); BackendResponse(200, token()) }
            else -> { assertEquals("GET", method); BackendResponse(200, "{}") }
        } }, store)
        try { client.postOnce("/v1/media/images", JSONObject()); fail("Expected rejection") } catch (failure: BackendFailure) { assertEquals(401, failure.status) }
        client.initialize(true); client.get("/v1/wallet")
        assertEquals(1, posts.get()); assertEquals(1, tokens.get())
    }
    @Test fun pausedInFlightPollCannotUpdateStateAndResumeUsesKnownJobWithoutPost() {
        val name = prefs(); val id = "b5000000-2222-4222-8222-222222222222"
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val returned = CountDownLatch(1); val gets = AtomicInteger()
        fun job(status: String) = JSONObject().put("jobId", id).put("kind", "image").put("prompt", "A mountain").put("status", status).put("assets", org.json.JSONArray())
        PhotoJournal(context).save(user, PhotoJournal.encode(PhotoDraft(), PhotoPhase.ACTIVE, job("running")))
        val transport = BackendTransport { method, path, _, _ ->
            assertEquals("GET", method)
            if (path == "/v1/media/jobs/$id") {
                if (gets.incrementAndGet() == 1) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); returned.countDown() }
                BackendResponse(200, job("completed").toString())
            } else read(path)
        }
        lateinit var backend: BackendController; var photo: PhotoGenerationController? = null
        main { backend = BackendController(context, transport, Store(session()), prefsName = name) }
        try {
            waitFor { settled(backend) }; main { photo = PhotoGenerationController(context, backend, pollMillis = 50); photo!!.activate(user) }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); main { photo!!.pause() }; release.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS))
            Thread.sleep(150); InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(PhotoPhase.ACTIVE, photo!!.state.value!!.phase)
            main { photo!!.activate(user) }; waitFor { photo!!.state.value!!.phase == PhotoPhase.COMPLETE }
            assertEquals(id, photo!!.state.value!!.submissionJobId); assertTrue(gets.get() >= 2)
        } finally { release.countDown(); main { photo?.close() }; close(backend, name); File(context.filesDir, "backend_photo/$user.json").delete() }
    }
}
