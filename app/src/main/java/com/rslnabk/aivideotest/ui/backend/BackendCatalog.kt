package com.rslnabk.aivideotest.ui.backend

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.ui.catalog.CardTitle
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.theme.Ds

/** Groups use server flags/codes; a PDF category is never assigned to an unrelated template. */
internal fun catalogGroup(template: RemoteTemplate): String = when {
    template.categories.any(String::isNotBlank) -> "category:" + template.categories.first(String::isNotBlank)
    !template.section.isNullOrBlank() -> "section:" + template.section
    template.trending -> "trending"
    template.isNew -> "new"
    else -> "all"
}
internal fun categoryTemplates(templates: List<RemoteTemplate>, category: String) = templates.filter { template ->
    when {
        category == "all" -> true
        category == "trending" -> template.trending
        category == "new" -> template.isNew
        category.startsWith("category:") -> category.removePrefix("category:") in template.categories
        category.startsWith("section:") -> template.section == category.removePrefix("section:")
        else -> false
    }
}
@Composable internal fun catalogTitle(category: String): String = when (category) {
    "all" -> stringResource(R.string.backend_all)
    "trending" -> stringResource(R.string.backend_trending)
    "new" -> stringResource(R.string.new_effects)
    else -> category.substringAfter(':').replace('_', ' ').replace('-', ' ').replaceFirstChar { it.titlecase() }
}

internal fun LazyListScope.remoteTrends(templates: List<RemoteTemplate>, state: BackendState, kind: String,
    favorite: (String) -> Unit, effect: (String, String) -> Unit, category: (String) -> Unit) {
    val featured = templates.firstOrNull { it.trending && it.cover != null } ?: templates.firstOrNull { it.cover != null }
    featured?.let { template -> item("banner") {
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(2.15f).clip(RoundedCornerShape(24.dp))
            .clickable { effect(kind, template.id) }.testTag("backend_banner")
            .semantics { contentDescription = template.title }) {
            RemoteImage(template.cover, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ds.colors.backgroundPrimaryAlpha))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(template.title, color = Ds.colors.labelPrimary, style = Ds.type.title3Emphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.backend_try_effect), Modifier.background(Ds.colors.accentPrimary, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 8.dp),
                    color = Ds.colors.labelPrimaryInverted, style = Ds.type.headlineEmphasized)
            }
        }
    } }
    val groups = templates.groupBy(::catalogGroup)
    groups.forEach { (group, effects) ->
        item("heading_$group") {
            val title = catalogTitle(group)
            val seeAll = stringResource(R.string.see_all)
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = Ds.colors.labelPrimary, style = Ds.type.title3Regular)
                DsButton(stringResource(R.string.see_all), Modifier.testTag("backend_see_all_$group")
                    .semantics { contentDescription = "$seeAll: $title" }) { category(group) }
            }
        }
        item("row_$group") {
            val columns = (effects.size + 1) / 2
            LazyRow(Modifier.testTag("backend_catalog_$group"), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(columns) { column -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(column, column + columns).filter { it < effects.size }.forEach { index ->
                        RemoteEffectCard(effects[index], state, Modifier.width(130.dp).height(231.dp), favorite) { effect(kind, effects[index].id) }
                    }
                } }
            }
        }
    }
}

