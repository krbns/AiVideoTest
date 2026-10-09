package com.rslnabk.aivideotest

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.data.media.ResultMedia
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class BackendPhotoFlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val user = "44444444-2222-3333-4444-555555555555"
    private val id = "aaaaaaaa-2222-3333-4444-555555555555"
    private fun model() = RemoteModel("creator", "Creator", "image",
        listOf("textToImage", "imageToImage").map { RemoteMode(it, listOf("1K", "2K"), emptyList(),
            listOf("prompt", "resolution", "aspectRatio", "outputFormat", "numImages"), listOf("1:1", "16:9"), listOf("png", "jpeg"),
            listOf("prompt"), mapOf("resolution" to "2K", "aspectRatio" to "1:1", "outputFormat" to "png")) },
        3, mapOf("1K" to 3, "2K" to 7), 2)
    private fun job(status: String) = JSONObject().put("jobId", id).put("kind", "image").put("prompt", "A mountain")
        .put("status", status).put("model", "creator").put("creditsCharged", 7).put("creditsRefunded", status == "failed")
        .put("progress", if (status == "completed") 1 else 0).put("assets", org.json.JSONArray(
            if (status == "completed") """[{"url":"https://example.invalid/result","contentType":null,"fileName":null}]""" else "[]"))
    private fun bytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.GREEN)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }
    @Test fun defaultsPriceAndOnlySupportedFieldsAreSent() {
        val draft = PhotoRequest.defaults(PhotoDraft(prompt = "A mountain"), model())
        assertEquals("2K", draft.resolution); assertEquals(7, PhotoRequest.cost(model(), draft))
        val request = PhotoRequest.build(model(), draft, emptyList())
        assertEquals("textToImage", request.getString("mode")); assertEquals(1, request.getInt("numImages"))
        assertFalse(request.has("style")); assertFalse(request.has("imageUrls")); assertFalse(request.has("seed"))
        assertTrue(PhotoRequest.ready(model(), draft))
        assertFalse(PhotoRequest.ready(model(), draft.copy(resolution = "4K")))
        val future = model().copy(modes = model().modes.map { it.copy(required = listOf("unknownMandatoryInput")) })
        assertFalse(PhotoRequest.ready(future, draft))
    }
    @Test fun referencesSelectImageModeAndRespectModelLimit() {
        val draft = PhotoRequest.defaults(PhotoDraft(prompt = "Portrait", photos = listOf("file:a.jpg")), model())
        assertEquals("imageToImage", PhotoRequest.build(model(), draft, listOf("https://example.invalid/ref")).getString("mode"))
        assertFalse(PhotoRequest.ready(model(), draft.copy(photos = List(3) { "file:a.jpg" })))
        val processor = model().copy(modes = listOf(RemoteMode("imageUpscale", emptyList(), emptyList())))
        assertTrue(PhotoRequest.models(BackendData(models = listOf(processor)), draft).isEmpty())
    }
    @Test fun mutationsNeverReplayOnUnauthorizedOrTimeout() {
        for (status in listOf(401, 502)) {
            val calls = AtomicInteger()
            val store = object : BackendAuthStore {
                override fun deviceId() = "test"
                override fun load() = AuthSession(user, "test", "access", "refresh", System.currentTimeMillis() + 600000, Long.MAX_VALUE)
                override fun save(session: AuthSession) = Unit
            }
            val client = BackendClient(BackendTransport { method, path, _, _ ->
                assertEquals("POST", method); assertEquals("/v1/media/images", path); calls.incrementAndGet(); BackendResponse(status, "{}")
            }, store)
            try { client.postOnce("/v1/media/images", JSONObject()); fail("Expected rejection") } catch (_: BackendFailure) {}
            assertEquals(1, calls.get())
        }
    }
    @Test fun journalRestoresUncertainSendWithoutReplayAndKnownJobWithoutResubmit() {
        val draft = PhotoRequest.defaults(PhotoDraft(prompt = "A mountain"), model())
        assertEquals(PhotoPhase.UNKNOWN, PhotoJournal.restore(PhotoJournal.encode(draft, PhotoPhase.SENDING)).phase)
        val upload = PhotoJournal.restore(PhotoJournal.encode(draft, PhotoPhase.UPLOADING))
        assertEquals(PhotoPhase.EDITING, upload.phase); assertEquals("interrupted", upload.failure!!.code)
        val accepted = PhotoJournal.restore(PhotoJournal.encode(draft, PhotoPhase.ACTIVE, job("queued")))
        assertEquals(id, accepted.submissionJobId); assertEquals(PhotoPhase.ACTIVE, accepted.phase)
        assertEquals(7, accepted.job!!.charged)
    }
    @Test fun downloadAndExportKeepOriginalBytesAndCachedCopyWorksAfterExpiry() {
        val original = bytes(); val fetches = AtomicInteger()
        val media = RemotePhotoMedia(context) { fetches.incrementAndGet(); original to "application/octet-stream" }
        val job = BackendJson.job(job("completed"))
        val local = media.load(user, job)
        try {
            assertEquals("image/png", local.mime); assertArrayEquals(original, local.file.readBytes())
            val expired = job.copy(assets = job.assets.map { it.copy(expiresAt = "2000-01-01T00:00:00Z") })
            assertEquals(local.file, media.load(user, expired).file); assertEquals(1, fetches.get())
            val exporter = ResultMedia(context); val uri = exporter.share(local.export(job.id))
            assertArrayEquals(original, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            val gallery = exporter.gallery(local.export(job.id), {})
            try { assertArrayEquals(original, GalleryProbe.read(context, gallery)) }
            finally { context.contentResolver.delete(gallery, null, null) }
        } finally { local.file.delete() }
    }
    @Test fun expiredUncachedResultsAndNonImagesAreRejected() {
        val past = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(System.currentTimeMillis() - 60000)) + ".999999Z"
        assertTrue(RemotePhotoMedia.expired(past))
        val media = RemotePhotoMedia(context) { fail("Expired file must not be fetched"); byteArrayOf() to null }
        val job = BackendJson.job(job("completed")).copy(id = "bbbbbbbb-2222-3333-4444-555555555555",
            assets = listOf(RemoteAsset("https://example.invalid/expired", null, null, "2000-01-01T00:00:00Z")))
        try { media.load(user, job); fail("Expected expiry") } catch (failure: BackendFailure) { assertEquals(410, failure.status) }
        val invalid = RemotePhotoMedia(context) { "<html>error</html>".toByteArray() to "image/png" }
        try { invalid.load(user, job.copy(assets = listOf(RemoteAsset("https://example.invalid/html", null, null, null)))); fail("Expected invalid bytes") }
        catch (failure: BackendFailure) { assertEquals("invalid_image", failure.code) }
    }
    private fun runFlow(response: () -> BackendResponse, expected: PhotoPhase, reference: Boolean = false) {
        context.getSharedPreferences("backend_source_v1", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "backend_photo/" + user + ".json").delete()
        val posts = AtomicInteger(); val polls = AtomicInteger(); val uploads = AtomicInteger()
        val completeAllowed = AtomicBoolean(false)
        val store = object : BackendAuthStore {
            override fun deviceId() = "test"
            override fun load() = AuthSession(user, "test", "access", "refresh", System.currentTimeMillis() + 600000, Long.MAX_VALUE)
            override fun save(session: AuthSession) = Unit
        }
        val modelJson = """{"models":[{"id":"creator","title":"Creator","kind":"image","credits":3,"maxInputImages":2,"resolutionCredits":{"1K":3,"2K":7},"modes":[{"mode":"textToImage","params":["prompt","resolution"],"requiredParams":["prompt"],"resolutions":["1K","2K"],"durations":[],"defaults":{"resolution":"2K"}},{"mode":"imageToImage","params":["prompt","resolution"],"requiredParams":["prompt","imageUrls"],"resolutions":["1K","2K"],"durations":[],"defaults":{"resolution":"2K"}}]}]}"""
        val transport = BackendTransport { method, path, _, body ->
            when {
                path == "/v1/media/uploads" -> {
                    uploads.incrementAndGet(); assertEquals("image/jpeg", JSONObject(body!!).getString("mediaType"))
                    BackendResponse(201, """{"url":"https://example.invalid/ref","mediaType":"image/jpeg","size":100,"expiresAt":null}""")
                }
                method == "POST" -> { posts.incrementAndGet(); assertEquals("/v1/media/images", path)
                    assertEquals(if (reference) "imageToImage" else "textToImage", JSONObject(body!!).getString("mode")); response() }
                path.startsWith("/v1/media/jobs/") -> { polls.incrementAndGet(); BackendResponse(200, job(if (completeAllowed.get()) "completed" else "running").toString()) }
                path == BackendSection.MODELS.path -> BackendResponse(200, modelJson)
                path == BackendSection.PHOTOS.path || path == BackendSection.VIDEOS.path -> BackendResponse(200, """{"templates":[]}""")
                path == BackendSection.JOBS.path -> BackendResponse(200, """{"jobs":[],"nextCursor":null}""")
                else -> BackendResponse(503, "{}")
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var backend: BackendController; lateinit var photos: PhotoGenerationController
        var created = false
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun waitFor(test: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 10000
            while (!test() && System.currentTimeMillis() < deadline) Thread.sleep(40)
            assertTrue(test())
        }
        main { backend = BackendController(context, transport, store); backend.connect() }
        try {
            waitFor { BackendSection.MODELS in backend.state.value!!.loaded && backend.state.value!!.loading.isEmpty() }
            main {
                photos = PhotoGenerationController(context, backend, pollMillis = 50); photos.activate(user)
                created = true
                photos.edit { PhotoRequest.defaults(it.copy(prompt = "A mountain"), backend.state.value!!.data.models.single()) }
            }
            if (reference) {
                val file = File(context.cacheDir, "b2-import.png").apply { writeBytes(bytes()) }
                main { photos.importPhoto(android.net.Uri.fromFile(file)) }
                waitFor { photos.state.value!!.draft.photos.size == 1 && !photos.state.value!!.importing }
                file.delete()
            }
            main { photos.submit(); photos.submit() }
            if (expected == PhotoPhase.COMPLETE) {
                waitFor { photos.state.value!!.phase == PhotoPhase.ACTIVE }
                main { photos.close(); photos = PhotoGenerationController(context, backend, pollMillis = 50); photos.activate(user) }
                assertEquals(PhotoPhase.ACTIVE, photos.state.value!!.phase); assertEquals(id, photos.state.value!!.submissionJobId)
                assertEquals(1, posts.get()); completeAllowed.set(true)
            }
            waitFor { photos.state.value!!.phase == expected }
            assertEquals(1, posts.get()); assertEquals(if (reference) 1 else 0, uploads.get())
            if (expected == PhotoPhase.COMPLETE) {
                assertTrue(polls.get() > 0); assertEquals("completed", photos.state.value!!.job!!.status)
                main { photos.close(); photos = PhotoGenerationController(context, backend, pollMillis = 50); photos.activate(user) }
                assertEquals(id, photos.state.value!!.job!!.id); assertEquals(1, posts.get())
            } else if (expected == PhotoPhase.UNKNOWN) {
                main { photos.close(); photos = PhotoGenerationController(context, backend, pollMillis = 50); photos.activate(user); photos.submit() }
                assertEquals(PhotoPhase.UNKNOWN, photos.state.value!!.phase); assertEquals(1, posts.get())
            } else assertEquals(0, polls.get())
        } finally {
            main { if (created) photos.close(); backend.useDemo(); backend.close() }
            context.getSharedPreferences("backend_source_v1", Context.MODE_PRIVATE).edit().clear().commit()
            File(context.filesDir, "backend_photo/" + user + ".json").delete()
        }
    }
    @Test fun textGenerationDoubleTapPollAndRestartNeverDuplicatePost() =
        runFlow({ BackendResponse(202, job("queued").toString()) }, PhotoPhase.COMPLETE)
    @Test fun referenceIsNormalizedUploadedThenSubmittedOnce() =
        runFlow({ BackendResponse(202, job("queued").toString()) }, PhotoPhase.COMPLETE, reference = true)
    @Test fun lostResponseStaysUnknownAfterRestart() =
        runFlow({ throw BackendFailure() }, PhotoPhase.UNKNOWN)
    @Test fun insufficientCreditsIsRejectedAndDoesNotPoll() =
        runFlow({ BackendResponse(409, """{"error":{"code":"insufficient_credits"}}""") }, PhotoPhase.REJECTED)
}
