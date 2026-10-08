package com.rslnabk.aivideotest

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.ui.backend.BackendWorkspace
import com.rslnabk.aivideotest.ui.theme.AiVideoTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackendUiTest : ComposeFlowTest() {
    private fun policy(credits: Int) = RemotePolicy(true, credits, 0, true, null, null, null, emptyList())

    @Test fun profileShowsWalletBalanceEvenWhenPolicyDiffers() {
        val state = BackendState(userId = "user", data = BackendData(wallet = 37, policy = policy(99),
            profile = RemoteProfile("account", "Test profile")))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme { BackendWorkspace(state, {}, {}, {}, {}) } } }
            tag("tab_settings").performClick()
            tag("backend_profile_balance").performScrollTo().assertTextEquals(context.getString(R.string.backend_credits, 37))
            tag("backend_balance").assertContentDescriptionEquals(context.getString(R.string.pro_balance_value, 37))
            tag("backend_profile_balance_saved").assertDoesNotExist()
        }
    }

    @Test fun profileBalanceUpdatesFromLoadingToUnavailableFallbackAndZero() {
        val state = mutableStateOf(BackendState(userId = "user", loading = setOf(BackendSection.WALLET, BackendSection.POLICY)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme { BackendWorkspace(state.value, {}, {}, {}, {}) } } }
            tag("tab_settings").performClick()
            tag("backend_profile_balance").performScrollTo().assertTextEquals(context.getString(R.string.backend_balance_loading))
            ui.runOnIdle { state.value = BackendState(userId = "user", errors = mapOf(BackendSection.WALLET to BackendFailure())) }
            tag("backend_profile_balance").performScrollTo().assertTextEquals(context.getString(R.string.backend_balance_unavailable))
            tag("backend_balance").assertContentDescriptionEquals(context.getString(R.string.backend_balance_unknown))
            ui.runOnIdle { state.value = BackendState(userId = "user", data = BackendData(policy = policy(19))) }
            tag("backend_profile_balance").performScrollTo().assertTextEquals(context.getString(R.string.backend_credits, 19))
            tag("backend_balance").assertContentDescriptionEquals(context.getString(R.string.pro_balance_value, 19))
            ui.runOnIdle { state.value = state.value.copy(data = state.value.data.copy(wallet = 0)) }
            tag("backend_profile_balance").assertTextEquals(context.getString(R.string.backend_credits, 0))
            tag("backend_balance").assertContentDescriptionEquals(context.getString(R.string.pro_balance_value, 0))
        }
    }

    @Test fun profileMarksSavedBalanceAndClearsNoticeAfterRefresh() {
        val state = mutableStateOf(BackendState(userId = "user", data = BackendData(wallet = 37), cached = setOf(BackendSection.WALLET)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme { BackendWorkspace(state.value, {}, {}, {}, {}) } } }
            tag("tab_settings").performClick()
            tag("backend_profile_balance").performScrollTo().assertTextEquals(context.getString(R.string.backend_credits, 37))
            tag("backend_profile_balance_saved").performScrollTo().assertIsDisplayed()
            tag("backend_balance").assertContentDescriptionEquals(context.getString(R.string.backend_saved_balance, 37))
            ui.runOnIdle { state.value = state.value.copy(data = BackendData(wallet = 12), cached = emptySet()) }
            tag("backend_profile_balance").performScrollTo().assertTextEquals(context.getString(R.string.backend_credits, 12))
            tag("backend_profile_balance_saved").assertDoesNotExist()
            tag("backend_balance").assertContentDescriptionEquals(context.getString(R.string.balance_value, 12))
        }
    }

    @Test fun unknownRemoteBalanceNeverUsesDemoAccountAndFailuresHaveRecovery() {
        var returned = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme {
                BackendWorkspace(BackendState(authError = BackendFailure()), {}, { returned = true }, {}, {})
            } } }
            ui.onNodeWithText(context.getString(R.string.backend_balance_unknown)).assertIsDisplayed()
            ui.onNodeWithText(context.getString(R.string.balance_value, 5)).assertDoesNotExist()
            text(R.string.backend_network_error).assertIsDisplayed()
            text(R.string.backend_back_demo).performClick(); assertTrue(returned)
        }
    }
    @Test fun remoteTemplateDetailsUseServerInputsAndCannotGenerateOrPurchase() {
        val template = RemoteTemplate("real-id", "Server effect", null, null, emptyList(), 17, 3, "image_video", false, true)
        val state = BackendState(userId = "user", data = BackendData(videos = listOf(template), wallet = 37), loaded = setOf(BackendSection.VIDEOS))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme { BackendWorkspace(state, {}, {}, {}, {}) } } }
            tag("remote_template_real-id").performScrollTo().performClick()
            ui.onNodeWithText(context.getString(R.string.backend_reference_count, 3)).performScrollTo().assertIsDisplayed()
            tag("backend_generate").performScrollTo().assertIsNotEnabled()
            ui.onNodeWithText(context.getString(R.string.backend_credits, 17)).performScrollTo().assertIsDisplayed()
        }
    }
    @Test fun cachedBalanceIsMarkedAndRemoteHistoryLoadsMoreWithoutDemoJobs() {
        var more = 0
        val state = BackendState(userId = "user", data = BackendData(wallet = 37,
            jobs = listOf(RemoteJob("server-job", "image", "Server creation", "completed", null, null)), nextCursor = "page2"),
            loaded = setOf(BackendSection.JOBS), cached = setOf(BackendSection.WALLET))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host -> host.setContent { AiVideoTheme { BackendWorkspace(state, {}, {}, {}, { more++ }) } } }
            ui.onNodeWithText(context.getString(R.string.backend_saved_balance, 37)).assertIsDisplayed()
            tag("tab_library").performClick()
            ui.onNodeWithText("Server creation").performScrollTo().assertIsDisplayed()
            text(R.string.backend_more_history).performScrollTo().performClick(); assertEquals(1, more)
        }
    }
}
