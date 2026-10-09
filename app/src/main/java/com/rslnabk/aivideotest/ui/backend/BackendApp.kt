@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.rslnabk.aivideotest.AppViewModel
import com.rslnabk.aivideotest.BuildConfig
import com.rslnabk.aivideotest.MainActivity
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.model.AppTab
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.catalog.EmptyPanel
import com.rslnabk.aivideotest.ui.navigation.AppTabBar
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun BackendApp(model: AppViewModel, host: MainActivity) {
    val state by model.backend.state.observeAsState(BackendState())
    val photo by model.backendPhotos.state.observeAsState(PhotoState())
    val video by model.backendVideos.state.observeAsState(PhotoState(draft = PhotoDraft(kind = "video")))
    val cacheBytes by model.backendCacheBytes.observeAsState(0L)
    val cacheBusy by model.backendCacheBusy.observeAsState(false)
    val cacheResult by model.backendCacheResult.observeAsState()
    var coverBytes by remember { mutableLongStateOf(0L) }
    DisposableEffect(state.userId) {
        model.backendPhotos.activate(state.userId); model.backendVideos.activate(state.userId)
        onDispose { model.backendPhotos.pause(); model.backendVideos.pause() }
    }
    BackendWorkspace(state, { model.backend.connect(it) }, model.backend::useDemo,
        model.backend::toggleFavorite, model.backend::loadMore,
        photoEditor = { RemotePhotoEditor(state, photo, model.backendPhotos, host, { model.backend.connect(true) }) },
        photoResult = { editor, removed, back -> RemotePhotoResult(photo, model.backendPhotos, model.backendExports, host, editor, removed, back) },
        photoJob = model.backendPhotos::openJob, submissionJobId = photo.submissionJobId, unknownPhoto = photo.phase == PhotoPhase.UNKNOWN,
        videoEditor = { RemotePhotoEditor(state, video, model.backendVideos, host, { model.backend.connect(true) }) },
        videoResult = { editor, removed, back -> RemotePhotoResult(video, model.backendVideos, model.backendExports, host, editor, removed, back) },
        videoJob = model.backendVideos::openJob, videoSubmissionId = video.submissionJobId, unknownVideo = video.phase == PhotoPhase.UNKNOWN,
        photoSentAt = photo.submission?.sentAt ?: 0, videoSentAt = video.submission?.sentAt ?: 0,
        templateCreate = { template -> (if (template.pipeline == "image") model.backendPhotos else model.backendVideos).configureTemplate(template) },
        promptSelected = { kind -> (if (kind == "video") model.backendVideos else model.backendPhotos).usePrompt() },
        canCreateEffect = { template -> (if (template.pipeline == "image") photo else video).canEdit && PhotoRequest.templateSupported(template, state.data) },
        settings = { RemoteSettings(state, { model.backend.connect(it) }, model.backend::useDemo,
            cacheBytes + coverBytes, cacheBusy, cacheResult,
            refreshCache = { coverBytes = remoteCoverCacheBytes(); model.refreshBackendCache() },
            clearCache = { clearRemoteCoverCache(); coverBytes = 0; model.clearBackendCache() }, acknowledgeCache = model::acknowledgeBackendCache) })
}
/** Server screens share the five destinations and existing design tokens. */
@Composable fun BackendWorkspace(state: BackendState, refresh: (Boolean) -> Unit, demo: () -> Unit,
    favorite: (String) -> Unit, loadMore: () -> Unit,
    photoEditor: (@Composable () -> Unit)? = null, photoResult: (@Composable (() -> Unit, () -> Unit, () -> Unit) -> Unit)? = null,
    photoJob: (RemoteJob) -> Unit = {}, submissionJobId: String? = null, unknownPhoto: Boolean = false,
    videoEditor: (@Composable () -> Unit)? = null, videoResult: (@Composable (() -> Unit, () -> Unit, () -> Unit) -> Unit)? = null,
    videoJob: (RemoteJob) -> Unit = {}, videoSubmissionId: String? = null, unknownVideo: Boolean = false,
    templateCreate: (RemoteTemplate) -> Boolean = { false }, promptSelected: (String) -> Unit = {},
    canCreateEffect: (RemoteTemplate) -> Boolean = { false }, photoSentAt: Long = 0, videoSentAt: Long = 0,
    settings: (@Composable () -> Unit)? = null) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val detail = entry?.destination?.route in listOf("backend/effect/{kind}/{id}", "backend/effect-editor/{kind}/{id}", "backend/photo-job", "backend/video-job", "backend/category/{kind}/{group}")
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
    Column(Modifier.fillMaxSize().background(Ds.colors.backgroundPrimary).safeDrawingPadding().imePadding()) {
        NavHost(nav, "backend/VIDEO", Modifier.weight(1f)) {
            AppTab.entries.forEach { current -> composable("backend/" + current.name) {
                if (current == AppTab.SETTINGS) Column(Modifier.fillMaxSize()) {
                    ScreenHeader(stringResource(current.title)) { RemoteBalance(state) }
                    settings?.invoke() ?: RemoteSettings(state, refresh, demo)
                } else BackendBody(current, state, refresh, demo, favorite, loadMore, photoEditor, videoEditor, unknownPhoto, unknownVideo,
                    promptSelected, modeRequest, { modeRequest = null }, { next, nextMode -> modeRequest = next to nextMode; select(next) },
                    { group, kind -> nav.navigate("backend/category/$kind/" + Uri.encode(group)) }, { job ->
                        if (job.kind == "video") videoJob(job) else photoJob(job)
                        nav.navigate(if (job.kind == "video") "backend/video-job" else "backend/photo-job") { launchSingleTop = true }
                    }) { kind, id -> nav.navigate("backend/effect/$kind/" + Uri.encode(id)) }

            } }
            composable("backend/photo-job") {
                photoResult?.invoke({ modeRequest = AppTab.PHOTO to 1; select(AppTab.PHOTO) },
                    { modeRequest = AppTab.LIBRARY to 0; select(AppTab.LIBRARY) }, { nav.popBackStack() })
            }
            composable("backend/video-job") {
                videoResult?.invoke({ modeRequest = AppTab.VIDEO to 1; select(AppTab.VIDEO) },
                    { modeRequest = AppTab.LIBRARY to 1; select(AppTab.LIBRARY) }, { nav.popBackStack() })
            }
            composable("backend/category/{kind}/{group}") { stack ->
                val kind = stack.arguments!!.getString("kind")!!
                RemoteCategoryScreen(kind, stack.arguments!!.getString("group")!!, state, favorite, { nav.popBackStack() },
                    { type, id -> nav.navigate("backend/effect/$type/" + Uri.encode(id)) }, refresh)
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
        if (!detail && !imeVisible) AppTabBar(tab) { select(it) }
    }
}
internal val BackendState.creditBalance: Int? get() = data.wallet ?: data.policy?.credits
internal val BackendState.balanceCached: Boolean get() =
    (if (data.wallet != null) BackendSection.WALLET else BackendSection.POLICY) in cached

@Composable internal fun RemoteBalance(state: BackendState) {
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

@Composable internal fun ProfileBalance(state: BackendState, compact: Boolean = false) {
    val value = state.creditBalance
    val loading = state.connecting || BackendSection.WALLET in state.loading || BackendSection.POLICY in state.loading
    val label = if (value != null) stringResource(R.string.backend_credits, value) else
        stringResource(if (loading) R.string.backend_balance_loading else R.string.backend_balance_unavailable)
    if (compact) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.backend_credit_balance), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
        Text(label, Modifier.testTag("backend_profile_balance"), color = if (value != null) Ds.colors.accentPrimary else Ds.colors.labelSecondary, style = Ds.type.headlineEmphasized)
    } else {
        Text(stringResource(R.string.backend_credit_balance), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
        Text(label, Modifier.testTag("backend_profile_balance"), color = if (value != null) Ds.colors.accentPrimary else Ds.colors.labelSecondary, style = Ds.type.title3Emphasized)
    }
    if (value != null && state.balanceCached) Text(stringResource(R.string.backend_balance_saved_notice),
        Modifier.testTag("backend_profile_balance_saved"), color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
}

@Composable private fun BackendBody(tab: AppTab, state: BackendState, refresh: (Boolean) -> Unit,
    demo: () -> Unit, favorite: (String) -> Unit, loadMore: () -> Unit,
    photoEditor: (@Composable () -> Unit)?, videoEditor: (@Composable () -> Unit)?, unknownPhoto: Boolean, unknownVideo: Boolean,
    promptSelected: (String) -> Unit, modeRequest: Pair<AppTab, Int>?, modeHandled: () -> Unit,
    select: (AppTab, Int) -> Unit, category: (String, String) -> Unit, photoJob: (RemoteJob) -> Unit,
    effect: (String, String) -> Unit) {
    var mode by rememberSaveable(tab) { mutableIntStateOf(if (tab == AppTab.PHOTO && unknownPhoto || tab == AppTab.VIDEO && unknownVideo) 1 else 0) }
    LaunchedEffect(unknownPhoto, unknownVideo) { if (tab == AppTab.PHOTO && unknownPhoto || tab == AppTab.VIDEO && unknownVideo) mode = 1 }
    LaunchedEffect(modeRequest) { if (modeRequest?.first == tab) { mode = modeRequest.second; modeHandled() } }
    val kind = if (tab == AppTab.VIDEO || (tab in listOf(AppTab.FAVORITES, AppTab.LIBRARY) && mode == 1)) "video" else "image"
    val section = if (kind == "video") BackendSection.VIDEOS else BackendSection.PHOTOS
    val templates = if (kind == "video") state.data.videos else state.data.photos
    val chrome: @Composable () -> Unit = {
        ScreenHeader(stringResource(tab.title)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteBalance(state)
                IconButton({ refresh(state.authError?.status == 401) }, Modifier.testTag("backend_header_refresh"), enabled = !state.connecting && state.loading.isEmpty()) {
                    DsIcon(R.drawable.ic_restore, stringResource(R.string.refresh), Ds.colors.labelTertiary)
                }
            }
        }
        if (tab in listOf(AppTab.PHOTO, AppTab.VIDEO)) Segmented(listOf(stringResource(R.string.trends), stringResource(R.string.prompt)),
            listOf(R.drawable.ic_sparkle, R.drawable.ic_prompt), mode, separate = true) { mode = it; if (it == 1) promptSelected(kind) }
        else KindTabs(mode) { mode = it }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scrollChrome = tab in listOf(AppTab.PHOTO, AppTab.VIDEO) && mode == 1 && maxHeight < 300.dp
        val scroll = rememberLazyListState()
        LaunchedEffect(mode) { scroll.scrollToItem(0) }
        Column(Modifier.fillMaxSize()) {
            if (!scrollChrome) chrome()
            LazyColumn(Modifier.fillMaxSize().testTag("backend_list"), scroll, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (scrollChrome) item("chrome") { chrome() }
                item("connection") { BackendConnectionStatus(state, refresh, demo) }
                when (tab) {
                    AppTab.VIDEO, AppTab.PHOTO -> {
                        if (mode == 0) {
                            remoteStatus(state, section)
                            remoteTrends(templates, state, kind, favorite, effect) { category(it, kind) }
                            if (templates.isEmpty() && section in state.loaded) item("empty_catalog") { BackendNotice(R.string.backend_empty) }
                        } else {
                            remoteStatus(state, BackendSection.MODELS)
                            if (tab == AppTab.PHOTO && photoEditor != null) item("photo_editor") { photoEditor() }
                            else if (tab == AppTab.VIDEO && videoEditor != null) item("video_editor") { videoEditor() }
                            else {
                                item("prompt_notice") { BackendNotice(R.string.backend_generation_later) }
                                items(state.data.models.filter { it.kind == kind }, key = { it.id }) { model -> BackendPanel {
                                    Text(model.title, color = Ds.colors.labelPrimary, style = Ds.type.title3Emphasized)
                                    Text(model.modes.flatMap { it.resolutions }.distinct().joinToString(" · "), color = Ds.colors.labelSecondary)
                                } }
                            }
                        }
                    }
                    AppTab.FAVORITES -> {
                        remoteStatus(state, section)
                        val favorites = templates.filter { it.id in state.favorites }
                        if (favorites.isEmpty() && section in state.loaded) item("empty_favorites") {
                            EmptyPanel(R.drawable.demo_empty_favorites, R.string.empty_favorites_title, R.string.empty_favorites_body, R.string.explore_effects) {
                                select(if (kind == "video") AppTab.VIDEO else AppTab.PHOTO, 0)
                            }
                        } else remoteEffectGrid(favorites, state, kind, favorite, effect)
                        if (favorites.isNotEmpty()) item("local_notice") { BackendNotice(R.string.backend_favorites_local) }
                    }
                    AppTab.LIBRARY -> {
                        remoteStatus(state, BackendSection.JOBS)
                        val jobs = state.data.jobs.filter { it.kind == kind }
                        if (jobs.isEmpty() && BackendSection.JOBS in state.loaded) item("empty_jobs") {
                            EmptyPanel(R.drawable.demo_empty_library, R.string.empty_library_title, R.string.empty_library_body, R.string.start_creating) {
                                val next = if (kind == "video") AppTab.VIDEO else AppTab.PHOTO
                                promptSelected(kind); select(next, 1)
                            }
                        } else remoteJobGrid(jobs, state, if (kind == "video") videoEditor != null else photoEditor != null, photoJob)
                        if (state.data.nextCursor != null) item("more") {
                            DsButton(stringResource(R.string.backend_more_history), Modifier.padding(horizontal = 16.dp),
                                enabled = !state.loadingMore && state.loading.isEmpty(), onClick = loadMore)
                        }
                        state.historyError?.let { failure -> item("history_error") { BackendFailureText(failure, Modifier.padding(horizontal = 16.dp)) } }
                    }
                    AppTab.SETTINGS -> Unit
                }
            }
        }
    }
}

@Composable internal fun BackendConnectionStatus(state: BackendState, refresh: (Boolean) -> Unit, demo: () -> Unit, padded: Boolean = true) {
    if (!state.connecting && state.authError == null && state.cached.isEmpty()) return
    Column(if (padded) Modifier.padding(horizontal = 16.dp) else Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.connecting) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ds.colors.accentPrimary)
            Text(stringResource(R.string.backend_connecting), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
        }
        state.authError?.let { error ->
            BackendFailureText(error)
            DsButton(stringResource(R.string.refresh)) { refresh(error.status == 401) }
            if (BuildConfig.DEBUG) DsButton(stringResource(R.string.backend_back_demo), onClick = demo)
        }
        if (state.cached.isNotEmpty()) Text(stringResource(R.string.backend_saved_data), style = Ds.type.caption1Regular, color = Ds.colors.labelTertiary)
    }
}
internal fun LazyListScope.remoteStatus(state: BackendState, section: BackendSection, padded: Boolean = true) {
    val modifier = if (padded) Modifier.padding(horizontal = 16.dp) else Modifier
    if (section in state.loading) item("loading_" + section.name) { LinearProgressIndicator(modifier.fillMaxWidth(), color = Ds.colors.accentPrimary) }
    state.errors[section]?.let { error -> item("error_" + section.name) { BackendFailureText(error, modifier) } }
}

