package com.rslnabk.aivideotest.ui.backend

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.backend.LocalPhoto
import com.rslnabk.aivideotest.ui.common.DsButton
import com.rslnabk.aivideotest.ui.theme.Ds

/** Only a verified local video file reaches the platform playback surface. */
@Composable fun RemoteVideoPlayer(local: LocalPhoto, id: String) {
    var paused by rememberSaveable(id) { mutableStateOf(false) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    var position by rememberSaveable(id, stateSaver = Saver<Int, Int>(
        save = { view?.currentPosition?.takeIf { it > 0 } ?: it }, restore = { it })) { mutableStateOf(0) }
    var ready by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var resumed by remember { mutableStateOf(false) }
    val latestPaused by rememberUpdatedState(paused)
    val latestPosition by rememberUpdatedState(position)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 500.dp).aspectRatio(local.aspectRatio ?: (16f / 9f)).background(Ds.colors.backgroundSecondary)) {
            AndroidView(factory = { context -> VideoView(context).also { view = it } }, modifier = Modifier.fillMaxSize().testTag("remote_video"),
                onRelease = { position = it.currentPosition.coerceAtLeast(position); it.stopPlayback(); view = null })
            if (!ready) local.poster?.let { LocalPhotoImage(it, Modifier.matchParentSize()) }
        }
        if (error) {
            androidx.compose.material3.Text(stringResource(R.string.backend_video_playback_error), color = Ds.colors.accentRed)
            DsButton(stringResource(R.string.retry)) { retry++ }
        } else DsButton(stringResource(if (paused) R.string.play else R.string.pause), Modifier.testTag("remote_playback"), enabled = ready) { paused = !paused }
    }
    LifecycleResumeEffect(id) {
        resumed = true
        onPauseOrDispose { resumed = false; view?.let { if (it.currentPosition > 0) position = it.currentPosition; it.pause() } }
    }
    LaunchedEffect(view, local.file.path, retry) {
        view?.apply {
            ready = false; error = false
            setOnPreparedListener { player ->
                player.isLooping = true; seekTo(latestPosition); ready = true
                if (resumed && !latestPaused) start()
            }
            setOnErrorListener { _, _, _ -> ready = false; error = true; true }
            setVideoPath(local.file.path)
        }
    }
    LaunchedEffect(paused, resumed, ready) { if (ready) { if (paused || !resumed) view?.pause() else view?.start() } }
}
