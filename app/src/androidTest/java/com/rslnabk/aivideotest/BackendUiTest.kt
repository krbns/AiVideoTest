package com.rslnabk.aivideotest

import androidx.activity.compose.setContent
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
