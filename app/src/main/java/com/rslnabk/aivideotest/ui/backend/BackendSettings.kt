@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rslnabk.aivideotest.ui.backend

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.BuildConfig
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.settings.SectionTitle
import com.rslnabk.aivideotest.ui.settings.SettingsRow
import com.rslnabk.aivideotest.ui.theme.Ds

/** Presentation uses remote account data; deferred actions never mutate demo finances/preferences. */
@Composable internal fun RemoteSettings(state: BackendState, refresh: (Boolean) -> Unit, demo: () -> Unit,
    cacheBytes: Long = 0, cacheBusy: Boolean = false, cacheResult: Boolean? = null,
    refreshCache: () -> Unit = {}, clearCache: (() -> Unit)? = null, acknowledgeCache: () -> Unit = {}) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmCache by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val shareText = stringResource(R.string.backend_share_app_text)
    val shareTitle = stringResource(R.string.share)
    LaunchedEffect(Unit) { refreshCache() }
    LaunchedEffect(cacheResult) { cacheResult?.let { notice = if (it) R.string.cache_cleared else R.string.cache_failed } }
    LazyColumn(Modifier.fillMaxSize().testTag("backend_list"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item("connection") { BackendConnectionStatus(state, refresh, demo, padded = false) }
        remoteStatus(state, BackendSection.PROFILE, padded = false)
        remoteStatus(state, BackendSection.POLICY, padded = false)
        remoteStatus(state, BackendSection.WALLET, padded = false)
        item("subscription") {
            val policy = state.data.policy
            val low = state.creditBalance == 0 && policy?.trial == 0 && !policy.canGenerate
            val color = if (low) Ds.colors.accentRed else Ds.colors.accentPrimary
            Column(Modifier.fillMaxWidth().background(if (low) color.copy(alpha = .25f) else Ds.colors.accentPrimaryAlpha, RoundedCornerShape(24.dp))
                .border(1.dp, color, RoundedCornerShape(24.dp)).padding(16.dp).testTag("backend_subscription_card"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.subscription_title), Modifier.weight(1f), style = Ds.type.title3Regular)
                    RoundAction(R.drawable.ic_crown, stringResource(R.string.subscription_title), Modifier.testTag("backend_account_details"), color) { sheet = "account" }
                }
                Text(stringResource(when { policy == null -> R.string.backend_subscription_unknown; policy.subscribed -> R.string.backend_subscription_active; else -> R.string.backend_subscription_inactive }),
                    color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
                ProfileBalance(state, compact = true)
                DsButton(stringResource(R.string.more), Modifier.fillMaxWidth().testTag("backend_account_more"), containerColor = color.copy(alpha = .12f), contentColor = color) { sheet = "account" }
            }
        }
        item("share_heading") { SectionTitle(R.string.share_section) }
        item("share") { SettingsRow(R.string.share_friends, R.drawable.ic_share, "backend_share_app") {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
            runCatching { context.startActivity(Intent.createChooser(intent, shareTitle)) }.onFailure { notice = R.string.export_handler_missing }
        } }
        item("rate") { SettingsRow(R.string.backend_like_rate, R.drawable.ic_heart_outline, "backend_rate") { sheet = "rating" } }
        item("option_heading") { SectionTitle(R.string.option_section) }
        item("notifications") {
            Row(Modifier.fillMaxWidth().background(Ds.colors.backgroundSecondary, RoundedCornerShape(16.dp))
                .clickable { notice = R.string.backend_notifications_pending }.heightIn(min = 56.dp).padding(16.dp).testTag("backend_notifications"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DsIcon(R.drawable.ic_bell, null)
                Text(stringResource(R.string.notifications), Modifier.weight(1f), style = Ds.type.calloutRegular)
                Switch(false, null, enabled = false, modifier = Modifier.testTag("backend_notification_switch"))
            }
        }
        item("report") { SettingsRow(R.string.report_problem, R.drawable.ic_report, "backend_report") { sheet = "report" } }
        item("restore") { SettingsRow(R.string.restore_purchases, R.drawable.ic_restore, "backend_restore") { notice = R.string.backend_purchases_pending } }
        item("cache") { SettingsRow(R.string.clear_cache, R.drawable.ic_trash, "backend_clear_cache",
            detail = if (cacheBusy) stringResource(R.string.clearing_cache) else Formatter.formatShortFileSize(context, cacheBytes),
            enabled = clearCache != null && !cacheBusy && !state.connecting && state.loading.isEmpty()) { confirmCache = true } }
        item("legal_heading") { SectionTitle(R.string.legal_section) }
        item("letter") { SettingsRow(R.string.letter, R.drawable.ic_letter, "backend_letter") { sheet = "letter" } }
        item("privacy") { SettingsRow(R.string.privacy_policy, R.drawable.ic_legal, "backend_privacy") { notice = R.string.backend_privacy_pending } }
        item("terms") { SettingsRow(R.string.terms_conditions, R.drawable.ic_legal, "backend_terms") { notice = R.string.backend_terms_pending } }
        item("version") { Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), Modifier.fillMaxWidth().padding(12.dp).testTag("backend_version"),
            color = Ds.colors.labelQuaternary, style = Ds.type.footnoteRegular, textAlign = TextAlign.Center) }
        item("sync") { DsButton(stringResource(R.string.refresh), Modifier.fillMaxWidth().testTag("backend_refresh"),
            enabled = !state.connecting && state.loading.isEmpty() && !cacheBusy) { refresh(state.authError?.status == 401) } }
        if (BuildConfig.DEBUG) item("demo") { DsButton(stringResource(R.string.backend_back_demo), Modifier.fillMaxWidth().testTag("backend_demo"), onClick = demo) }
    }
    if (confirmCache) InfoDialog(stringResource(R.string.clear_cache), stringResource(R.string.backend_clear_cache_body),
        dismiss = { confirmCache = false }, confirmText = stringResource(R.string.clear_action), cancelText = stringResource(R.string.cancel),
        confirm = { confirmCache = false; clearCache?.invoke() })
    notice?.let { message -> InfoDialog(null, stringResource(message), { notice = null; acknowledgeCache() }) }
    sheet?.let { selected -> RemoteSettingsSheet(selected, state, { sheet = null }, { sheet = it }, { sheet = null; notice = R.string.feedback_saved }) }
}

