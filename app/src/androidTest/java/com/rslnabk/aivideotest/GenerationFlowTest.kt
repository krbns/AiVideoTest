package com.rslnabk.aivideotest

import android.content.Context
import android.view.View
import android.net.Uri
import java.io.File
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.model.*
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenerationFlowTest {
    @Before fun reset() { ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("demo_state_v1",Context.MODE_PRIVATE).edit().clear().commit() }
    private fun editor(id: Int) = onView(allOf(withId(id),withEffectiveVisibility(Visibility.VISIBLE)))
    private fun awaitState(scenario: ActivityScenario<MainActivity>, predicate: (AppViewModel) -> Boolean) {
        val deadline = System.currentTimeMillis() + 12000
        while (System.currentTimeMillis() < deadline) {
            var ready = false; scenario.onActivity { ready = predicate(ViewModelProvider(it)[AppViewModel::class.java]) }
            if (ready) { onIdle(); return }
            Thread.sleep(100)
        }
        fail("Demo state did not settle")
    }
    @Test fun promptValidationDraftRotationAndPhotoResult() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.tab_photo)).perform(click())
            onView(allOf(withText(R.string.prompt),isDisplayed())).perform(click())
            editor(R.id.generate).check(matches(isNotEnabled()))
            editor(R.id.promptInput).perform(replaceText("a".repeat(301)),closeSoftKeyboard())
            editor(R.id.generate).check(matches(isNotEnabled()))
            editor(R.id.counter).check(matches(withText("301/300")))
            editor(R.id.promptInput).perform(replaceText("Тёплый закат"),closeSoftKeyboard())
            scenario.recreate()
            editor(R.id.promptInput).check(matches(withText("Тёплый закат")))
            editor(R.id.generate).perform(scrollTo(),click())
            onView(withText(R.string.demo_credits)).perform(click())
            awaitState(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.SUCCEEDED }
            onView(withId(R.id.share)).check(matches(isDisplayed()))
            scenario.onActivity { assertEquals(95,ViewModelProvider(it)[AppViewModel::class.java].snapshot.value!!.account.tokens) }
            pressBack()
            editor(R.id.promptInput).check(matches(withText("Тёплый закат")))
        }
    }
    @Test fun effectInstructionSamplePhotoUploadFailureRetryAndVideo() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(allOf(withContentDescription("Open Golden Hour effect"),isDisplayed())).perform(click())
            onView(withId(R.id.useEffect)).perform(click())
            onView(withText(R.string.continue_action)).perform(scrollTo(),click())
            scenario.onActivity { ViewModelProvider(it)[AppViewModel::class.java].failNextPhoto = true }
            onView(withText(R.string.sample_photo)).perform(click())
            onView(withContentDescription("Sample portrait 1")).perform(click())
            awaitState(scenario) { it.draft("video_gold").photoStatus == PhotoStatus.FAILED }
            editor(R.id.retryPhoto).perform(scrollTo(),click())
            awaitState(scenario) { it.draft("video_gold").photoStatus == PhotoStatus.READY }
            editor(R.id.photoCard).perform(click())
            onView(withText(R.string.cancel)).perform(click())
            scenario.onActivity { assertEquals("asset:good1",ViewModelProvider(it)[AppViewModel::class.java].draft("video_gold").photo) }
            editor(R.id.generate).perform(scrollTo(),click())
            onView(withText(R.string.demo_credits)).perform(click())
            awaitState(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.SUCCEEDED }
            onView(withId(R.id.video)).check(matches(isDisplayed()))
            onView(withId(R.id.playback)).perform(click()).check(matches(withText(R.string.play)))
            scenario.recreate()
            onView(withId(R.id.playback)).check(matches(withText(R.string.play)))
        }
    }
    @Test fun fileImportNormalizesPhotoAndSurvivesNewActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val input = File(context.cacheDir,"photo-import-test.webp")
        context.resources.openRawResource(R.drawable.demo_good_2).use { source -> input.outputStream().use { source.copyTo(it) } }
        var reference = ""
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { ViewModelProvider(it)[AppViewModel::class.java].loadPhoto("prompt_photo",Uri.fromFile(input).toString()) }
                awaitState(scenario) { it.draft("prompt_photo").photoStatus == PhotoStatus.READY }
                scenario.onActivity {
                    reference = ViewModelProvider(it)[AppViewModel::class.java].draft("prompt_photo").photo!!
                    assertTrue(reference.startsWith("file:")); assertFalse(reference.startsWith("file:/"))
                }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertEquals(reference,ViewModelProvider(it)[AppViewModel::class.java].draft("prompt_photo").photo) }
                assertTrue(File(context.filesDir,"reference_photos/${reference.removePrefix("file:")}").exists())
            }
        } finally { input.delete(); if (reference.isNotEmpty()) File(context.filesDir,"reference_photos/${reference.removePrefix("file:")}").delete() }
    }
    @Test fun leavingCreationKeepsJobAndFailureRefundsThenRetrySucceeds() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(allOf(withText(R.string.prompt),isDisplayed())).perform(click())
            editor(R.id.promptInput).perform(replaceText("Sunlight"),closeSoftKeyboard())
            scenario.onActivity { ViewModelProvider(it)[AppViewModel::class.java].apply { setAccount(DemoAccount(100)); failNextGeneration = true } }
            editor(R.id.generate).perform(scrollTo(),click())
            onView(withId(R.id.okay)).perform(click())
            onView(withId(R.id.tab_library)).check(matches(isDisplayed()))
            awaitState(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.FAILED }
            scenario.onActivity { assertEquals(100,ViewModelProvider(it)[AppViewModel::class.java].snapshot.value!!.account.tokens) }
            onView(allOf(withText(R.string.read_more),isDisplayed())).perform(click())
            onView(withText(R.string.refresh)).perform(click())
            awaitState(scenario) { it.snapshot.value!!.jobs.lastOrNull()?.status == JobStatus.SUCCEEDED }
            onView(withId(R.id.share)).check(matches(isDisplayed()))
            scenario.onActivity { assertEquals(90,ViewModelProvider(it)[AppViewModel::class.java].snapshot.value!!.account.tokens) }
        }
    }
}
