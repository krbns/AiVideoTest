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
}
