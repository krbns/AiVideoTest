package com.rslnabk.aivideotest

import com.rslnabk.aivideotest.data.DemoStore
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Test

class PurchaseSessionTest {
    private class MemoryStore(var value: DemoSnapshot = DemoSnapshot()) : DemoStore {
        override fun load() = value
        override fun save(snapshot: DemoSnapshot) { value = snapshot }
    }
    private fun session(store: MemoryStore = MemoryStore()) = DemoSession(store, DemoCatalogRepository())
    @Test fun `token purchase is durable exactly once and concurrent begins are rejected`() {
        val store = MemoryStore(); val first = session(store)
        assertTrue(first.beginPurchase("purchase", DemoProduct.TOKENS_2, 0, DemoPurchaseOutcome.SUCCESS))
        assertFalse(first.beginPurchase("duplicate", DemoProduct.TOKENS_3, 0, DemoPurchaseOutcome.SUCCESS))
        first.reconcile(1499); assertEquals(5, first.snapshot.account.tokens)
        val restored = session(store); restored.reconcile(1500); restored.reconcile(9000)
        assertEquals(105, restored.snapshot.account.tokens); assertEquals(1, restored.snapshot.commerce.receipts.size)
        assertFalse(restored.cancelPurchase("purchase"))
        assertFalse(restored.acknowledgePurchase("stale"))
        assertTrue(restored.acknowledgePurchase("purchase"))
        assertEquals(105, session(store).snapshot.account.tokens)
    }
    @Test fun `cancel error and retry retain request and never grant early`() {
        val s = session(); val intent = CreationIntent(CreationAction.GENERATE, "prompt_photo")
        s.beginPurchase("cancel", DemoProduct.PRO_YEAR, 0, DemoPurchaseOutcome.SUCCESS, intent)
        assertTrue(s.cancelPurchase("cancel")); s.reconcile(9000)
        assertFalse(s.snapshot.account.isPro); assertEquals(5, s.snapshot.account.tokens)
        s.acknowledgePurchase("cancel")
        s.beginPurchase("failed", DemoProduct.TOKENS_1, 0, DemoPurchaseOutcome.ERROR, intent)
        s.reconcile(1500); assertEquals(PurchasePhase.FAILED, s.snapshot.commerce.operation!!.phase)
        assertEquals(intent, s.snapshot.commerce.operation!!.continuation)
        assertTrue(s.retryPurchase("failed", "retry", 1500, DemoPurchaseOutcome.SUCCESS))
        assertFalse(s.retryPurchase("failed", "stale", 1500, DemoPurchaseOutcome.SUCCESS))
        s.reconcile(3000); assertEquals(105, s.snapshot.account.tokens)
    }
    @Test fun `restore only restores subscription and never credits consumables again`() {
        val s = session()
        s.beginPurchase("empty", null, 0, DemoPurchaseOutcome.SUCCESS, action = PurchaseAction.RESTORE)
        s.reconcile(1500); assertEquals(PurchasePhase.EMPTY, s.snapshot.commerce.operation!!.phase); s.acknowledgePurchase("empty")
        s.beginPurchase("tokens", DemoProduct.TOKENS_1, 0, DemoPurchaseOutcome.SUCCESS); s.reconcile(1500); s.acknowledgePurchase("tokens")
        s.beginPurchase("year", DemoProduct.PRO_YEAR, 0, DemoPurchaseOutcome.SUCCESS); s.reconcile(1500); s.acknowledgePurchase("year")
        s.setAccount(DemoAccount(0))
        repeat(2) {
            val id = "restore$it"
            s.beginPurchase(id, null, 0, DemoPurchaseOutcome.SUCCESS, action = PurchaseAction.RESTORE); s.reconcile(1500)
            assertEquals(DemoAccount(0, true, SubscriptionPlan.YEAR), s.snapshot.account)
            s.acknowledgePurchase(id)
        }
        assertEquals(2, s.snapshot.commerce.receipts.size)
    }
    @Test fun `purchase continuation accepts one request and charges once in same snapshot`() {
        val store = MemoryStore(); val s = session(store)
        s.updateDraft("prompt_video") { it.copy(prompt = "Night city", resolution = 1080) }
        s.beginPurchase("buy", DemoProduct.TOKENS_4, 0, DemoPurchaseOutcome.SUCCESS, CreationIntent(CreationAction.GENERATE, "prompt_video"))
        s.reconcile(1500)
        assertEquals(SubmitResult.Accepted("job"), s.resumePurchase("buy", "job", 1500))
        assertNull(s.resumePurchase("buy", "duplicate", 1500))
        val restored = session(store)
        assertEquals(75, restored.snapshot.account.tokens); assertEquals(1, restored.snapshot.jobs.size)
        assertNull(restored.snapshot.commerce.operation); assertEquals("Night city", restored.draft("prompt_video").prompt)
    }
    @Test fun `retry continuation retains failed job identity and edited draft`() {
        val s = session(); s.setAccount(DemoAccount(10)); s.updateDraft("prompt_video") { it.copy(prompt = "Original") }
        s.submit("prompt_video", "job", 0, true); s.reconcile(4000); s.setAccount(DemoAccount(0))
        s.updateDraft("prompt_video") { it.copy(prompt = "Edited") }
        s.beginPurchase("buy", DemoProduct.TOKENS_1, 4000, DemoPurchaseOutcome.SUCCESS, CreationIntent(CreationAction.RETRY, "job"))
        s.reconcile(5500); assertEquals(SubmitResult.Accepted("job"), s.resumePurchase("buy", "unused", 5500))
        assertEquals(90, s.snapshot.account.tokens); assertEquals("Original", s.snapshot.jobs.single().draft.prompt)
        assertEquals("Edited", s.draft("prompt_video").prompt); assertEquals(1, s.snapshot.jobs.size)
    }
    @Test fun `PRO never replenishes tokens and insufficient continuation can be consumed once`() {
        val s = session(); s.updateDraft("prompt_photo") { it.copy(prompt = "Portrait") }
        s.beginPurchase("pro", DemoProduct.PRO_WEEK, 0, DemoPurchaseOutcome.SUCCESS, CreationIntent(CreationAction.GENERATE, "prompt_photo"))
        s.reconcile(1500); assertEquals(DemoAccount(5, true, SubscriptionPlan.WEEK), s.snapshot.account)
        assertEquals(SubmitResult.InsufficientBalance(10), s.resumePurchase("pro", "job", 1500))
        assertNull(s.resumePurchase("pro", "duplicate", 1500)); assertTrue(s.snapshot.jobs.isEmpty())
        s.reset(); assertEquals(DemoSnapshot(), s.snapshot)
    }
    @Test fun `stale acknowledgements and resume cannot consume a newer transaction`() {
        val s = session(); s.beginPurchase("old", DemoProduct.TOKENS_1, 0, DemoPurchaseOutcome.SUCCESS)
        s.reconcile(1500); s.acknowledgePurchase("old")
        s.beginPurchase("new", DemoProduct.TOKENS_1, 2000, DemoPurchaseOutcome.SUCCESS)
        assertFalse(s.acknowledgePurchase("old")); assertNull(s.resumePurchase("old", "job", 4000))
        assertEquals("new", s.snapshot.commerce.operation!!.id)
        s.reconcile(3500); assertEquals(205, s.snapshot.account.tokens)
    }
}
