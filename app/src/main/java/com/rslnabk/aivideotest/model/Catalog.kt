package com.rslnabk.aivideotest.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.rslnabk.aivideotest.R

enum class MediaKind { VIDEO, PHOTO }
enum class Category(@param:StringRes val title: Int) {
    POPULAR(R.string.popular), ANIME(R.string.anime), FASHION(R.string.fashion),
    NEW(R.string.new_effects), RETRO(R.string.retro)
}
enum class AppTab( @param:StringRes val title: Int, val kind: MediaKind? = null) {
    VIDEO(R.string.ai_video, MediaKind.VIDEO),
    PHOTO(R.string.ai_photo, MediaKind.PHOTO),
    FAVORITES(R.string.favorites),
    LIBRARY(R.string.library), SETTINGS(R.string.settings)
}
data class Effect(
    val id: String, val kind: MediaKind, @param:StringRes val title: Int,
    @param:DrawableRes val image: Int, val categories: Set<Category>, val tokenCost: Int
)
data class DemoAccount(val tokens: Int = 5, val isPro: Boolean = false, val plan: SubscriptionPlan? = null)
data class DemoSnapshot(
    val favorites: Set<String> = emptySet(), val account: DemoAccount = DemoAccount(),
    val drafts: Map<String, GenerationDraft> = emptyMap(), val jobs: List<GenerationJob> = emptyList(),
    val instructionSeen: Boolean = false, val commerce: DemoCommerce = DemoCommerce()
)
