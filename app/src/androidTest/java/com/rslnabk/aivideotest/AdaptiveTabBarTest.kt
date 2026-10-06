package com.rslnabk.aivideotest

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.rslnabk.aivideotest.model.AppTab
import com.rslnabk.aivideotest.ui.navigation.AppTabBar
import com.rslnabk.aivideotest.ui.theme.AiVideoTheme
import com.rslnabk.aivideotest.ui.common.ScreenHeader
import com.rslnabk.aivideotest.ui.common.DsButton
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveTabBarTest {
    @get:Rule val ui = createComposeRule()

    private fun labelsFit(width: Int, scale: Float, singleRow: Boolean) {
        ui.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, scale)) {
                AiVideoTheme {
                    var selected by remember { mutableStateOf(AppTab.VIDEO) }
                    Box(Modifier.width(width.dp)) { AppTabBar(selected) { selected = it } }
                }
            }
        }
        val tops = mutableSetOf<Float>()
        AppTab.entries.forEach { tab ->
            val item = ui.onNodeWithTag("tab_${tab.name.lowercase()}")
            item.assertIsDisplayed().performClick().assertIsSelected()
            val bounds = item.fetchSemanticsNode().boundsInRoot
            assertTrue("Tab has a 48dp touch target", bounds.height >= 96f && bounds.width >= 96f)
            tops += bounds.top
            val label = ui.onNode(hasText(ApplicationProvider.getApplicationContext<Context>().getString(tab.title)) and hasAnyAncestor(hasTestTag("tab_${tab.name.lowercase()}")), useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            // Paragraph width is fractional, while the Text's measured bounds are integral pixels.
            assertTrue("${tab.name} loses characters at $width dp / $scale font", layout.getLineEnd(0, visibleEnd = true) == layout.layoutInput.text.length)
            assertFalse(layout.didOverflowHeight)
            assertFalse(layout.isLineEllipsized(0))
            assertTrue("${tab.name} extends beyond its label at $width dp / $scale font: ${layout.getLineRight(0)} / ${layout.size.width}", layout.getLineRight(0) <= layout.size.width + 1f)
            assertTrue(label.fetchSemanticsNode().boundsInRoot.let { it.left >= bounds.left && it.right <= bounds.right })
        }
        assertEquals(singleRow, tops.size == 1)
    }

    @Test fun referenceWidthRetainsSingleRow() = labelsFit(390, 1f, true)
    @Test fun compactLargeFontKeepsAllLabelsAndTouchTargets() = labelsFit(320, 1.5f, false)
    @Test fun compactMaximumFontKeepsAllLabelsAndTouchTargets() = labelsFit(320, 2f, false)

    private fun headerFits(scale: Float) {
        ui.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, scale)) {
                AiVideoTheme {
                    Box(Modifier.width(320.dp)) {
                        ScreenHeader("Settings") { DsButton("PRO · 100 ✦") {} }
                    }
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        ui.onNodeWithText("Settings").assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals("Settings".length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1))
        ui.onNodeWithText("PRO · 100 ✦").assertIsDisplayed().performClick()
    }

    @Test fun compactLargeFontHeaderKeepsTitleAndBalance() = headerFits(1.5f)
    @Test fun compactMaximumFontHeaderKeepsTitleAndBalance() = headerFits(2f)
}
