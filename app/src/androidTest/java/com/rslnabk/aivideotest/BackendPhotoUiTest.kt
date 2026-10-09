package com.rslnabk.aivideotest

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.data.media.ExportController
import com.rslnabk.aivideotest.ui.backend.RemotePhotoEditor
import com.rslnabk.aivideotest.ui.backend.RemotePhotoResult
import com.rslnabk.aivideotest.ui.theme.AiVideoTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class BackendPhotoUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val user = "55555555-2222-3333-4444-555555555555"
    private val id = "cccccccc-2222-3333-4444-555555555555"
    @Before fun demoSource() {
        compose.activity.getSharedPreferences("backend_source_v1", 0).edit().clear().commit()
    }
    private fun flow(lost: Boolean, restricted: Boolean = false, recover: Boolean = false, ambiguous: Boolean = false) {
        val context = compose.activity.applicationContext
        File(context.filesDir, "backend_photo/" + user + ".json").delete()
        val posts = AtomicInteger()
        val details = AtomicInteger()
        val second = "dddddddd-2222-3333-4444-555555555555"
        val response = """{"jobId":"$id","kind":"image","prompt":"A blue mountain","model":"creator","status":"completed","creditsCharged":3,"creditsRefunded":false,"progress":1,"assets":[{"url":"https://example.invalid/ui-result","contentType":null}]}"""
        fun candidate(jobId: String) = JSONObject(response).put("jobId", jobId).put("mode", "textToImage")
            .put("parameters", JSONObject().put("resolution", "1K")).put("inputImageUrls", org.json.JSONArray())
            .put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()))
        val store = object : BackendAuthStore {
            override fun deviceId() = "test"
            override fun load() = AuthSession(user, "test", "access", "refresh", System.currentTimeMillis() + 600000, Long.MAX_VALUE)
            override fun save(session: AuthSession) = Unit
        }
        val transport = BackendTransport { method, path, _, _ ->
            if (method == "POST") { posts.incrementAndGet(); if (lost) throw BackendFailure(); BackendResponse(202, response) }
            else when (path) {
                "/v1/media/jobs?kind=image&limit=20" -> BackendResponse(200, JSONObject().put("jobs", org.json.JSONArray().apply {
                    if (recover) { put(candidate(id)); if (ambiguous) put(candidate(second)) }
                }).toString())
                "/v1/media/jobs/$id", "/v1/media/jobs/$second" -> { details.incrementAndGet(); BackendResponse(200, candidate(path.substringAfterLast('/')).toString()) }
                BackendSection.MODELS.path -> BackendResponse(200, """{"models":[{"id":"creator","title":"Creator","kind":"image","credits":3,"maxInputImages":1,"resolutionCredits":{"1K":3},"modes":[{"mode":"textToImage","params":["prompt","resolution"],"requiredParams":["prompt"],"resolutions":["1K"],"durations":[],"defaults":{"resolution":"1K"}}]}]}""")
                BackendSection.PHOTOS.path, BackendSection.VIDEOS.path -> BackendResponse(200, """{"templates":[]}""")
                BackendSection.JOBS.path -> BackendResponse(200, """{"jobs":[],"nextCursor":null}""")
                BackendSection.POLICY.path -> if (restricted) BackendResponse(200, """{"isSubscribed":false,"creditsBalance":0,"trialRemaining":0,"canGenerateCreditsMode":false}""")
                    else BackendResponse(200, """{"isSubscribed":true,"creditsBalance":100,"trialRemaining":0,"canGenerateCreditsMode":true}""")
                else -> BackendResponse(503, "{}")
            }
        }
        lateinit var backend: BackendController; lateinit var controller: PhotoGenerationController; lateinit var exports: ExportController
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
        compose.runOnUiThread {
            backend = BackendController(context, transport, store); backend.connect()
            controller = PhotoGenerationController(context, backend, RemotePhotoMedia(context) { bytes to "image/png" })
            exports = ExportController(context, "backend_ui_export_test")
        }
        try {
            compose.waitUntil(10000) { BackendSection.MODELS in backend.state.value!!.loaded && backend.state.value!!.loading.isEmpty() }
            compose.runOnUiThread { controller.activate(user) }
            compose.runOnUiThread { compose.activity.setContent {
                val catalog by backend.state.observeAsState(BackendState())
                val photo by controller.state.observeAsState(PhotoState())
                AiVideoTheme {
                    if (photo.job == null) Column(Modifier.verticalScroll(rememberScrollState())) {
                        RemotePhotoEditor(catalog, photo, controller, compose.activity) {}
                    } else RemotePhotoResult(photo, controller, exports, compose.activity)
                }
            } }
            compose.onNodeWithTag("remote_prompt").performTextInput("A blue mountain")
            if (restricted) {
                compose.onNodeWithTag("remote_generate").performScrollTo().assertIsNotEnabled()
                assertEquals(0, posts.get())
                return
            }
            compose.onNodeWithTag("remote_generate").performScrollTo().assertIsEnabled().performClick()
            if (lost) {
                compose.waitUntil(10000) { controller.state.value!!.phase == PhotoPhase.UNKNOWN }
                compose.onNodeWithTag("remote_generate").assertIsNotEnabled()
                compose.onNodeWithTag("remote_unknown").performScrollTo().assertIsDisplayed()
                if (recover) {
                    compose.waitUntil(10000) { !controller.state.value!!.recovery.loading && controller.state.value!!.recovery.candidates.size == if (ambiguous) 2 else 1 }
                    assertEquals(PhotoPhase.UNKNOWN, controller.state.value!!.phase); assertEquals(0, details.get())
                    val choice = if (ambiguous) second else id
                    compose.onNodeWithTag("remote_recovery_" + choice).performScrollTo().performClick()
                    compose.onNodeWithText("Cancel").performClick()
                    assertEquals(PhotoPhase.UNKNOWN, controller.state.value!!.phase); assertEquals(0, details.get())
                    compose.onNodeWithTag("remote_recovery_" + choice).performClick()
                    compose.onNodeWithTag("remote_recovery_confirm").performClick()
                    compose.waitUntil(10000) { controller.state.value!!.local != null }
                    assertEquals(choice, controller.state.value!!.submissionJobId); assertEquals(1, details.get())
                    compose.onNodeWithTag("remote_options").performClick()
                    compose.onNodeWithTag("remote_save_gallery").assertIsEnabled()
                } else {
                    compose.waitUntil(10000) { controller.state.value!!.recovery.searched && !controller.state.value!!.recovery.loading }
                    compose.onNodeWithTag("remote_recovery_empty").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("remote_generate").performScrollTo().assertIsNotEnabled()
                }
            } else {
                compose.waitUntil(10000) { controller.state.value!!.local != null }
                compose.onNodeWithTag("remote_job_status").assertTextEquals("Ready")
                compose.onNodeWithTag("remote_options").performClick()
                compose.onNodeWithTag("remote_save_gallery").assertIsEnabled()
            }
            assertEquals(1, posts.get())
        } finally {
            compose.runOnUiThread { controller.close(); exports.close(); backend.useDemo(); backend.close() }
            context.getSharedPreferences("backend_source_v1", 0).edit().clear().commit()
            File(context.filesDir, "backend_photo/" + user + ".json").delete()
            File(context.cacheDir, "backend_results").listFiles()?.filter { it.name.startsWith(user) }?.forEach(File::delete)
        }
    }
    @Test fun promptToDownloadedResultEnablesSave() = flow(false)
    @Test fun lostAnswerShowsWarningAndCannotSubmitAgain() = flow(true)
    @Test fun serverDenialDisablesGenerationWithoutSendingAnything() = flow(false, restricted = true)
    @Test fun evenOneMatchingJobNeedsConfirmationAndResumesWithoutPost() = flow(true, recover = true)
    @Test fun identicalJobsRemainUnknownUntilUserChoosesOne() = flow(true, recover = true, ambiguous = true)
}