internal fun LazyListScope.remoteEffectGrid(templates: List<RemoteTemplate>, state: BackendState, kind: String,
    favorite: (String) -> Unit, effect: (String, String) -> Unit) {
    items(templates.chunked(2), key = { "pair_" + it.first().id }) { pair ->
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { template -> RemoteEffectCard(template, state, Modifier.weight(1f).aspectRatio(1f / 1.78f), favorite) { effect(kind, template.id) } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
@Composable private fun RemoteEffectCard(template: RemoteTemplate, state: BackendState, modifier: Modifier,
    favorite: (String) -> Unit, open: () -> Unit) {
    val liked = template.id in state.favorites
    val description = stringResource(R.string.effect_accessibility, template.title)
    Box(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = open)
        .testTag("remote_template_" + template.id).semantics { contentDescription = description }) {
        RemoteImage(template.cover, Modifier.matchParentSize())
        CardTitle(template.title)
        RoundAction(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline,
            stringResource(if (liked) R.string.remove_favorite else R.string.add_favorite) + ": " + template.title,
            Modifier.align(Alignment.TopEnd).padding(6.dp).semantics { selected = liked },
            if (liked) Ds.colors.accentPrimary else Ds.colors.labelPrimary) { favorite(template.id) }
    }
}

@Composable internal fun RemoteCategoryScreen(kind: String, initial: String, state: BackendState, favorite: (String) -> Unit,
    back: () -> Unit, effect: (String, String) -> Unit, refresh: (Boolean) -> Unit) {
    val templates = if (kind == "video") state.data.videos else state.data.photos
    val section = if (kind == "video") BackendSection.VIDEOS else BackendSection.PHOTOS
    var selected by rememberSaveable { mutableStateOf(initial) }
    val categories = buildList {
        add("all")
        if (templates.any { it.trending }) add("trending")
        if (templates.any { it.isNew }) add("new")
        addAll(templates.flatMap { it.categories }.filter(String::isNotBlank).distinct().map { "category:$it" })
        addAll(templates.mapNotNull { it.section?.takeIf(String::isNotBlank) }.distinct().map { "section:$it" })
        if (initial !in this) add(initial)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("", back) { RemoteBalance(state) }
        LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it }) { category -> RemoteOptionChip(catalogTitle(category), selected == category,
                Modifier.testTag("backend_category_$category"), role = Role.Tab) { selected = category } }
        }
        val scroll = rememberLazyListState()
        LaunchedEffect(selected) { scroll.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxSize().testTag("backend_category_list"), scroll, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            remoteStatus(state, section)
            val filtered = categoryTemplates(templates, selected)
            remoteEffectGrid(filtered, state, kind, favorite, effect)
            if (filtered.isEmpty() && section in state.loaded) item { BackendNotice(R.string.backend_empty) }
            if (section in state.errors) item { DsButton(stringResource(R.string.refresh), Modifier.padding(16.dp)) { refresh(false) } }
        }
    }
}

@Composable internal fun RemoteOptionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true,
    role: Role = Role.RadioButton, select: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).border(1.dp, if (selected) Ds.colors.accentPrimary else Ds.colors.separatorPrimary, RoundedCornerShape(22.dp))
        .selectable(selected = selected, enabled = enabled, role = role, onClick = select).heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, style = Ds.type.subheadlineRegular, color = if (!enabled) Ds.colors.labelQuaternary else if (selected) Ds.colors.accentPrimary else Ds.colors.labelTertiary)
    }
}

internal fun LazyListScope.remoteJobGrid(jobs: List<RemoteJob>, state: BackendState, canOpen: Boolean, open: (RemoteJob) -> Unit) {
    items(jobs.chunked(2), key = { "jobs_" + it.first().id }) { pair ->
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { job ->
                val title = state.data.photos.plus(state.data.videos).find { it.id == job.templateId }?.title ?: job.prompt.ifBlank { stringResource(R.string.backend_creation) }
                val status = remoteJobStatus(job)
                Box(Modifier.weight(1f).aspectRatio(1f / 1.78f).clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = canOpen) { open(job) }.testTag("remote_job_${job.id}")
                    .semantics { contentDescription = "$title · $status" }) {
                    RemoteImage(job.thumbnail ?: state.data.photos.plus(state.data.videos).find { it.id == job.templateId }?.cover, Modifier.matchParentSize())
                    CardTitle(title)
                    if (job.status != "completed") Column(Modifier.matchParentSize().background(Ds.colors.backgroundPrimaryAlpha).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                        if (job.status in listOf("queued", "running")) CircularProgressIndicator(Modifier.size(28.dp), color = Ds.colors.accentPrimary, strokeWidth = 2.dp)
                        else DsIcon(R.drawable.ic_report, null, Ds.colors.accentRed)
                        Text(status, color = Ds.colors.labelPrimary, style = Ds.type.caption1Regular)
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
@Composable internal fun remoteJobStatus(job: RemoteJob) = stringResource(when (job.status) {
    "queued", "running" -> R.string.job_running
    "completed" -> R.string.job_ready
    "failed" -> if (job.refunded) R.string.backend_job_failed_refunded else R.string.backend_job_failed
    else -> R.string.backend_unknown_status
})