@Composable private fun RemoteSettingsSheet(selected: String, state: BackendState, dismiss: () -> Unit,
    change: (String) -> Unit, saved: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Ds.colors.backgroundPrimary) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (selected) {
                "account" -> {
                    Text(state.data.profile?.name ?: stringResource(R.string.backend_account), style = Ds.type.title2Emphasized)
                    state.data.profile?.accountId?.let { accountId ->
                        val context = LocalContext.current
                        Text(accountId, color = Ds.colors.labelSecondary, modifier = Modifier.testTag("backend_account_id"))
                        TextButton({ (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Account ID", accountId)) }) { Text(stringResource(R.string.backend_copy_account)) }
                    }
                    ProfileBalance(state)
                    state.data.policy?.let { policy ->
                        Text(stringResource(if (policy.subscribed) R.string.backend_subscription_active else R.string.backend_subscription_inactive), style = Ds.type.headlineEmphasized)
                        policy.plan?.let { Text(stringResource(R.string.backend_plan_value, it), color = Ds.colors.labelSecondary) }
                        policy.expiresAt?.let { Text(stringResource(R.string.backend_expiry_value, accountDate(it)), color = Ds.colors.labelSecondary) }
                        policy.willRenew?.let { Text(stringResource(if (it) R.string.backend_renews else R.string.backend_no_renewal), color = Ds.colors.labelSecondary) }
                        Text(stringResource(R.string.backend_trial, policy.trial), color = Ds.colors.labelSecondary)
                        Text(stringResource(if (policy.canGenerate) R.string.backend_access_ready else R.string.backend_access_restricted), color = Ds.colors.labelTertiary)
                    }
                    if (state.data.products.isNotEmpty()) {
                        Text(stringResource(R.string.backend_available_products), style = Ds.type.headlineEmphasized)
                        state.data.products.forEach { product -> DsCardSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(product.title ?: stringResource(R.string.backend_product), style = Ds.type.calloutEmphasized)
                                product.credits?.let { Text(stringResource(R.string.backend_credits, it), style = Ds.type.caption1Regular, color = Ds.colors.labelSecondary) }
                            }
                        } }
                    }
                    Text(stringResource(R.string.backend_purchases_pending), color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
                }
                "rating" -> {
                    Image(painterResource(R.drawable.demo_rating_hearts), null, Modifier.align(Alignment.CenterHorizontally).size(200.dp), contentScale = ContentScale.Fit)
                    Text(stringResource(R.string.rating_title), Modifier.fillMaxWidth(), style = Ds.type.title2Emphasized, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.rating_body), Modifier.fillMaxWidth(), style = Ds.type.subheadlineRegular, color = Ds.colors.labelTertiary, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DsButton(stringResource(R.string.rating_no), Modifier.weight(1f), onClick = dismiss)
                        DsButton(stringResource(R.string.rating_yes), Modifier.weight(1f), primary = true) { change("review") }
                    }
                }
                else -> RemoteFeedbackForm(selected, state.userId, saved)
            }
            DsButton(stringResource(R.string.close), Modifier.fillMaxWidth().testTag("backend_sheet_close"), onClick = dismiss)
        }
    }
}
private fun accountDate(value: String) = PhotoRecovery.time(value)?.let {
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it))
} ?: value

@Composable private fun RemoteFeedbackForm(kind: String, userId: String?, saved: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("backend_settings_drafts_v1", Context.MODE_PRIVATE) }
    val key = "${userId ?: "unconnected"}:$kind"
    var value by rememberSaveable(key) { mutableStateOf(prefs.getString(key, "").orEmpty()) }
    var rating by rememberSaveable(key) { mutableIntStateOf(prefs.getInt("$key:rating", 0)) }
    val title = if (kind == "report") R.string.report_problem else if (kind == "letter") R.string.letter else R.string.backend_write_review
    Text(stringResource(title), style = Ds.type.title2Emphasized)
    Text(stringResource(R.string.backend_feedback_local), color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
    if (kind == "review") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        (1..5).forEach { star -> IconButton({ rating = star }, Modifier.size(48.dp).testTag("backend_review_star_$star")) {
            DsIcon(R.drawable.ic_star, stringResource(R.string.star_rating, star), if (star <= rating) Ds.colors.accentPrimary else Ds.colors.labelQuaternary)
        } }
    }
    val count = value.codePointCount(0, value.length)
    OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth().testTag("backend_message_input"),
        label = { Text(stringResource(R.string.message_text)) }, minLines = 4, isError = count > 1000,
        supportingText = { Text("$count / 1000", style = Ds.type.caption1Regular) }, textStyle = Ds.type.calloutRegular)
    DsButton(stringResource(R.string.save_locally), Modifier.fillMaxWidth().testTag("backend_message_save"), primary = true,
        enabled = value.isNotBlank() && count <= 1000 && (kind != "review" || rating in 1..5)) {
        prefs.edit().putString(key, value).putInt("$key:rating", rating).apply(); saved()
    }
}
