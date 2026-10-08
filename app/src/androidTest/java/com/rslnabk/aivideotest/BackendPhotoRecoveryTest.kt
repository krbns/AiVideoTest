package com.rslnabk.aivideotest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rslnabk.aivideotest.data.backend.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class BackendPhotoRecoveryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val user = "66666666-2222-3333-4444-555555555555"
    private val id = "eeeeeeee-2222-3333-4444-555555555555"
    private val second = "ffffffff-2222-3333-4444-555555555555"
    private val draft = PhotoDraft("A mountain", "creator", resolution = "1K", aspectRatio = "1:1", outputFormat = "jpeg")
    private fun body() = JSONObject().put("model", "creator").put("mode", "textToImage").put("prompt", draft.prompt)
        .put("resolution", "1K").put("aspectRatio", "1:1").put("outputFormat", "jpeg").put("numImages", 1)
    private fun stamp(now: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(now))
    private fun job(jobId: String = id, status: String = "running", now: Long = System.currentTimeMillis()) = JSONObject()
        .put("jobId", jobId).put("kind", "image").put("model", "creator").put("mode", "textToImage")
        .put("prompt", draft.prompt).put("templateId", JSONObject.NULL).put("status", status).put("assets", JSONArray())
        .put("parameters", JSONObject().put("resolution", "1K").put("aspectRatio", "1:1").put("outputFormat", "jpeg").put("numImages", 1.0))
        .put("inputImageUrls", JSONArray()).put("createdAt", stamp(now)).put("creditsCharged", 8).put("creditsRefunded", false)
    private fun page(vararg jobs: JSONObject, cursor: String? = null) = BackendResponse(200,
        JSONObject().put("jobs", JSONArray(jobs.toList())).put("nextCursor", cursor ?: JSONObject.NULL).toString())

    @Test fun matchingUsesTypedParametersAndRejectsEveryConflictingField() {
        val sent = PhotoSubmission(System.currentTimeMillis(), body().toString(), emptySet())
        val parsed = BackendJson.job(job())
        assertTrue(PhotoRecovery.matches(parsed, draft, sent)); assertFalse(PhotoRecovery.incomplete(parsed, sent))
        for (changed in listOf(parsed.copy(model = "other"), parsed.copy(prompt = "Other"), parsed.copy(kind = "video"),
            parsed.copy(mode = "imageToImage"), parsed.copy(templateId = "template"),
            parsed.copy(parameters = """{"resolution":"2K","aspectRatio":"1:1","outputFormat":"jpeg","numImages":1}"""),
            parsed.copy(parameters = """{"resolution":"1K","aspectRatio":"1:1","outputFormat":"jpeg","numImages":"1"}"""))) {
            assertFalse(PhotoRecovery.matches(changed, draft, sent))
        }
        assertEquals("textToImage", parsed.mode); assertEquals(emptyList<String>(), parsed.inputImageUrls)
        assertTrue(parsed.parameters!!.contains("resolution")); assertNotNull(parsed.createdAt)
    }
    @Test fun referencesKnownIdsAndSendingWindowExcludeUnrelatedTasks() {
        val now = System.currentTimeMillis()
        val referenceDraft = draft.copy(photos = listOf("file:local.jpg"))
        val request = body().put("mode", "imageToImage").put("imageUrls", JSONArray(listOf("https://example.invalid/ref")))
        val sent = PhotoSubmission(now, request.toString(), setOf(second))
        val candidate = BackendJson.job(job().put("mode", "imageToImage").put("inputImageUrls", JSONArray(listOf("https://example.invalid/ref"))))
        assertTrue(PhotoRecovery.matches(candidate, referenceDraft, sent))
        assertFalse(PhotoRecovery.matches(candidate.copy(id = second), referenceDraft, sent))
        assertFalse(PhotoRecovery.matches(candidate.copy(inputImageUrls = listOf("https://example.invalid/other")), referenceDraft, sent))
        assertFalse(PhotoRecovery.matches(candidate.copy(createdAt = stamp(now - 6 * 60000L)), referenceDraft, sent))
        assertFalse(PhotoRecovery.matches(candidate.copy(createdAt = stamp(now + 11 * 60000L)), referenceDraft, sent))
        assertTrue(PhotoRecovery.matches(candidate.copy(createdAt = stamp(now - 60000)), referenceDraft, sent))
        assertNull(PhotoRecovery.time("2026-10-08T12:00:00Zgarbage"))
        assertNotNull(PhotoRecovery.time("2026-10-08T12:00:00.123456+00:00"))
    }
    @Test fun legacyRequestsAndNullableMetadataRemainExplicitlyIncomplete() {
        val legacy = BackendJson.job(job().put("mode", JSONObject.NULL).put("parameters", JSONObject.NULL)
            .put("inputImageUrls", JSONObject.NULL).put("createdAt", JSONObject.NULL))
        assertTrue(PhotoRecovery.matches(legacy, draft, null)); assertTrue(PhotoRecovery.incomplete(legacy, null))
        assertFalse(PhotoRecovery.matches(legacy.copy(prompt = "Other"), draft, null))
        val request = PhotoSubmission(System.currentTimeMillis(), body().toString(), emptySet())
        val restored = PhotoJournal.restore(PhotoJournal.encode(draft, PhotoPhase.SENDING, submission = request))
        assertEquals(PhotoPhase.UNKNOWN, restored.phase); assertEquals(request, restored.submission)
        assertFalse(request.toString().contains("A mountain")); assertFalse(request.toString().contains("https://"))
        assertNull(PhotoJournal.restore(PhotoJournal.encode(draft, PhotoPhase.UNKNOWN)).submission)
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun waitFor(test: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10000
        while (!test() && System.currentTimeMillis() < deadline) Thread.sleep(30)
        assertTrue(test())
    }
    private fun flow(history: (String) -> BackendResponse, detail: () -> BackendResponse,
        assertions: (BackendController, PhotoGenerationController, AtomicInteger, () -> PhotoGenerationController) -> Unit) {
        context.getSharedPreferences("backend_source_v1", 0).edit().clear().commit()
        PhotoJournal(context).save(user, PhotoJournal.encode(draft, PhotoPhase.SENDING,
            submission = PhotoSubmission(System.currentTimeMillis(), body().toString(), emptySet())))
        val posts = AtomicInteger()
        val store = object : BackendAuthStore {
            override fun deviceId() = "test"
            override fun load() = AuthSession(user, "test", "access", "refresh", System.currentTimeMillis() + 600000, Long.MAX_VALUE)
            override fun save(session: AuthSession) = Unit
        }
        val transport = BackendTransport { method, path, _, _ ->
            if (method != "GET") { posts.incrementAndGet(); throw BackendFailure(code = "unexpected_post") }
            when {
                path.startsWith("/v1/media/jobs?kind=image") -> history(path)
                path.startsWith("/v1/media/jobs/") -> detail()
                path == BackendSection.JOBS.path -> page()
                path == BackendSection.PHOTOS.path || path == BackendSection.VIDEOS.path -> BackendResponse(200, """{"templates":[]}""")
                path == BackendSection.MODELS.path -> BackendResponse(200, """{"models":[]}""")
                else -> BackendResponse(503, "{}")
            }
        }
        lateinit var backend: BackendController; lateinit var photos: PhotoGenerationController
        main { backend = BackendController(context, transport, store); backend.connect() }
        try {
            waitFor { backend.state.value!!.userId == user && backend.state.value!!.loading.isEmpty() }
            main { photos = PhotoGenerationController(context, backend, pollMillis = 50); photos.activate(user) }
            waitFor { photos.state.value!!.recovery.searched && !photos.state.value!!.recovery.loading }
            val restart = {
                main { photos.close(); photos = PhotoGenerationController(context, backend, pollMillis = 50); photos.activate(user) }
                photos
            }
            assertions(backend, photos, posts, restart)
            assertEquals(0, posts.get())
        } finally {
            main { photos.close(); backend.useDemo(); backend.close() }
            context.getSharedPreferences("backend_source_v1", 0).edit().clear().commit()
            File(context.filesDir, "backend_photo/$user.json").delete()
            File(context.cacheDir, "backend_read/$user.json").delete()
        }
    }
    @Test fun paginationDeduplicatesAndUserChoicePersistsAcrossRestartWithoutPost() {
        val details = AtomicInteger()
        flow({ path -> if ("cursor=" in path) {
            assertTrue(path.endsWith("cursor=page%2B%26two")); page(job(), job(second))
        } else page(job(), job("abababab-2222-3333-4444-555555555555").put("mode", "imageUpscale"), cursor = "page+&two") },
            { details.incrementAndGet(); BackendResponse(200, job(second, "completed").toString()) }) { _, photos, _, restart ->
            assertEquals(PhotoPhase.UNKNOWN, photos.state.value!!.phase); assertEquals(1, photos.state.value!!.recovery.candidates.size)
            assertEquals(0, details.get())
            main { photos.searchRecovery(more = true) }
            waitFor { !photos.state.value!!.recovery.loading && photos.state.value!!.recovery.candidates.size == 2 }
            main { photos.recoverJob(second) }
            waitFor { photos.state.value!!.phase == PhotoPhase.COMPLETE }
            assertEquals(second, photos.state.value!!.submissionJobId); assertEquals(1, details.get())
            val restored = restart(); assertEquals(second, restored.state.value!!.submissionJobId)
            assertEquals(PhotoPhase.COMPLETE, restored.state.value!!.phase); assertNotNull(restored.state.value!!.submission)
        }
    }
    @Test fun runningRecoveryRestartsPollingWithoutResubmitting() {
        val complete = AtomicBoolean(false)
        flow({ page(job()) }, { BackendResponse(200, job(status = if (complete.get()) "completed" else "running").toString()) }) { _, photos, _, restart ->
            main { photos.recoverJob(id) }; waitFor { photos.state.value!!.phase == PhotoPhase.ACTIVE }
            val restored = restart(); assertEquals(id, restored.state.value!!.submissionJobId)
            complete.set(true); waitFor { restored.state.value!!.phase == PhotoPhase.COMPLETE }
        }
    }
    @Test fun emptyHistoryAndRestartNeverReleaseUnknownOrSendPost() {
        flow({ page() }, { throw AssertionError("No task should be polled") }) { _, photos, _, restart ->
            assertTrue(photos.state.value!!.recovery.candidates.isEmpty()); assertEquals(PhotoPhase.UNKNOWN, photos.state.value!!.phase)
            main { photos.submit() }
            val restored = restart(); waitFor { !restored.state.value!!.recovery.loading }
            assertEquals(PhotoPhase.UNKNOWN, restored.state.value!!.phase); assertNotNull(restored.state.value!!.submission)
        }
    }
    @Test fun searchFailureAndMissingDetailKeepUnknownAndAllowGetRefresh() {
        val failSearch = AtomicBoolean(true)
        flow({ if (failSearch.get()) BackendResponse(503, "{}") else page(job()) }, { BackendResponse(404, "{}") }) { _, photos, _, _ ->
            assertEquals(503, photos.state.value!!.recovery.failure!!.status)
            failSearch.set(false); main { photos.searchRecovery() }
            waitFor { !photos.state.value!!.recovery.loading && photos.state.value!!.recovery.candidates.isNotEmpty() }
            main { photos.recoverJob(id) }
            waitFor { !photos.state.value!!.recovery.loading && photos.state.value!!.recovery.failure?.status == 404 }
            assertEquals(PhotoPhase.UNKNOWN, photos.state.value!!.phase); assertNull(photos.state.value!!.submissionJobId)
        }
    }
    @Test fun changedDetailAndRepeatedCursorAreRejectedWithoutBinding() {
        flow({ page(job(), cursor = "same") }, { BackendResponse(200, job().put("prompt", "Other request").toString()) }) { _, photos, _, _ ->
            main { photos.searchRecovery(more = true) }
            waitFor { !photos.state.value!!.recovery.loading && photos.state.value!!.recovery.failure != null }
            assertNull(photos.state.value!!.recovery.nextCursor); assertEquals(1, photos.state.value!!.recovery.candidates.size)
            main { photos.recoverJob(id) }
            waitFor { !photos.state.value!!.recovery.loading && photos.state.value!!.recovery.failure?.code == "recovery_changed" }
            assertEquals(PhotoPhase.UNKNOWN, photos.state.value!!.phase)
        }
    }
}
