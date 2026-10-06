package com.rslnabk.aivideotest.data.demo

import com.rslnabk.aivideotest.data.CatalogRepository
import com.rslnabk.aivideotest.data.DemoStore
import com.rslnabk.aivideotest.model.*

/** One source of truth for account and favorite IDs across every screen. */
class DemoSession(private val store: DemoStore, private val catalog: CatalogRepository) {
    var snapshot = store.load().let { it.copy(favorites = it.favorites.filter { id -> catalog.effect(id) != null }.toSet()) }
        private set
    fun toggleFavorite(id: String) {
        if (catalog.effect(id) == null) return
        update(snapshot.copy(favorites = if (id in snapshot.favorites) snapshot.favorites - id else snapshot.favorites + id))
    }
    fun setAccount(account: DemoAccount) = update(snapshot.copy(account = account.copy(tokens = account.tokens.coerceAtLeast(0), plan = if (account.isPro) account.plan else null)))
    fun reset() = update(DemoSnapshot())
    fun draft(key: String): GenerationDraft = snapshot.drafts[key] ?: catalog.effect(key)?.let {
        GenerationDraft(key, it.kind, effectId = it.id)
    } ?: GenerationDraft(key, if (key == "prompt_photo") MediaKind.PHOTO else MediaKind.VIDEO)
    fun updateDraft(key: String, change: (GenerationDraft) -> GenerationDraft) =
        update(snapshot.copy(drafts = snapshot.drafts + (key to change(draft(key)))))
    fun markInstructionSeen() = update(snapshot.copy(instructionSeen = true))
    fun advanceIntro(expected: IntroStep): Boolean {
        if (snapshot.preferences.introStep != expected || expected == IntroStep.DONE || expected == IntroStep.PHOTOS || expected == IntroStep.NOTIFICATIONS) return false
        preferences { it.copy(introStep = IntroStep.entries[expected.ordinal + 1]) }; return true
    }
    fun finishIntroPhoto(choice: IntroPhotoChoice): Boolean {
        if (snapshot.preferences.introStep != IntroStep.PHOTOS) return false
        preferences { it.copy(introStep = IntroStep.NOTIFICATIONS, introPhotoChoice = choice) }; return true
    }
    fun backIntro() {
        val step = snapshot.preferences.introStep
        if (step != IntroStep.WELCOME && step != IntroStep.DONE)
            preferences { it.copy(introStep = IntroStep.entries[step.ordinal - 1]) }
    }
    fun completeIntro(): Boolean {
        if (snapshot.preferences.introStep == IntroStep.DONE) return false
        preferences { it.copy(introStep = IntroStep.DONE, introOfferPending = true) }; return true
    }
    fun replayIntro() = preferences { it.copy(introStep = IntroStep.WELCOME, introPhotoChoice = IntroPhotoChoice.NONE, introOfferPending = false) }
    fun markIntroOfferShown() = preferences { it.copy(introOfferPending = false) }
    fun markNotificationAsked() = preferences { it.copy(notificationAsked = true) }
    fun setNotifications(enabled: Boolean) = preferences { it.copy(notificationsEnabled = enabled,
        // Opting in does not announce old history. Only future ready results are claimed.
        notifiedJobIds = if (enabled) it.notifiedJobIds + snapshot.jobs.filter { job -> job.status == JobStatus.SUCCEEDED }.map { job -> job.id } else it.notifiedJobIds) }
    fun claimReadyNotifications(): List<GenerationJob> {
        if (!snapshot.preferences.notificationsEnabled) return emptyList()
        val ready = snapshot.jobs.filter { it.status == JobStatus.SUCCEEDED && it.id !in snapshot.preferences.notifiedJobIds }
        if (ready.isNotEmpty()) preferences { it.copy(notifiedJobIds = it.notifiedJobIds + ready.map { job -> job.id }) }
        return ready
    }
    fun editReview(rating: Int? = null, name: String? = null, text: String? = null) = preferences {
        it.copy(ratingDraft = rating?.coerceIn(0, 5) ?: it.ratingDraft, reviewName = name ?: it.reviewName, reviewText = text ?: it.reviewText)
    }
    fun declineRating() = preferences { it.copy(ratingDecision = RatingDecision.DECLINED) }
    fun saveReview(id: String): Boolean {
        val p = snapshot.preferences
        if (p.ratingDraft !in 1..5 || p.reviewName.codePointCount(0, p.reviewName.length) > 80 || p.reviewText.codePointCount(0, p.reviewText.length) > 1000) return false
        preferences { it.copy(review = DemoReview(id, p.ratingDraft, p.reviewName.trim(), p.reviewText.trim()), ratingDecision = RatingDecision.SAVED,
            ratingDraft = 0, reviewName = "", reviewText = "") }; return true
    }
    fun editMessage(kind: MessageKind, text: String) = preferences {
        if (kind == MessageKind.REPORT) it.copy(reportDraft = text) else it.copy(letterDraft = text)
    }
    fun saveMessage(kind: MessageKind, id: String): Boolean {
        val p = snapshot.preferences; val text = if (kind == MessageKind.REPORT) p.reportDraft else p.letterDraft
        if (text.isBlank() || text.codePointCount(0, text.length) > 1000) return false
        preferences { it.copy(reportDraft = if (kind == MessageKind.REPORT) "" else it.reportDraft,
            letterDraft = if (kind == MessageKind.LETTER) "" else it.letterDraft,
            messages = (it.messages + DemoMessage(id, kind, text.trim())).takeLast(20)) }; return true
    }
    private fun preferences(change: (DemoPreferences) -> DemoPreferences) {
        val value = change(snapshot.preferences)
        if (value != snapshot.preferences) update(snapshot.copy(preferences = value))
    }
    fun cost(draft: GenerationDraft): Int = draft.effectId?.let { catalog.effect(it)?.tokenCost }
        ?: if (draft.kind == MediaKind.VIDEO && draft.resolution == 1080) 30 else 10
    fun submit(key: String, id: String, now: Long, fail: Boolean = false): SubmitResult = submit(key, id, now, fail, snapshot.commerce)
    private fun submit(key: String, id: String, now: Long, fail: Boolean, commerce: DemoCommerce): SubmitResult {
        val draft = draft(key)
        snapshot.jobs.find { it.id == draft.activeJobId && it.status == JobStatus.RUNNING }?.let {
            return SubmitResult.Accepted(it.id)
        }
        if (!draft.isValid) return SubmitResult.InvalidDraft
        val required = cost(draft)
        if (snapshot.account.tokens < required) return SubmitResult.InsufficientBalance(required)
        val image = DemoResultFixtures.image(draft, catalog)
        val job = GenerationJob(id, draft, required, image, now, now + 4000, fail)
        update(snapshot.copy(account = snapshot.account.copy(tokens = snapshot.account.tokens - required),
            drafts = snapshot.drafts + (key to draft.copy(activeJobId = id)), jobs = snapshot.jobs + job, commerce = commerce))
        return SubmitResult.Accepted(id)
    }
    /** Settles persisted jobs by deadline, independently of the current screen. Failure refunds once. */
    fun reconcile(now: Long) {
        var refund = 0
        val jobs = snapshot.jobs.map { job ->
            if (job.status == JobStatus.RUNNING && now >= job.readyAt) {
                if (job.willFail) refund += job.tokenCost
                job.copy(status = if (job.willFail) JobStatus.FAILED else JobStatus.SUCCEEDED)
            } else job
        }
        if (jobs != snapshot.jobs) update(snapshot.copy(jobs = jobs,
            account = snapshot.account.copy(tokens = snapshot.account.tokens + refund)))
        settlePurchase(now)
    }
    /** Retry the immutable failed request in its existing history slot. Edited input stays untouched. */
    fun retryJob(id: String, now: Long, fail: Boolean = false): SubmitResult = retryJob(id, now, fail, snapshot.commerce)
    private fun retryJob(id: String, now: Long, fail: Boolean, commerce: DemoCommerce): SubmitResult {
        val job = snapshot.jobs.find { it.id == id } ?: return SubmitResult.InvalidDraft
        if (job.status == JobStatus.RUNNING) return SubmitResult.Accepted(id)
        if (job.status != JobStatus.FAILED || !job.draft.isValid) return SubmitResult.InvalidDraft
        if (snapshot.account.tokens < job.tokenCost) return SubmitResult.InsufficientBalance(job.tokenCost)
        update(snapshot.copy(account = snapshot.account.copy(tokens = snapshot.account.tokens - job.tokenCost),
            jobs = snapshot.jobs.map { if (it.id == id) it.copy(status = JobStatus.RUNNING,
                readyAt = now + 4000, willFail = fail) else it }, commerce = commerce))
        return SubmitResult.Accepted(id)
    }
    /** Active jobs finish normally. Removing settled history never changes tokens or favorites. */
    fun deleteJob(id: String): Boolean {
        val job = snapshot.jobs.find { it.id == id } ?: return false
        if (job.status == JobStatus.RUNNING) return false
        update(snapshot.copy(jobs = snapshot.jobs.filterNot { it.id == id },
            drafts = snapshot.drafts.mapValues { (_, draft) ->
                if (draft.activeJobId == id) draft.copy(activeJobId = null) else draft
            }))
        return true
    }
    fun beginPurchase(id: String, product: DemoProduct?, now: Long, outcome: DemoPurchaseOutcome,
        continuation: CreationIntent? = null, action: PurchaseAction = PurchaseAction.BUY): Boolean {
        if (snapshot.commerce.operation != null || (action == PurchaseAction.BUY && product == null)) return false
        update(snapshot.copy(commerce = snapshot.commerce.copy(operation = DemoPurchase(id, action, product, now + 1500, outcome, continuation = continuation))))
        return true
    }
    fun cancelPurchase(id: String): Boolean {
        val op = snapshot.commerce.operation ?: return false
        if (op.id != id || op.phase != PurchasePhase.LOADING) return false
        update(snapshot.copy(commerce = snapshot.commerce.copy(operation = op.copy(phase = PurchasePhase.CANCELLED))))
        return true
    }
    fun acknowledgePurchase(id: String): Boolean {
        val op = snapshot.commerce.operation ?: return false
        if (op.id != id || op.phase == PurchasePhase.LOADING) return false
        update(snapshot.copy(commerce = snapshot.commerce.copy(operation = null)))
        return true
    }
    fun retryPurchase(id: String, newId: String, now: Long, outcome: DemoPurchaseOutcome): Boolean {
        val op = snapshot.commerce.operation ?: return false
        if (op.id != id || op.phase != PurchasePhase.FAILED) return false
        update(snapshot.copy(commerce = snapshot.commerce.copy(operation = op.copy(id = newId, readyAt = now + 1500,
            phase = PurchasePhase.LOADING, outcome = outcome))))
        return true
    }
    /** Clearing the successful purchase and accepting its continuation are one stored snapshot. */
    fun resumePurchase(id: String, newJobId: String, now: Long, fail: Boolean = false): SubmitResult? {
        val op = snapshot.commerce.operation ?: return null
        if (op.id != id || op.phase != PurchasePhase.SUCCEEDED) return null
        val intent = op.continuation ?: return null
        val commerce = snapshot.commerce.copy(operation = null)
        val result = when (intent.action) {
            CreationAction.GENERATE -> submit(intent.target, newJobId, now, fail, commerce)
            CreationAction.RETRY -> retryJob(intent.target, now, fail, commerce)
        }
        // Invalid/insufficient drafts and an already-running job also consume the continuation once.
        if (snapshot.commerce.operation?.id == id) update(snapshot.copy(commerce = commerce))
        return result
    }
    private fun settlePurchase(now: Long) {
        val op = snapshot.commerce.operation ?: return
        if (op.phase != PurchasePhase.LOADING || now < op.readyAt) return
        var account = snapshot.account
        var receipts = snapshot.commerce.receipts
        val phase = when (op.outcome) {
            DemoPurchaseOutcome.CANCEL -> PurchasePhase.CANCELLED
            DemoPurchaseOutcome.ERROR -> PurchasePhase.FAILED
            DemoPurchaseOutcome.SUCCESS -> {
                val product = if (op.action == PurchaseAction.RESTORE)
                    receipts.lastOrNull { it.product.kind == OfferKind.PRO }?.product else op.product
                if (product == null) PurchasePhase.EMPTY
                else {
                    if (product.kind == OfferKind.PRO) account = account.copy(isPro = true, plan = product.plan)
                    else account = account.copy(tokens = (account.tokens.toLong() + product.tokens).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    if (op.action == PurchaseAction.BUY) receipts = receipts + DemoReceipt(op.id, product)
                    PurchasePhase.SUCCEEDED
                }
            }
        }
        update(snapshot.copy(account = account, commerce = DemoCommerce(receipts, op.copy(phase = phase))))
    }
    private fun update(value: DemoSnapshot) { snapshot = value; store.save(value) }
}
