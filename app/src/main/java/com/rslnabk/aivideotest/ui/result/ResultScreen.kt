package com.rslnabk.aivideotest.ui.result

import android.widget.VideoView
import androidx.core.net.toUri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.demo.DemoResultFixtures
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun ResultScreen(id: String, snapshot: DemoSnapshot, export: ExportState?, host: MainActivity, actions: AppNavigator) {
    val job = snapshot.jobs.find { it.id == id }
    LaunchedEffect(job == null) { if (job == null) actions.selectTab(AppTab.LIBRARY) }
    if (job == null) return
    var options by rememberSaveable { mutableStateOf(false) }
    val busy = export?.busy == true
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.result), actions::back) {
            Box {
                TextButton({ options = true }, Modifier.testTag("options"), enabled = !busy) {
                    Text(stringResource(R.string.options_symbol), color = Ds.colors.accentPrimary)
                }
                DropdownMenu(options, { options = false }, containerColor = Ds.colors.backgroundSecondary) {
                    listOf(R.string.save_gallery, R.string.save_files, R.string.delete_generation).forEachIndexed { index, resource ->
                        DropdownMenuItem(text = { Text(stringResource(resource), color = if (index == 2) Ds.colors.accentRed else Ds.colors.accentPrimary) }, onClick = {
                            options = false
                            if (index == 2) host.confirmDelete(id) else host.exportResult(id, if (index == 0) ExportDestination.GALLERY else ExportDestination.FILES)
                        }, enabled = !busy)
                    }
                }
            }
        }
        Box(Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(32.dp))) {
            Image(painterResource(job.resultImage), stringResource(R.string.demo_result), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (job.draft.kind == MediaKind.VIDEO) DemoVideo(job, host, actions)
            Text(stringResource(R.string.demo_result), Modifier.align(Alignment.BottomStart).padding(16.dp).background(Ds.colors.backgroundPrimaryAlpha, RoundedCornerShape(12.dp)).padding(10.dp),
                color = Ds.colors.labelPrimary, style = Ds.type.caption1Regular)
        }
        if (busy && export.jobId == id) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Ds.colors.accentPrimary)
            Text(stringResource(when (export.phase) { ExportPhase.CHOOSING -> R.string.choose_file_location; ExportPhase.PERMISSION -> R.string.awaiting_permission; else -> R.string.exporting }),
                Modifier.align(Alignment.CenterHorizontally).padding(8.dp), color = Ds.colors.labelSecondary, style = Ds.type.footnoteRegular)
        }
        DsButton(stringResource(R.string.share), Modifier.fillMaxWidth().padding(16.dp).testTag("share"), primary = true, enabled = !busy) { host.exportResult(id, ExportDestination.SHARE) }
    }
}
/** VideoView is a platform media surface, not a legacy application screen or layout. */
@Composable private fun BoxScope.DemoVideo(job: GenerationJob, host: MainActivity, actions: AppNavigator) {
    var paused by rememberSaveable(job.id) { mutableStateOf(false) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    var position by rememberSaveable(job.id, stateSaver = Saver<Int, Int>(
        save = { view?.currentPosition?.takeIf { it > 0 } ?: it }, restore = { it })) { mutableStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    val latestPaused by rememberUpdatedState(paused)
    val latestPosition by rememberUpdatedState(position)
    val retry by actions.nav.currentBackStackEntry!!.savedStateHandle.getStateFlow("video_retry", 0L).collectAsState()
    AndroidView(factory = { context -> VideoView(context).also { view = it } }, modifier = Modifier.fillMaxSize().testTag("video"),
        onRelease = { position = it.currentPosition.coerceAtLeast(position); it.stopPlayback(); view = null })
    LifecycleResumeEffect(job.id) {
        resumed = true
        onPauseOrDispose { resumed = false; view?.let { if (it.currentPosition > 0) position = it.currentPosition; it.pause() } }
    }
    LaunchedEffect(view, retry) {
        view?.apply {
            ready = false
            setOnPreparedListener { player ->
                player.isLooping = true; seekTo(latestPosition); ready = true
                if (resumed && !latestPaused) start()
            }
            setOnErrorListener { _, _, _ -> ready = false; actions.show("video_error"); true }
            setVideoURI("android.resource://${host.packageName}/${DemoResultFixtures.video(job.draft)}".toUri())
        }
    }
    LaunchedEffect(paused, resumed, ready) {
        if (ready) { if (paused || !resumed) view?.pause() else view?.start() }
    }
    if (!ready) Image(painterResource(job.resultImage), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    DsButton(stringResource(if (paused) R.string.play else R.string.pause), Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("playback")) {
        paused = !paused
    }
}
