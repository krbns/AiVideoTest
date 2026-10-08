package com.rslnabk.aivideotest.ui.backend

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.MainActivity
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.data.media.ExportController
import com.rslnabk.aivideotest.data.media.PhotoThumbnail
import com.rslnabk.aivideotest.model.ExportDestination
import com.rslnabk.aivideotest.model.ExportPhase
import com.rslnabk.aivideotest.ui.common.DsButton
import com.rslnabk.aivideotest.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun RemotePhotoEditor(catalog: BackendState, state: PhotoState, controller: PhotoGenerationController,
    host: MainActivity, reconnect: () -> Unit) {
    val models = PhotoRequest.models(catalog.data, state.draft)
    val model = models.find { it.id == state.draft.modelId }
    LaunchedEffect(models, state.draft.mode) {
        if (state.canEdit && models.isNotEmpty()) controller.edit { PhotoRequest.defaults(it, model ?: models.first()) }
    }
    val mode = model?.let { PhotoRequest.mode(it, state.draft) }
    val focus = LocalFocusManager.current
    var chooseModel by remember { mutableStateOf(false) }
    var acknowledge by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.describe_idea), color = Ds.colors.accentPrimary, style = Ds.type.title3Emphasized)
        TextField(state.draft.prompt, { value -> controller.edit { it.copy(prompt = value) } },
            Modifier.fillMaxWidth().heightIn(min = 130.dp).testTag("remote_prompt"), enabled = state.canEdit,
            placeholder = { Text(stringResource(R.string.prompt_hint)) }, textStyle = Ds.type.bodyRegular,
            colors = TextFieldDefaults.colors(focusedContainerColor = Ds.colors.backgroundSecondary,
                unfocusedContainerColor = Ds.colors.backgroundSecondary, focusedTextColor = Ds.colors.labelPrimary,
                unfocusedTextColor = Ds.colors.labelPrimary, cursorColor = Ds.colors.accentPrimary))
        Text(stringResource(R.string.backend_prompt_length, state.draft.prompt.codePointCount(0, state.draft.prompt.length)),
            color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
        Box {
            DsButton(model?.title ?: stringResource(R.string.backend_choose_model), Modifier.testTag("remote_model"),
                enabled = state.canEdit && models.isNotEmpty()) { chooseModel = true }
            DropdownMenu(chooseModel, { chooseModel = false }) {
                models.forEach { candidate -> DropdownMenuItem({ Text(candidate.title) }, {
                    controller.edit { PhotoRequest.defaults(it.copy(resolution = null, aspectRatio = null, outputFormat = null), candidate) }
                    chooseModel = false
                }) }
            }
        }
        if (models.isEmpty()) Text(stringResource(R.string.backend_no_photo_models), color = Ds.colors.labelTertiary)
        PhotoOptions(R.string.resolutions, mode?.resolutions.orEmpty(), state.draft.resolution, state.canEdit) { value -> controller.edit { it.copy(resolution = value) } }
        PhotoOptions(R.string.backend_aspect_ratio, mode?.aspectRatios.orEmpty(), state.draft.aspectRatio, state.canEdit) { value -> controller.edit { it.copy(aspectRatio = value) } }
        PhotoOptions(R.string.backend_output_format, mode?.outputFormats.orEmpty(), state.draft.outputFormat, state.canEdit) { value -> controller.edit { it.copy(outputFormat = value) } }
        val context = LocalContext.current
        state.draft.photos.forEachIndexed { index, reference ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LocalPhotoImage(File(context.filesDir, "backend_reference_photos/" + reference.removePrefix("file:")), Modifier.fillMaxWidth().height(160.dp))
                DsButton(stringResource(R.string.backend_remove_reference, index + 1), enabled = state.canEdit) { controller.removePhoto(reference) }
            }
        }
        val remaining = (model?.maxImages ?: catalog.data.models.filter { it.kind == "image" && it.modes.any { m -> m.name == "imageToImage" } }.maxOfOrNull { it.maxImages } ?: 0) - state.draft.photos.size
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DsButton(stringResource(R.string.choose_photo), enabled = state.canEdit && remaining > 0) { focus.clearFocus(); host.pickBackendPhoto() }
            DsButton(stringResource(R.string.backend_camera), enabled = state.canEdit && remaining > 0) { focus.clearFocus(); host.takeBackendPhoto() }
        }
        Text(stringResource(R.string.backend_reference_privacy), color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
        if (state.importing || state.phase in listOf(PhotoPhase.UPLOADING, PhotoPhase.SENDING)) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ds.colors.accentPrimary)
            Text(stringResource(if (state.importing) R.string.backend_importing else if (state.phase == PhotoPhase.UPLOADING) R.string.backend_uploading else R.string.backend_submitting), color = Ds.colors.labelSecondary)
        }
        if (state.phase == PhotoPhase.UNKNOWN) {
            Text(stringResource(R.string.backend_unknown_submission), Modifier.testTag("remote_unknown"), color = Ds.colors.accentRed)
            PhotoRecoveryChoices(state, catalog, controller)
            DsButton(stringResource(R.string.backend_acknowledge_unknown), enabled = !state.recovery.loading) { acknowledge = true }
        }
        if (state.failure != null && state.phase != PhotoPhase.UNKNOWN) PhotoFailure(state.failure)
        if (state.failure?.status == 401) DsButton(stringResource(R.string.backend_reconnect), onClick = reconnect)
        val price = model?.let { PhotoRequest.cost(it, state.draft) }
        if (price != null) Text(stringResource(R.string.backend_estimated_cost, price), color = Ds.colors.labelSecondary)
        if ((catalog.data.policy?.trial ?: 0) > 0) Text(stringResource(R.string.backend_trial_notice), color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
        if (!PhotoRequest.allowed(catalog.data)) Text(stringResource(R.string.backend_access_restricted), color = Ds.colors.accentRed)
        val fresh = BackendSection.MODELS in catalog.loaded && BackendSection.MODELS !in catalog.cached && catalog.authError == null
        DsButton(stringResource(R.string.backend_generate_photo), Modifier.fillMaxWidth().testTag("remote_generate"), primary = true,
            enabled = fresh && PhotoRequest.allowed(catalog.data) && state.canEdit && PhotoRequest.ready(model, state.draft)) { focus.clearFocus(); controller.submit() }
        if (state.phase == PhotoPhase.ACTIVE) Text(stringResource(R.string.backend_creation_pending), color = Ds.colors.labelSecondary)
    }
    if (acknowledge) AlertDialog(onDismissRequest = { acknowledge = false }, title = { Text(stringResource(R.string.backend_check_history)) },
        text = { Text(stringResource(R.string.backend_new_request_warning)) },
        confirmButton = { TextButton({ controller.acknowledgeUnknown(); acknowledge = false }) { Text(stringResource(R.string.backend_checked_history)) } },
        dismissButton = { TextButton({ acknowledge = false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun PhotoRecoveryChoices(state: PhotoState, catalog: BackendState, controller: PhotoGenerationController) {
    val recovery = state.recovery
    var selected by remember { mutableStateOf<RemoteJob?>(null) }
    Text(stringResource(R.string.backend_recovery_notice), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
    if (state.submission == null) Text(stringResource(R.string.backend_recovery_legacy), color = Ds.colors.labelTertiary)
    if (recovery.loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ds.colors.accentPrimary)
        Text(stringResource(R.string.backend_recovery_loading), color = Ds.colors.labelSecondary)
    }
    recovery.failure?.let { PhotoFailure(it) }
    DsButton(stringResource(R.string.backend_recovery_search), Modifier.testTag("remote_recovery_search"), enabled = !recovery.loading) { controller.searchRecovery() }
    if (recovery.searched && !recovery.loading && recovery.failure == null && recovery.candidates.isEmpty())
        Text(stringResource(R.string.backend_recovery_empty), Modifier.testTag("remote_recovery_empty"), color = Ds.colors.labelTertiary)
    recovery.candidates.forEach { candidate ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ds.colors.backgroundSecondary).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(catalog.data.models.find { it.id == candidate.model }?.title ?: candidate.model,
                color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
            Text(candidateTime(candidate), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
            Text(stringResource(when (candidate.status) { "completed" -> R.string.job_ready; "failed" -> R.string.job_failed;
                "queued", "running" -> R.string.job_running; else -> R.string.backend_unknown_status }), color = Ds.colors.labelSecondary)
            Text(stringResource(R.string.backend_actual_cost, candidate.charged), color = Ds.colors.labelSecondary)
            Text(candidate.id, color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
            if (PhotoRecovery.incomplete(candidate, state.submission)) Text(stringResource(R.string.backend_recovery_incomplete), color = Ds.colors.accentPrimary)
            DsButton(stringResource(R.string.backend_recovery_choose), Modifier.testTag("remote_recovery_" + candidate.id), enabled = !recovery.loading) { selected = candidate }
        }
    }
    if (recovery.nextCursor != null) DsButton(stringResource(R.string.backend_recovery_more), Modifier.testTag("remote_recovery_more"), enabled = !recovery.loading) { controller.searchRecovery(more = true) }
    selected?.let { candidate ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(stringResource(R.string.backend_recovery_confirm_title)) },
            text = { Text(stringResource(R.string.backend_recovery_confirm_body, candidateTime(candidate), candidate.id, candidate.charged)) },
            confirmButton = { TextButton({ selected = null; controller.recoverJob(candidate.id) }, Modifier.testTag("remote_recovery_confirm")) { Text(stringResource(R.string.backend_recovery_confirm)) } },
            dismissButton = { TextButton({ selected = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
private fun candidateTime(job: RemoteJob): String = PhotoRecovery.time(job.createdAt)?.let {
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it))
} ?: "—"
@Composable private fun PhotoOptions(title: Int, values: List<String>, selected: String?, enabled: Boolean, change: (String) -> Unit) {
    if (values.isEmpty()) return
    Text(stringResource(title), color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(value == selected, { change(value) }, enabled = enabled, label = { Text(value) }) }
    }
}
@Composable fun RemotePhotoResult(state: PhotoState, controller: PhotoGenerationController, exports: ExportController, host: MainActivity) {
    val job = state.job
    val export by exports.state.observeAsState()
    LaunchedEffect(job?.id, job?.status) { if (job?.status == "completed") controller.download() }
    LaunchedEffect(export?.id, export?.phase) { if (export?.phase == ExportPhase.SHARE_READY) host.shareBackendReady() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (job == null) { Text(stringResource(R.string.backend_empty), color = Ds.colors.labelSecondary); return@Column }
        Text(job.prompt, color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
        Text(stringResource(when (job.status) { "queued", "running" -> R.string.job_running; "completed" -> R.string.job_ready; "failed" -> R.string.job_failed; else -> R.string.backend_unknown_status }),
            color = Ds.colors.labelSecondary, modifier = Modifier.testTag("remote_job_status"))
        if (job.status in listOf("queued", "running")) {
            LinearProgressIndicator(progress = { job.progress }, Modifier.fillMaxWidth(), color = Ds.colors.accentPrimary)
            Text(stringResource(R.string.backend_polling_notice), color = Ds.colors.labelTertiary)
        }
        Text(stringResource(R.string.backend_actual_cost, job.charged), color = Ds.colors.accentPrimary)
        if (job.refunded) Text(stringResource(R.string.backend_credits_refunded), color = Ds.colors.labelSecondary)
        if (state.failure != null) { PhotoFailure(state.failure); DsButton(stringResource(R.string.refresh), onClick = controller::refreshJob) }
        if (job.status == "failed") Text(stringResource(if (job.errorCode == "canceled") R.string.backend_job_canceled else R.string.backend_generation_failed), color = Ds.colors.accentRed)
        if (state.downloading) { CircularProgressIndicator(color = Ds.colors.accentPrimary); Text(stringResource(R.string.backend_downloading), color = Ds.colors.labelSecondary) }
        state.local?.let { LocalPhotoImage(it.file, Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 500.dp).aspectRatio(1f)) }
        state.downloadError?.let { PhotoFailure(it); DsButton(stringResource(R.string.retry), onClick = controller::download) }
        if (job.status == "completed") {
            val ready = state.local != null && export?.busy != true
            DsButton(stringResource(R.string.save_gallery), Modifier.fillMaxWidth().testTag("remote_save_gallery"), enabled = ready) { host.exportBackendPhoto(ExportDestination.GALLERY) }
            DsButton(stringResource(R.string.save_files), Modifier.fillMaxWidth(), enabled = ready) { host.exportBackendPhoto(ExportDestination.FILES) }
            DsButton(stringResource(R.string.share), Modifier.fillMaxWidth(), enabled = ready) { host.exportBackendPhoto(ExportDestination.SHARE) }
        }
        export?.takeIf { it.jobId == job.id }?.let { operation ->
            if (operation.phase == ExportPhase.WRITING) CircularProgressIndicator(color = Ds.colors.accentPrimary)
            if (operation.phase == ExportPhase.SUCCEEDED) Text(stringResource(R.string.backend_saved_result), color = Ds.colors.accentPrimary)
            operation.message?.let { Text(stringResource(it), color = Ds.colors.accentRed) }
        }
    }
}
@Composable fun LocalPhotoImage(file: File, modifier: Modifier) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp)).background(Ds.colors.backgroundSecondary)) {
        val width = constraints.maxWidth.coerceAtLeast(1); val height = constraints.maxHeight.coerceAtLeast(1)
        val bitmap by produceState<android.graphics.Bitmap?>(null, file.path, width, height) {
            value = withContext(Dispatchers.IO) { runCatching { PhotoThumbnail.decode(file, width, height) }.getOrNull() }
        }
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}
@Composable fun PhotoFailure(failure: BackendFailure) {
    val message = when {
        failure.code in listOf("recovery_changed", "recovery_cursor") -> R.string.backend_recovery_changed
        failure.code in listOf("invalid_image", "unsupported_image", "no_output") -> R.string.backend_invalid_result
        failure.status == 409 -> R.string.backend_insufficient_credits
        failure.status == 410 || failure.code == "result_expired" -> R.string.backend_result_expired
        failure.status == 404 -> if (failure.code == "download") R.string.result_missing else R.string.backend_job_missing
        failure.status == 401 -> R.string.backend_session_expired
        failure.status == 429 -> R.string.backend_rate_limited
        failure.status == 503 -> R.string.backend_unavailable
        failure.status == 422 -> R.string.backend_invalid_settings
        failure.code == "photo_import" -> R.string.photo_failed
        failure.code == "interrupted" -> R.string.backend_upload_interrupted
        failure.code == "photo_journal" -> R.string.backend_journal_error
        else -> R.string.backend_network_error
    }
    Text(stringResource(message), color = Ds.colors.accentRed, style = Ds.type.subheadlineRegular)
}
