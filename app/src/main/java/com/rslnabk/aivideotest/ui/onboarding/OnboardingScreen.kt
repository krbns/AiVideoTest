package com.rslnabk.aivideotest.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds
import kotlinx.coroutines.delay

@Composable fun LaunchScreen(ready: () -> Unit) {
    BackHandler { }
    LaunchedEffect(Unit) { delay(450); ready() }
    Box(Modifier.fillMaxSize().testTag("splash")) {
        Box(Modifier.align(Alignment.Center).size(90.dp).clip(RoundedCornerShape(24.dp)).background(Ds.colors.accentPrimary))
        LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).padding(40.dp).fillMaxWidth(), color = Ds.colors.accentPrimary, trackColor = Ds.colors.backgroundSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OnboardingScreen(preferences: DemoPreferences, model: AppViewModel, host: MainActivity, actions: AppNavigator) {
    OnboardingScreen(preferences, model::backIntro, model::advanceIntro, host::pickIntroPhoto,
        { model.loadPhoto("prompt_photo", "asset:good1"); model.finishIntroPhoto(IntroPhotoChoice.SAMPLE) },
        { model.finishIntroPhoto(IntroPhotoChoice.SKIPPED) }, { host.requestNotifications(true) }, host::finishIntro,
        rate = { actions.nav.navigate("rate") })
}

/** Shared presentation; each data source owns photo import, permissions and completion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OnboardingScreen(preferences: DemoPreferences, back: () -> Unit, next: (IntroStep) -> Unit,
    gallery: () -> Unit, sample: () -> Unit, skipPhoto: () -> Unit, notifications: () -> Unit, finish: () -> Unit,
    rate: (() -> Unit)? = null, photoBusy: Boolean = false, server: Boolean = false) {
    val step = preferences.introStep
    BackHandler { if (!photoBusy && step != IntroStep.WELCOME) back() }
    val index = when(step) { IntroStep.WELCOME -> 0; IntroStep.PROMPT -> 1; IntroStep.SHARE -> 2; IntroStep.REVIEWS, IntroStep.PHOTOS -> 3; else -> 4 }
    val images = listOf(R.drawable.demo_intro_welcome, R.drawable.demo_intro_prompt, R.drawable.demo_intro_share, R.drawable.demo_intro_reviews, R.drawable.demo_intro_notifications)
    val titles = listOf(R.string.intro_welcome, R.string.intro_prompt, R.string.intro_share, R.string.intro_reviews, R.string.intro_notifications)
    val subtitles = listOf(R.string.intro_welcome_body, R.string.intro_prompt_body, R.string.intro_share_body, R.string.intro_reviews_body, R.string.intro_notifications_body)
    Column(Modifier.fillMaxSize().testTag("intro_${step.name.lowercase()}")) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Image(painterResource(images[index]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (index > 0) RoundAction(R.drawable.ic_back, stringResource(R.string.back), Modifier.align(Alignment.TopStart).padding(12.dp).testTag("intro_back"), onClick = back)
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(if (server && index == 3) R.string.server_intro_reviews else titles[index]), color = Ds.colors.labelPrimary, style = Ds.type.largeTitleEmphasized, textAlign = TextAlign.Center)
            Text(stringResource(if (server && index == 3) R.string.server_intro_reviews_body
                else if (server && index == 4) R.string.server_intro_notifications_body else subtitles[index]),
                color = Ds.colors.labelTertiary, style = Ds.type.title3Regular, textAlign = TextAlign.Center)
            Text(stringResource(R.string.intro_progress, index + 1), color = Ds.colors.labelQuaternary, style = Ds.type.caption2Regular)
            if (step == IntroStep.REVIEWS && rate != null) TextButton(rate, Modifier.testTag("intro_rate")) { Text(stringResource(R.string.intro_demo_review), color = Ds.colors.accentPrimary) }
            DsButton(stringResource(R.string.next), Modifier.fillMaxWidth().testTag("intro_next"), primary = true) {
                if (step == IntroStep.NOTIFICATIONS) notifications() else next(step)
            }
            if (step == IntroStep.NOTIFICATIONS) TextButton(finish, Modifier.testTag("intro_not_now")) { Text(stringResource(R.string.not_now), color = Ds.colors.labelTertiary) }
        }
    }
    if (step == IntroStep.PHOTOS) ModalBottomSheet(
        onDismissRequest = { if (!photoBusy) skipPhoto() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ds.colors.backgroundSecondary) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.intro_photo_title), style = Ds.type.title2Emphasized)
            Text(stringResource(if (server) R.string.server_intro_photo_body else R.string.intro_photo_body), style = Ds.type.calloutRegular, color = Ds.colors.labelTertiary)
            if (photoBusy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("intro_photo_loading"), color = Ds.colors.accentPrimary)
            DsButton(stringResource(R.string.gallery), Modifier.fillMaxWidth().testTag("intro_gallery"), primary = true, enabled = !photoBusy, onClick = gallery)
            DsButton(stringResource(R.string.sample_photo), Modifier.fillMaxWidth().testTag("intro_sample"), enabled = !photoBusy, onClick = sample)
            TextButton(skipPhoto, Modifier.fillMaxWidth().testTag("intro_photo_skip"), enabled = !photoBusy) { Text(stringResource(R.string.not_now), color = Ds.colors.labelTertiary) }
        }
    }
}
