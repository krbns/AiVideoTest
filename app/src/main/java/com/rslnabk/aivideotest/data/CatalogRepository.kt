package com.rslnabk.aivideotest.data

import com.rslnabk.aivideotest.model.*

interface CatalogRepository {
    fun effects(kind: MediaKind, category: Category? = null): List<Effect>
    fun effect(id: String): Effect?
}
interface DemoStore {
    fun load(): DemoSnapshot
    fun save(snapshot: DemoSnapshot)
}
