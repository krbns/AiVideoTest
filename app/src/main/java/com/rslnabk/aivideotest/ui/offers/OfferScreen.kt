package com.rslnabk.aivideotest.ui.offers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rslnabk.aivideotest.AppViewModel
import com.rslnabk.aivideotest.MainActivity
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import com.rslnabk.aivideotest.ui.navigation.AppNavigator
import com.rslnabk.aivideotest.ui.theme.Ds

@Composable fun OfferScreen(kind: OfferKind, continuation: CreationIntent?, snapshot: DemoSnapshot, model: AppViewModel, actions: AppNavigator) {
    var selection by rememberSaveable { mutableStateOf(DemoProduct.PRO_YEAR.name) }
    val busy = snapshot.commerce.operation != null
    val title = stringResource(if (kind == OfferKind.PRO) R.string.unlock_pro else R.string.need_tokens)
    val close = stringResource(R.string.close)
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("offer_${kind.name.lowercase()}")) {
        val heroHeight = (maxHeight * .52f).coerceAtMost(440.dp)
        val contentOffset = (maxHeight * .38f).coerceIn(160.dp, 330.dp)
        Box(Modifier.fillMaxWidth().height(heroHeight).graphicsLayer { translationY = -scroll.value.toFloat() }) {
        Image(painterResource(R.drawable.demo_offer_hero), null, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        Box(Modifier.matchParentSize()
            .background(Brush.verticalGradient(0f to Color.Transparent, .35f to Color.Transparent,
                .75f to Ds.colors.backgroundPrimary, 1f to Ds.colors.backgroundPrimary)))
        }
        Column(Modifier.fillMaxSize().padding(top = 64.dp).verticalScroll(scroll).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(contentOffset - 64.dp))
            Text(title, Modifier.fillMaxWidth(), style = Ds.type.title1Emphasized, color = Ds.colors.labelPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (kind == OfferKind.PRO) {
                Column(Modifier.align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(R.string.pro_effects, R.string.pro_unlimited, R.string.pro_functions).forEach { text ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DsIcon(R.drawable.ic_sparkle, null, Ds.colors.labelSecondary, Modifier.size(16.dp))
                            Text(stringResource(text), style = Ds.type.calloutRegular, color = Ds.colors.labelSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                listOf(DemoProduct.PRO_YEAR, DemoProduct.PRO_WEEK).forEach { product ->
                    PlanCard(product, selection == product.name, !busy) { selection = product.name }
                    Spacer(Modifier.height(8.dp))
                }
                LegalFooter(true, !busy, actions) { model.restorePurchases(continuation) }
                DsButton(stringResource(R.string.continue_action), Modifier.fillMaxWidth().testTag("purchase"), primary = true, enabled = !busy) {
                    model.purchase(DemoProduct.valueOf(selection), continuation)
                }
                Text(stringResource(R.string.pro_demo_policy), Modifier.fillMaxWidth().padding(top = 12.dp), style = Ds.type.caption1Regular,
                    color = Ds.colors.labelTertiary, textAlign = TextAlign.Center)
            } else {
                Text(stringResource(R.string.tokens_subtitle), Modifier.fillMaxWidth(), style = Ds.type.calloutRegular, color = Ds.colors.labelTertiary, textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.my_tokens), color = Ds.colors.labelPrimary, style = Ds.type.headlineEmphasized)
                    Text(snapshot.account.tokens.toString(), color = Ds.colors.accentPrimary, style = Ds.type.headlineEmphasized, modifier = Modifier.testTag("offer_balance"))
                }
                Spacer(Modifier.height(24.dp))
                if (continuation != null) {
                    val cost = if (continuation.action == CreationAction.RETRY) snapshot.jobs.find { it.id == continuation.target }?.tokenCost
                        else model.cost(continuation.target)
                    cost?.let { Text(pluralStringResource(R.plurals.insufficient_body, it, it), Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        style = Ds.type.footnoteRegular, color = Ds.colors.labelSecondary, textAlign = TextAlign.Center) }
                }
                DemoProduct.entries.filter { it.kind == OfferKind.TOKENS }.forEachIndexed { index, product ->
                    TokenCard(product, index + 1, !busy) { model.purchase(product, continuation) }
                    Spacer(Modifier.height(12.dp))
                }
                LegalFooter(false, !busy, actions) {}
            }
            Text(stringResource(R.string.offer_demo_note), Modifier.fillMaxWidth().padding(vertical = 12.dp),
                style = Ds.type.caption1Regular, color = Ds.colors.labelTertiary, textAlign = TextAlign.Center)
        }
        TextButton({ actions.closeOffers() }, Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp).testTag("offer_close"), enabled = !busy) {
            Text(stringResource(R.string.remove_symbol), color = Ds.colors.labelPrimary, style = Ds.type.title1Regular,
                modifier = Modifier.semantics { contentDescription = close })
        }
    }
}
@Composable private fun PlanCard(product: DemoProduct, selected: Boolean, enabled: Boolean, click: () -> Unit) {
    val period = stringResource(if (product.plan == SubscriptionPlan.YEAR) R.string.plan_year else R.string.plan_week)
    val price = stringResource(R.string.plan_price, product.price, period)
    val topPadding = if (product.savings) with(LocalDensity.current) { Ds.type.caption2Emphasized.lineHeight.toDp() } + 10.dp else 14.dp
    DsCardSurface(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = click)
        .testTag("plan_${product.plan!!.name.lowercase()}"), selected) { Box {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected, null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = Ds.colors.accentPrimary, unselectedColor = Ds.colors.labelTertiary))
            Column(Modifier.weight(1f)) {
                Text(price, style = Ds.type.headlineEmphasized, color = Ds.colors.labelPrimary)
                Text(stringResource(R.string.plan_renewable), style = Ds.type.caption1Regular, color = Ds.colors.labelTertiary)
            }
        }
        if (product.savings) SavingsBadge(Modifier.align(Alignment.TopEnd))
    } }
}
@Composable private fun TokenCard(product: DemoProduct, index: Int, enabled: Boolean, click: () -> Unit) {
    val description = pluralStringResource(R.plurals.token_pack_description, product.tokens, index, product.tokens, product.price)
    val stackedBadge = LocalDensity.current.fontScale > 1.2f
    DsCardSurface(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = click)
        .semantics { contentDescription = description }.testTag("pack_$index")) {
    Column {
    Row(Modifier.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pluralStringResource(R.plurals.token_pack_amount, product.tokens, product.tokens), Modifier.weight(1f), color = Ds.colors.labelSecondary, style = Ds.type.subheadlineRegular)
        if (product.savings && !stackedBadge) SavingsBadge()
        Text(product.price, color = Ds.colors.labelTertiary, style = Ds.type.subheadlineRegular)
        Text("›", color = Ds.colors.labelSecondary, style = Ds.type.title2Regular)
    }
    if (product.savings && stackedBadge) SavingsBadge(Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 12.dp))
    } }
}
@Composable private fun SavingsBadge(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.save_40), modifier.background(Ds.colors.accentPrimary, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        color = Ds.colors.labelPrimaryInverted, style = Ds.type.caption2Emphasized)
}
@Composable private fun LegalFooter(restore: Boolean, enabled: Boolean, actions: AppNavigator, onRestore: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton({ actions.show("terms") }, enabled = enabled, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.terms_of_use), style = Ds.type.caption2Regular, color = Ds.colors.labelTertiary) }
        if (restore) TextButton(onRestore, enabled = enabled, modifier = Modifier.testTag("restore"), contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.restore_purchases), style = Ds.type.caption2Regular, color = Ds.colors.labelTertiary) }
        TextButton({ actions.show("privacy") }, enabled = enabled, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.privacy_policy), style = Ds.type.caption2Regular, color = Ds.colors.labelTertiary) }
    }
}
@Composable fun PurchaseStatus(operation: DemoPurchase?, host: MainActivity, model: AppViewModel, actions: AppNavigator) {
    if (operation == null) return
    val restore = operation.action == PurchaseAction.RESTORE
    val loading = operation.phase == PurchasePhase.LOADING
    BackHandler(loading) { model.cancelPurchase(operation.id) }
    val dismiss = {
        if (loading) model.cancelPurchase(operation.id)
        else { model.acknowledgePurchase(operation.id); if (operation.phase != PurchasePhase.EMPTY) actions.closeOffers() }
    }
    when (operation.phase) {
        PurchasePhase.LOADING -> AlertDialog(onDismissRequest = dismiss,
            containerColor = Ds.colors.backgroundSecondary,
            title = { Text(stringResource(if (restore) R.string.restore_loading else R.string.purchase_loading)) },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.testTag("purchase_progress"), color = Ds.colors.accentPrimary)
                Text(stringResource(R.string.offer_demo_note), style = Ds.type.calloutRegular)
            } }, confirmButton = { TextButton(dismiss, Modifier.testTag("purchase_cancel")) { Text(stringResource(R.string.cancel)) } })
        PurchasePhase.SUCCEEDED -> {
            val title = if (restore) stringResource(R.string.restore_success) else if (operation.product?.kind == OfferKind.PRO)
                stringResource(R.string.purchase_pro_success) else pluralStringResource(R.plurals.purchase_tokens_success, operation.product?.tokens ?: 0, operation.product?.tokens ?: 0)
            val message = if (operation.product?.kind == OfferKind.PRO && operation.continuation != null) R.string.purchase_pro_continue_body else R.string.purchase_success_body
            // Dismissal keeps the saved draft; only the explicit button consumes/accepts a continuation.
            InfoDialog(title, stringResource(message), dismiss,
                stringResource(if (operation.continuation == null) R.string.purchase_done else R.string.purchase_resume), { host.finishPurchase(operation.id) })
        }
        PurchasePhase.FAILED -> InfoDialog(stringResource(if (restore) R.string.restore_failed else R.string.purchase_failed), stringResource(R.string.purchase_failed_body), dismiss,
            stringResource(R.string.retry), { model.retryPurchase(operation.id) }, stringResource(R.string.cancel))
        PurchasePhase.CANCELLED -> InfoDialog(stringResource(if (restore) R.string.restore_cancelled else R.string.purchase_cancelled), stringResource(R.string.purchase_cancelled_body), dismiss)
        PurchasePhase.EMPTY -> InfoDialog(stringResource(R.string.restore_empty), stringResource(R.string.restore_empty_body), dismiss)
    }
}
