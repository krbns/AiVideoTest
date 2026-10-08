package com.rslnabk.aivideotest.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun SettingsScreen(snapshot: DemoSnapshot, model: AppViewModel, host: MainActivity, actions: AppNavigator) {
    val allowed by model.notificationsAllowed.observeAsState(false)
    val busy by model.cacheBusy.observeAsState(false)
    val bytes by model.cacheBytes.observeAsState(0L)
    LaunchedEffect(Unit) { model.refreshCache(); model.refreshNotifications() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.settings)) { BalanceButton(snapshot, actions) }
        LazyColumn(Modifier.fillMaxSize().testTag("settings_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (BuildConfig.DEBUG) item("backend_connection") {
                SettingsRow(R.string.backend_connect, R.drawable.ic_clock, "backend_connect") { model.backend.connect() }
            }
            item("subscription") {
                val low = snapshot.account.subscriptionCard == SubscriptionCard.LOW_BALANCE
                val color = if (low) Ds.colors.accentRed else Ds.colors.accentPrimary
                Column(Modifier.fillMaxWidth().background(if (low) color.copy(alpha = .5f) else Ds.colors.accentPrimaryAlpha, RoundedCornerShape(24.dp))
                    .border(1.dp, color, RoundedCornerShape(24.dp)).padding(16.dp).testTag("subscription_${snapshot.account.subscriptionCard.name.lowercase()}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.subscription_title), Modifier.weight(1f), style = Ds.type.title3Regular)
                        RoundAction(R.drawable.ic_crown, stringResource(R.string.subscription_title), Modifier.testTag("open_pro"), color) { actions.openOffers(OfferKind.PRO) }
                    }
                    Text(stringResource(if (low) R.string.low_balance_body else R.string.subscription_body), style = Ds.type.subheadlineRegular, color = Ds.colors.labelTertiary)
                    Text(snapshot.account.plan?.let { stringResource(R.string.account_plan, stringResource(if (it == SubscriptionPlan.YEAR) R.string.plan_year else R.string.plan_week)) }
                        ?: stringResource(if (snapshot.account.isPro) R.string.account_pro else R.string.account_free), style = Ds.type.caption1Regular, color = color, modifier = Modifier.testTag("account_status"))
                    if (snapshot.account.subscriptionCard != SubscriptionCard.PRO) DsButton(stringResource(R.string.more), Modifier.fillMaxWidth().testTag("card_more"), primary = low, containerColor = if (low) color else null, contentColor = if (low) Ds.colors.labelPrimary else null) {
                        actions.openOffers(if (low) OfferKind.TOKENS else OfferKind.PRO)
                    }
                }
            }
            item("share_heading") { SectionTitle(R.string.share_section) }
            item("share") { SettingsRow(R.string.share_friends, R.drawable.ic_share, "share_app", action = host::shareApp) }
            item("like") { SettingsRow(R.string.like_us, R.drawable.ic_heart_outline, "like_us") { actions.nav.navigate("rate") } }
            item("rate") { SettingsRow(R.string.rate_us, R.drawable.ic_star, "rate_us") { actions.nav.navigate("review") } }
            item("option_heading") { SectionTitle(R.string.option_section) }
            item("notifications") {
                Row(Modifier.fillMaxWidth().background(Ds.colors.backgroundSecondary, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DsIcon(R.drawable.ic_bell, null)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.notifications), style = Ds.type.calloutRegular)
                        if (snapshot.preferences.notificationsEnabled && !allowed) Text(stringResource(R.string.notification_off_body), style = Ds.type.caption1Regular, color = Ds.colors.labelTertiary)
                    }
                    Switch(snapshot.preferences.notificationsEnabled && allowed, { if (it) host.requestNotifications() else model.setNotifications(false) }, Modifier.testTag("notification_switch"),
                        colors = SwitchDefaults.colors(checkedTrackColor = Ds.colors.accentPrimary, checkedThumbColor = Ds.colors.labelPrimaryInverted, uncheckedTrackColor = Ds.colors.backgroundTertiary,
                            uncheckedThumbColor = Ds.colors.labelPrimary, uncheckedBorderColor = Ds.colors.separatorPrimary))
                }
            }
            item("report") { SettingsRow(R.string.report_problem, R.drawable.ic_report, "report_problem") { actions.nav.navigate("message/REPORT") } }
            item("restore") { SettingsRow(R.string.restore_purchases, R.drawable.ic_restore, "restore_settings", enabled = snapshot.commerce.operation == null) { model.restorePurchases() } }
            item("cache") { SettingsRow(R.string.clear_cache, R.drawable.ic_trash, "clear_cache", detail = if (busy) stringResource(R.string.clearing_cache) else Formatter.formatShortFileSize(LocalContext.current, bytes), enabled = !busy) { actions.show("clear_cache") } }
            item("legal_heading") { SectionTitle(R.string.legal_section) }
            item("letter") { SettingsRow(R.string.letter, R.drawable.ic_letter, "letter") { actions.nav.navigate("message/LETTER") } }
            item("privacy") { SettingsRow(R.string.privacy_policy, R.drawable.ic_legal, "settings_privacy") { actions.show("privacy") } }
            item("terms") { SettingsRow(R.string.terms_conditions, R.drawable.ic_legal, "settings_terms") { actions.show("terms") } }
            item("version") { Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), Modifier.fillMaxWidth().padding(12.dp).testTag("app_version"), color = Ds.colors.labelQuaternary, style = Ds.type.footnoteRegular) }
        }
    }
}
@Composable private fun SectionTitle(title: Int) { Text(stringResource(title), Modifier.padding(top = 18.dp, bottom = 6.dp), style = Ds.type.headlineEmphasized) }
@Composable private fun SettingsRow(title: Int, icon: Int, tag: String, detail: String? = null, enabled: Boolean = true, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Ds.colors.backgroundSecondary, RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = action)
        .heightIn(min = 56.dp).padding(16.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DsIcon(icon, null, if (enabled) Ds.colors.accentPrimary else Ds.colors.labelQuaternary)
        Text(stringResource(title), Modifier.weight(1f), style = Ds.type.calloutRegular, color = if (enabled) Ds.colors.labelPrimary else Ds.colors.labelQuaternary)
        if (detail != null) Text(detail, style = Ds.type.footnoteRegular, color = Ds.colors.labelTertiary)
        DsIcon(R.drawable.ic_back, null, Ds.colors.labelTertiary, Modifier.size(16.dp).rotate(180f))
    }
}
