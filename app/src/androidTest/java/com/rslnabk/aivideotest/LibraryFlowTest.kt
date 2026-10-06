package com.rslnabk.aivideotest

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFlowTest : ComposeFlowTest() {
    private fun job(id: String, kind: MediaKind, status: JobStatus = JobStatus.SUCCEEDED): GenerationJob {
        val draft = GenerationDraft("prompt_${kind.name.lowercase()}", kind, prompt = "$id original", resolution = 1080)
        return GenerationJob(id, draft, if (kind == MediaKind.VIDEO) 30 else 10, DemoResultFixtures.image(draft, DemoCatalogRepository()), 0, 0, status = status)
    }
    private fun seed(vararg jobs: GenerationJob) {
        PreferencesDemoStore(context.getSharedPreferences("demo_state_v1", 0)).save(DemoSnapshot(preferences = DemoPreferences(introStep = IntroStep.DONE), jobs = jobs.toList(), account = DemoAccount(100)))
    }
    @Test fun libraryFiltersResultBackDeleteCancellationRotationAndLastEmpty() {
        seed(job("Photo", MediaKind.PHOTO), job("Video", MediaKind.VIDEO))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("tab_library").performClick(); tag("job_Photo").performClick(); back(scenario)
            text(R.string.photos).assertIsSelected(); tag("job_Photo").performClick()
            tag("options").performClick(); text(R.string.delete_generation).performClick()
            scenario.recreate(); text(R.string.cancel).performClick(); tag("share").assertIsDisplayed()
            scenario.onActivity { assertEquals(2, it.model.snapshot.value!!.jobs.size) }
            tag("options").performClick(); text(R.string.delete_generation).performClick(); text(R.string.delete_generation).performClick()
            text(R.string.empty_library_title).assertIsDisplayed(); text(R.string.videos).performClick()
            scenario.recreate(); text(R.string.videos).assertIsSelected(); tag("job_Video").performClick(); tag("video").assertIsDisplayed()
            scenario.onActivity { assertEquals(100, it.model.snapshot.value!!.account.tokens) }
        }
    }
    @Test fun failedRefreshKeepsIdentityAndOriginalInputThenReturnsToLibrary() {
        seed(job("Video", MediaKind.VIDEO, JobStatus.FAILED))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.model.editDraft("prompt_video") { draft -> draft.copy(prompt = "Edited", resolution = 720) } }
            tag("tab_library").performClick(); text(R.string.videos).performClick(); text(R.string.read_more).performClick()
            scenario.recreate(); text(R.string.refresh).performClick(); tag("okay").performClick()
            await(scenario) { it.snapshot.value!!.jobs.single().status == JobStatus.SUCCEEDED }
            scenario.onActivity {
                val state = it.model.snapshot.value!!
                assertEquals(1, state.jobs.size); assertEquals("Video", state.jobs.single().id)
                assertEquals("Video original", state.jobs.single().draft.prompt); assertEquals(70, state.account.tokens)
            }
            tag("job_Video").performTouchInput { longClick() }; text(R.string.cancel).performClick(); tag("job_Video").assertIsDisplayed()
        }
    }
    @Test fun unlikeFromEffectUpdatesFavoritesEmptyAndKeepsFilter() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.model.toggleFavorite("video_gold") }
            tag("tab_favorites").performClick(); text(R.string.videos).performClick(); tag("effect_video_gold").performClick()
            tag("like").performClick(); back(scenario); text(R.string.empty_favorites_title).assertIsDisplayed()
            scenario.recreate(); text(R.string.videos).assertIsSelected(); text(R.string.empty_favorites_title).assertIsDisplayed()
        }
    }
}
