package com.rslnabk.aivideotest

import com.rslnabk.aivideotest.data.DemoStore
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Test

class LibrarySessionTest {
    private class Store(var value: DemoSnapshot = DemoSnapshot(account = DemoAccount(100))) : DemoStore {
        override fun load() = value
        override fun save(snapshot: DemoSnapshot) { value = snapshot }
    }
    private fun failed(store: Store = Store()): DemoSession {
        return DemoSession(store,DemoCatalogRepository()).apply {
            updateDraft("prompt_video") { it.copy(prompt = "Original",resolution = 1080) }
            submit("prompt_video","job",1000,true); reconcile(5000)
        }
    }
    @Test fun `retry uses original request in same slot and only charges once`() {
        val session = failed()
        session.updateDraft("prompt_video") { it.copy(prompt = "Edited",resolution = 720) }
        assertEquals(SubmitResult.Accepted("job"),session.retryJob("job",6000))
        assertEquals(SubmitResult.Accepted("job"),session.retryJob("job",6001))
        assertEquals(1,session.snapshot.jobs.size)
        assertEquals("Original",session.snapshot.jobs.single().draft.prompt)
        assertEquals(1080,session.snapshot.jobs.single().draft.resolution)
        assertEquals("Edited",session.draft("prompt_video").prompt)
        assertEquals(70,session.snapshot.account.tokens)
        session.reconcile(10000)
        assertEquals(JobStatus.SUCCEEDED,session.snapshot.jobs.single().status)
    }
    @Test fun `retry failure refunds exactly once after restoration`() {
        val store = Store();val session = failed(store)
        session.retryJob("job",6000,true)
        val restored = DemoSession(store,DemoCatalogRepository());restored.reconcile(10000);restored.reconcile(11000)
        assertEquals(100,restored.snapshot.account.tokens)
        assertEquals(1,restored.snapshot.jobs.size)
    }
    @Test fun `insufficient retry leaves history unchanged`() {
        val session = failed();session.setAccount(DemoAccount(5))
        val before = session.snapshot
        assertEquals(SubmitResult.InsufficientBalance(30),session.retryJob("job",6000))
        assertEquals(before,session.snapshot)
    }
    @Test fun `delete clears only settled history and matching pointer`() {
        val store = Store();val session = failed(store)
        session.toggleFavorite("video_gold")
        assertTrue(session.deleteJob("job"));assertFalse(session.deleteJob("job"))
        val restored = DemoSession(store,DemoCatalogRepository())
        assertTrue(restored.snapshot.jobs.isEmpty());assertNull(restored.draft("prompt_video").activeJobId)
        assertEquals("Original",restored.draft("prompt_video").prompt)
        assertEquals(100,restored.snapshot.account.tokens);assertTrue("video_gold" in restored.snapshot.favorites)
    }
    @Test fun `running delete and succeeded retry are rejected`() {
        val session = failed();session.retryJob("job",6000)
        assertFalse(session.deleteJob("job"));session.reconcile(10000)
        assertEquals(SubmitResult.InvalidDraft,session.retryJob("job",11000))
        assertTrue(session.deleteJob("job"));assertEquals(70,session.snapshot.account.tokens)
    }
}
