package com.rslnabk.aivideotest

import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoMatrixTest : ComposeFlowTest() {
    private fun create(kind: MediaKind, effect: Boolean) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.model.setAccount(DemoAccount(100)) }
            val key = if (effect) "${kind.name.lowercase()}_gold" else "prompt_${kind.name.lowercase()}"
            if (kind == MediaKind.PHOTO) tag("tab_photo").performClick()
            if (effect) {
                tag("effect_$key").performClick(); tag("use_effect").performClick()
                text(R.string.continue_action).performScrollTo().performClick()
                text(R.string.sample_photo).performClick(); tag("sample_0").performClick()
                await(scenario) { it.draft(key).photoStatus == PhotoStatus.READY }
            } else {
                text(R.string.prompt).performClick()
                tag("prompt_input").performTextReplacement("Закат 👋")
                if (kind == MediaKind.VIDEO) ui.onNodeWithText("1080").performScrollTo().performClick()
                else {
                    tag("styles").performScrollTo().performScrollToIndex(PhotoStyle.FANTASY.ordinal)
                    ui.onNodeWithContentDescription(context.getString(R.string.style_fantasy)).assertIsDisplayed().performClick().assertIsSelected()
                }
            }
            var jobId = ""
            tag("generate").performScrollTo().performClick()
            await(scenario) { it.snapshot.value!!.jobs.size == 1 }
            scenario.onActivity {
                val snapshot = it.model.snapshot.value!!
                val job = snapshot.jobs.single(); jobId = job.id
                assertEquals(key, job.draft.key)
                assertEquals(kind, job.draft.kind)
                assertEquals(100 - job.tokenCost, snapshot.account.tokens)
                if (!effect && kind == MediaKind.VIDEO) assertEquals(1080, job.draft.resolution)
                if (!effect && kind == MediaKind.PHOTO) assertEquals(PhotoStyle.FANTASY, job.draft.style)
            }
            scenario.recreate()
            await(scenario) { it.snapshot.value!!.jobs.single().status == JobStatus.SUCCEEDED }
            tag("share").assertIsDisplayed()
            if (kind == MediaKind.VIDEO) tag("video").assertIsDisplayed()
            back(scenario)
            if (effect) tag("photo_card").assertExists() else tag("prompt_input").assertTextContains("Закат 👋")
            scenario.onActivity { it.openLibrary(kind) }
            tag("job_$jobId").performClick(); tag("share").assertIsDisplayed()
            scenario.onActivity { assertEquals(1, it.model.snapshot.value!!.jobs.size) }
        }
    }

    @Test fun videoPromptHighResolutionToResultAndHistory() = create(MediaKind.VIDEO, false)
    @Test fun photoPromptStyleToResultAndHistory() = create(MediaKind.PHOTO, false)
    @Test fun videoEffectReferenceToResultAndHistory() = create(MediaKind.VIDEO, true)
    @Test fun photoEffectReferenceToResultAndHistory() = create(MediaKind.PHOTO, true)

    private fun video(view: View): VideoView? = when (view) {
        is VideoView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { video(view.getChildAt(it)) }
        else -> null
    }

    @Test fun videoPausesInBackgroundAndCanResumeAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.model.setAccount(DemoAccount(100))
                it.model.editDraft("prompt_video") { draft -> draft.copy(prompt = "Playback") }
                it.generate("prompt_video")
            }
            await(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.SUCCEEDED }
            fun playing(): Boolean {
                var result = false
                scenario.onActivity { result = video(it.window.decorView)?.isPlaying == true }
                return result
            }
            ui.waitUntil(8000) { playing() }
            tag("video_poster").assertDoesNotExist()
            tag("result_image").assertDoesNotExist()
            tag("playback").performClick().assertTextEquals("Play")
            assertFalse(playing())
            scenario.recreate(); tag("playback").assertTextEquals("Play")
            tag("playback").performClick()
            ui.waitUntil(8000) { playing() }
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(playing())
            scenario.moveToState(Lifecycle.State.RESUMED)
            ui.waitUntil(8000) { playing() }
            tag("video_poster").assertDoesNotExist()
        }
    }
}
