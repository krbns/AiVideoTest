package com.rslnabk.aivideotest.ui.common

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun DsButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) {
    val c = Ds.colors
    Button(onClick, modifier.heightIn(min = dimensionResource(if (primary) R.dimen.ds_button_large_min_height else R.dimen.ds_button_medium_min_height)), enabled = enabled,
        shape = RoundedCornerShape(28.dp), border = if (primary) null else BorderStroke(1.dp, c.separatorPrimary),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) c.accentPrimary else c.backgroundSecondary,
            contentColor = if (primary) c.labelPrimaryInverted else c.accentPrimary,
            disabledContainerColor = c.backgroundSecondary, disabledContentColor = c.labelQuaternary),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text, style = Ds.type.headlineEmphasized, maxLines = 2)
    }
}
@Composable fun DsIcon(resource: Int, description: String?, tint: Color = Ds.colors.accentPrimary, modifier: Modifier = Modifier) {
    Icon(painterResource(resource), description, modifier.size(24.dp), tint)
}
@Composable fun RoundAction(resource: Int, description: String, modifier: Modifier = Modifier,
    tint: Color = Ds.colors.accentPrimary, onClick: () -> Unit) {
    IconButton(onClick, modifier.size(48.dp).clip(CircleShape).background(Ds.colors.backgroundSecondary).semantics { contentDescription = description }) {
        DsIcon(resource, null, tint)
    }
}
@Composable fun ScreenHeader(title: String, back: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (back != null) RoundAction(R.drawable.ic_back, stringResource(R.string.back), Modifier.testTag("back"), onClick = back)
        Text(title, Modifier.weight(1f), style = if (back == null) Ds.type.largeTitleEmphasized else Ds.type.headlineEmphasized,
            color = Ds.colors.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}
@Composable fun Segmented(labels: List<String>, icons: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
        .clip(RoundedCornerShape(24.dp)).background(Ds.colors.backgroundSecondary).padding(4.dp)) {
        labels.forEachIndexed { index, label ->
            Row(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                .background(if (selected == index) Ds.colors.backgroundPrimary else Color.Transparent)
                .selectable(selected == index, role = Role.Tab) { onSelect(index) }
                .heightIn(min = 44.dp).padding(8.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                val color = if (selected == index) Ds.colors.accentPrimary else Ds.colors.labelTertiary
                DsIcon(icons[index], null, color, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(label, style = Ds.type.headlineEmphasized, color = color)
            }
        }
    }
}
@Composable fun InfoDialog(title: String?, message: String?, dismiss: () -> Unit, confirmText: String = stringResource(R.string.okay),
    confirm: () -> Unit = dismiss, cancelText: String? = null) {
    AlertDialog(onDismissRequest = dismiss, containerColor = Ds.colors.backgroundSecondary,
        title = title?.let { { Text(it, style = Ds.type.title2Emphasized) } },
        text = message?.let { { Text(it, style = Ds.type.calloutRegular) } },
        confirmButton = { TextButton(confirm) { Text(confirmText, color = Ds.colors.accentPrimary) } },
        dismissButton = cancelText?.let { { TextButton(dismiss) { Text(it, color = Ds.colors.labelSecondary) } } })
}

/** Shared foundations for future token packs, subscription cards and settings search. */
@Composable fun DsCardSurface(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(dimensionResource(R.dimen.ds_plan_corner_radius)),
        color = if (selected) Ds.colors.accentPrimaryAlpha else Ds.colors.backgroundSecondary,
        contentColor = Ds.colors.labelPrimary,
        border = if (selected) BorderStroke(dimensionResource(R.dimen.ds_android_selected_stroke_width), Ds.colors.accentPrimary) else null,
        content = content)
}
@Composable fun DsSearchField(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), placeholder = { Text(hint) }, singleLine = true,
        textStyle = Ds.type.subheadlineRegular, shape = RoundedCornerShape(dimensionResource(R.dimen.ds_search_corner_radius)),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Ds.colors.accentPrimary,
            unfocusedBorderColor = Ds.colors.separatorPrimary, focusedTextColor = Ds.colors.labelPrimary,
            unfocusedTextColor = Ds.colors.labelPrimary, focusedContainerColor = Ds.colors.backgroundSecondary,
            unfocusedContainerColor = Ds.colors.backgroundSecondary, cursorColor = Ds.colors.accentPrimary))
}
