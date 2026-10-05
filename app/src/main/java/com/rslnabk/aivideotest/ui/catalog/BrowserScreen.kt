package com.rslnabk.aivideotest.ui.catalog

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.generator.PromptEditor
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun BrowserScreen(tab: AppTab, categoryKind: MediaKind?, initialCategory: Category?,
    handle: SavedStateHandle, snapshot: DemoSnapshot, model: AppViewModel, host: MainActivity, actions: AppNavigator) {
    val kindName by handle.getStateFlow("kind", (categoryKind ?: tab.kind ?: MediaKind.PHOTO).name).collectAsState()
    val kind = MediaKind.valueOf(kindName)
    val mode by handle.getStateFlow("mode", 0).collectAsState()
    var categoryName by rememberSaveable { mutableStateOf((initialCategory ?: Category.POPULAR).name) }
    val category = Category.valueOf(categoryName)
    val scroll = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (initialCategory == null) stringResource(tab.title) else "", if (initialCategory == null) null else actions::back) {
            BalanceButton(snapshot, actions)
        }
        if (initialCategory != null) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Category.entries) { filter ->
                    val name = stringResource(filter.title)
                    Box(Modifier.clip(RoundedCornerShape(22.dp)).border(1.dp, if (category == filter) Ds.colors.accentPrimary else Ds.colors.separatorPrimary, RoundedCornerShape(22.dp))
                        .selectable(category == filter, role = Role.Tab) { categoryName = filter.name }
                        .padding(12.dp).semantics { contentDescription = host.getString(R.string.filter_accessibility, name) }) {
                        Text(name, color = if (category == filter) Ds.colors.accentPrimary else Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
                    }
                }
            }
        } else if (tab.kind != null) {
            Segmented(listOf(stringResource(R.string.trends), stringResource(R.string.prompt)), listOf(R.drawable.ic_sparkle, R.drawable.ic_prompt), mode) { handle["mode"] = it }
        } else if (tab == AppTab.FAVORITES || tab == AppTab.LIBRARY) {
            Segmented(listOf(stringResource(R.string.photos), stringResource(R.string.videos)), listOf(R.drawable.ic_photo, R.drawable.ic_video), if (kind == MediaKind.PHOTO) 0 else 1) {
                handle["kind"] = if (it == 0) "PHOTO" else "VIDEO"
            }
        }
        LaunchedEffect(kind, mode, category) { if (handle.get<String>("last_filter") != "$kind:$mode:$category") { scroll.scrollToItem(0); handle["last_filter"] = "$kind:$mode:$category" } }
        LazyColumn(Modifier.fillMaxSize().testTag("browser_list"), scroll, contentPadding = PaddingValues(bottom = 24.dp)) {
            when {
                initialCategory != null -> effectGrid(model.catalog.effects(kind, category), snapshot, model, host)
                tab.kind != null && mode == 0 -> {
                    item(key = "banner") {
                        Image(painterResource(R.drawable.demo_banner), stringResource(R.string.try_anime),
                            Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth().aspectRatio(2.15f).clip(RoundedCornerShape(24.dp))
                                .clickable { host.openEffect(model.catalog.effects(kind, Category.ANIME).first().id) }, contentScale = ContentScale.Crop)
                    }
                    Category.entries.forEach { group ->
                        item(key = "heading_$group") {
                            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(group.title), Modifier.weight(1f), color = Ds.colors.labelPrimary, style = Ds.type.title3Regular)
                                DsButton(stringResource(R.string.see_all), Modifier.semantics { contentDescription = host.getString(R.string.see_all) + ": " + host.getString(group.title) }) { host.openCategory(kind, group) }
                            }
                        }
                        item(key = "row_$group") {
                            val effects = model.catalog.effects(kind, group)
                            val columns = (effects.size + 1) / 2
                            LazyRow(Modifier.testTag("catalog_${group.name.lowercase()}"), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(columns) { column ->
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(column, column + columns).filter { it < effects.size }.forEach { index ->
                                            EffectCard(effects[index], snapshot, model, host, Modifier.width(130.dp).height(231.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                tab.kind != null -> item(key = "prompt") { PromptEditor("prompt_${kind.name.lowercase()}", snapshot, model, host) }
                tab == AppTab.FAVORITES -> {
                    val effects = model.catalog.effects(kind).filter { it.id in snapshot.favorites }
                    if (effects.isEmpty()) item { EmptyPanel(R.drawable.demo_empty_favorites, R.string.empty_favorites_title, R.string.empty_favorites_body, R.string.explore_effects) { actions.selectTab(if (kind == MediaKind.VIDEO) AppTab.VIDEO else AppTab.PHOTO) } }
                    else effectGrid(effects, snapshot, model, host)
                }
                tab == AppTab.LIBRARY -> {
                    val jobs = snapshot.jobs.filter { it.draft.kind == kind }.reversed()
                    if (jobs.isEmpty()) item { EmptyPanel(R.drawable.demo_empty_library, R.string.empty_library_title, R.string.empty_library_body, R.string.start_creating) { actions.openPrompt(kind) } }
                    else items(jobs.chunked(2), key = { it.first().id }) { pair ->
                        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { GenerationCard(it, model, host, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                tab == AppTab.SETTINGS -> item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ds.colors.accentPrimaryAlpha)
                            .border(1.dp, Ds.colors.accentPrimary, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.subscription_title), style = Ds.type.title3Regular, color = Ds.colors.labelPrimary)
                            Text(stringResource(R.string.subscription_body), color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
                            DsButton(stringResource(R.string.more), Modifier.fillMaxWidth()) { actions.show("subscription") }
                        }
                        Text(stringResource(R.string.demo_account), style = Ds.type.headlineEmphasized, color = Ds.colors.labelPrimary)
                        Text(stringResource(R.string.settings_preview), color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
                    }
                }
            }
        }
    }
}
private fun LazyListScope.effectGrid(effects: List<Effect>, snapshot: DemoSnapshot, model: AppViewModel, host: MainActivity) {
    items(effects.chunked(2), key = { it.first().id }) { pair ->
        Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { effect -> EffectCard(effect, snapshot, model, host, Modifier.weight(1f).aspectRatio(1f / 1.78f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
@Composable fun EffectCard(effect: Effect, snapshot: DemoSnapshot, model: AppViewModel, host: MainActivity, modifier: Modifier) {
    val title = stringResource(effect.title)
    val liked = effect.id in snapshot.favorites
    Box(modifier.clip(RoundedCornerShape(20.dp)).clickable { host.openEffect(effect.id) }
        .semantics { contentDescription = host.getString(R.string.effect_accessibility, title) }.testTag("effect_${effect.id}")) {
        Image(painterResource(effect.image), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        CardTitle(title)
        RoundAction(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline,
            stringResource(if (liked) R.string.remove_favorite else R.string.add_favorite) + ": " + title,
            Modifier.align(Alignment.TopEnd).padding(6.dp).semantics { selected = liked },
            if (liked) Ds.colors.accentPrimary else Ds.colors.labelPrimary) { model.toggleFavorite(effect.id) }
    }
}
@Composable private fun BoxScope.CardTitle(title: String) {
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
        .background(Brush.verticalGradient(listOf(Color.Transparent, Ds.colors.backgroundPrimaryAlpha))))
    Text(title, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 14.dp), color = Ds.colors.labelPrimary,
        style = Ds.type.caption1Regular, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
@Composable fun GenerationCard(job: GenerationJob, model: AppViewModel, host: MainActivity, modifier: Modifier) {
    val title = job.draft.effectId?.let { model.catalog.effect(it)?.title }?.let { stringResource(it) }
        ?: job.draft.prompt.ifBlank { stringResource(if (job.draft.kind == MediaKind.VIDEO) R.string.demo_video_title else R.string.demo_photo_title) }
    val status = stringResource(when (job.status) { JobStatus.RUNNING -> R.string.job_running; JobStatus.SUCCEEDED -> R.string.job_ready; JobStatus.FAILED -> R.string.job_failed })
    Box(modifier.aspectRatio(1f / 1.78f).clip(RoundedCornerShape(20.dp))
        .combinedClickable(onClick = { if (job.status == JobStatus.FAILED) host.showFailedActions(job.id) else host.openJob(job.id) },
            onLongClick = if (job.status == JobStatus.RUNNING) null else { { if (job.status == JobStatus.FAILED) host.showFailedActions(job.id) else host.confirmDelete(job.id) } })
        .semantics { contentDescription = host.getString(R.string.creation_named_status, title, status) }.testTag("job_${job.id}")) {
        Image(painterResource(job.resultImage), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        CardTitle(title)
        if (job.status != JobStatus.SUCCEEDED) {
            Box(Modifier.matchParentSize().background(Ds.colors.labelPrimaryInvertedInvariably))
            Column(Modifier.align(Alignment.Center).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (job.status == JobStatus.RUNNING) CircularProgressIndicator(Modifier.size(28.dp), color = Ds.colors.accentPrimary, strokeWidth = 2.dp)
                Text(stringResource(if (job.status == JobStatus.RUNNING) R.string.loading_generation else R.string.generation_error), color = Ds.colors.labelPrimary, style = Ds.type.caption1Regular)
                if (job.status == JobStatus.FAILED) DsButton(stringResource(R.string.read_more)) { host.showFailedActions(job.id) }
            }
        }
    }
}
@Composable private fun EmptyPanel(image: Int, title: Int, body: Int, action: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Image(painterResource(image), null, Modifier.size(220.dp, 240.dp), contentScale = ContentScale.Fit)
        Text(stringResource(title), style = Ds.type.title2Emphasized, color = Ds.colors.labelPrimary, textAlign = TextAlign.Center)
        Text(stringResource(body), style = Ds.type.subheadlineRegular, color = Ds.colors.labelTertiary, textAlign = TextAlign.Center)
        DsButton(stringResource(action), Modifier.fillMaxWidth(), primary = true, onClick = onClick)
    }
}
