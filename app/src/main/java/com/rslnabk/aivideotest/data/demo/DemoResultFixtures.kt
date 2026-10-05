package com.rslnabk.aivideotest.data.demo

import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.data.CatalogRepository
import com.rslnabk.aivideotest.model.*

object DemoResultFixtures {
    fun image(draft: GenerationDraft, catalog: CatalogRepository): Int =
        draft.effectId?.let { catalog.effect(it)?.image } ?: when (draft.style) {
            PhotoStyle.NONE -> R.drawable.demo_beach
            PhotoStyle.GHIBLI -> R.drawable.demo_style_ghibli
            PhotoStyle.PERSON_3D -> R.drawable.demo_style_3d
            PhotoStyle.SIMPSONS -> R.drawable.demo_style_simpsons
            PhotoStyle.FANTASY -> R.drawable.demo_style_fantasy
        }
    fun video(draft: GenerationDraft): Int = when (draft.effectId?.removePrefix("video_")) {
        "gold" -> R.raw.demo_video_gold
        "penguin" -> R.raw.demo_video_penguin
        "japan" -> R.raw.demo_video_japan
        "underwater" -> R.raw.demo_video_underwater
        "fashion" -> R.raw.demo_video_fashion
        "anime_story", "dream_world", "magic_moment" -> R.raw.demo_video_anime_1
        "portrait" -> R.raw.demo_video_portrait
        "anime_portrait" -> R.raw.demo_video_anime_portrait
        else -> R.raw.demo_video
    }
}
