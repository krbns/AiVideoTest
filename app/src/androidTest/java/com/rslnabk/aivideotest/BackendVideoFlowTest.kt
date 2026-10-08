package com.rslnabk.aivideotest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.data.media.ResultMedia
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Controlled transport: no test in this class sends a paid request to the live server. */
internal class VideoFixture(val context: Context) : AutoCloseable {
    val user = "66666666-2222-3333-4444-555555555555"
    val id = "eeeeeeee-2222-3333-4444-555555555555"
    val posts = AtomicInteger(); val cancels = AtomicInteger(); val deletes = AtomicInteger(); val gets = AtomicInteger()
    @Volatile var status = "running"
    @Volatile var lostSend = false
    @Volatile var lostCancel = false
    @Volatile var lostDelete = false
    @Volatile var deleted = false
    @Volatile var rejectCancel = false
    var lastRequest: JSONObject? = null
    val models = """{"models":[{"id":"video","title":"Video creator","kind":"video","credits":7,"baseDurationSeconds":4,"resolutionMultipliers":{"720p":1,"1080p":2},"audioMultiplier":1.5,"supportsAudio":true,"maxInputImages":1,"modes":[{"mode":"textToVideo","params":["prompt","resolution","duration","generateAudio"],"requiredParams":["prompt"],"resolutions":["720p","1080p"],"durations":["4s","6s","8s"],"defaults":{"resolution":"720p","duration":"4s","generateAudio":false}},{"mode":"imageToVideo","params":["prompt","resolution","duration","generateAudio"],"requiredParams":["prompt","imageUrl"],"resolutions":["720p","1080p"],"durations":["4s","8s"],"defaults":{"resolution":"720p","duration":"4s","generateAudio":false}}]},{"id":"photo","title":"Photo creator","kind":"image","credits":2,"maxInputImages":2,"modes":[{"mode":"imageToImage","params":["prompt"],"requiredParams":["prompt","imageUrls"]}]}]}"""
    val templateJson = """{"id":"chain","title":"Two step effect","coverMediaType":"image/png","model":"video","mode":"imageToVideo","tokens":19,"requiredInputImages":2,"pipeline":"image_video","steps":[{"index":1,"kind":"image","model":"photo","requiredInputImages":2},{"index":2,"kind":"video","model":"video","requiredInputImages":0}]}"""
    fun job() = JSONObject().put("jobId", id).put("kind", "video").put("model", "video")
        .put("prompt", if (lastRequest?.has("templateId") == true) "" else "A mountain")
        .put("status", status).put("mode", "textToVideo").put("parameters", JSONObject().put("duration", "4s").put("resolution", "720p").put("generateAudio", false))
        .put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()))
        .put("inputImageUrls", JSONArray()).put("templateId", lastRequest?.optString("templateId")?.takeIf { it.isNotEmpty() })
        .put("pipelineStage", if (lastRequest?.has("templateId") == true && status == "running") "image" else JSONObject.NULL)
        .put("creditsCharged", 7).put("creditsRefunded", status == "failed").put("errorCode", if (status == "failed") "canceled" else JSONObject.NULL)
        .put("assets", JSONArray().apply { if (status == "completed") put(JSONObject().put("url", "https://example.invalid/video")) })
    val store = object : BackendAuthStore {
        override fun deviceId() = "test"
        override fun load() = AuthSession(user, "test", "access", "refresh", System.currentTimeMillis() + 600000, Long.MAX_VALUE)
        override fun save(session: AuthSession) = Unit
    }
    val transport = BackendTransport { method, path, _, body -> when {
        path == "/v1/media/uploads" -> BackendResponse(201, """{"url":"https://example.invalid/ref","mediaType":"image/jpeg"}""")
        method == "POST" && path == "/v1/media/videos" -> { posts.incrementAndGet(); lastRequest = JSONObject(body!!); if (lostSend) throw BackendFailure(); BackendResponse(202, job().toString()) }
        path.endsWith("/cancel") -> { cancels.incrementAndGet(); if (rejectCancel) BackendResponse(409, "{}") else {
            status = "failed"; if (lostCancel) throw BackendFailure(); BackendResponse(200, job().toString()) } }
        method == "DELETE" -> { deletes.incrementAndGet(); deleted = true; if (lostDelete) throw BackendFailure(); BackendResponse(200, """{"deleted":true}""") }
        path == "/v1/media/jobs/$id" -> { gets.incrementAndGet(); if (deleted) BackendResponse(404, "{}") else BackendResponse(200, job().toString()) }
        path == "/v1/media/jobs?kind=video&limit=20" -> BackendResponse(200, JSONObject().put("jobs", JSONArray().put(job())).toString())
        path == BackendSection.MODELS.path -> BackendResponse(200, models)
        path == BackendSection.VIDEOS.path -> BackendResponse(200, "{\"templates\":[$templateJson]}")
        path == BackendSection.PHOTOS.path -> BackendResponse(200, """{"templates":[]}""")
        path == BackendSection.JOBS.path -> BackendResponse(200, """{"jobs":[],"nextCursor":null}""")
        path == BackendSection.POLICY.path -> BackendResponse(200, """{"isSubscribed":true,"creditsBalance":100,"trialRemaining":0,"canGenerateCreditsMode":true}""")
        else -> BackendResponse(503, "{}")
    } }
    lateinit var backend: BackendController
    lateinit var controller: PhotoGenerationController
    fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    fun waitFor(test: () -> Boolean) {
        val until = System.currentTimeMillis() + 10000
        while (!test() && System.currentTimeMillis() < until) Thread.sleep(40)
        assertTrue("Video state did not settle", test())
    }
    fun start() {
        context.getSharedPreferences("backend_source_v1", 0).edit().clear().commit()
        File(context.filesDir, "backend_video/$user.json").delete()
        main { backend = BackendController(context, transport, store); backend.connect() }
        waitFor { BackendSection.MODELS in backend.state.value!!.loaded && backend.state.value!!.loading.isEmpty() }
        main { controller = newController(); controller.activate(user); controller.edit { PhotoRequest.defaults(it.copy(prompt = "A mountain"), backend.state.value!!.data.models.first()) } }
    }
    fun newController() = PhotoGenerationController(context, backend, pollMillis = 50, kind = "video",
        videoMedia = RemoteVideoMedia(context) { _, file -> context.resources.openRawResource(R.raw.demo_video).use { input -> file.outputStream().use(input::copyTo) } })
    fun restart() = main { controller.close(); controller = newController(); controller.activate(user) }
    override fun close() {
        main { controller.close(); backend.useDemo(); backend.close() }
        context.getSharedPreferences("backend_source_v1", 0).edit().clear().commit()
        File(context.filesDir, "backend_video/$user.json").delete()
        File(context.cacheDir, "backend_video_results").listFiles()?.filter { it.name.startsWith(user) }?.forEach(File::delete)
    }
}
class BackendVideoFlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private inline fun fixture(test: (VideoFixture) -> Unit) { VideoFixture(context).use { it.start(); test(it) } }
    @Test fun catalogDefaultsVideoPricesAndFutureMandatoryFields() = fixture { f ->
        val model = f.backend.state.value!!.data.models.first(); val draft = f.controller.state.value!!.draft
        assertEquals("4s", draft.duration); assertEquals(false, draft.generateAudio); assertEquals(7, PhotoRequest.cost(model, draft))
        assertEquals(42, PhotoRequest.cost(model, draft.copy(duration = "6s", resolution = "1080p", generateAudio = true)))
        assertEquals(11, PhotoRequest.cost(model, draft.copy(generateAudio = true)))
        assertNull(PhotoRequest.cost(model.copy(baseDuration = null), draft))
        assertFalse(PhotoRequest.ready(model, draft.copy(duration = "5s")))
        assertFalse(PhotoRequest.ready(model.copy(modes = model.modes.map { it.copy(required = listOf("videoUrl")) }), draft))
        val imageMode = PhotoRequest.defaults(draft.copy(photos = listOf("file:x")), model)
        val body = PhotoRequest.build(model, imageMode, listOf("https://example.invalid/ref"))
        assertEquals("imageToVideo", body.getString("mode")); assertFalse(body.has("numImages")); assertFalse(body.has("outputFormat"))
    }
    @Test fun templatesNeedExactPhotosAndSendOnlyServerTemplateAndReferences() = fixture { f ->
        val data = f.backend.state.value!!.data; val template = data.videos.single()
        val draft = PhotoDraft(kind = "video", modelId = "video", templateId = template.id, photos = listOf("file:a", "file:b"))
        assertTrue(PhotoRequest.ready(data, draft)); assertEquals(19, PhotoRequest.cost(data, draft))
        val body = PhotoRequest.buildTemplate(template, data, draft, listOf("https://example.invalid/a", "https://example.invalid/b"))
        assertEquals(setOf("templateId", "imageUrls"), body.keys().asSequence().toSet())
        assertFalse(PhotoRequest.ready(data, draft.copy(photos = draft.photos.take(1))))
        assertFalse(PhotoRequest.templateSupported(template.copy(mode = "motionControl"), data))
        assertFalse(PhotoRequest.templateSupported(template.copy(model = "unknown"), data))
        assertFalse(PhotoRequest.templateSupported(template.copy(steps = listOf(RemoteTemplateStep(1, "audio", "video", 1))), data))
        val photo = template.copy(id = "photo-effect", model = "photo", mode = "imageToImage", pipeline = "image", steps = template.steps.take(1))
        val photoData = data.copy(photos = listOf(photo))
        assertTrue(PhotoRequest.ready(photoData, draft.copy(kind = "image", modelId = "photo", templateId = photo.id)))
        val video = template.copy(requiredImages = 0, mode = "textToVideo", pipeline = "video", steps = template.steps.drop(1))
        assertTrue(PhotoRequest.ready(data.copy(videos = listOf(video)), draft.copy(photos = emptyList())))
    }
    @Test fun videoRecoveryRejectsOtherKindsDurationsAndAudioButTemplateReferencesAreIncomplete() = fixture { f ->
        val draft = f.controller.state.value!!.draft
        val request = PhotoRequest.build(f.backend.state.value!!.data.models.first(), draft, emptyList())
        val submission = PhotoSubmission(System.currentTimeMillis(), request.toString(), emptySet())
        val job = BackendJson.job(f.job())
        assertTrue(PhotoRecovery.matches(job, draft, submission))
        assertFalse(PhotoRecovery.matches(job.copy(kind = "image"), draft, submission))
        assertFalse(PhotoRecovery.matches(job.copy(parameters = "{\"duration\":\"8s\"}"), draft, submission))
        assertFalse(PhotoRecovery.matches(job.copy(parameters = "{\"generateAudio\":true}"), draft, submission))
        val templateSubmission = submission.copy(body = "{\"templateId\":\"chain\",\"imageUrls\":[\"https://example.invalid/user\"]}")
        val templateJob = job.copy(prompt = "", templateId = "chain", inputImageUrls = listOf("https://example.invalid/hidden"))
        assertTrue(PhotoRecovery.matches(templateJob, draft.copy(prompt = "", templateId = "chain"), templateSubmission))
        assertTrue(PhotoRecovery.incomplete(templateJob, templateSubmission))
    }
    @Test fun doubleTapAndColdRestartPollOneVideoJobWithoutResubmitting() = fixture { f ->
        f.main { f.controller.submit(); f.controller.submit() }; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.ACTIVE }
        f.restart(); assertEquals(f.id, f.controller.state.value!!.submissionJobId); assertEquals(1, f.posts.get())
        f.status = "completed"; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.COMPLETE }
        f.main { f.controller.download() }; f.waitFor { f.controller.state.value!!.local != null }
        assertEquals("video/mp4", f.controller.state.value!!.local!!.mime); assertEquals(1, f.posts.get())
    }
    @Test fun lostVideoSendRecoversOnlyAfterExplicitHistoryChoice() = fixture { f ->
        f.lostSend = true; f.main { f.controller.submit() }; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.UNKNOWN && !f.controller.state.value!!.recovery.loading }
        f.restart(); f.main { f.controller.submit() }; assertEquals(1, f.posts.get())
        f.waitFor { f.controller.state.value!!.recovery.candidates.size == 1 }
        assertEquals(PhotoPhase.UNKNOWN, f.controller.state.value!!.phase)
        f.main { f.controller.recoverJob(f.id) }; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.ACTIVE }
        assertEquals(1, f.posts.get()); assertTrue(f.gets.get() > 0)
    }
    @Test fun imageVideoChainIsOnePostEvenWhenMainModelAcceptsOnlyOnePhoto() = fixture { f ->
        val template = f.backend.state.value!!.data.videos.single()
        // Normalized reference bytes; their content is irrelevant to controlled upload.
        val refs = listOf("b3-a.jpg", "b3-b.jpg")
        File(context.filesDir, "backend_reference_photos").mkdirs()
        refs.forEach { File(context.filesDir, "backend_reference_photos/$it").writeBytes(byteArrayOf(1)) }
        try {
            f.main { assertTrue(f.controller.configureTemplate(template)); f.controller.edit { it.copy(photos = refs.map { "file:$it" }) }; f.controller.submit() }
            f.waitFor { f.controller.state.value!!.phase == PhotoPhase.ACTIVE }
            assertEquals("image", f.controller.state.value!!.job!!.pipelineStage); assertEquals(1, f.posts.get())
            assertEquals(setOf("templateId", "imageUrls"), f.lastRequest!!.keys().asSequence().toSet())
            f.status = "completed"; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.COMPLETE }; assertEquals(1, f.posts.get())
        } finally { refs.forEach { File(context.filesDir, "backend_reference_photos/$it").delete() } }
    }
    @Test fun cancelDoubleTapUsesOneMutationAndServerRefund() = cancel(false)
    @Test fun lostCancelResponseIsReconciledWithoutRepeatingMutation() = cancel(true)
    private fun cancel(lost: Boolean) = fixture { f ->
        f.main { f.controller.submit() }; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.ACTIVE }; f.lostCancel = lost
        f.main { f.controller.cancelJob(); f.controller.cancelJob() }
        f.waitFor { !f.controller.state.value!!.actionBusy && f.controller.state.value!!.job!!.status == "failed" }
        assertTrue(f.controller.state.value!!.job!!.refunded); assertEquals(1, f.cancels.get()); assertEquals(1, f.posts.get()); assertNull(f.controller.state.value!!.actionError)
    }
    @Test fun cancelConflictRefreshesCompletedStateAndDoesNotRefundLocally() = fixture { f ->
        f.main { f.controller.submit() }; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.ACTIVE }
        f.status = "completed"; f.rejectCancel = true
        f.main { f.controller.cancelJob() }; f.waitFor { !f.controller.state.value!!.actionBusy && f.controller.state.value!!.job!!.status == "completed" }
        assertFalse(f.controller.state.value!!.job!!.refunded); assertEquals(1, f.cancels.get()); assertNotNull(f.controller.state.value!!.actionError)
    }
    @Test fun deleteTerminalJobOnceAndLostDeleteResponseReconciles404() = fixture { f ->
        f.main { f.controller.submit() }; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.ACTIVE }
        f.main { f.controller.deleteJob() }; assertEquals(0, f.deletes.get())
        f.status = "completed"; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.COMPLETE }; f.lostDelete = true
        f.main { f.controller.deleteJob(); f.controller.deleteJob() }; f.waitFor { f.controller.state.value!!.deletedJobId == f.id }
        assertEquals(1, f.deletes.get()); assertNull(f.controller.state.value!!.job); f.restart(); assertNull(f.controller.state.value!!.submissionJobId)
    }
    @Test fun reviewingNewGenerationDoesNotSendUntilExplicitSubmit() = fixture { f ->
        f.status = "failed"; f.main { f.controller.submit() }; f.waitFor { f.controller.state.value!!.phase == PhotoPhase.COMPLETE }
        f.main { assertTrue(f.controller.reviewNewGeneration()) }; assertEquals(PhotoPhase.EDITING, f.controller.state.value!!.phase); assertEquals(1, f.posts.get())
        f.main { f.controller.submit() }; f.waitFor { f.posts.get() == 2 && f.controller.state.value!!.phase == PhotoPhase.COMPLETE }
    }
    @Test fun photoAndVideoJournalsStaySeparateAndRejectWrongKind() {
        val f = VideoFixture(context); val photo = PhotoDraft(prompt = "Photo"); val video = PhotoDraft(kind = "video", prompt = "Video", duration = "4s")
        try {
            PhotoJournal(context).save(f.user, PhotoJournal.encode(photo, PhotoPhase.EDITING))
            PhotoJournal(context, "video").save(f.user, PhotoJournal.encode(video, PhotoPhase.SENDING))
            assertEquals("Photo", PhotoJournal.restore(PhotoJournal(context).load(f.user)).draft.prompt)
            assertEquals(PhotoPhase.UNKNOWN, PhotoJournal.restore(PhotoJournal(context, "video").load(f.user), "video").phase)
            assertTrue(runCatching { PhotoJournal.restore(PhotoJournal.encode(video, PhotoPhase.ACTIVE)) }.isFailure)
        } finally { File(context.filesDir, "backend_photo/${f.user}.json").delete(); File(context.filesDir, "backend_video/${f.user}.json").delete() }
    }
    @Test fun validatedVideoExportsOriginalBytesAndCachedFileSurvivesUrlExpiry() {
        val f = VideoFixture(context); f.status = "completed"; val job = BackendJson.job(f.job()); val bytes = context.resources.openRawResource(R.raw.demo_video).use { it.readBytes() }
        val count = AtomicInteger(); val media = RemoteVideoMedia(context) { _, file -> count.incrementAndGet(); file.writeBytes(bytes) }; val local = media.load(f.user, job)
        try {
            assertEquals("video/mp4", local.mime); assertTrue(local.aspectRatio!! > 0); assertArrayEquals(bytes, local.file.readBytes())
            assertEquals(local.file, media.load(f.user, job.copy(assets = job.assets.map { it.copy(expiresAt = "2000-01-01T00:00:00Z") })).file); assertEquals(1, count.get())
            val exports = ResultMedia(context); val share = exports.share(local.export(job.id)); assertArrayEquals(bytes, context.contentResolver.openInputStream(share)!!.use { it.readBytes() })
            val gallery = exports.gallery(local.export(job.id), {})
            try { assertArrayEquals(bytes, context.contentResolver.openInputStream(gallery)!!.use { it.readBytes() }) } finally { context.contentResolver.delete(gallery, null, null) }
        } finally { local.file.delete(); local.poster?.delete() }
    }
    @Test fun expiredOrNonVideoResultNeverBecomesPlayable() {
        val f = VideoFixture(context); f.status = "completed"; val job = BackendJson.job(f.job()).copy(assets = listOf(RemoteAsset("https://example.invalid/invalid", null, null, null)))
        val media = RemoteVideoMedia(context) { _, file -> file.writeText("<html>not a video</html>") }
        try { media.load(f.user, job); fail() } catch (e: BackendFailure) { assertEquals("unsupported_video", e.code) }
        try { media.load(f.user, job.copy(assets = job.assets.map { it.copy(expiresAt = "2000-01-01T00:00:00Z") })); fail() }
        catch (e: BackendFailure) { assertEquals(410, e.status) }
    }
}
