package com.rslnabk.aivideotest

import android.content.Context
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.data.demo.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreferencesFlowTest : ComposeFlowTest() {
    private fun setting(key: String, node: String = key): SemanticsNodeInteraction {
        tag("settings_list").performScrollToKey(key)
        return tag(node)
    }
    @Test fun completedIntroPendingOfferRecoversOnceAfterColdLaunch() {
        val prefs = context.getSharedPreferences("demo_state_v1", Context.MODE_PRIVATE)
        PreferencesDemoStore(prefs).save(DemoSnapshot(preferences = DemoPreferences(introStep = IntroStep.DONE, introOfferPending = true)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ui.waitUntil(12000) { ui.onAllNodesWithTag("offer_pro").fetchSemanticsNodes().isNotEmpty() }
            tag("offer_pro").assertIsDisplayed(); scenario.recreate(); tag("offer_pro").assertIsDisplayed()
            scenario.onActivity { assertFalse(it.model.snapshot.value!!.preferences.introOfferPending) }
            tag("offer_close").performClick()
        }
        ActivityScenario.launch(MainActivity::class.java).use { tag("tab_video").assertIsDisplayed(); tag("offer_pro").assertDoesNotExist() }
    }
    @Test fun introResumeBackSampleFinishOnceAndSkipOnNextLaunch() {
        context.getSharedPreferences("demo_state_v1", Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ui.waitUntil(12000) { ui.onAllNodesWithTag("intro_welcome").fetchSemanticsNodes().isNotEmpty() }
            tag("intro_welcome").assertIsDisplayed(); tag("intro_next").performClick()
            scenario.recreate(); tag("intro_prompt").assertIsDisplayed()
            tag("intro_back").performClick(); tag("intro_welcome").assertIsDisplayed(); tag("intro_next").performClick()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ui.waitUntil(12000) { ui.onAllNodesWithTag("intro_prompt").fetchSemanticsNodes().isNotEmpty() }
            tag("intro_prompt").assertIsDisplayed(); tag("intro_next").performClick(); tag("intro_share").assertIsDisplayed()
            tag("intro_next").performClick(); tag("intro_reviews").assertIsDisplayed(); tag("intro_next").performClick()
            scenario.recreate(); tag("intro_sample").performClick(); tag("intro_notifications").assertIsDisplayed()
            tag("intro_not_now").performClick(); tag("offer_pro").assertIsDisplayed()
            scenario.onActivity { it.finishIntro(); assertEquals(IntroStep.DONE, it.model.snapshot.value!!.preferences.introStep); assertEquals(IntroPhotoChoice.SAMPLE, it.model.snapshot.value!!.preferences.introPhotoChoice) }
            tag("offer_close").performClick(); tag("tab_photo").performClick(); text(R.string.prompt).performClick()
            await(scenario) { it.draft("prompt_photo").photoStatus == PhotoStatus.READY }
            scenario.onActivity { assertEquals("asset:good1", it.model.draft("prompt_photo").photo) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { tag("tab_video").assertIsDisplayed(); tag("intro_welcome").assertDoesNotExist() }
    }
    @Test fun reviewDraftBackRotationUnicodeValidationAndLocalSave() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("tab_settings").performClick(); setting("rate", "rate_us").performClick()
            tag("review_save").performScrollTo().assertIsNotEnabled()
            tag("review_star_4").performScrollTo().performClick(); tag("review_name").performTextReplacement("Имя")
            tag("review_text").performTextReplacement("Нравится")
            tag("back").performClick(); setting("rate", "rate_us").performClick()
            tag("review_name").assertTextContains("Имя"); scenario.recreate(); tag("review_star_4").assertIsSelected()
            tag("review_text").performTextReplacement("😀".repeat(1001)); tag("review_save").performScrollTo().assertIsNotEnabled()
            tag("review_text").performScrollTo().performTextReplacement("Отлично"); tag("review_save").performScrollTo().performClick()
            text(R.string.feedback_saved).assertIsDisplayed(); text(R.string.okay).performClick()
            scenario.onActivity { assertEquals(4, it.model.snapshot.value!!.preferences.review!!.rating); assertEquals("Отлично", it.model.snapshot.value!!.preferences.review!!.text); assertFalse(it.model.saveReview()) }
        }
    }
    @Test fun messageDraftSurvivesBackAndSavesWithoutSending() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("tab_settings").performClick(); setting("report", "report_problem").performClick()
            tag("message_save").assertIsNotEnabled(); tag("message_input").performTextReplacement("Ошибка после выбора фото")
            tag("back").performClick(); setting("report", "report_problem").performClick()
            tag("message_input").assertTextContains("Ошибка после выбора фото"); scenario.recreate()
            tag("message_save").performScrollTo().performClick(); text(R.string.okay).performClick()
            setting("letter", "letter").performClick(); tag("message_input").performTextReplacement("Привет команде")
            tag("message_save").performScrollTo().performClick(); text(R.string.okay).performClick()
            scenario.onActivity { assertEquals(listOf(MessageKind.REPORT, MessageKind.LETTER), it.model.snapshot.value!!.preferences.messages.map { m -> m.kind }); assertTrue(it.model.snapshot.value!!.jobs.isEmpty()) }
        }
    }
    @Test fun subscriptionCardsClearCancelFailureRetryAndLegalKeepData() {
        val fixture = DemoSession(PreferencesDemoStore(context.getSharedPreferences("demo_state_v1", Context.MODE_PRIVATE)), DemoCatalogRepository())
        fixture.setAccount(DemoAccount(100)); fixture.updateDraft("prompt_photo") { it.copy(prompt = "Keep history", photo = "asset:good1", photoStatus = PhotoStatus.READY) }
        fixture.submit("prompt_photo", "keep-history", 0); fixture.reconcile(4000); fixture.setAccount(DemoAccount(5))
        val preview = File(context.cacheDir, "previews/p6-test").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(2048)) }
        val export = File(context.cacheDir, "exports/p6-protected").apply { parentFile!!.mkdirs(); writeText("keep") }
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("tab_settings").performClick(); tag("subscription_low_balance").assertIsDisplayed()
            scenario.onActivity { it.model.setAccount(DemoAccount(10)) }; tag("subscription_free").assertIsDisplayed()
            scenario.onActivity { it.model.setAccount(DemoAccount(10, true)); it.model.toggleFavorite("photo_gold") }
            tag("subscription_pro").assertIsDisplayed(); tag("card_more").assertDoesNotExist()
            setting("cache", "clear_cache").performClick(); text(R.string.cancel).performClick(); assertTrue(preview.exists())
            scenario.onActivity { it.model.failNextCache = true }
            setting("cache", "clear_cache").performClick(); text(R.string.clear_action).performClick()
            await(scenario) { it.cacheResult.value == false }; text(R.string.retry).performClick()
            await(scenario) { it.cacheResult.value == true }; text(R.string.okay).performClick()
            assertFalse(preview.exists()); assertEquals("keep", export.readText())
            scenario.onActivity { assertEquals(DemoAccount(10, true), it.model.snapshot.value!!.account); assertEquals(setOf("photo_gold"), it.model.snapshot.value!!.favorites); assertEquals("keep-history", it.model.snapshot.value!!.jobs.single().id); assertEquals("asset:good1", it.model.draft("prompt_photo").photo) }
            setting("privacy", "settings_privacy").performClick(); text(R.string.demo_legal_body).assertIsDisplayed(); text(R.string.okay).performClick()
            setting("terms", "settings_terms").performClick(); text(R.string.demo_legal_body).assertIsDisplayed(); text(R.string.okay).performClick()
            setting("version", "app_version").assertTextEquals("Version " + BuildConfig.VERSION_NAME)
        } } finally { preview.delete(); export.delete() }
    }
}
