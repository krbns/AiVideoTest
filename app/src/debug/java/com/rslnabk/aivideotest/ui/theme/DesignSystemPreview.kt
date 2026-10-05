package com.rslnabk.aivideotest.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.ui.common.*

/** Replaces the former debug-only XML preview; never appears in the product's navigation. */
@Preview(name = "PDF design system", widthDp = 412, heightDp = 900, showBackground = true)
@Composable private fun DesignSystemPreview() {
    AiVideoTheme {
        var input by remember { mutableStateOf("") }
        Column(Modifier.background(Ds.colors.backgroundPrimary).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("PDF design system", color = Ds.colors.labelPrimary, style = Ds.type.largeTitleEmphasized)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Ds.colors.accentPrimary, Ds.colors.accentSecondary, Ds.colors.accentRed, Ds.colors.backgroundSecondary).forEach {
                    Box(Modifier.size(56.dp).background(it))
                }
            }
            listOf("Large Title" to Ds.type.largeTitleRegular, "Title 1" to Ds.type.title1Regular,
                "Title 2" to Ds.type.title2Regular, "Title 3" to Ds.type.title3Regular,
                "Headline" to Ds.type.headlineRegular, "Body" to Ds.type.bodyRegular,
                "Callout" to Ds.type.calloutRegular, "Subheadline" to Ds.type.subheadlineRegular,
                "Footnote" to Ds.type.footnoteRegular, "Caption 1" to Ds.type.caption1Regular,
                "Caption 2" to Ds.type.caption2Regular).forEach { (name, style) -> Text(name, style = style, color = Ds.colors.labelPrimary) }
            DsButton("Primary", Modifier.fillMaxWidth(), primary = true, onClick = {})
            DsButton("Secondary", Modifier.fillMaxWidth(), onClick = {})
            DsButton("Disabled", Modifier.fillMaxWidth(), primary = true, enabled = false, onClick = {})
            DsSearchField(input, { input = it }, "Search")
            DsCardSurface(selected = true) { Text("Selected card", Modifier.padding(16.dp), style = Ds.type.headlineEmphasized) }
        }
    }
}
