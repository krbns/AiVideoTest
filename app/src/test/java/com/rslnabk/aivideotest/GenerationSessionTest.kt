package com.rslnabk.aivideotest

import com.rslnabk.aivideotest.data.DemoStore
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Test

class GenerationSessionTest {
    private class Store(var value: DemoSnapshot = DemoSnapshot(account = DemoAccount(100))) : DemoStore {
        override fun load() = value
        override fun save(snapshot: DemoSnapshot) { value = snapshot }
    }
    private fun session(store: Store = Store()) = DemoSession(store, DemoCatalogRepository())
    @Test fun `blank and overflow are invalid while Unicode counts code points`() {
        val draft = GenerationDraft("prompt_video",MediaKind.VIDEO)
        assertFalse(draft.copy(prompt = "  \n").isValid)
        listOf(1,299,300).forEach { assertTrue(draft.copy(prompt = "👋".repeat(it)).isValid) }
        assertEquals(300,draft.copy(prompt = "👋".repeat(300)).characterCount)
        assertFalse(draft.copy(prompt = "a".repeat(301)).isValid)
    }
    @Test fun `effect requires a ready photo and failed replacement retains the original`() {
        val draft = GenerationDraft("video_gold",MediaKind.VIDEO,effectId = "video_gold")
        assertFalse(draft.isValid)
        assertTrue(draft.copy(photo = "asset:good1",photoStatus = PhotoStatus.READY).isValid)
        assertFalse(draft.copy(photo = "asset:good1",photoStatus = PhotoStatus.LOADING).isValid)
        assertFalse(draft.copy(photo = "asset:good1",photoStatus = PhotoStatus.FAILED).isValid)
    }
    @Test fun `double submission reuses running job and charges once`() {
        val session = session();session.updateDraft("prompt_video") { it.copy(prompt = "Fly through a city") }
        assertEquals(SubmitResult.Accepted("one"),session.submit("prompt_video","one",1000))
        assertEquals(SubmitResult.Accepted("one"),session.submit("prompt_video","two",1001))
        assertEquals(1,session.snapshot.jobs.size);assertEquals(90,session.snapshot.account.tokens)
    }
    @Test fun `job completes after session restoration without resubmission`() {
        val store = Store();val first = session(store)
        first.updateDraft("prompt_photo") { it.copy(prompt = "Тёплый закат",style = PhotoStyle.GHIBLI) }
        first.submit("prompt_photo","photo",1000)
        val restored = session(store);restored.reconcile(5000)
        assertEquals(JobStatus.SUCCEEDED,restored.snapshot.jobs.single().status)
        assertEquals(90,restored.snapshot.account.tokens)
        assertEquals(PhotoStyle.GHIBLI,restored.draft("prompt_photo").style)
    }
    @Test fun `failure refunds once and retry creates a fresh job`() {
        val session = session();session.updateDraft("prompt_video") { it.copy(prompt = "Sample",resolution = 1080) }
        session.submit("prompt_video","failed",1000,true);assertEquals(70,session.snapshot.account.tokens)
        session.reconcile(5000);session.reconcile(6000)
        assertEquals(100,session.snapshot.account.tokens);assertEquals(JobStatus.FAILED,session.snapshot.jobs.single().status)
        session.submit("prompt_video","retry",6000)
        assertEquals(70,session.snapshot.account.tokens);assertEquals(2,session.snapshot.jobs.size)
    }
    @Test fun `insufficient balance and invalid input never create or charge a job`() {
        val session = session(Store(DemoSnapshot(account = DemoAccount(5,true))))
        assertEquals(SubmitResult.InvalidDraft,session.submit("prompt_photo","bad",1000))
        session.updateDraft("prompt_photo") { it.copy(prompt = "Sample") }
        assertEquals(SubmitResult.InsufficientBalance(10),session.submit("prompt_photo","poor",1000))
        assertEquals(5,session.snapshot.account.tokens);assertTrue(session.snapshot.jobs.isEmpty())
    }
    @Test fun `drafts remain independent and loading does not overwrite last photo`() {
        val store = Store();val session = session(store)
        session.updateDraft("prompt_video") { it.copy(prompt = "Video",photo = "asset:good1",photoStatus = PhotoStatus.READY) }
        session.updateDraft("prompt_photo") { it.copy(prompt = "Photo",style = PhotoStyle.FANTASY) }
        session.updateDraft("prompt_video") { it.copy(pendingPhoto = "asset:good2",photoStatus = PhotoStatus.LOADING) }
        val restored = session(store)
        assertEquals("asset:good1",restored.draft("prompt_video").photo)
        assertEquals("Photo",restored.draft("prompt_photo").prompt)
        assertEquals(PhotoStyle.FANTASY,restored.draft("prompt_photo").style)
    }
}
