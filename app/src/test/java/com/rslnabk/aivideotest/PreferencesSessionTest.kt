package com.rslnabk.aivideotest

import com.rslnabk.aivideotest.data.DemoStore
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Test

class PreferencesSessionTest {
    private class Store(var value: DemoSnapshot = DemoSnapshot()) : DemoStore {
        override fun load() = value
        override fun save(snapshot: DemoSnapshot) { value = snapshot }
    }
    private fun session(store: Store = Store()) = DemoSession(store, DemoCatalogRepository())
    @Test fun `server intro completion persists without scheduling a demo offer or changing account`() {
        val store = Store(); val s = session(store)
        s.setAccount(DemoAccount(45, true)); s.toggleFavorite("photo_gold")
        val before = s.snapshot
        assertTrue(s.completeIntro(showOffer = false)); assertFalse(s.completeIntro(showOffer = false))
        val restored = session(store).snapshot
        assertEquals(IntroStep.DONE, restored.preferences.introStep)
        assertFalse(restored.preferences.introOfferPending)
        assertEquals(before.account, restored.account); assertEquals(before.favorites, restored.favorites)
        assertEquals(before.jobs, restored.jobs)
    }
    @Test fun `intro advances once from expected step and resumes a stored step`() {
        val store = Store(); val first = session(store)
        assertTrue(first.advanceIntro(IntroStep.WELCOME)); assertFalse(first.advanceIntro(IntroStep.WELCOME))
        val restored = session(store); assertEquals(IntroStep.PROMPT, restored.snapshot.preferences.introStep)
        restored.backIntro(); assertEquals(IntroStep.WELCOME, restored.snapshot.preferences.introStep)
        for (step in listOf(IntroStep.WELCOME, IntroStep.PROMPT, IntroStep.SHARE, IntroStep.REVIEWS)) assertTrue(restored.advanceIntro(step))
        assertTrue(restored.finishIntroPhoto(IntroPhotoChoice.CANCELLED)); assertFalse(restored.finishIntroPhoto(IntroPhotoChoice.PICKED))
        assertFalse(restored.advanceIntro(IntroStep.NOTIFICATIONS)); assertTrue(restored.completeIntro()); assertFalse(restored.completeIntro())
        assertEquals(IntroStep.DONE, session(store).snapshot.preferences.introStep)
        assertTrue(session(store).snapshot.preferences.introOfferPending)
        restored.markIntroOfferShown(); assertFalse(session(store).snapshot.preferences.introOfferPending)
    }
    @Test fun `replaying intro preserves account favorites review and prompt`() {
        val s = session(); s.setAccount(DemoAccount(100, true)); s.toggleFavorite("photo_gold")
        s.updateDraft("prompt_photo") { it.copy(prompt = "Morning") }; s.editReview(rating = 4); s.saveReview("r")
        s.completeIntro(); val before = s.snapshot; s.replayIntro()
        assertEquals(before.copy(preferences = before.preferences.copy(introStep = IntroStep.WELCOME, introPhotoChoice = IntroPhotoChoice.NONE, introOfferPending = false)), s.snapshot)
        s.reset(); assertEquals(DemoSnapshot(), s.snapshot)
    }
    @Test fun `notification opt in skips history future success claimed once across sessions`() {
        val store = Store(); val s = session(store); s.setAccount(DemoAccount(100))
        s.updateDraft("prompt_photo") { it.copy(prompt = "First") }; s.submit("prompt_photo", "old", 0); s.reconcile(4000)
        s.setNotifications(true); assertTrue(s.claimReadyNotifications().isEmpty())
        s.submit("prompt_photo", "new", 5000); s.reconcile(9000)
        assertEquals(listOf("new"), s.claimReadyNotifications().map { it.id })
        assertTrue(session(store).claimReadyNotifications().isEmpty()); assertEquals(80, s.snapshot.account.tokens)
        s.setNotifications(false); s.submit("prompt_photo", "off", 10000); s.reconcile(14000)
        assertTrue(s.claimReadyNotifications().isEmpty()); s.setNotifications(true); assertTrue(s.claimReadyNotifications().isEmpty())
    }
    @Test fun `failed creation never claims success and restores balance`() {
        val s = session(); s.setAccount(DemoAccount(100)); s.setNotifications(true)
        s.updateDraft("prompt_photo") { it.copy(prompt = "First") }; s.submit("prompt_photo", "bad", 0, true); s.reconcile(4000)
        assertTrue(s.claimReadyNotifications().isEmpty()); assertEquals(100, s.snapshot.account.tokens)
    }
    @Test fun `review validates stars and Unicode lengths before saving once`() {
        val s = session(); assertFalse(s.saveReview("a")); s.editReview(5, "😀".repeat(80), "я".repeat(1001)); assertFalse(s.saveReview("b"))
        s.editReview(text = "😀".repeat(1000)); assertTrue(s.saveReview("c")); assertFalse(s.saveReview("d"))
        assertEquals(5, s.snapshot.preferences.review!!.rating); assertEquals("c", s.snapshot.preferences.review!!.id)
        assertEquals(2000, s.snapshot.preferences.review!!.text.length); assertEquals(0, s.snapshot.preferences.ratingDraft)
        s.declineRating(); assertEquals(RatingDecision.DECLINED, s.snapshot.preferences.ratingDecision)
    }
    @Test fun `support drafts are independent valid once and bounded locally`() {
        val s = session(); s.editMessage(MessageKind.REPORT, "  "); assertFalse(s.saveMessage(MessageKind.REPORT, "blank"))
        s.editMessage(MessageKind.LETTER, "Letter"); s.editMessage(MessageKind.REPORT, "😀".repeat(1001)); assertFalse(s.saveMessage(MessageKind.REPORT, "long"))
        s.editMessage(MessageKind.REPORT, "Problem"); assertTrue(s.saveMessage(MessageKind.REPORT, "1")); assertFalse(s.saveMessage(MessageKind.REPORT, "2"))
        assertEquals("Letter", s.snapshot.preferences.letterDraft)
        repeat(22) { s.editMessage(MessageKind.LETTER, "Message $it"); assertTrue(s.saveMessage(MessageKind.LETTER, "$it")) }
        assertEquals(20, s.snapshot.preferences.messages.size); assertEquals("Message 2", s.snapshot.preferences.messages.first().text)
    }
    @Test fun `card uses low balance precedence and independent PRO access`() {
        assertEquals(SubscriptionCard.LOW_BALANCE, DemoAccount(0, true).subscriptionCard)
        assertEquals(SubscriptionCard.LOW_BALANCE, DemoAccount(9).subscriptionCard)
        assertEquals(SubscriptionCard.FREE, DemoAccount(10).subscriptionCard)
        assertEquals(SubscriptionCard.PRO, DemoAccount(10, true).subscriptionCard)
    }
}
