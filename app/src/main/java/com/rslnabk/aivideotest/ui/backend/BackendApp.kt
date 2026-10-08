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
import com.rslnabk.aivideotest.MainActivity
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.model.AppTab
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppTabBar
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun BackendApp(model: AppViewModel, host: MainActivity) {
    val state by model.backend.state.observeAsState(BackendState())
    val photo by model.backendPhotos.state.observeAsState(PhotoState())
    val video by model.backendVideos.state.observeAsState(PhotoState(draft = PhotoDraft(kind = "video")))
    DisposableEffect(state.userId) {
        model.backendPhotos.activate(state.userId); model.backendVideos.activate(state.userId)
        onDispose { model.backendPhotos.pause(); model.backendVideos.pause() }
    }
    BackendWorkspace(state, { model.backend.connect(it) }, model.backend::useDemo,
        model.backend::toggleFavorite, model.backend::loadMore,
        photoEditor = { RemotePhotoEditor(state, photo, model.backendPhotos, host, { model.backend.connect(true) }) },
        photoResult = { editor, removed -> RemotePhotoResult(photo, model.backendPhotos, model.backendExports, host, editor, removed) },
        photoJob = model.backendPhotos::openJob, submissionJobId = photo.submissionJobId, unknownPhoto = photo.phase == PhotoPhase.UNKNOWN,
        videoEditor = { RemotePhotoEditor(state, video, model.backendVideos, host, { model.backend.connect(true) }) },
        videoResult = { editor, removed -> RemotePhotoResult(video, model.backendVideos, model.backendExports, host, editor, removed) },
        videoJob = model.backendVideos::openJob, videoSubmissionId = video.submissionJobId, unknownVideo = video.phase == PhotoPhase.UNKNOWN,
        photoSentAt = photo.submission?.sentAt ?: 0, videoSentAt = video.submission?.sentAt ?: 0,
        templateCreate = { template -> (if (template.pipeline == "image") model.backendPhotos else model.backendVideos).configureTemplate(template) },
        promptSelected = { kind -> (if (kind == "video") model.backendVideos else model.backendPhotos).usePrompt() },
        canCreateEffect = { template -> (if (template.pipeline == "image") photo else video).canEdit && PhotoRequest.templateSupported(template, state.data) })
}
/** Server screens share the five destinations and existing design tokens. */
@Composable fun BackendWorkspace(state: BackendState, refresh: (Boolean) -> Unit, demo: () -> Unit,
    favorite: (String) -> Unit, loadMore: () -> Unit,
    photoEditor: (@Composable () -> Unit)? = null, photoResult: (@Composable (() -> Unit, () -> Unit) -> Unit)? = null,
    photoJob: (RemoteJob) -> Unit = {}, submissionJobId: String? = null, unknownPhoto: Boolean = false,
    videoEditor: (@Composable () -> Unit)? = null, videoResult: (@Composable (() -> Unit, () -> Unit) -> Unit)? = null,
    videoJob: (RemoteJob) -> Unit = {}, videoSubmissionId: String? = null, unknownVideo: Boolean = false,
    templateCreate: (RemoteTemplate) -> Boolean = { false }, promptSelected: (String) -> Unit = {},
    canCreateEffect: (RemoteTemplate) -> Boolean = { false }, photoSentAt: Long = 0, videoSentAt: Long = 0) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val detail = entry?.destination?.route in listOf("backend/effect/{kind}/{id}", "backend/effect-editor/{kind}/{id}", "backend/photo-job", "backend/video-job")
    var modeRequest by remember { mutableStateOf<Pair<AppTab, Int>?>(null) }
    val tab = AppTab.entries.find { entry?.destination?.route == "backend/" + it.name } ?: AppTab.VIDEO
    fun select(next: AppTab) = nav.navigate("backend/" + next.name) {
        popUpTo("backend/VIDEO") { saveState = true }; launchSingleTop = true; restoreState = true
    }
    BackHandler(!detail && tab != AppTab.VIDEO) { select(AppTab.VIDEO) }
    var seenPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var seenVideo by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(submissionJobId, videoSubmissionId) {
        val newPhoto = submissionJobId != null && submissionJobId != seenPhoto
        val newVideo = videoSubmissionId != null && videoSubmissionId != seenVideo
        seenPhoto = submissionJobId; seenVideo = videoSubmissionId
        if (newPhoto || newVideo) {
            val video = newVideo && (!newPhoto || videoSentAt > photoSentAt)
            nav.navigate(if (video) "backend/video-job" else "backend/photo-job") { launchSingleTop = true }
        }
    }
    LaunchedEffect(unknownPhoto) { if (unknownPhoto) select(AppTab.PHOTO) }
    LaunchedEffect(unknownVideo) { if (unknownVideo) select(AppTab.VIDEO) }
    Column(Modifier.fillMaxSize().background(Ds.colors.backgroundPrimary).safeDrawingPadding()) {
        NavHost(nav, "backend/VIDEO", Modifier.weight(1f)) {
            AppTab.entries.forEach { current -> composable("backend/" + current.name) {
                Column(Modifier.fillMaxSize()) {
                    ScreenHeader(stringResource(current.title)) { RemoteBalance(state) }
                    BackendBody(current, state, refresh, demo, favorite, loadMore, photoEditor, videoEditor, unknownPhoto, unknownVideo,
                        promptSelected, modeRequest, { modeRequest = null }, { job ->
                        if (job.kind == "video") videoJob(job) else photoJob(job)
                        nav.navigate(if (job.kind == "video") "backend/video-job" else "backend/photo-job") { launchSingleTop = true }
                    }) { kind, id ->
                        nav.navigate("backend/effect/" + kind + "/" + Uri.encode(id))
                    }
                }
            } }
            composable("backend/photo-job") {
                Column(Modifier.fillMaxSize()) {
                    ScreenHeader(stringResource(R.string.photo_result), { nav.popBackStack() }) { RemoteBalance(state) }
                    photoResult?.invoke({ modeRequest = AppTab.PHOTO to 1; select(AppTab.PHOTO) },
                        { modeRequest = AppTab.LIBRARY to 0; select(AppTab.LIBRARY) })
                }
            }
            composable("backend/video-job") {
                Column(Modifier.fillMaxSize()) {
                    ScreenHeader(stringResource(R.string.backend_video_result), { nav.popBackStack() }) { RemoteBalance(state) }
                    videoResult?.invoke({ modeRequest = AppTab.VIDEO to 1; select(AppTab.VIDEO) },
                        { modeRequest = AppTab.LIBRARY to 1; select(AppTab.LIBRARY) })
                }
            }
            composable("backend/effect-editor/{kind}/{id}") { stack ->
                val kind = stack.arguments!!.getString("kind")!!
                val template = (state.data.photos + state.data.videos).find { it.id == stack.arguments!!.getString("id") }
                Column(Modifier.fillMaxSize()) {
                    ScreenHeader(template?.title ?: stringResource(R.string.backend_creation), { nav.popBackStack() }) { RemoteBalance(state) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                        if (kind == "video") videoEditor?.invoke() else photoEditor?.invoke()
                    }
                }
            }
            composable("backend/effect/{kind}/{id}") { stack ->
                val kind = stack.arguments!!.getString("kind")!!
                val templates = if (kind == "image") state.data.photos else state.data.videos
                val template = templates.find { it.id == stack.arguments!!.getString("id") }
                TemplateDetail(template, state, nav, favorite, template?.let(canCreateEffect) == true) {
                    if (template != null && templateCreate(template)) nav.navigate("backend/effect-editor/" +
                        (if (template.pipeline == "image") "image" else "video") + "/" + Uri.encode(template.id))
                }
            }
        }
        if (!detail) AppTabBar(tab) { select(it) }
    }
}
private val BackendState.creditBalance: Int? get() = data.wallet ?: data.policy?.credits
private val BackendState.balanceCached: Boolean get() =
    (if (data.wallet != null) BackendSection.WALLET else BackendSection.POLICY) in cached

