package com.rslnabk.aivideotest

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.model.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogNavigationTest : ComposeFlowTest() {
    @Test fun categoryEffectFavoriteRotationAndRestart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ui.onNodeWithContentDescription("See all: Popular").performClick()
            tag("effect_video_gold").performClick(); tag("like").performClick()
            scenario.recreate(); tag("like").assertIsSelected()
            back(scenario); ui.onNodeWithContentDescription("Category: Popular").assertIsSelected()
            back(scenario); tag("tab_favorites").performClick(); text(R.string.videos).performClick()
            tag("effect_video_gold").performClick(); tag("like").assertIsSelected()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            ui.onNodeWithContentDescription("Remove from Favorites: Golden Hour", useUnmergedTree = true).assertIsSelected()
        }
    }
    @Test fun scrollAndOriginSurviveDetailTabSwitchAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("browser_list").performScrollToKey("heading_ANIME")
            text(R.string.anime).assertIsDisplayed()
            scenario.onActivity { it.openCategory(MediaKind.VIDEO, Category.ANIME) }
            tag("effect_video_anime_story").performClick(); back(scenario)
            ui.onNodeWithContentDescription("Category: Anime").assertIsSelected()
            back(scenario); text(R.string.anime).assertIsDisplayed()
            tag("tab_photo").performClick(); tag("tab_video").performClick()
            text(R.string.anime).assertIsDisplayed()
            scenario.recreate(); text(R.string.anime).assertIsDisplayed()
        }
    }
    @Test fun photoAndVideoFavoritesAreIndependent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("tab_photo").performClick(); tag("effect_photo_gold").performClick(); tag("like").performClick()
            back(scenario); tag("tab_video").performClick()
            ui.onNodeWithContentDescription("Add to Favorites: Golden Hour", useUnmergedTree = true).assertIsNotSelected()
            tag("tab_library").performClick(); text(R.string.empty_library_title).assertIsDisplayed()
            tag("tab_settings").performClick(); text(R.string.subscription_title).assertIsDisplayed()
        }
    }
}
