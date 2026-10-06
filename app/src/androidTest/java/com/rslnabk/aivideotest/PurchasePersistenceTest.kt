package com.rslnabk.aivideotest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurchasePersistenceTest {
    @Test fun legacySnapshotRemainsReadableAndTransactionsRoundTripAtomically() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("purchase_codec_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("tokens", 12).putBoolean("pro", true).commit()
        try {
            val catalog = DemoCatalogRepository()
            val store = PreferencesDemoStore(prefs)
            assertEquals(DemoAccount(12, true), store.load().account)
            assertEquals(DemoCommerce(), store.load().commerce)
            val first = DemoSession(store, catalog)
            first.updateDraft("prompt_video") { it.copy(prompt = "A long night", resolution = 1080) }
            first.beginPurchase("persisted", DemoProduct.TOKENS_2, 1000, DemoPurchaseOutcome.SUCCESS, CreationIntent(CreationAction.GENERATE, "prompt_video"))
            val pending = DemoSession(PreferencesDemoStore(prefs), catalog)
            assertEquals(first.snapshot, pending.snapshot)
            pending.reconcile(2500)
            val settled = DemoSession(PreferencesDemoStore(prefs), catalog)
            settled.reconcile(9000)
            assertEquals(112, settled.snapshot.account.tokens); assertEquals(1, settled.snapshot.commerce.receipts.size)
            assertEquals(SubmitResult.Accepted("job"), settled.resumePurchase("persisted", "job", 9000))
            val accepted = DemoSession(PreferencesDemoStore(prefs), catalog)
            assertEquals(82, accepted.snapshot.account.tokens); assertEquals(1, accepted.snapshot.jobs.size)
            assertNull(accepted.snapshot.commerce.operation); assertNull(accepted.resumePurchase("persisted", "duplicate", 9000))
            assertEquals("A long night", accepted.snapshot.jobs.single().draft.prompt)
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun unknownProductsAndMalformedJsonDoNotInvalidateSavedAccount() {
        assertEquals(DemoCommerce(), CommerceJson.decode("invalid"))
        assertEquals(DemoCommerce(), CommerceJson.decode("{\"receipts\":[{\"id\":\"unknown\",\"product\":\"removed\"}],\"operation\":{\"id\":\"unknown\"}}"))
        val pro = DemoCommerce(listOf(DemoReceipt("year", DemoProduct.PRO_YEAR)), DemoPurchase("restore", PurchaseAction.RESTORE, null, 10, DemoPurchaseOutcome.SUCCESS))
        assertEquals(pro, CommerceJson.decode(CommerceJson.encode(pro)))
    }
}
