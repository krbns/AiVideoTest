package com.rslnabk.aivideotest.data.demo

import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.CatalogRepository
import com.rslnabk.aivideotest.model.*

/** Assets are shared between types; IDs and demo request costs remain distinct. */
class DemoCatalogRepository : CatalogRepository {
    private data class Template(val key: String, val title: Int, val image: Int, val groups: Set<Category>)
    private val templates = listOf(
        Template("gold", R.string.effect_name_gold, R.drawable.demo_gold, setOf(Category.POPULAR, Category.FASHION)),
        Template("beach", R.string.effect_name_beach, R.drawable.demo_beach, setOf(Category.POPULAR, Category.NEW)),
        Template("penguin", R.string.effect_name_penguin, R.drawable.demo_penguin, setOf(Category.POPULAR, Category.NEW)),
        Template("japan", R.string.effect_name_japan, R.drawable.demo_japan, setOf(Category.POPULAR, Category.RETRO)),
        Template("underwater", R.string.effect_name_underwater, R.drawable.demo_underwater, setOf(Category.POPULAR, Category.NEW)),
        Template("fashion", R.string.effect_name_fashion, R.drawable.demo_fashion, setOf(Category.POPULAR, Category.FASHION, Category.RETRO)),
        Template("anime_story", R.string.effect_name_anime_1, R.drawable.demo_anime_1, setOf(Category.ANIME, Category.NEW)),
        Template("dream_world", R.string.effect_name_anime_2, R.drawable.demo_anime_1, setOf(Category.ANIME, Category.RETRO)),
        Template("magic_moment", R.string.effect_name_anime_3, R.drawable.demo_anime_1, setOf(Category.ANIME)),
        Template("portrait", R.string.effect_name_portrait, R.drawable.demo_portrait, setOf(Category.FASHION)),
        Template("anime_portrait", R.string.effect_name_anime_portrait, R.drawable.demo_anime_portrait, setOf(Category.ANIME, Category.NEW))
    )
    private val all = MediaKind.entries.flatMap { kind ->
        templates.map { Effect("${kind.name.lowercase()}_${it.key}", kind, it.title, it.image, it.groups,
            if (kind == MediaKind.VIDEO) DemoRules.VIDEO_COST else DemoRules.PHOTO_COST) }
    }
    override fun effects(kind: MediaKind, category: Category?): List<Effect> =
        all.filter { it.kind == kind && (category == null || category in it.categories) }
            .let { if (kind == MediaKind.PHOTO && category == Category.POPULAR) it.sortedBy { effect ->
                listOf("gold", "japan", "underwater", "penguin", "beach", "fashion").indexOf(effect.id.removePrefix("photo_"))
            } else it }
    override fun effect(id: String): Effect? = all.find { it.id == id }
}

/** Temporary rules, separate from UI and explicitly documented for the next batch. */
object DemoRules {
    const val VIDEO_COST = 40
    const val PHOTO_COST = 20
    const val PROMPT_LIMIT = 300
}
