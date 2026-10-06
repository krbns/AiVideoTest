package com.rslnabk.aivideotest.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.AppTab
import com.rslnabk.aivideotest.ui.common.DsIcon
import com.rslnabk.aivideotest.ui.theme.Ds

/** Keep the PDF's five tabs; give their scaled labels room instead of shrinking text. */
@Composable fun AppTabBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val icons = listOf(R.drawable.ic_video, R.drawable.ic_photo, R.drawable.ic_heart_outline, R.drawable.ic_clock, R.drawable.ic_settings)
    val labels = AppTab.entries.map { stringResource(it.title) }
    val style = Ds.type.caption2Regular
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = with(density) { labels.maxOf { measurer.measure(it, style, maxLines = 1).size.width }.toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("tab_bar")) {
        // Material's single-row items reserve 12dp on each side of the label.
        val columns = (maxWidth / (labelWidth + 24.dp)).toInt().coerceIn(1, AppTab.entries.size)
        if (columns == AppTab.entries.size) {
            NavigationBar(containerColor = Ds.colors.backgroundPrimary, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
                AppTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(selected == tab, { onSelect(tab) },
                        icon = { DsIcon(icons[index], null, if (selected == tab) Ds.colors.accentPrimary else Ds.colors.labelTertiary) },
                        label = { Text(labels[index], style = style, maxLines = 1) },
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Ds.colors.accentPrimary,
                            selectedTextColor = Ds.colors.accentPrimary, indicatorColor = Ds.colors.backgroundPrimary,
                            unselectedTextColor = Ds.colors.labelTertiary))
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                AppTab.entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { tab ->
                            val index = tab.ordinal
                            val color = if (selected == tab) Ds.colors.accentPrimary else Ds.colors.labelTertiary
                            Column(Modifier.weight(1f).testTag("tab_${tab.name.lowercase()}")
                                .selectable(selected == tab, role = Role.Tab) { onSelect(tab) }
                                .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                                DsIcon(icons[index], null, color)
                                Text(labels[index], style = style, color = color, maxLines = 1)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
