package com.rslnabk.aivideotest

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.model.ExportDestination
import com.rslnabk.aivideotest.model.ExportPhase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Intercepts the chooser; no data is sent to a recipient. Exercises the actual Activity export bridge. */
class BackendShareTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    @Test fun videoShareKeepsSelectedJobAfterClaimAndNeverReplays() = share("video")
    @Test fun photoShareStillSelectsItsOwnBytesWhenVideoResultAlsoExists() = share("image")
    private fun share(kind: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val hits = AtomicInteger(); val captured = AtomicReference<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                @Suppress("DEPRECATION")
                captured.set(intent.getParcelableExtra(Intent.EXTRA_INTENT))
                hits.incrementAndGet()
                return Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        val video = File(context.cacheDir, "b3-share-video.mp4")
        val image = File(context.cacheDir, "b3-share-photo.png")
        context.resources.openRawResource(R.raw.demo_video).use { input -> video.outputStream().use(input::copyTo) }
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val imageId = "11111111-1111-4111-8111-111111111111"; val videoId = "22222222-2222-4222-8222-222222222222"
        lateinit var host: MainActivity
        try {
            activity.scenario.onActivity {
                host = it; it.model.backend.useDemo(); it.model.backendExports.reset()
                @Suppress("UNCHECKED_CAST")
                (it.model.backendPhotos.state as MutableLiveData<PhotoState>).value = PhotoState(job = RemoteJob(imageId, "image", "", "completed", null, null),
                    local = LocalPhoto(image, "image/png", "photo.png"))
                @Suppress("UNCHECKED_CAST")
                (it.model.backendVideos.state as MutableLiveData<PhotoState>).value = PhotoState(draft = PhotoDraft(kind = "video"),
                    job = RemoteJob(videoId, "video", "", "completed", null, null), local = LocalPhoto(video, "video/mp4", "video.mp4"))
                it.exportBackendPhoto(ExportDestination.SHARE, kind)
            }
            val until = System.currentTimeMillis() + 10000
            while (host.model.backendExports.state.value?.phase != ExportPhase.SHARE_READY && System.currentTimeMillis() < until) Thread.sleep(30)
            assertEquals(ExportPhase.SHARE_READY, host.model.backendExports.state.value?.phase)
            activity.scenario.onActivity { it.shareBackendReady(); it.shareBackendReady() }
            assertEquals(1, hits.get()); assertNull(host.model.backendExports.state.value)
            val send = captured.get(); assertNotNull("Chooser was not opened", send)
            assertEquals(Intent.ACTION_SEND, send.action); assertEquals(if (kind == "video") "video/mp4" else "image/png", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            @Suppress("DEPRECATION") val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertArrayEquals((if (kind == "video") video else image).readBytes(), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        } finally {
            instrumentation.removeMonitor(monitor); video.delete(); image.delete()
        }
    }
}
