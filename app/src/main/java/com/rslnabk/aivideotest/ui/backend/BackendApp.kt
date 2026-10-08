package com.rslnabk.aivideotest.ui.backend

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.rslnabk.aivideotest.AppViewModel
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.model.AppTab
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppTabBar
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun BackendApp(model: AppViewModel) {
    val state by model.backend.state.observeAsState(BackendState())
    BackendWorkspace(state, { model.backend.connect(it) }, model.backend::useDemo,
        model.backend::toggleFavorite, model.backend::loadMore)
}
/** Same five destinations and design tokens; phase B1 has no mutation or purchase actions. */
@Composable fun BackendWorkspace(state: BackendState, refresh: (Boolean) -> Unit, demo: () -> Unit,
    favorite: (String) -> Unit, loadMore: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val detail = entry?.destination?.route == "backend/effect/{kind}/{id}"
    val tab = AppTab.entries.find { entry?.destination?.route == "backend/" + it.name } ?: AppTab.VIDEO
    fun select(next: AppTab) = nav.navigate("backend/" + next.name) {
        popUpTo("backend/VIDEO") { saveState = true }; launchSingleTop = true; restoreState = true
    }
    BackHandler(!detail && tab != AppTab.VIDEO) { select(AppTab.VIDEO) }
    Column(Modifier.fillMaxSize().background(Ds.colors.backgroundPrimary).safeDrawingPadding()) {
        NavHost(nav, "backend/VIDEO", Modifier.weight(1f)) {
            AppTab.entries.forEach { current -> composable("backend/" + current.name) {
                Column(Modifier.fillMaxSize()) {
                    ScreenHeader(stringResource(current.title)) { RemoteBalance(state) }
                    BackendBody(current, state, refresh, demo, favorite, loadMore) { kind, id ->
                        nav.navigate("backend/effect/" + kind + "/" + Uri.encode(id))
                    }
                }
            } }
            composable("backend/effect/{kind}/{id}") { stack ->
                val kind = stack.arguments!!.getString("kind")!!
                val templates = if (kind == "image") state.data.photos else state.data.videos
                val template = templates.find { it.id == stack.arguments!!.getString("id") }
                TemplateDetail(template, state, nav, favorite)
            }
        }
        if (!detail) AppTabBar(tab) { select(it) }
    }
}
@Composable private fun RemoteBalance(state: BackendState) {
    val value = state.data.wallet ?: state.data.policy?.credits
    val cached = BackendSection.WALLET in state.cached || (state.data.wallet == null && BackendSection.POLICY in state.cached)
    val label = if (value == null) stringResource(R.string.backend_balance_unknown) else
        stringResource(if (cached) R.string.backend_saved_balance else if (state.data.policy?.subscribed == true) R.string.pro_balance_value else R.string.balance_value, value)
    Box(Modifier.clip(RoundedCornerShape(22.dp)).background(Ds.colors.backgroundSecondary)
        .padding(horizontal = 16.dp, vertical = 12.dp).testTag("backend_balance")
        .semantics { contentDescription = label }) {
        Text(label, color = Ds.colors.accentPrimary, style = Ds.type.headlineEmphasized)
    }
}
@Composable private fun BackendBody(tab: AppTab, state: BackendState, refresh: (Boolean) -> Unit,
    demo: () -> Unit, favorite: (String) -> Unit, loadMore: () -> Unit, effect: (String, String) -> Unit) {
    var mode by rememberSaveable(tab) { mutableIntStateOf(0) }
    var filter by rememberSaveable(tab) { mutableIntStateOf(0) }
    val kind = if (tab == AppTab.VIDEO || (tab in listOf(AppTab.FAVORITES, AppTab.LIBRARY) && mode == 1)) "video" else "image"
    val section = if (kind == "video") BackendSection.VIDEOS else BackendSection.PHOTOS
    LazyColumn(Modifier.fillMaxSize().testTag("backend_list"), contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("connection") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.backend_server_data), style = Ds.type.caption1Regular, color = Ds.colors.labelTertiary)
                    DsButton(stringResource(R.string.refresh), enabled = !state.connecting && state.loading.isEmpty() && !state.loadingMore) {
                        refresh(state.authError?.status == 401)
                    }
                }
                if (state.connecting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ds.colors.accentPrimary)
                    Text(stringResource(R.string.backend_connecting), color = Ds.colors.labelSecondary)
                }
                if (state.authError != null) {
                    FailureText(state.authError)
                    DsButton(stringResource(R.string.backend_back_demo), onClick = demo)
                }
                if (state.cached.isNotEmpty()) Text(stringResource(R.string.backend_saved_data), style = Ds.type.caption1Regular, color = Ds.colors.labelTertiary)
            }
        }
        when (tab) {
            AppTab.VIDEO, AppTab.PHOTO -> {
                item("mode") { Segmented(listOf(stringResource(R.string.trends), stringResource(R.string.prompt)),
                    listOf(R.drawable.ic_sparkle, R.drawable.ic_prompt), mode) { mode = it } }
                if (mode == 0) {
                    item("filters") { Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.backend_all, R.string.backend_trending, R.string.new_effects).forEachIndexed { index, label ->
                            FilterChip(filter == index, { filter = index }, label = { Text(stringResource(label)) })
                        }
                    } }
                    remoteStatus(state, section)
                    val templates = if (kind == "video") state.data.videos else state.data.photos
                    templateRows(templates.filter { filter == 0 || (filter == 1 && it.trending) || (filter == 2 && it.isNew) },
                        kind, state, favorite, effect, section in state.loaded)
                } else {
                    remoteStatus(state, BackendSection.MODELS)
                    item("prompt_notice") { Notice(R.string.backend_generation_later) }
                    items(state.data.models.filter { it.kind == kind }, key = { it.id }) { model ->
                        Panel {
                            Text(model.title, color = Ds.colors.labelPrimary, style = Ds.type.title3Emphasized)
                            val resolutions = model.modes.flatMap { it.resolutions }.distinct()
                            val durations = model.modes.flatMap { it.durations }.distinct()
                            if (resolutions.isNotEmpty()) Text(resolutions.joinToString(" · "), color = Ds.colors.labelSecondary)
                            if (durations.isNotEmpty()) Text(durations.joinToString(" · "), color = Ds.colors.labelSecondary)
                        }
                    }
                    if (BackendSection.MODELS in state.loaded && state.data.models.none { it.kind == kind })
                        item("empty_models") { Notice(R.string.backend_empty) }
                }
            }
            AppTab.FAVORITES -> {
                item("mode") { KindTabs(mode) { mode = it } }
                item("local_notice") { Notice(R.string.backend_favorites_local) }
                remoteStatus(state, section)
                val templates = if (kind == "video") state.data.videos else state.data.photos
                templateRows(templates.filter { it.id in state.favorites }, kind, state, favorite, effect, section in state.loaded)
            }
            AppTab.LIBRARY -> {
                item("mode") { KindTabs(mode) { mode = it } }
                remoteStatus(state, BackendSection.JOBS)
                item("history_notice") { Notice(R.string.backend_history_readonly) }
                val jobs = state.data.jobs.filter { it.kind == kind }
                items(jobs, key = { it.id }) { job -> Panel {
                    Text(job.prompt.ifBlank { stringResource(R.string.backend_creation) }, color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
                    Text(stringResource(when (job.status) {
                        "queued", "running" -> R.string.job_running
                        "completed" -> R.string.job_ready
                        "failed" -> R.string.job_failed
                        else -> R.string.backend_unknown_status
                    }), color = Ds.colors.labelSecondary)
                    if (job.thumbnail != null) RemoteImage(job.thumbnail, Modifier.fillMaxWidth().height(160.dp))
                } }
                if (jobs.isEmpty() && BackendSection.JOBS in state.loaded) item("empty_jobs") { Notice(R.string.backend_empty) }
                if (state.data.nextCursor != null) item("more") {
                    DsButton(stringResource(R.string.backend_more_history), Modifier.padding(horizontal = 16.dp),
                        enabled = !state.loadingMore && state.loading.isEmpty(), onClick = loadMore)
                }
                if (state.historyError != null) item("history_error") { FailureText(state.historyError, Modifier.padding(horizontal = 16.dp)) }
            }
            AppTab.SETTINGS -> {
                remoteStatus(state, BackendSection.PROFILE)
                item("profile") { Panel {
                    Text(state.data.profile?.name ?: stringResource(R.string.backend_account), color = Ds.colors.labelPrimary, style = Ds.type.title3Emphasized)
                    state.data.profile?.accountId?.let { Text(it, color = Ds.colors.labelSecondary) }
                } }
                remoteStatus(state, BackendSection.POLICY)
                remoteStatus(state, BackendSection.WALLET)
                item("policy") { Panel {
                    val policy = state.data.policy
                    Text(stringResource(if (policy == null) R.string.backend_subscription_unknown else if (policy.subscribed) R.string.backend_subscription_active else R.string.backend_subscription_inactive),
                        color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
                    policy?.let {
                        Text(stringResource(R.string.backend_trial, it.trial), color = Ds.colors.labelSecondary)
                        Text(stringResource(if (it.canGenerate) R.string.backend_access_ready else R.string.backend_access_restricted), color = Ds.colors.labelSecondary)
                    }
                } }
                remoteStatus(state, BackendSection.PRODUCTS)
                items(state.data.products, key = { it.id }) { product -> Panel {
                    Text(product.title ?: stringResource(R.string.backend_product), color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
                    product.credits?.let { Text(stringResource(R.string.backend_credits, it), color = Ds.colors.labelSecondary) }
                } }
                item("payments_notice") { Notice(R.string.backend_payments_later) }
                item("demo") { DsButton(stringResource(R.string.backend_back_demo), Modifier.padding(horizontal = 16.dp).testTag("backend_demo"), onClick = demo) }
            }
        }
    }
}
private fun LazyListScope.remoteStatus(state: BackendState, section: BackendSection) {
    if (section in state.loading) item("loading_" + section.name) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = Ds.colors.accentPrimary)
    }
    state.errors[section]?.let { error -> item("error_" + section.name) { FailureText(error, Modifier.padding(horizontal = 16.dp)) } }
}
private fun LazyListScope.templateRows(templates: List<RemoteTemplate>, kind: String, state: BackendState,
    favorite: (String) -> Unit, effect: (String, String) -> Unit, loaded: Boolean) {
    if (templates.isEmpty() && loaded) item("empty_templates") { Notice(R.string.backend_empty) }
    items(templates.chunked(2), key = { it.first().id }) { pair ->
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { template ->
                Box(Modifier.weight(1f).aspectRatio(1f / 1.78f).clip(RoundedCornerShape(20.dp))
                    .clickable { effect(kind, template.id) }.testTag("remote_template_" + template.id)) {
                    RemoteImage(template.cover, Modifier.matchParentSize())
                    Text(template.title, Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Ds.colors.backgroundPrimaryAlpha).padding(12.dp),
                        color = Ds.colors.labelPrimary, style = Ds.type.caption1Regular)
                    val liked = template.id in state.favorites
                    RoundAction(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                        stringResource(if (liked) R.string.remove_favorite else R.string.add_favorite) + ": " + template.title,
                        Modifier.align(Alignment.TopEnd).padding(6.dp), onClick = { favorite(template.id) })
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
@Composable private fun TemplateDetail(template: RemoteTemplate?, state: BackendState, nav: NavHostController, favorite: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(template?.title ?: stringResource(R.string.backend_creation), { nav.popBackStack() }) { RemoteBalance(state) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (template == null) item { Notice(R.string.backend_empty) } else {
                item { RemoteImage(template.cover, Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(1f / 1.2f).clip(RoundedCornerShape(24.dp))) }
                item { Panel {
                    Text(template.title, color = Ds.colors.labelPrimary, style = Ds.type.title2Emphasized)
                    Text(stringResource(R.string.backend_credits, template.tokens), color = Ds.colors.accentPrimary)
                    Text(stringResource(R.string.backend_reference_count, template.requiredImages), color = Ds.colors.labelSecondary)
                    DsButton(stringResource(if (template.id in state.favorites) R.string.remove_favorite else R.string.add_favorite)) { favorite(template.id) }
                } }
                item { Notice(R.string.backend_generation_later) }
                item { DsButton(stringResource(R.string.use_effect), Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("backend_generate"), primary = true, enabled = false) {} }
            }
        }
    }
}
@Composable private fun KindTabs(selected: Int, change: (Int) -> Unit) = Segmented(
    listOf(stringResource(R.string.photos), stringResource(R.string.videos)), listOf(R.drawable.ic_photo, R.drawable.ic_video), selected, change)
@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ds.colors.backgroundSecondary).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable private fun Notice(message: Int) {
    Text(stringResource(message), Modifier.padding(horizontal = 16.dp), color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
}
@Composable private fun FailureText(error: BackendFailure, modifier: Modifier = Modifier) {
    val message = when {
        error.status == 401 -> R.string.backend_session_expired
        error.status == 403 -> R.string.backend_access_restricted
        error.status == 429 -> R.string.backend_rate_limited
        error.status == 503 -> R.string.backend_unavailable
        error.code == "session_storage" || error.code == "identity_mismatch" -> R.string.backend_session_problem
        error.code == "network" -> R.string.backend_network_error
        else -> R.string.backend_data_error
    }
    Text(stringResource(message), modifier, color = Ds.colors.accentRed, style = Ds.type.subheadlineRegular)
}
