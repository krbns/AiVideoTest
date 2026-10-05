package com.rslnabk.aivideotest.ui.generator

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun CreatingScreen(id: String, snapshot: DemoSnapshot, host: MainActivity, actions: AppNavigator) {
    val job = snapshot.jobs.find { it.id == id }
    LifecycleResumeEffect(job?.status) {
        if (job == null) actions.selectTab(AppTab.VIDEO)
        else if (job.status == JobStatus.SUCCEEDED) actions.showReadyResult(id)
        onPauseOrDispose { }
    }
    if (job == null) return
    val failed = job.status == JobStatus.FAILED
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("", actions::back) { BalanceButton(snapshot, actions) }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
            Image(painterResource(R.drawable.demo_creating), null, Modifier.size(220.dp))
            if (!failed) CircularProgressIndicator(Modifier.testTag("creating_progress"), color = Ds.colors.accentPrimary)
            Text(stringResource(if (failed) R.string.generation_failed else R.string.creating), color = Ds.colors.labelPrimary, style = Ds.type.title1Emphasized)
            Text(stringResource(if (failed) R.string.generation_failed_body else if (job.draft.kind == MediaKind.VIDEO) R.string.creating_video else R.string.creating_photo),
                color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular, textAlign = TextAlign.Center)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (failed) DsButton(stringResource(R.string.retry), Modifier.fillMaxWidth().testTag("retry_generation"), primary = true) { host.retryJob(id) }
            DsButton(stringResource(if (failed) R.string.back_to_input else R.string.okay), Modifier.fillMaxWidth().testTag("okay"), primary = !failed) {
                if (failed) actions.back() else actions.openLibrary(job.draft.kind)
            }
        }
    }
}
