package com.rslnabk.aivideotest.ui.generator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.media.PhotoThumbnail
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun PromptEditor(key: String, snapshot: DemoSnapshot, model: AppViewModel, host: MainActivity) {
    val draft = snapshot.drafts[key] ?: model.draft(key)
    val focus = LocalFocusManager.current
    val running = snapshot.jobs.any { it.id == draft.activeJobId && it.status == JobStatus.RUNNING }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Ds.colors.backgroundSecondary).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (draft.effectId == null) R.string.describe_idea else R.string.prepare_photo), style = Ds.type.title3Emphasized, color = Ds.colors.accentPrimary)
            if (draft.effectId == null) {
                TextField(draft.prompt, { value -> model.editDraft(key) { it.copy(prompt = value) } },
                    Modifier.fillMaxWidth().heightIn(min = 130.dp).testTag("prompt_input"),
                    placeholder = { Text(stringResource(R.string.prompt_hint), style = Ds.type.subheadlineRegular) },
                    textStyle = Ds.type.subheadlineRegular, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(dimensionResource(R.dimen.ds_input_corner_radius)),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Ds.colors.backgroundSecondary,
                        unfocusedContainerColor = Ds.colors.backgroundSecondary, focusedTextColor = Ds.colors.labelPrimary,
                        unfocusedTextColor = Ds.colors.labelPrimary, cursorColor = Ds.colors.accentPrimary,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.counter_format, draft.characterCount), Modifier.testTag("counter"),
                        style = Ds.type.caption1Regular, color = if (draft.characterCount > 300) Ds.colors.accentRed else Ds.colors.labelTertiary)
                    if (draft.prompt.isNotEmpty()) {
                        TextButton({ (host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText(host.getString(R.string.prompt), draft.prompt)); host.toast(R.string.copied) }) { Text(stringResource(R.string.copy), color = Ds.colors.accentPrimary) }
                        TextButton({ model.editDraft(key) { it.copy(prompt = "") } }, Modifier.testTag("clear_prompt")) { Text(stringResource(R.string.clear_prompt), color = Ds.colors.accentPrimary) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val description = stringResource(if (draft.photo == null) R.string.choose_photo else R.string.replace_photo)
                Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(Ds.colors.backgroundPrimary)
                    .clickable { focus.clearFocus(); host.requestPhoto(key) }.semantics { contentDescription = description }.testTag("photo_card"), contentAlignment = Alignment.Center) {
                    ReferencePhoto(draft.photo, Modifier.fillMaxSize())
                    if (draft.photoStatus == PhotoStatus.LOADING) CircularProgressIndicator(Modifier.size(28.dp), color = Ds.colors.accentPrimary)
                }
                Text(description, Modifier.weight(1f), style = Ds.type.subheadlineRegular, color = Ds.colors.labelSecondary)
                if (draft.photo != null || draft.photoStatus == PhotoStatus.FAILED)
                    TextButton({ model.removePhoto(key) }, Modifier.testTag("remove_photo")) { Text(stringResource(R.string.remove_photo), color = Ds.colors.accentPrimary) }
            }
            val error = when { draft.characterCount > 300 -> R.string.prompt_too_long; draft.photoStatus == PhotoStatus.FAILED -> R.string.photo_failed; else -> null }
            if (error != null) Text(stringResource(error), color = Ds.colors.accentRed, style = Ds.type.subheadlineRegular)
            if (draft.photoStatus == PhotoStatus.FAILED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DsButton(stringResource(R.string.retry), Modifier.weight(1f).testTag("retry_photo")) { model.retryPhoto(key) }
                    DsButton(stringResource(R.string.choose_another), Modifier.weight(1f)) { host.requestPhoto(key) }
                }
            }
        }
        if (draft.kind == MediaKind.VIDEO) {
            Text(stringResource(R.string.resolutions), color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(720, 1080).forEach { resolution ->
                    ResolutionChip(resolution.toString(), draft.resolution == resolution) { model.editDraft(key) { it.copy(resolution = resolution) } }
                }
            }
        }
        if (draft.kind == MediaKind.PHOTO && draft.effectId == null) {
            Text(stringResource(R.string.select_style), color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
            val titles = listOf(R.string.style_none, R.string.style_ghibli, R.string.style_3d, R.string.style_simpsons, R.string.style_fantasy)
            val images = listOf(R.drawable.ic_sparkle, R.drawable.demo_style_ghibli, R.drawable.demo_style_3d, R.drawable.demo_style_simpsons, R.drawable.demo_style_fantasy)
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            LazyRow(Modifier.testTag("styles"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(PhotoStyle.entries) { index, style ->
                    val title = stringResource(titles[index])
                    val labelWidth = with(density) { measurer.measure(title, Ds.type.caption1Regular).size.width.toDp() } + 8.dp
                    Column(Modifier.width(labelWidth.coerceAtLeast(72.dp)).selectable(draft.style == style, role = Role.RadioButton) { model.editDraft(key) { it.copy(style = style) } }
                        .semantics { contentDescription = title }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Ds.colors.backgroundSecondary)
                            .border(if (draft.style == style) 2.dp else 0.dp, if (draft.style == style) Ds.colors.accentPrimary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            if (style == PhotoStyle.NONE) DsIcon(images[index], null)
                            else Image(painterResource(images[index]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text(title, Modifier.padding(top = 6.dp), color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
                    }
                }
            }
        }
        DsButton(if (running) stringResource(R.string.view_creating) else pluralStringResource(R.plurals.generate_cost, model.cost(key), model.cost(key)),
            Modifier.fillMaxWidth().testTag("generate"), primary = true, enabled = draft.isValid || running) { focus.clearFocus(); host.generate(key) }
        Text(stringResource(R.string.demo_generation_note), color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
    }
}
@Composable private fun ResolutionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(22.dp)).border(1.dp, if (selected) Ds.colors.accentPrimary else Ds.colors.separatorPrimary, RoundedCornerShape(22.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)) {
        Text(label, style = Ds.type.headlineEmphasized, color = if (selected) Ds.colors.accentPrimary else Ds.colors.labelTertiary)
    }
}
@Composable fun ReferencePhoto(reference: String?, modifier: Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val bitmap by produceState<android.graphics.Bitmap?>(null, reference, width, height) {
            value = null
            value = if (reference?.startsWith("file:") == true) withContext(Dispatchers.IO) {
                PhotoThumbnail.decode(File(context.filesDir, "reference_photos/${reference.removePrefix("file:")}"), width, height)
            } else null
        }
        when {
            reference == "asset:good1" || reference == "asset:good2" -> Image(painterResource(if (reference == "asset:good1") R.drawable.demo_good_1 else R.drawable.demo_good_2), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            bitmap != null -> Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DsIcon(R.drawable.ic_photo, null) }
        }
    }
}
@Composable fun GeneratorScreen(key: String, snapshot: DemoSnapshot, model: AppViewModel, host: MainActivity, actions: AppNavigator) {
    val draft = snapshot.drafts[key] ?: model.draft(key)
    var requested by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (!requested) { requested = true; if (draft.photo == null) host.requestPhoto(key) }
    }
    val title = draft.effectId?.let { model.catalog.effect(it)?.title } ?: if (draft.kind == MediaKind.VIDEO) R.string.ai_video else R.string.ai_photo
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(title), actions::back) { BalanceButton(snapshot, actions) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { PromptEditor(key, snapshot, model, host) }
    }
}
