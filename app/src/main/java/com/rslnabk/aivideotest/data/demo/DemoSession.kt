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
    private fun update(value: DemoSnapshot) { snapshot = value; store.save(value) }
}
