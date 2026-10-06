package com.rslnabk.aivideotest

import android.content.Context
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.data.demo.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurchaseFlowTest : ComposeFlowTest() {
    @Test fun weeklySelectionRotationPurchaseAndRestoreShareOneAccount() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("tab_settings").performClick(); tag("open_pro").performClick()
            tag("plan_year").performScrollTo().assertIsSelected()
            tag("plan_week").performScrollTo().performClick().assertIsSelected()
            scenario.recreate(); tag("plan_week").performScrollTo().assertIsSelected()
            tag("purchase").performScrollTo().performClick()
            scenario.recreate()
            await(scenario) { it.snapshot.value!!.commerce.operation?.phase == PurchasePhase.SUCCEEDED }
            text(R.string.purchase_done).performClick()
            tag("account_status").assertTextEquals("PRO · Week")
            scenario.onActivity {
                assertEquals(DemoAccount(5, true, SubscriptionPlan.WEEK), it.model.snapshot.value!!.account)
                it.model.setAccount(DemoAccount(0))
            }
            tag("restore_settings").performScrollTo().performClick()
            await(scenario) { it.snapshot.value!!.commerce.operation?.phase == PurchasePhase.SUCCEEDED }
            text(R.string.purchase_done).performClick()
            scenario.onActivity { assertEquals(DemoAccount(0, true, SubscriptionPlan.WEEK), it.model.snapshot.value!!.account) }
        }
    }
    @Test fun failedTokenPurchaseRetriesOnceAndSurvivesNewActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("balance").performClick(); tag("offer_balance").assertTextEquals("5")
            scenario.onActivity { it.model.nextPurchaseOutcome = DemoPurchaseOutcome.ERROR }
            tag("pack_4").performScrollTo().performClick()
            await(scenario) { it.snapshot.value!!.commerce.operation?.phase == PurchasePhase.FAILED }
            scenario.onActivity { assertEquals(5, it.model.snapshot.value!!.account.tokens) }
            scenario.recreate(); text(R.string.retry).performClick()
            await(scenario) { it.snapshot.value!!.commerce.operation?.phase == PurchasePhase.SUCCEEDED }
            text(R.string.purchase_done).performClick()
            scenario.onActivity { assertEquals(105, it.model.snapshot.value!!.account.tokens); assertEquals(1, it.model.snapshot.value!!.commerce.receipts.size) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals(105, it.model.snapshot.value!!.account.tokens); assertNull(it.model.snapshot.value!!.commerce.operation) }
            tag("balance").performClick(); tag("offer_balance").assertTextEquals("105")
        }
    }
    @Test fun insufficientPromptCancelCloseAndSuccessfulContinuationKeepInput() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            text(R.string.prompt).performClick(); tag("prompt_input").performTextReplacement("City at night")
            ui.waitForIdle()
            tag("generate").performScrollTo().performClick()
            tag("offer_close").performClick(); tag("prompt_input").assertTextContains("City at night")
            scenario.onActivity { assertEquals(5, it.model.snapshot.value!!.account.tokens); assertTrue(it.model.snapshot.value!!.jobs.isEmpty()) }
            tag("generate").performScrollTo().performClick()
            scenario.onActivity { it.model.nextPurchaseOutcome = DemoPurchaseOutcome.CANCEL }
            tag("pack_2").performScrollTo().performClick()
            await(scenario) { it.snapshot.value!!.commerce.operation?.phase == PurchasePhase.CANCELLED }
            text(R.string.okay).performClick(); tag("prompt_input").assertTextContains("City at night")
            tag("generate").performScrollTo().performClick(); tag("pack_3").performScrollTo().performClick()
            await(scenario) { it.snapshot.value!!.commerce.operation?.phase == PurchasePhase.SUCCEEDED }
            scenario.recreate(); text(R.string.purchase_resume).performClick()
            await(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.SUCCEEDED }
            scenario.onActivity { assertEquals(95, it.model.snapshot.value!!.account.tokens); assertEquals(1, it.model.snapshot.value!!.commerce.receipts.size) }
            tag("share").assertIsDisplayed(); back(scenario); tag("prompt_input").assertTextContains("City at night")
        }
    }
    @Test fun cancelDuringLoadingAndEmptyRestoreDoNotAlterAccount() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("balance").performClick(); tag("pack_1").performScrollTo().performClick()
            tag("purchase_cancel").performClick()
            text(R.string.purchase_cancelled).assertIsDisplayed(); text(R.string.okay).performClick()
            tag("tab_settings").performClick(); tag("open_pro").performClick()
            tag("restore").performScrollTo().performClick()
            await(scenario) { it.snapshot.value!!.commerce.operation?.phase == PurchasePhase.EMPTY }
            text(R.string.okay).performClick(); tag("offer_pro").assertIsDisplayed()
            scenario.onActivity { assertEquals(DemoAccount(), it.model.snapshot.value!!.account); assertTrue(it.model.snapshot.value!!.commerce.receipts.isEmpty()) }
            text(R.string.terms_of_use).performScrollTo().performClick(); text(R.string.demo_legal_body).assertIsDisplayed()
            text(R.string.okay).performClick(); tag("offer_close").performClick(); tag("tab_settings").assertIsDisplayed()
        }
    }
    @Test fun coldLaunchSettlesPersistedPurchaseAndRestoresInputOriginOnce() {
        val store = PreferencesDemoStore(context.getSharedPreferences("demo_state_v1", Context.MODE_PRIVATE))
        val session = DemoSession(store, DemoCatalogRepository())
        session.updateDraft("prompt_photo") { it.copy(prompt = "A quiet morning") }
        session.beginPurchase("cold-buy", DemoProduct.TOKENS_1, 0, DemoPurchaseOutcome.SUCCESS, CreationIntent(CreationAction.GENERATE, "prompt_photo"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            text(R.string.purchase_resume).performClick()
            await(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.SUCCEEDED }
            scenario.onActivity { it.finishPurchase("cold-buy"); assertEquals(95, it.model.snapshot.value!!.account.tokens); assertEquals(1, it.model.snapshot.value!!.jobs.size) }
            back(scenario); tag("prompt_input").assertTextContains("A quiet morning")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals(95, it.model.snapshot.value!!.account.tokens); assertNull(it.model.snapshot.value!!.commerce.operation) }
        }
    }
}
