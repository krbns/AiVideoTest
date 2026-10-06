package com.rslnabk.aivideotest

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import com.rslnabk.aivideotest.model.AppTab
import com.rslnabk.aivideotest.ui.catalog.BrowserScreen
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.generator.PromptEditor
import com.rslnabk.aivideotest.ui.theme.AiVideoTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromptLayoutTest : ComposeFlowTest() {
    private fun controlsFit(scale: Float) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host ->
                host.model.editDraft("prompt_photo") { it.copy(prompt = "👋".repeat(300)) }
                host.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                        AiVideoTheme {
                            val snapshot by host.model.snapshot.observeAsState(host.model.snapshot.value!!)
                            Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                PromptEditor("prompt_photo", snapshot, host.model, host)
                            }
                        }
                    }
                }
            }
            listOf(tag("counter"), text(R.string.copy), text(R.string.clear_prompt)).forEach { node ->
                node.performScrollTo().assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
                assertFalse(layout.didOverflowHeight)
            }
            tag("counter").assertTextEquals("300/300")
            text(R.string.clear_prompt).performScrollTo().performClick()
            tag("counter").assertTextEquals("0/300")
            tag("generate").performScrollTo().assertIsNotEnabled()
        }
    }

    @Test fun compactLargeFontPromptCounterCopyAndClearRemainReadable() = controlsFit(1.5f)
    @Test fun compactMaximumFontPromptCounterCopyAndClearRemainReadable() = controlsFit(2f)

    @Test fun landscapeImeHeightKeepsPromptAndActionsReachable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { host ->
                val handle = SavedStateHandle(mapOf("mode" to 1))
                host.setContent {
                    AiVideoTheme {
                        val snapshot by host.model.snapshot.observeAsState(host.model.snapshot.value!!)
                        val nav = rememberNavController()
                        val actions = remember(nav) { AppNavigator(nav) }
                        Box(Modifier.width(640.dp).height(90.dp)) {
                            BrowserScreen(AppTab.VIDEO, null, null, handle, snapshot, host.model, host, actions)
                        }
                    }
                }
            }
            tag("browser_list").performScrollToNode(hasTestTag("prompt_input"))
            tag("prompt_input").performScrollTo().assertIsDisplayed().performTextInput("Landscape input")
            await(scenario) { it.draft("prompt_video").prompt == "Landscape input" }
            tag("generate").performScrollTo().assertIsDisplayed()
            tag("counter").performScrollTo().assertTextEquals("15/300")
        }
    }
}
