@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rslnabk.aivideotest.ui.backend

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
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
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable fun RemotePhotoEditor(catalog: BackendState, state: PhotoState, controller: PhotoGenerationController,
    host: MainActivity, reconnect: () -> Unit) {
    val models = PhotoRequest.models(catalog.data, state.draft)
    val model = models.find { it.id == state.draft.modelId }
    val template = PhotoRequest.template(catalog.data, state.draft)
    LaunchedEffect(models, state.draft.mode) {
        if (state.canEdit && state.draft.templateId == null && models.isNotEmpty()) controller.edit { PhotoRequest.defaults(it, model ?: models.first()) }
    }
    val mode = model?.let { PhotoRequest.mode(it, state.draft) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val promptLabel = stringResource(R.string.prompt)
    var chooseModel by remember { mutableStateOf(false) }
    var acknowledge by remember { mutableStateOf(false) }
    var instruction by rememberSaveable { mutableStateOf(false) }
    val remaining = (if (state.draft.templateId != null) template?.requiredImages ?: 0 else model?.maxImages ?: catalog.data.models.filter {
        it.kind == state.draft.kind && it.modes.any { m -> m.name == if (state.draft.kind == "video") "imageToVideo" else "imageToImage" }
    }.maxOfOrNull { it.maxImages } ?: 0) - state.draft.photos.size
    Column(Modifier.padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Ds.colors.backgroundSecondary).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (template == null) R.string.describe_idea else R.string.prepare_photo), color = Ds.colors.accentPrimary, style = Ds.type.title3Emphasized)
            if (state.draft.templateId != null) {
                Text(template?.title ?: stringResource(R.string.backend_empty), color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
                Text(stringResource(R.string.backend_effect_settings), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
                template?.let {
                    Text(stringResource(R.string.backend_reference_count, it.requiredImages), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
                    TextButton({ instruction = true }) { Text(stringResource(R.string.instruction), color = Ds.colors.accentPrimary) }
                }
            } else {
                TextField(state.draft.prompt, { value -> controller.edit { it.copy(prompt = value) } },
                    Modifier.fillMaxWidth().heightIn(min = 130.dp).testTag("remote_prompt"), enabled = state.canEdit,
                    placeholder = { Text(stringResource(R.string.prompt_hint), style = Ds.type.subheadlineRegular) }, textStyle = Ds.type.subheadlineRegular,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Ds.colors.backgroundSecondary,
                        unfocusedContainerColor = Ds.colors.backgroundSecondary, disabledContainerColor = Ds.colors.backgroundSecondary,
                        focusedTextColor = Ds.colors.labelPrimary, unfocusedTextColor = Ds.colors.labelPrimary, cursorColor = Ds.colors.accentPrimary,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent))
                val count = state.draft.prompt.codePointCount(0, state.draft.prompt.length)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.backend_prompt_length, count), Modifier.testTag("remote_prompt_counter"),
                        color = if (count > 10000) Ds.colors.accentRed else Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
                    if (state.draft.prompt.isNotEmpty()) {
                        TextButton({ (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText(promptLabel, state.draft.prompt)); host.toast(R.string.copied) }, Modifier.testTag("remote_copy_prompt")) {
                            Text(stringResource(R.string.copy), color = Ds.colors.accentPrimary)
                        }
                        TextButton({ controller.edit { it.copy(prompt = "") } }, Modifier.testTag("remote_clear_prompt"), enabled = state.canEdit) { Text(stringResource(R.string.clear_prompt), color = Ds.colors.accentPrimary) }
                    }
                }
            }
            if (state.draft.photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.draft.photos, key = { _, reference -> reference }) { index, reference ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LocalPhotoImage(File(context.filesDir, "backend_reference_photos/" + reference.removePrefix("file:")), Modifier.size(80.dp))
                        TextButton({ controller.removePhoto(reference) }, enabled = state.canEdit, modifier = Modifier.testTag("remote_remove_reference_$index")) {
                            Text(stringResource(R.string.backend_remove_reference, index + 1), color = Ds.colors.accentPrimary, style = Ds.type.caption1Regular)
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                DsButton(stringResource(R.string.choose_photo), Modifier.testTag("remote_choose_photo"), enabled = state.canEdit && remaining > 0) { focus.clearFocus(); host.pickBackendPhoto(state.draft.kind) }
                DsButton(stringResource(R.string.backend_camera), enabled = state.canEdit && remaining > 0) { focus.clearFocus(); host.takeBackendPhoto(state.draft.kind) }
            }
        }
        if (state.draft.templateId == null) {
            Text(stringResource(R.string.backend_model_label), color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
            Box {
                DsButton(model?.title ?: stringResource(R.string.backend_choose_model), Modifier.fillMaxWidth().testTag("remote_model"),
                    enabled = state.canEdit && models.isNotEmpty()) { chooseModel = true }
                DropdownMenu(chooseModel, { chooseModel = false }, containerColor = Ds.colors.backgroundSecondary) {
                    models.forEach { candidate -> DropdownMenuItem({ Text(candidate.title) }, {
                        controller.edit { PhotoRequest.defaults(it.copy(resolution = null, aspectRatio = null, outputFormat = null), candidate) }; chooseModel = false
                    }) }
                }
            }
            if (models.isEmpty()) Text(stringResource(R.string.backend_no_photo_models), color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
            PhotoOptions(R.string.resolutions, mode?.resolutions.orEmpty(), state.draft.resolution, state.canEdit) { value -> controller.edit { it.copy(resolution = value) } }
            PhotoOptions(R.string.backend_aspect_ratio, mode?.aspectRatios.orEmpty(), state.draft.aspectRatio, state.canEdit) { value -> controller.edit { it.copy(aspectRatio = value) } }
            PhotoOptions(R.string.backend_output_format, mode?.outputFormats.orEmpty(), state.draft.outputFormat, state.canEdit) { value -> controller.edit { it.copy(outputFormat = value) } }
            PhotoOptions(R.string.backend_duration, mode?.durations.orEmpty(), state.draft.duration, state.canEdit) { value -> controller.edit { it.copy(duration = value) } }
            if (state.draft.generateAudio != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.backend_generate_audio), Modifier.weight(1f), color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
                Switch(state.draft.generateAudio, { value -> controller.edit { it.copy(generateAudio = value) } }, enabled = state.canEdit, modifier = Modifier.testTag("remote_audio"),
                    colors = SwitchDefaults.colors(checkedTrackColor = Ds.colors.accentPrimary, checkedThumbColor = Ds.colors.labelPrimaryInverted))
            }
        }
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
        val price = PhotoRequest.cost(catalog.data, state.draft)
        if (price != null) Text(stringResource(R.string.backend_estimated_cost, price), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
        if ((catalog.data.policy?.trial ?: 0) > 0) Text(stringResource(R.string.backend_trial_notice), color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
        if (!PhotoRequest.allowed(catalog.data)) Text(stringResource(R.string.backend_access_restricted), color = Ds.colors.accentRed, style = Ds.type.subheadlineRegular)
        val section = if (state.draft.kind == "video") BackendSection.VIDEOS else BackendSection.PHOTOS
        val fresh = BackendSection.MODELS in catalog.loaded && BackendSection.MODELS !in catalog.cached && catalog.authError == null &&
            (state.draft.templateId == null || section in catalog.loaded && section !in catalog.cached)
        DsButton(stringResource(if (state.draft.templateId != null) R.string.use_effect else if (state.draft.kind == "video") R.string.backend_generate_video else R.string.backend_generate_photo),
            Modifier.fillMaxWidth().testTag("remote_generate"), primary = true,
            enabled = fresh && PhotoRequest.allowed(catalog.data) && state.canEdit && PhotoRequest.ready(catalog.data, state.draft)) { focus.clearFocus(); controller.submit() }
        Text(stringResource(R.string.backend_reference_privacy), color = Ds.colors.labelTertiary, style = Ds.type.caption1Regular)
        if (state.phase == PhotoPhase.ACTIVE) Text(stringResource(R.string.backend_creation_pending), color = Ds.colors.labelSecondary)
    }
    if (instruction && template != null) RemoteReferenceInstruction(template.requiredImages, { instruction = false }) { instruction = false }
    if (acknowledge) InfoDialog(stringResource(R.string.backend_check_history), stringResource(R.string.backend_new_request_warning),
        dismiss = { acknowledge = false }, confirmText = stringResource(R.string.backend_checked_history), cancelText = stringResource(R.string.cancel),
        confirm = { controller.acknowledgeUnknown(); acknowledge = false })
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
            Text(remoteJobStatus(candidate), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
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
        values.forEach { value -> RemoteOptionChip(value, value == selected, enabled = enabled) { change(value) } }
    }
}
@Composable fun RemotePhotoResult(state: PhotoState, controller: PhotoGenerationController, exports: ExportController, host: MainActivity,
    onEditor: () -> Unit = {}, onDeleted: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    val job = state.job
    val export by exports.state.observeAsState()
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.deletedJobId) { if (state.deletedJobId != null) onDeleted() }
    LaunchedEffect(job?.id, job?.status) { if (job?.status == "completed") controller.download() }
    LaunchedEffect(export?.id, export?.phase) { if (export?.phase == ExportPhase.SHARE_READY) host.shareBackendReady() }
    if (job == null) { Text(stringResource(R.string.backend_empty), Modifier.padding(16.dp), color = Ds.colors.labelSecondary); return }
    val active = job.status in listOf("queued", "running")
    val canAct = !state.actionBusy && export?.busy != true
    val ready = state.local != null && canAct
    val operation = export?.takeIf { it.jobId == job.id }
    val openEditor = { if (controller.reviewNewGeneration()) { details = false; onEditor() } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (active) "" else stringResource(R.string.result), onBack, centerTitle = true) {
            Box {
                TextButton({ options = true }, Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(Ds.colors.backgroundSecondary).testTag("remote_options"), enabled = canAct) {
                    Text(stringResource(R.string.options_symbol), color = Ds.colors.accentPrimary, style = Ds.type.title3Emphasized)
                }
                DropdownMenu(options, { options = false }, containerColor = Ds.colors.backgroundSecondary) {
                    if (job.status == "completed") {
                        DropdownMenuItem({ Text(stringResource(R.string.save_gallery), color = Ds.colors.accentPrimary) },
                            { options = false; host.exportBackendPhoto(ExportDestination.GALLERY, job.kind) }, enabled = ready, modifier = Modifier.testTag("remote_save_gallery"))
                        DropdownMenuItem({ Text(stringResource(R.string.save_files), color = Ds.colors.accentPrimary) },
                            { options = false; host.exportBackendPhoto(ExportDestination.FILES, job.kind) }, enabled = ready, modifier = Modifier.testTag("remote_save_files"))
                    }
                    DropdownMenuItem({ Text(stringResource(R.string.backend_view_details)) }, { options = false; details = true }, modifier = Modifier.testTag("remote_details"))
                    if (!active) {
                        DropdownMenuItem({ Text(stringResource(R.string.backend_review_new_creation)) }, { options = false; openEditor() },
                            enabled = state.canEdit && canAct, modifier = Modifier.testTag("remote_new_creation"))
                        DropdownMenuItem({ Text(stringResource(R.string.delete_generation), color = Ds.colors.accentRed) }, { options = false; action = "delete" },
                            enabled = canAct, modifier = Modifier.testTag("remote_delete"))
                    }
                }
            }
        }
        if (active || job.status == "failed") {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
                Image(painterResource(R.drawable.demo_creating), null, Modifier.size(220.dp))
                if (active) CircularProgressIndicator(color = Ds.colors.accentPrimary)
                Text(stringResource(if (active) R.string.creating else R.string.generation_failed), style = Ds.type.title1Emphasized, color = Ds.colors.labelPrimary)
                Text(remoteJobStatus(job), Modifier.testTag("remote_job_status"), style = Ds.type.subheadlineRegular, color = Ds.colors.labelSecondary)
                Text(stringResource(if (active) R.string.backend_creating_body else if (job.errorCode == "canceled") R.string.backend_job_canceled else R.string.backend_generation_failed),
                    style = Ds.type.subheadlineRegular, color = Ds.colors.labelTertiary, textAlign = TextAlign.Center)
                job.pipelineStage?.let { stage -> Text(stringResource(R.string.backend_pipeline_stage,
                    stringResource(when (stage) { "image" -> R.string.photos; "video" -> R.string.videos; else -> R.string.backend_unknown_status })), color = Ds.colors.labelSecondary) }
                if (job.refunded) Text(stringResource(R.string.backend_credits_refunded), color = Ds.colors.accentPrimary, style = Ds.type.subheadlineRegular)
                state.failure?.let { PhotoFailure(it); DsButton(stringResource(R.string.refresh), onClick = controller::refreshJob) }
                state.actionError?.let { Text(stringResource(R.string.backend_job_action_failed), color = Ds.colors.accentRed); DsButton(stringResource(R.string.refresh), onClick = controller::refreshJob) }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active) DsButton(stringResource(R.string.backend_cancel_creation), Modifier.fillMaxWidth().testTag("remote_cancel"), enabled = canAct) { action = "cancel" }
                else DsButton(stringResource(R.string.backend_review_new_creation), Modifier.fillMaxWidth().testTag("remote_failed_edit"), primary = true, enabled = state.canEdit && canAct, onClick = openEditor)
                DsButton(stringResource(R.string.okay), Modifier.fillMaxWidth().testTag("remote_library"), primary = active, onClick = onDeleted)
            }
        } else {
            Box(Modifier.weight(1f).padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Ds.colors.backgroundPrimary)) {
                state.local?.let { local ->
                    if (job.kind == "video") RemoteVideoPlayer(local, job.id, Modifier.fillMaxSize())
                    else LocalPhotoImage(local.file, Modifier.fillMaxSize())
                }
                if (state.downloading || state.local == null && state.downloadError == null) Column(Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Ds.colors.accentPrimary)
                    Text(stringResource(R.string.backend_downloading), style = Ds.type.subheadlineRegular, color = Ds.colors.labelSecondary)
                }
                state.downloadError?.let { error -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhotoFailure(error); DsButton(stringResource(R.string.retry), onClick = controller::download)
                } }
                Text(remoteJobStatus(job), Modifier.align(Alignment.BottomStart).padding(16.dp).background(Ds.colors.backgroundPrimaryAlpha, RoundedCornerShape(12.dp)).padding(8.dp).testTag("remote_job_status"),
                    color = Ds.colors.labelPrimary, style = Ds.type.caption1Regular)
            }
            if (state.failure != null || state.actionError != null) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.backend_job_action_failed), Modifier.weight(1f), color = Ds.colors.accentRed, style = Ds.type.caption1Regular)
                TextButton(controller::refreshJob) { Text(stringResource(R.string.refresh)) }
            }
            if (operation?.busy == true || state.actionBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp), color = Ds.colors.accentPrimary)
            DsButton(stringResource(R.string.share), Modifier.fillMaxWidth().padding(16.dp).testTag("remote_share"), primary = true, enabled = ready) { host.exportBackendPhoto(ExportDestination.SHARE, job.kind) }
        }
    }
    action?.let { selected -> AlertDialog(onDismissRequest = { action = null }, containerColor = Ds.colors.backgroundSecondary,
        title = { Text(stringResource(if (selected == "delete") R.string.delete_generation else R.string.backend_cancel_creation), style = Ds.type.title2Emphasized) },
        text = { Text(stringResource(if (selected == "delete") R.string.backend_delete_creation_notice else R.string.backend_cancel_creation_notice), style = Ds.type.calloutRegular) },
        confirmButton = { TextButton({ action = null; if (selected == "delete") controller.deleteJob() else controller.cancelJob() }, Modifier.testTag("remote_action_confirm")) { Text(stringResource(R.string.backend_confirm_action), color = Ds.colors.accentPrimary) } },
        dismissButton = { TextButton({ action = null }) { Text(stringResource(R.string.cancel), color = Ds.colors.labelSecondary) } }) }
    if (details) ModalBottomSheet(onDismissRequest = { details = false }, containerColor = Ds.colors.backgroundPrimary) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.backend_view_details), style = Ds.type.title2Emphasized)
            if (job.prompt.isNotBlank()) Text(job.prompt, style = Ds.type.subheadlineRegular)
            Text(remoteJobStatus(job), color = Ds.colors.labelSecondary)
            Text(stringResource(R.string.backend_actual_cost, job.charged), color = Ds.colors.accentPrimary)
            if (job.refunded) Text(stringResource(R.string.backend_credits_refunded), color = Ds.colors.labelSecondary)
            if (job.model.isNotBlank()) Text(stringResource(R.string.backend_model_label) + ": " + job.model, color = Ds.colors.labelSecondary)
            state.failure?.let { PhotoFailure(it) }
            DsButton(stringResource(R.string.refresh), Modifier.fillMaxWidth(), enabled = canAct, onClick = controller::refreshJob)
            DsButton(stringResource(R.string.close), Modifier.fillMaxWidth()) { details = false }
        }
    }
    operation?.takeIf { it.phase == ExportPhase.SUCCEEDED || it.message != null && !it.busy }?.let { finished ->
        InfoDialog(null, stringResource(finished.message ?: R.string.backend_saved_result), { exports.acknowledge(finished.id) })
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
        failure.code in listOf("invalid_image", "unsupported_image", "invalid_video", "unsupported_video", "no_output", "result_too_large") -> R.string.backend_invalid_result
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