@Composable private fun TemplateDetail(template: RemoteTemplate?, state: BackendState, nav: NavHostController,
    favorite: (String) -> Unit, canCreate: Boolean = false, create: () -> Unit = {}) {
    var instruction by rememberSaveable(template?.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("", { nav.popBackStack() }) { RemoteBalance(state) }
        BoxWithConstraints(Modifier.weight(1f)) {
            val previewHeight = maxHeight.coerceAtLeast(180.dp)
            LazyColumn(Modifier.fillMaxSize().testTag("backend_detail_list"), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (template == null) item { BackendNotice(R.string.backend_empty) } else {
                    item { Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(32.dp))) {
                        RemoteImage(template.cover, Modifier.matchParentSize())
                        Text(stringResource(R.string.backend_effect_details_hint), Modifier.align(Alignment.BottomStart).padding(16.dp)
                            .background(Ds.colors.backgroundPrimaryAlpha, RoundedCornerShape(12.dp)).padding(8.dp), color = Ds.colors.labelPrimary, style = Ds.type.caption1Regular)
                    } }
                    item { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(template.title, style = Ds.type.title3Emphasized)
                        Text(stringResource(R.string.backend_credits, template.tokens), color = Ds.colors.accentPrimary)
                        Text(stringResource(R.string.backend_reference_count, template.requiredImages), color = Ds.colors.labelSecondary)
                        if (!canCreate) Text(stringResource(R.string.backend_effect_unavailable), color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
                    } }
                }
            }
        }
        template?.let {
            val liked = it.id in state.favorites
            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundAction(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                    stringResource(if (liked) R.string.remove_favorite else R.string.add_favorite), Modifier.testTag("backend_detail_like").semantics { selected = liked },
                    if (liked) Ds.colors.accentPrimary else Ds.colors.labelPrimary) { favorite(it.id) }
                DsButton(stringResource(R.string.use_effect), Modifier.weight(1f).testTag("backend_generate"), primary = true, enabled = canCreate) {
                    if (it.requiredImages > 0) instruction = true else create()
                }
            }
        }
    }
    if (instruction && template != null) RemoteReferenceInstruction(template.requiredImages, { instruction = false }) { instruction = false; create() }
}
@Composable internal fun RemoteReferenceInstruction(required: Int, dismiss: () -> Unit, proceed: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Ds.colors.backgroundPrimary) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.instruction), style = Ds.type.title2Emphasized)
            Text(stringResource(R.string.backend_reference_count, required), color = Ds.colors.labelSecondary)
            Text(stringResource(R.string.good_choice), style = Ds.type.headlineEmphasized)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.drawable.demo_good_1, R.drawable.demo_good_2).forEach { image -> Image(painterResource(image), null, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop) }
            }
            Text(stringResource(R.string.backend_good_reference), style = Ds.type.subheadlineRegular, color = Ds.colors.labelTertiary)
            Text(stringResource(R.string.bad_choice), style = Ds.type.headlineEmphasized)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.drawable.demo_bad_1, R.drawable.demo_bad_2).forEach { image -> Image(painterResource(image), null, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop) }
            }
            Text(stringResource(R.string.backend_bad_reference), style = Ds.type.subheadlineRegular, color = Ds.colors.labelTertiary)
            DsButton(stringResource(R.string.continue_action), Modifier.fillMaxWidth().testTag("backend_instruction_continue"), primary = true, onClick = proceed)
        }
    }
}
@Composable private fun KindTabs(selected: Int, change: (Int) -> Unit) = Segmented(
    listOf(stringResource(R.string.photos), stringResource(R.string.videos)), listOf(R.drawable.ic_photo, R.drawable.ic_video), selected, separate = true, onSelect = change)
@Composable internal fun BackendPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ds.colors.backgroundSecondary).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable internal fun BackendNotice(message: Int) {
    Text(stringResource(message), Modifier.padding(horizontal = 16.dp), color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
}
@Composable internal fun BackendFailureText(error: BackendFailure, modifier: Modifier = Modifier) {
    val message = when {
        error.status == 401 -> R.string.backend_session_expired
        error.status == 403 -> R.string.backend_access_restricted
        error.status == 429 -> R.string.backend_rate_limited
        error.status == 503 -> R.string.backend_unavailable
        error.code in listOf("session_storage", "identity_mismatch") -> R.string.backend_session_problem
        error.code == "network" -> R.string.backend_network_error
        else -> R.string.backend_data_error
    }
    Text(stringResource(message), modifier, color = Ds.colors.accentRed, style = Ds.type.subheadlineRegular)
}
