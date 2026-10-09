package com.rslnabk.aivideotest

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.ui.backend.BackendWorkspace
import com.rslnabk.aivideotest.ui.backend.RemoteSettings
import com.rslnabk.aivideotest.ui.theme.AiVideoTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendDesignFlowTest : ComposeFlowTest() {
    @Test fun effectInstructionOpensEditorOnlyAfterContinue() {
        var configured = 0
        val template = template("instruction", "portrait").copy(requiredImages = 3)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme {
                BackendWorkspace(state().copy(data = BackendData(videos = listOf(template))), {}, {}, {}, {},
                    canCreateEffect = { true }, templateCreate = { configured++; true }, videoEditor = { Text("Effect editor") })
            } } }
            tag("remote_template_instruction").performScrollTo().performClick()
            tag("backend_generate").performClick(); assertEquals(0, configured)
            ui.onNodeWithText(context.getString(R.string.backend_reference_count, 3)).assertIsDisplayed()
            tag("backend_instruction_continue").performScrollTo().performClick()
            assertEquals(1, configured); ui.onNodeWithText("Effect editor").assertIsDisplayed()
        }
    }
    private fun template(id: String, category: String, trending: Boolean = false) = RemoteTemplate(id, "Effect $id", null, null,
        listOf(category), 7, 1, "video", false, trending)
    private fun state() = BackendState(userId = "design-user", data = BackendData(wallet = 37), loaded = BackendSection.entries.toSet())

    @Test fun categoryUsesActualCodesAndFavoriteSurvivesNavigation() {
        val unusual = "anime+mix/日本"
        val a = template("server-a", unusual, true); val b = template("server-b", "fashion")
        val state = mutableStateOf(state().copy(data = BackendData(wallet = 37, videos = listOf(a, b))))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme {
                BackendWorkspace(state.value, {}, {}, { id -> state.value = state.value.copy(favorites = state.value.favorites + id) }, {})
            } } }
            tag("backend_see_all_category:$unusual").performScrollTo().performClick()
            tag("backend_category_category:$unusual").performScrollTo().performClick()
            tag("remote_template_server-a").assertIsDisplayed()
            tag("remote_template_server-b").assertDoesNotExist()
            ui.onNodeWithContentDescription(context.getString(R.string.add_favorite) + ": Effect server-a").performClick()
            assertEquals(setOf(a.id), state.value.favorites)
            back(scenario)
            tag("tab_favorites").performClick(); text(R.string.videos).performClick()
            tag("remote_template_server-a").performScrollTo().performClick()
            tag("backend_detail_like").assertIsSelected()
            back(scenario)
            tag("remote_template_server-a").assertIsDisplayed()
        }
    }

    @Test fun emptyStatesLeadToCorrectCatalogAndPromptWithoutGeneration() {
        var selected: String? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme {
                BackendWorkspace(state(), {}, {}, {}, {}, promptSelected = { selected = it })
            } } }
            tag("tab_favorites").performClick()
            text(R.string.empty_favorites_title).performScrollTo().assertIsDisplayed()
            text(R.string.explore_effects).performScrollTo().performClick()
            tag("tab_photo").assertIsSelected()
            tag("tab_library").performClick(); text(R.string.videos).performClick()
            text(R.string.start_creating).performScrollTo().performClick()
            tag("tab_video").assertIsSelected()
            assertEquals("video", selected)
            text(R.string.prompt).assertIsSelected()
        }
    }

    @Test fun libraryShowsServerRefundOnlyAndOpensSelectedJob() {
        var opened: String? = null
        val jobs = listOf(RemoteJob("failed", "image", "Failed without refund", "failed", null, null),
            RemoteJob("refunded", "image", "Refunded", "failed", null, null, refunded = true),
            RemoteJob("running", "image", "Running", "running", null, null))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme {
                BackendWorkspace(state().copy(data = BackendData(jobs = jobs)), {}, {}, {}, {}, photoEditor = {},
                    photoJob = { opened = it.id }, photoResult = { _, _, _ -> Text("Selected job") })
            } } }
            tag("tab_library").performClick()
            text(R.string.backend_job_failed).assertIsDisplayed()
            text(R.string.backend_job_failed_refunded).assertIsDisplayed()
            text(R.string.job_failed).assertDoesNotExist()
            tag("remote_job_running").performScrollTo().performClick()
            assertEquals("running", opened); ui.onNodeWithText("Selected job").assertIsDisplayed()
        }
    }

    @Test fun settingsShowsRealPlanAndDeferredActionsDoNotChangeBalance() {
        val state = state().copy(data = BackendData(wallet = 0, profile = RemoteProfile("9994-test", "Real account"),
            policy = RemotePolicy(false, 0, 0, false, "weekly", "2026-10-20T12:00:00Z", false, emptyList())))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme { BackendWorkspace(state, {}, {}, {}, {}) } } }
            tag("tab_settings").performClick()
            tag("backend_profile_balance").assertTextEquals(context.getString(R.string.backend_credits, 0))
            tag("backend_account_more").performScrollTo().performClick()
            tag("backend_account_id").assertTextEquals("9994-test")
            ui.onNodeWithText(context.getString(R.string.backend_plan_value, "weekly")).assertIsDisplayed()
            text(R.string.backend_no_renewal).performScrollTo().assertIsDisplayed()
            tag("backend_sheet_close").performScrollTo().performClick()
            tag("backend_restore").performScrollTo().performClick()
            text(R.string.backend_purchases_pending).assertIsDisplayed(); text(R.string.okay).performClick()
            tag("backend_notifications").performScrollTo().performClick()
            text(R.string.backend_notifications_pending).assertIsDisplayed(); text(R.string.okay).performClick()
            tag("backend_profile_balance").performScrollTo().assertTextEquals(context.getString(R.string.backend_credits, 0))
        }
    }

    @Test fun serverFeedbackDraftIsLocalAndScopedToAccount() {
        val state = mutableStateOf(state())
        val prefs = context.getSharedPreferences("backend_settings_drafts_v1", 0)
        prefs.edit().remove("design-user:report").remove("another-design-user:report").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme { BackendWorkspace(state.value, {}, {}, {}, {}) } } }
            tag("tab_settings").performClick(); tag("backend_report").performScrollTo().performClick()
            tag("backend_message_input").performTextInput("A draft to review")
            tag("backend_message_save").performScrollTo().performClick()
            text(R.string.feedback_saved).assertIsDisplayed(); text(R.string.okay).performClick()
            assertEquals("A draft to review", prefs.getString("design-user:report", null))
            tag("backend_report").performClick(); tag("backend_message_input").assertTextContains("A draft to review")
            tag("backend_sheet_close").performScrollTo().performClick()
            ui.runOnIdle { state.value = state.value.copy(userId = "another-design-user") }
            tag("backend_report").performClick()
            tag("backend_message_input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        }
    }

    @Test fun clearCacheRequiresConfirmationAndCanBeCancelled() {
        var cleared = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme { RemoteSettings(state(), {}, {}, cacheBytes = 1024, clearCache = { cleared++ }) } } }
            tag("backend_clear_cache").performScrollTo().performClick(); assertEquals(0, cleared)
            text(R.string.cancel).performClick(); assertEquals(0, cleared)
            tag("backend_clear_cache").performClick(); text(R.string.clear_action).performClick(); assertEquals(1, cleared)
        }
    }
}
