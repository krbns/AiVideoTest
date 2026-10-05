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
    fun setAccount(account: DemoAccount) = update(snapshot.copy(account = account.copy(tokens = account.tokens.coerceAtLeast(0))))
    fun reset() = update(DemoSnapshot())
    fun draft(key: String): GenerationDraft = snapshot.drafts[key] ?: catalog.effect(key)?.let {
        GenerationDraft(key, it.kind, effectId = it.id)
    } ?: GenerationDraft(key, if (key == "prompt_photo") MediaKind.PHOTO else MediaKind.VIDEO)
    fun updateDraft(key: String, change: (GenerationDraft) -> GenerationDraft) =
        update(snapshot.copy(drafts = snapshot.drafts + (key to change(draft(key)))))
    fun markInstructionSeen() = update(snapshot.copy(instructionSeen = true))
    fun cost(draft: GenerationDraft): Int = draft.effectId?.let { catalog.effect(it)?.tokenCost }
        ?: if (draft.kind == MediaKind.VIDEO && draft.resolution == 1080) 30 else 10
    fun submit(key: String, id: String, now: Long, fail: Boolean = false): SubmitResult {
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
            drafts = snapshot.drafts + (key to draft.copy(activeJobId = id)), jobs = snapshot.jobs + job))
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
    }
    /** Retry the immutable failed request in its existing history slot. Edited input stays untouched. */
    fun retryJob(id: String, now: Long, fail: Boolean = false): SubmitResult {
        val job = snapshot.jobs.find { it.id == id } ?: return SubmitResult.InvalidDraft
        if (job.status == JobStatus.RUNNING) return SubmitResult.Accepted(id)
        if (job.status != JobStatus.FAILED || !job.draft.isValid) return SubmitResult.InvalidDraft
        if (snapshot.account.tokens < job.tokenCost) return SubmitResult.InsufficientBalance(job.tokenCost)
        update(snapshot.copy(account = snapshot.account.copy(tokens = snapshot.account.tokens - job.tokenCost),
            jobs = snapshot.jobs.map { if (it.id == id) it.copy(status = JobStatus.RUNNING,
                readyAt = now + 4000, willFail = fail) else it }))
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
    private fun update(value: DemoSnapshot) { snapshot = value; store.save(value) }
}
