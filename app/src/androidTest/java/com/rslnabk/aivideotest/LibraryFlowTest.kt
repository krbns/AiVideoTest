package com.rslnabk.aivideotest

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun job(id: String,kind: MediaKind,status: JobStatus = JobStatus.SUCCEEDED): GenerationJob {
        val draft = GenerationDraft("prompt_${kind.name.lowercase()}",kind,prompt = "$id original",resolution = 1080)
        return GenerationJob(id,draft,if (kind == MediaKind.VIDEO) 30 else 10,DemoResultFixtures.image(draft,DemoCatalogRepository()),0,0,status = status)
    }
    @Before fun reset() {
        context.getSharedPreferences("demo_state_v1",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("result_export_v1",Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun seed(vararg jobs: GenerationJob) {
        PreferencesDemoStore(context.getSharedPreferences("demo_state_v1",Context.MODE_PRIVATE)).save(DemoSnapshot(jobs = jobs.toList(),account = DemoAccount(100)))
    }
    @Test fun libraryFiltersResultBackDeleteCancellationRotationAndLastEmpty() {
        seed(job("Photo",MediaKind.PHOTO),job("Video",MediaKind.VIDEO))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.tab_library)).perform(click())
            onView(withContentDescription("Photo original · Ready")).perform(click())
            pressBack()
            onView(allOf(withText(R.string.photos),isDisplayed())).check(matches(isSelected()))
            onView(withContentDescription("Photo original · Ready")).perform(click())
            onView(withId(R.id.options)).perform(click())
            onView(withText(R.string.delete_generation)).perform(click())
            scenario.recreate()
            onView(withText(R.string.cancel)).perform(click())
            onView(withId(R.id.share)).check(matches(isDisplayed()))
            scenario.onActivity { assertEquals(2,ViewModelProvider(it)[AppViewModel::class.java].snapshot.value!!.jobs.size) }
            onView(withId(R.id.options)).perform(click())
            onView(withText(R.string.delete_generation)).perform(click())
            onView(withText(R.string.delete_generation)).perform(click())
            onView(withText(R.string.empty_library_title)).check(matches(isDisplayed()))
            onView(allOf(withText(R.string.videos),isDisplayed())).perform(click())
            scenario.recreate()
            onView(allOf(withText(R.string.videos),isDisplayed())).check(matches(isSelected()))
            onView(withContentDescription("Video original · Ready")).perform(click())
            onView(withId(R.id.video)).check(matches(isDisplayed()))
            scenario.onActivity { assertEquals(100,ViewModelProvider(it)[AppViewModel::class.java].snapshot.value!!.account.tokens) }
        }
    }
    @Test fun failedRefreshKeepsIdentityAndOriginalInputThenReturnsToLibrary() {
        seed(job("Video",MediaKind.VIDEO,JobStatus.FAILED))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[AppViewModel::class.java].editDraft("prompt_video") { it.copy(prompt = "Edited",resolution = 720) } }
            onView(withId(R.id.tab_library)).perform(click())
            onView(allOf(withText(R.string.videos),isDisplayed())).perform(click())
            onView(withText(R.string.read_more)).perform(click())
            scenario.recreate()
            onView(withText(R.string.refresh)).perform(click())
            onView(withId(R.id.okay)).perform(click())
            val deadline = System.currentTimeMillis()+10000
            var finished=false
            while (System.currentTimeMillis()<deadline && !finished) {
                scenario.onActivity {
                    val state=ViewModelProvider(it)[AppViewModel::class.java].snapshot.value!!
                    assertEquals(1,state.jobs.size);assertEquals("Video",state.jobs.single().id)
                    assertEquals("Video original",state.jobs.single().draft.prompt);assertEquals(70,state.account.tokens)
                    finished=state.jobs.single().status==JobStatus.SUCCEEDED
                }
                if (!finished) Thread.sleep(100)
            }
            assertTrue(finished);onIdle()
            onView(withContentDescription("Video original · Ready")).perform(longClick())
            onView(withText(R.string.cancel)).perform(click())
            onView(withContentDescription("Video original · Ready")).check(matches(isDisplayed()))
        }
    }
    @Test fun unlikeFromEffectUpdatesFavoritesEmptyAndKeepsFilter() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[AppViewModel::class.java].toggleFavorite("video_gold") }
            onView(withId(R.id.tab_favorites)).perform(click())
            onView(allOf(withText(R.string.videos),isDisplayed())).perform(click())
            onView(allOf(withContentDescription("Open Golden Hour effect"),isDisplayed())).perform(click())
            onView(withId(R.id.like)).perform(click());pressBack()
            onView(withText(R.string.empty_favorites_title)).check(matches(isDisplayed()))
            scenario.recreate()
            onView(allOf(withText(R.string.videos),isDisplayed())).check(matches(isSelected()))
            onView(withText(R.string.empty_favorites_title)).check(matches(isDisplayed()))
        }
    }
}
