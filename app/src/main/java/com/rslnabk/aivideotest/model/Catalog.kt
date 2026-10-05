package com.rslnabk.aivideotest.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.rslnabk.aivideotest.R

enum class MediaKind { VIDEO, PHOTO }
enum class Category(@param:StringRes val title: Int) {
    POPULAR(R.string.popular), ANIME(R.string.anime), FASHION(R.string.fashion),
    NEW(R.string.new_effects), RETRO(R.string.retro)
}
enum class AppTab(val menuId: Int, @param:StringRes val title: Int, val kind: MediaKind? = null) {
    VIDEO(R.id.tab_video, R.string.ai_video, MediaKind.VIDEO),
    PHOTO(R.id.tab_photo, R.string.ai_photo, MediaKind.PHOTO),
    FAVORITES(R.id.tab_favorites, R.string.favorites),
    LIBRARY(R.id.tab_library, R.string.library), SETTINGS(R.id.tab_settings, R.string.settings)
}
data class Effect(
    val id: String, val kind: MediaKind, @param:StringRes val title: Int,
    @param:DrawableRes val image: Int, val categories: Set<Category>, val tokenCost: Int
)
data class DemoAccount(val tokens: Int = 5, val isPro: Boolean = false)
data class DemoSnapshot(
    val favorites: Set<String> = emptySet(), val account: DemoAccount = DemoAccount(),
    val drafts: Map<String, GenerationDraft> = emptyMap(), val jobs: List<GenerationJob> = emptyList(),
    val instructionSeen: Boolean = false
)
