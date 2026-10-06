package com.rslnabk.aivideotest.ui.common

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun BalanceButton(snapshot: DemoSnapshot, actions: AppNavigator) {
    val account = snapshot.account
    val description = stringResource(R.string.balance_accessibility, account.tokens)
    Box(Modifier.clip(RoundedCornerShape(22.dp)).background(Ds.colors.backgroundSecondary)
        .combinedClickable(onClick = { actions.openOffers(OfferKind.TOKENS) }, onLongClick = if (BuildConfig.DEBUG) { { actions.show("debug") } } else null)
        .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 12.dp)
        .semantics { contentDescription = description }.testTag("balance")) {
        Text(stringResource(if (account.isPro) R.string.pro_balance_value else R.string.balance_value, account.tokens), color = Ds.colors.accentPrimary, style = Ds.type.headlineEmphasized)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppDialogs(actions: AppNavigator, snapshot: DemoSnapshot, model: AppViewModel, host: MainActivity) {
    val key = actions.dialogKey
    val dismiss = actions::dismiss
    when (actions.dialog) {
        "terms", "privacy" -> InfoDialog(stringResource(if (actions.dialog == "terms") R.string.terms_of_use else R.string.privacy_policy), stringResource(R.string.demo_legal_body), dismiss)
        "reset" -> InfoDialog(stringResource(R.string.debug_reset_title), stringResource(R.string.debug_reset_body), dismiss,
            stringResource(R.string.reset), { dismiss(); model.reset(); actions.startIntro() }, stringResource(R.string.cancel))
        "feedback_saved" -> InfoDialog(null, stringResource(R.string.feedback_saved), dismiss)
        "clear_cache" -> AlertDialog(onDismissRequest = dismiss, containerColor = Ds.colors.backgroundSecondary,
            title = { Text(stringResource(R.string.clear_cache), style = Ds.type.title2Emphasized) },
            text = { Text(stringResource(R.string.clear_cache_body), style = Ds.type.calloutRegular) },
            confirmButton = { TextButton({ dismiss(); model.clearCache() }) { Text(stringResource(R.string.clear_action), color = Ds.colors.accentRed) } },
            dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel), color = Ds.colors.accentPrimary) } })
        "notification_blocked" -> {
            val intro = actions.dialogValue == "intro"
            InfoDialog(stringResource(R.string.notification_blocked), stringResource(R.string.notification_blocked_body),
                { dismiss(); if (intro) host.finishIntro() }, stringResource(R.string.open_android_settings), {
                    dismiss(); host.openNotificationSettings(); if (intro) host.finishIntro()
                }, stringResource(R.string.not_now))
        }
        "delete" -> InfoDialog(stringResource(R.string.delete_title), stringResource(R.string.delete_body), dismiss,
            stringResource(R.string.delete_generation), { dismiss(); host.deleteGeneration(key) }, stringResource(R.string.cancel))
        "video_error" -> InfoDialog(null, stringResource(R.string.video_unavailable), dismiss, stringResource(R.string.retry), {
            dismiss(); actions.nav.currentBackStackEntry?.savedStateHandle?.set("video_retry", System.nanoTime())
        }, stringResource(R.string.cancel))
        "debug" -> AlertDialog(onDismissRequest = dismiss, containerColor = Ds.colors.backgroundSecondary,
            title = { Text(stringResource(R.string.debug_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val options = listOf(R.string.debug_free, R.string.debug_pro, R.string.debug_zero, R.string.debug_reset, R.string.debug_photo_error, R.string.debug_generation_error, R.string.debug_export_error,
                        R.string.debug_purchase_success, R.string.debug_purchase_cancel, R.string.debug_purchase_error, R.string.debug_intro, R.string.debug_cache_error)
                    options.forEachIndexed { index, resource ->
                        TextButton({
                            dismiss()
                            when (index) {
                                0 -> model.setAccount(DemoAccount())
                                1 -> model.setAccount(DemoAccount(100, true))
                                2 -> model.setAccount(DemoAccount(0))
                                3 -> actions.show("reset")
                                4 -> { model.failNextPhoto = true; host.toast(R.string.debug_error_ready) }
                                5 -> { model.failNextGeneration = true; host.toast(R.string.debug_error_ready) }
                                6 -> { model.exports.failNext = true; host.toast(R.string.debug_error_ready) }
                                10 -> { model.replayIntro(); actions.startIntro() }
                                11 -> { model.failNextCache = true; host.toast(R.string.debug_error_ready) }
                                7, 8, 9 -> { model.nextPurchaseOutcome = DemoPurchaseOutcome.entries[index - 7]; host.toast(R.string.debug_purchase_ready) }
                            }
                        }, Modifier.fillMaxWidth()) { Text(stringResource(resource), color = Ds.colors.accentPrimary) }
                    }
                }
            }, confirmButton = { TextButton(dismiss) { Text(stringResource(R.string.close)) } })
        "instruction", "source", "samples", "failed" -> {
            ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Ds.colors.backgroundSecondary, contentColor = Ds.colors.labelPrimary) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (actions.dialog) {
                        "instruction" -> {
                            SheetTitle(R.string.instruction); SheetTitle(R.string.good_choice)
                            SheetPhotos(listOf(R.drawable.demo_good_1, R.drawable.demo_good_2), 145)
                            Text(stringResource(R.string.good_choice_body), style = Ds.type.subheadlineRegular)
                            SheetTitle(R.string.bad_choice); SheetPhotos(listOf(R.drawable.demo_bad_1, R.drawable.demo_bad_2), 145)
                            Text(stringResource(R.string.bad_choice_body), style = Ds.type.subheadlineRegular)
                            DsButton(stringResource(R.string.continue_action), Modifier.fillMaxWidth(), primary = true) { model.markInstructionSeen(); actions.show("source", key) }
                        }
                        "source" -> {
                            SheetTitle(R.string.photo_source)
                            DsButton(stringResource(R.string.gallery), Modifier.fillMaxWidth()) { dismiss(); host.pickGallery(key) }
                            DsButton(stringResource(R.string.camera), Modifier.fillMaxWidth()) { dismiss(); host.takePhoto(key) }
                            DsButton(stringResource(R.string.sample_photo), Modifier.fillMaxWidth(), primary = true) { actions.show("samples", key) }
                            DsButton(stringResource(R.string.cancel), Modifier.fillMaxWidth(), onClick = dismiss)
                        }
                        "samples" -> {
                            SheetTitle(R.string.sample_photos)
                            SheetPhotos(listOf(R.drawable.demo_good_1, R.drawable.demo_good_2), 240) { index -> dismiss(); model.loadPhoto(key, if (index == 0) "asset:good1" else "asset:good2") }
                            DsButton(stringResource(R.string.cancel), Modifier.fillMaxWidth(), onClick = dismiss)
                        }
                        "failed" -> {
                            SheetTitle(R.string.select_action)
                            Text(stringResource(R.string.failed_action_body), style = Ds.type.subheadlineRegular, color = Ds.colors.labelTertiary)
                            if (snapshot.jobs.find { it.id == key }?.status == JobStatus.FAILED)
                                DsButton(stringResource(R.string.refresh), Modifier.fillMaxWidth()) { dismiss(); host.retryJob(key) }
                            TextButton({ actions.show("delete", key) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.delete_generation), color = Ds.colors.accentRed) }
                            DsButton(stringResource(R.string.cancel), Modifier.fillMaxWidth(), onClick = dismiss)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
@Composable private fun SheetTitle(resource: Int) { Text(stringResource(resource), style = Ds.type.title2Emphasized) }
@Composable private fun SheetPhotos(images: List<Int>, height: Int, choose: ((Int) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        images.forEachIndexed { index, image ->
            val modifier = Modifier.weight(1f).height(height.dp).clip(RoundedCornerShape(24.dp))
            Image(painterResource(image), if (choose == null) null else stringResource(if (index == 0) R.string.sample_one else R.string.sample_two),
                if (choose == null) modifier else modifier.clickable { choose(index) }.testTag("sample_$index"), contentScale = ContentScale.Crop)
        }
    }
}
