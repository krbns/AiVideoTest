package com.rslnabk.aivideotest

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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackendVideoUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun show(f: VideoFixture, exports: ExportController) = compose.runOnUiThread {
        compose.activity.setContent {
            val catalog by f.backend.state.observeAsState(BackendState())
            val state by f.controller.state.observeAsState(PhotoState(draft = PhotoDraft(kind = "video")))
            AiVideoTheme {
                if (state.job == null) Column(Modifier.verticalScroll(rememberScrollState())) {
                    RemotePhotoEditor(catalog, state, f.controller, compose.activity) {}
                } else RemotePhotoResult(state, f.controller, exports, compose.activity)
            }
        }
    }
    private fun fixture(test: (VideoFixture) -> Unit) {
        val f = VideoFixture(compose.activity.applicationContext); f.start()
        lateinit var exports: ExportController
        compose.runOnUiThread { exports = ExportController(compose.activity.applicationContext, "b3_ui_exports") }
        try { show(f, exports); test(f) } finally { compose.runOnUiThread { exports.close() }; f.close() }
    }
    @Test fun videoDurationAudioAndPriceComeFromCatalogThenOneGenerate() = fixture { f ->
        compose.onNodeWithText("Model price: 7 credits").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("6s").performScrollTo().performClick()
        compose.onNodeWithTag("remote_audio").performScrollTo().performClick()
        compose.onNodeWithText("1080p").performScrollTo().performClick()
        compose.onNodeWithText("Model price: 42 credits").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("remote_generate").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10000) { f.controller.state.value!!.phase == PhotoPhase.ACTIVE }
        assertEquals(1, f.posts.get()); assertEquals("6s", f.lastRequest!!.getString("duration")); assertTrue(f.lastRequest!!.getBoolean("generateAudio"))
    }
    @Test fun effectHasRequiredPhotoCountAndDoesNotExposeHiddenPrompt() = fixture { f ->
        compose.runOnUiThread { assertTrue(f.controller.configureTemplate(f.backend.state.value!!.data.videos.single())) }
        compose.onNodeWithTag("remote_prompt").assertDoesNotExist()
        compose.onNodeWithText("Two step effect").assertIsDisplayed()
        compose.onNodeWithTag("remote_generate").performScrollTo().assertIsNotEnabled()
        assertEquals(0, f.posts.get())
    }
    @Test fun cancelAndDeleteRequireConfirmationAndPlaybackCanPause() = fixture { f ->
        compose.onNodeWithTag("remote_generate").performScrollTo().performClick()
        compose.waitUntil(10000) { f.controller.state.value!!.phase == PhotoPhase.ACTIVE }
        compose.onNodeWithTag("remote_cancel").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick(); assertEquals(0, f.cancels.get())
        f.status = "completed"
        compose.waitUntil(10000) { f.controller.state.value!!.local != null }
        compose.onNodeWithTag("remote_video").performScrollTo()
        compose.waitUntil(10000) { runCatching { compose.onNodeWithTag("remote_playback").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("remote_playback").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("remote_playback").assertTextContains("Play")
        compose.onNodeWithTag("remote_delete").performScrollTo().performClick()
        assertEquals(0, f.deletes.get())
        compose.onNodeWithTag("remote_action_confirm").performClick()
        compose.waitUntil(10000) { f.deletes.get() == 1 && f.controller.state.value!!.job == null }
        assertEquals(1, f.deletes.get()); assertEquals(1, f.posts.get())
    }
}
