package com.rslnabk.aivideotest

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import androidx.core.widget.NestedScrollView
import com.rslnabk.aivideotest.model.Category
import com.rslnabk.aivideotest.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogNavigationTest {
    @Before fun reset() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("demo_state_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun categoryEffectFavoriteRotationAndRestart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(allOf(withContentDescription("See all: Popular"), isDisplayed())).perform(click())
            onView(allOf(withContentDescription("Open Golden Hour effect"), isDisplayed())).perform(click())
            onView(withId(R.id.like)).perform(click())
            scenario.recreate()
            onView(withId(R.id.like)).check(matches(isSelected()))
            pressBack()
            onView(allOf(withContentDescription("Category: Popular"), isDisplayed())).check(matches(isSelected()))
            pressBack()
            onView(withId(R.id.tab_favorites)).perform(click())
            onView(allOf(withText("Videos"), isDisplayed())).perform(click())
            onView(allOf(withContentDescription("Open Golden Hour effect"), isDisplayed())).perform(click())
            onView(withId(R.id.like)).check(matches(isSelected()))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(allOf(withContentDescription("Remove from Favorites: Golden Hour"), isDisplayed())).check(matches(isSelected()))
        }
    }
    @Test fun scrollAndOriginSurviveDetailTabSwitchAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun currentScroll(activity: MainActivity) = activity.supportFragmentManager.fragments
                .first { it.isAdded && !it.isHidden }.requireView().findViewById<NestedScrollView>(R.id.scroll)
            scenario.onActivity { activity ->
                currentScroll(activity).scrollTo(0, 600)
                activity.openCategory(MediaKind.VIDEO, Category.ANIME)
            }
            onView(allOf(withContentDescription("Open Anime Story effect"), isDisplayed())).perform(click())
            pressBack()
            onView(allOf(withContentDescription("Category: Anime"), isDisplayed())).check(matches(isSelected()))
            pressBack()
            scenario.onActivity { assertEquals(600, currentScroll(it).scrollY) }
            onView(withId(R.id.tab_photo)).perform(click())
            onView(withId(R.id.tab_video)).perform(click())
            scenario.onActivity { assertEquals(600, currentScroll(it).scrollY) }
            scenario.recreate()
            onView(withId(R.id.tab_video)).check(matches(isDisplayed()))
            scenario.onActivity { assertEquals(600, currentScroll(it).scrollY) }
        }
    }
    @Test fun photoAndVideoFavoritesAreIndependent() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.tab_photo)).perform(click())
            onView(allOf(withContentDescription("Open Golden Hour effect"), isDisplayed())).perform(click())
            onView(withId(R.id.like)).perform(click())
            pressBack()
            onView(withId(R.id.tab_video)).perform(click())
            onView(allOf(withContentDescription("Add to Favorites: Golden Hour"), isDisplayed())).check(matches(isNotSelected()))
            onView(withId(R.id.tab_library)).perform(click())
            onView(withText(R.string.empty_library_title)).check(matches(isDisplayed()))
            onView(withId(R.id.tab_settings)).perform(click())
            onView(withText(R.string.subscription_title)).check(matches(isDisplayed()))
        }
    }
}
