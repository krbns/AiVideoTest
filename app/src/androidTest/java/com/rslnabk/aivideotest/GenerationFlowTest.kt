package com.rslnabk.aivideotest

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.model.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenerationFlowTest : ComposeFlowTest() {
    @Test fun promptValidationDraftRotationAndPhotoResult() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("tab_photo").performClick(); text(R.string.prompt).performClick()
            tag("generate").assertIsNotEnabled()
            tag("prompt_input").performTextReplacement("a".repeat(301))
            scenario.onActivity { it.currentFocus?.clearFocus() }
            tag("generate").assertIsNotEnabled(); tag("counter").assertTextEquals("301/300")
            tag("prompt_input").performTextReplacement("Тёплый закат")
            ui.waitForIdle()
            scenario.onActivity { assertEquals("Тёплый закат", it.model.draft("prompt_photo").prompt) }
            scenario.recreate(); tag("prompt_input").assertTextContains("Тёплый закат")
            tag("generate").performScrollTo().performClick(); buyTokens(scenario)
            await(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.SUCCEEDED }
            tag("share").assertIsDisplayed()
            scenario.onActivity { assertEquals(95, it.model.snapshot.value!!.account.tokens) }
            back(scenario); tag("prompt_input").assertTextContains("Тёплый закат")
        }
    }
    @Test fun effectInstructionSamplePhotoUploadFailureRetryAndVideo() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("effect_video_gold").performClick(); tag("use_effect").performClick()
            text(R.string.continue_action).performScrollTo().performClick()
            scenario.onActivity { it.model.failNextPhoto = true }
            text(R.string.sample_photo).performClick(); tag("sample_0").performClick()
            await(scenario) { it.draft("video_gold").photoStatus == PhotoStatus.FAILED }
            tag("retry_photo").performScrollTo().performClick()
            await(scenario) { it.draft("video_gold").photoStatus == PhotoStatus.READY }
            tag("photo_card").performClick(); text(R.string.cancel).performClick()
            scenario.onActivity { assertEquals("asset:good1", it.model.draft("video_gold").photo) }
            tag("generate").performScrollTo().performClick(); buyTokens(scenario)
            await(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.SUCCEEDED }
            tag("video").assertIsDisplayed(); tag("playback").performClick().assertTextEquals("Play")
            scenario.recreate(); tag("playback").assertTextEquals("Play")
        }
    }
    @Test fun fileImportNormalizesPhotoAndSurvivesNewActivity() {
        val input = File(context.cacheDir, "photo-import-test.webp")
        context.resources.openRawResource(R.drawable.demo_good_2).use { source -> input.outputStream().use { source.copyTo(it) } }
        var reference = ""
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.model.loadPhoto("prompt_photo", Uri.fromFile(input).toString()) }
                await(scenario) { it.draft("prompt_photo").photoStatus == PhotoStatus.READY }
                scenario.onActivity { reference = it.model.draft("prompt_photo").photo!!; assertTrue(reference.startsWith("file:")); assertFalse(reference.startsWith("file:/")) }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertEquals(reference, it.model.draft("prompt_photo").photo) }
                assertTrue(File(context.filesDir, "reference_photos/${reference.removePrefix("file:")}").exists())
            }
        } finally { input.delete(); if (reference.isNotEmpty()) File(context.filesDir, "reference_photos/${reference.removePrefix("file:")}").delete() }
    }
    @Test fun leavingCreationKeepsJobAndFailureRefundsThenRetrySucceeds() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            text(R.string.prompt).performClick(); tag("prompt_input").performTextReplacement("Sunlight")
            scenario.onActivity { it.model.apply { setAccount(DemoAccount(100)); failNextGeneration = true } }
            tag("generate").performScrollTo().performClick(); tag("okay").performClick()
            tag("tab_library").assertIsDisplayed()
            await(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.FAILED }
            scenario.onActivity { assertEquals(100, it.model.snapshot.value!!.account.tokens) }
            text(R.string.read_more).performClick(); text(R.string.refresh).performClick()
            await(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.SUCCEEDED }
            tag("share").assertIsDisplayed()
            scenario.onActivity { assertEquals(90, it.model.snapshot.value!!.account.tokens) }
        }
    }
    private fun buyTokens(scenario: ActivityScenario<MainActivity>) {
        tag("pack_1").performScrollTo().performClick()
        await(scenario) { it.snapshot.value!!.commerce.operation?.phase == PurchasePhase.SUCCEEDED }
        text(R.string.purchase_resume).performClick()
    }
}
