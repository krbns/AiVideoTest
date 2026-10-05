package com.rslnabk.aivideotest

import com.rslnabk.aivideotest.data.DemoStore
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Test

class DemoSessionTest {
    private class MemoryStore(var saved: DemoSnapshot = DemoSnapshot()) : DemoStore {
        override fun load() = saved
        override fun save(snapshot: DemoSnapshot) { saved = snapshot }
    }
    private val catalog = DemoCatalogRepository()
    @Test fun `favorite survives a new session and stays independent across media kinds`() {
        val store = MemoryStore(); val first = DemoSession(store, catalog)
        first.toggleFavorite("video_beach")
        val restored = DemoSession(store, catalog)
        assertEquals(setOf("video_beach"), restored.snapshot.favorites)
        restored.toggleFavorite("photo_beach")
        restored.toggleFavorite("video_beach")
        assertEquals(setOf("photo_beach"), store.saved.favorites)
    }
    @Test fun `unknown effects cannot become favorites and stale ids are pruned`() {
        val store = MemoryStore(DemoSnapshot(setOf("missing", "video_gold")))
        val session = DemoSession(store, catalog)
        session.toggleFavorite("missing")
        assertEquals(setOf("video_gold"), session.snapshot.favorites)
    }
    @Test fun `pro and balance are independent and reset restores the entire session`() {
        val store = MemoryStore(); val session = DemoSession(store, catalog)
        session.toggleFavorite("photo_gold")
        session.setAccount(DemoAccount(0, true))
        assertTrue(session.snapshot.account.isPro)
        assertEquals(0, session.snapshot.account.tokens)
        session.reset()
        assertEquals(DemoSnapshot(), store.saved)
    }
    @Test fun `every category has discoverable effects and IDs are unique`() {
        val all = MediaKind.entries.flatMap { catalog.effects(it) }
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (kind in MediaKind.entries) for (category in Category.entries) {
            val effects = catalog.effects(kind, category)
            assertTrue(effects.isNotEmpty())
            assertTrue(effects.all { it.kind == kind && category in it.categories })
        }
        assertNotEquals(catalog.effects(MediaKind.VIDEO, Category.POPULAR).map { it.image }, catalog.effects(MediaKind.PHOTO, Category.POPULAR).map { it.image })
    }
}
