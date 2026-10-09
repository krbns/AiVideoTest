package com.rslnabk.aivideotest

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.rslnabk.aivideotest.data.backend.PhotoDraft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File
import java.util.UUID

/** Real ActivityResult bridge with controlled OS results; no personal photo or upload is used. */
class BackendMediaBridgeTest {
    @get:Rule(order = 0) val cleanDemo = object : ExternalResource() {
        override fun before() {
            // The launch screen allows the bridge fixture to replace content before catalog decoding.
            context.getSharedPreferences("demo_state_v1", 0).edit().clear().commit()
        }
    }
    @get:Rule(order = 1) val activity = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun waitFor(predicate: () -> Boolean) {
        val until = System.currentTimeMillis() + 10000
        while (!predicate() && System.currentTimeMillis() < until) Thread.sleep(25)
        assertTrue("Platform media result was not imported", predicate())
    }
    // Pre-26 ActivityMonitor cannot expose EXTRA_OUTPUT; inspect the platform bridge pending path.
    private fun captureFile(host: MainActivity) = File(MainActivity::class.java.getDeclaredField("cameraFile")
        .apply { isAccessible = true }.get(host) as String)
    private fun jpeg(file: File) {
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        try { bitmap.eraseColor(android.graphics.Color.CYAN); file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
        finally { bitmap.recycle() }
    }
    private fun fixture(block: (MainActivity) -> Unit) {
        val user = UUID.randomUUID().toString(); lateinit var host: MainActivity
        activity.scenario.onActivity {
            host = it; it.model.backend.useDemo()
            // This test exercises ActivityResult, not the bitmap-heavy demo catalog.
            it.setContent {}
            it.model.backendPhotos.activate(user); it.model.backendVideos.activate(user)
            it.model.backendPhotos.edit { PhotoDraft() }; it.model.backendVideos.edit { PhotoDraft(kind = "video") }
        }
        try { block(host) } finally {
            listOf(host.model.backendPhotos, host.model.backendVideos).flatMap { it.state.value!!.draft.photos }.forEach {
                File(context.filesDir, "backend_reference_photos/" + it.removePrefix("file:")).delete()
            }
            File(context.filesDir, "backend_photo/$user.json").delete(); File(context.filesDir, "backend_video/$user.json").delete()
        }
    }
    @Test fun galleryResultRoutesToVideoAndNormalizedCopySurvivesRecreation() = fixture { host ->
        val input = File(File(context.cacheDir, "exports").apply { mkdirs() }, "picker-${UUID.randomUUID()}.jpg"); jpeg(input)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", input)
        val action = ActivityResultContracts.PickVisualMedia().createIntent(context,
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)).action!!
        val monitor = Instrumentation.ActivityMonitor(IntentFilter(action).apply { addDataType("image/*") }, Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
        instrumentation.addMonitor(monitor)
        try {
            activity.scenario.onActivity { it.pickBackendPhoto("video") }
            waitFor { host.model.backendVideos.state.value!!.draft.photos.size == 1 }
            assertEquals(1, monitor.hits); assertTrue(host.model.backendPhotos.state.value!!.draft.photos.isEmpty())
            val reference = host.model.backendVideos.state.value!!.draft.photos.single()
            input.delete(); activity.scenario.recreate()
            activity.scenario.onActivity { it.setContent {}; assertEquals(reference, it.model.backendVideos.state.value!!.draft.photos.single()) }
            val file = File(context.filesDir, "backend_reference_photos/" + reference.removePrefix("file:"))
            assertTrue(file.exists()); assertEquals(0xff, file.readBytes()[0].toInt() and 0xff)
        } finally { instrumentation.removeMonitor(monitor); input.delete() }
    }
    @Test fun cameraFullSizeResultIsImportedThroughFileProvider() = fixture { host ->
        val folder = File(context.cacheDir, "camera")
        val monitor = Instrumentation.ActivityMonitor(IntentFilter(MediaStore.ACTION_IMAGE_CAPTURE), Instrumentation.ActivityResult(Activity.RESULT_OK, null), true)
        instrumentation.addMonitor(monitor)
        var captured: File? = null
        try {
            activity.scenario.onActivity {
                it.takeBackendPhoto()
                captured = captureFile(it)
                jpeg(captured!!)
            }
            waitFor { host.model.backendPhotos.state.value!!.draft.photos.size == 1 }
            assertEquals(1, monitor.hits); assertTrue(host.model.backendVideos.state.value!!.draft.photos.isEmpty())
            assertNull(host.model.backendPhotos.state.value!!.failure)
        } finally { instrumentation.removeMonitor(monitor); captured?.delete() }
    }
    @Test fun cancelledPickerAndCameraLeaveDraftEmptyAndRemoveTemporaryCapture() = fixture { host ->
        val action = ActivityResultContracts.PickVisualMedia().createIntent(context,
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)).action!!
        val picker = Instrumentation.ActivityMonitor(IntentFilter(action).apply { addDataType("image/*") }, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        val camera = Instrumentation.ActivityMonitor(IntentFilter(MediaStore.ACTION_IMAGE_CAPTURE), Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        val folder = File(context.cacheDir, "camera"); val before = folder.listFiles().orEmpty().toSet()
        instrumentation.addMonitor(picker); instrumentation.addMonitor(camera)
        try {
            activity.scenario.onActivity { it.pickBackendPhoto() }; instrumentation.waitForIdleSync()
            activity.scenario.onActivity {
                it.takeBackendPhoto("video")
                captureFile(it).writeBytes(byteArrayOf(1))
            }
            waitFor { folder.listFiles().orEmpty().toSet() == before }
            assertEquals(1, picker.hits); assertEquals(1, camera.hits)
            assertTrue(host.model.backendPhotos.state.value!!.draft.photos.isEmpty()); assertTrue(host.model.backendVideos.state.value!!.draft.photos.isEmpty())
        } finally { instrumentation.removeMonitor(picker); instrumentation.removeMonitor(camera) }
    }
}
