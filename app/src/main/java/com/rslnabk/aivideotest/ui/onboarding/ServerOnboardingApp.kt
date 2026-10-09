package com.rslnabk.aivideotest.ui.onboarding

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rslnabk.aivideotest.AppViewModel
import com.rslnabk.aivideotest.MainActivity
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.backend.BackendApp
import com.rslnabk.aivideotest.ui.common.InfoDialog
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun ServerOnboardingApp(model: AppViewModel, host: MainActivity) {
    val snapshot by model.snapshot.observeAsState(model.snapshot.value!!)
    val backend by model.backend.state.observeAsState(BackendState())
    val pending by model.serverIntroPhoto.state.observeAsState(ServerIntroPhotoState())
    val photo by model.backendPhotos.state.observeAsState(PhotoState())
    val preferences = snapshot.preferences
    // Auth/catalog loading runs independently: onboarding also works without a connection.
    DisposableEffect(backend.userId) {
        model.backendPhotos.activate(backend.userId)
        onDispose { model.backendPhotos.pause() }
    }
    LaunchedEffect(backend.userId, pending, photo.canEdit, photo.draft, preferences.introStep) {
        // Recover a committed import if the process died before advancing the local intro step.
        if (!pending.importing && pending.reference != null && pending.advancePending) {
            if (preferences.introStep == IntroStep.PHOTOS) model.finishIntroPhoto(pending.choice)
            model.serverIntroPhoto.markAdvanced()
        }
        if (preferences.introStep == IntroStep.DONE)
            backend.userId?.let { model.serverIntroPhoto.deliver(it, model.backendPhotos::acceptOnboardingPhoto) }
    }
    if (preferences.introStep == IntroStep.DONE) BackendApp(model, host)
    else Column(Modifier.fillMaxSize().background(Ds.colors.backgroundPrimary).safeDrawingPadding().imePadding()) {
        OnboardingScreen(preferences, model::backIntro, model::advanceIntro, host::pickIntroPhoto,
            sample = { model.importServerIntroPhoto(Uri.parse("android.resource://${host.packageName}/${R.drawable.demo_good_1}"), IntroPhotoChoice.SAMPLE) },
            skipPhoto = { model.finishIntroPhoto(IntroPhotoChoice.SKIPPED) },
            notifications = { host.requestNotifications(true) }, finish = host::finishIntro,
            photoBusy = pending.importing, server = true)
    }
    if (pending.failed) InfoDialog(null, stringResource(R.string.server_intro_photo_failed), model.serverIntroPhoto::acknowledgeError)
    if (host.introNotificationsBlocked) InfoDialog(stringResource(R.string.notification_blocked),
        stringResource(R.string.notification_blocked_body), host::dismissIntroNotifications,
        stringResource(R.string.open_android_settings), {
            host.openNotificationSettings(); host.dismissIntroNotifications()
        }, stringResource(R.string.not_now))
}