@Composable private fun RemoteBalance(state: BackendState) {
    val value = state.creditBalance
    val cached = state.balanceCached
    val label = if (value == null) stringResource(R.string.backend_balance_unknown) else
        stringResource(if (cached) R.string.backend_saved_balance else if (state.data.policy?.subscribed == true) R.string.pro_balance_value else R.string.balance_value, value)
    Box(Modifier.clip(RoundedCornerShape(22.dp)).background(Ds.colors.backgroundSecondary)
        .padding(horizontal = 16.dp, vertical = 12.dp).testTag("backend_balance")
        .semantics { contentDescription = label }) {
        Text(label, color = Ds.colors.accentPrimary, style = Ds.type.headlineEmphasized)
    }
}
@Composable private fun ProfileBalance(state: BackendState) {
    val value = state.creditBalance
    val loading = state.connecting || BackendSection.WALLET in state.loading || BackendSection.POLICY in state.loading
    Text(stringResource(R.string.backend_credit_balance), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
    Text(if (value != null) stringResource(R.string.backend_credits, value) else
        stringResource(if (loading) R.string.backend_balance_loading else R.string.backend_balance_unavailable),
        Modifier.testTag("backend_profile_balance"),
        color = if (value != null) Ds.colors.accentPrimary else Ds.colors.labelSecondary,
        style = Ds.type.title3Emphasized)
    if (value != null && state.balanceCached) Text(stringResource(R.string.backend_balance_saved_notice),
        Modifier.testTag("backend_profile_balance_saved"), color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
}
@Composable private fun BackendBody(tab: AppTab, state: BackendState, refresh: (Boolean) -> Unit,
    demo: () -> Unit, favorite: (String) -> Unit, loadMore: () -> Unit,
    photoEditor: (@Composable () -> Unit)?, videoEditor: (@Composable () -> Unit)?, unknownPhoto: Boolean, unknownVideo: Boolean,
    promptSelected: (String) -> Unit, modeRequest: Pair<AppTab, Int>?, modeHandled: () -> Unit, photoJob: (RemoteJob) -> Unit,
    effect: (String, String) -> Unit) {
    var mode by rememberSaveable(tab) { mutableIntStateOf(if (tab == AppTab.PHOTO && unknownPhoto || tab == AppTab.VIDEO && unknownVideo) 1 else 0) }
    LaunchedEffect(unknownPhoto, unknownVideo) { if (tab == AppTab.PHOTO && unknownPhoto || tab == AppTab.VIDEO && unknownVideo) mode = 1 }
    LaunchedEffect(modeRequest) { if (modeRequest?.first == tab) { mode = modeRequest.second; modeHandled() } }
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
                    listOf(R.drawable.ic_sparkle, R.drawable.ic_prompt), mode) { mode = it; if (it == 1) promptSelected(kind) } }
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
                    if (tab == AppTab.PHOTO && photoEditor != null) item("photo_editor") { photoEditor() }
                    else if (tab == AppTab.VIDEO && videoEditor != null) item("video_editor") { videoEditor() }
                    else {
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
                item("history_notice") { Notice(if (videoEditor != null) R.string.backend_media_history else if (photoEditor != null) R.string.backend_photo_history else R.string.backend_history_readonly) }
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
                    if (job.kind == "image" && photoEditor != null || job.kind == "video" && videoEditor != null) DsButton(stringResource(R.string.backend_open_creation)) { photoJob(job) }
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
                    ProfileBalance(state)
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
@Composable private fun TemplateDetail(template: RemoteTemplate?, state: BackendState, nav: NavHostController, favorite: (String) -> Unit, canCreate: Boolean = false, create: () -> Unit = {}) {
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
                if (!canCreate) item { Notice(R.string.backend_effect_unavailable) }
                item { DsButton(stringResource(R.string.use_effect), Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("backend_generate"), primary = true, enabled = canCreate, onClick = create) }
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
