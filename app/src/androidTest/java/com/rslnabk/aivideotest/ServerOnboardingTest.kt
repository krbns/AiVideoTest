package com.rslnabk.aivideotest

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.*
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.rslnabk.aivideotest.data.backend.*
import com.rslnabk.aivideotest.data.demo.PreferencesDemoStore
import com.rslnabk.aivideotest.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/** Exercise the real root gate/ActivityResult bridge without contacting a server. */
class ServerOnboardingTest : ComposeFlowTest() {
    private val user = UUID.randomUUID().toString()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var references = emptySet<String>()
    @Before fun freshIntro() {
        context.getSharedPreferences("demo_state_v1", 0).edit().clear().commit()
        context.getSharedPreferences("server_intro_photo_v1", 0).edit().clear().commit()
    }
    @After fun cleanup() {
        val pending = context.getSharedPreferences("server_intro_photo_v1", 0)
        references = references + listOfNotNull(pending.getString("reference", null))
        references.forEach { File(context.filesDir, "backend_reference_photos/${it.removePrefix("file:")}").delete() }
        pending.edit().clear().commit()
        File(context.filesDir, "backend_photo/$user.json").delete()
        File(context.filesDir, "backend_video/$user.json").delete()
    }
    private fun server(scenario: ActivityScenario<MainActivity>, account: String? = user) {
        scenario.onActivity {
            // The runner launches in demo. Close its idle backend before selecting server UI.
            it.model.backend.close()
            (it.model.backend.state as MutableLiveData<BackendState>).value = BackendState(userId = account,
                data = BackendData(wallet = 37, profile = RemoteProfile("intro-account", null)),
                loaded = if (account == null) emptySet() else BackendSection.entries.toSet(),
                authError = if (account == null) BackendFailure() else null)
            (it.model.backend.source as MutableLiveData<DataSource>).value = DataSource.SERVER
        }
        ui.waitForIdle()
    }
    private fun awaitTag(value: String) {
        ui.waitUntil(12000) { ui.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty() }
        tag(value).assertIsDisplayed()
    }
    private fun photos() {
        awaitTag("intro_welcome")
        repeat(4) { tag("intro_next").performClick() }
        awaitTag("intro_gallery")
    }
    private fun rememberReferences(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { references = references + it.model.backendPhotos.state.value!!.draft.photos }
    }
    @Test fun firstRunResumesAcrossRecreationAndColdLaunchThenFinishesWithoutDemoOffer() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            server(scenario, null); awaitTag("intro_welcome")
            back(scenario); tag("intro_welcome").assertIsDisplayed()
            tag("intro_next").performClick(); scenario.recreate(); tag("intro_prompt").assertIsDisplayed()
            tag("intro_back").performClick(); tag("intro_welcome").assertIsDisplayed(); tag("intro_next").performClick()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            server(scenario, null); awaitTag("intro_prompt")
            repeat(3) { tag("intro_next").performClick() }
            tag("intro_photo_skip").performClick(); tag("intro_not_now").performClick()
            tag("tab_video").assertIsDisplayed(); tag("offer_pro").assertDoesNotExist()
            scenario.onActivity {
                assertEquals(DataSource.SERVER, it.model.backend.source.value)
                assertEquals(IntroStep.DONE, it.model.snapshot.value!!.preferences.introStep)
                assertFalse(it.model.snapshot.value!!.preferences.introOfferPending)
                assertTrue(it.model.snapshot.value!!.jobs.isEmpty())
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            server(scenario); tag("tab_video").assertIsDisplayed(); tag("intro_welcome").assertDoesNotExist()
            tag("tab_settings").performClick(); tag("backend_profile_balance").assertTextEquals(context.getString(R.string.backend_credits, 37))
        }
    }
    @Test fun offlineSampleIsNormalizedThenDeliveredOnceToSameAccountAfterColdLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            server(scenario, null); photos(); tag("intro_sample").performClick(); awaitTag("intro_notifications")
            scenario.onActivity {
                val pending = it.model.serverIntroPhoto.state.value!!
                assertTrue(pending.reference!!.startsWith("file:")); references = references + pending.reference
                assertNull(pending.owner); assertTrue(it.model.backendPhotos.state.value!!.draft.photos.isEmpty())
                assertNull(it.model.draft("prompt_photo").photo)
            }
            tag("intro_not_now").performClick(); tag("tab_video").assertIsDisplayed()
        }
        repeat(2) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                server(scenario)
                await(scenario) { it.backendPhotos.state.value!!.draft.photos.size == 1 }
                scenario.onActivity {
                    val reference = it.model.backendPhotos.state.value!!.draft.photos.single()
                    assertEquals(references.single(), reference)
                    assertTrue(File(context.filesDir, "backend_reference_photos/${reference.removePrefix("file:")}").isFile)
                    assertNull(it.model.serverIntroPhoto.state.value!!.reference)
                    assertEquals(IntroPhotoChoice.SAMPLE, it.model.snapshot.value!!.preferences.introPhotoChoice)
                }
            }
        }
    }
    @Test fun galleryResultGoesToServerDraftAndCancelStillAllowsCompletion() {
        val sample = File(context.cacheDir, "exports/intro-${UUID.randomUUID()}.png").apply {
            parentFile!!.mkdirs()
            context.resources.openRawResource(R.drawable.demo_good_1).use { input -> outputStream().use(input::copyTo) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", sample)
        val action = ActivityResultContracts.PickVisualMedia().createIntent(context,
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)).action!!
        val monitor = Instrumentation.ActivityMonitor(IntentFilter(action).apply { addDataType("image/*") },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                server(scenario); photos(); tag("intro_gallery").performClick(); awaitTag("intro_notifications")
                assertEquals(1, monitor.hits); sample.delete(); scenario.recreate()
                tag("intro_not_now").performClick()
                await(scenario) { it.backendPhotos.state.value!!.draft.photos.size == 1 }
                rememberReferences(scenario)
                scenario.onActivity {
                    assertEquals(IntroPhotoChoice.PICKED, it.model.snapshot.value!!.preferences.introPhotoChoice)
                    assertNull(it.model.draft("prompt_photo").photo)
                }
            }
        } finally { instrumentation.removeMonitor(monitor); sample.delete() }
    }
    @Test fun cancelledGallerySkipsPhotoWithoutDraftMutation() {
        val action = ActivityResultContracts.PickVisualMedia().createIntent(context,
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)).action!!
        val monitor = Instrumentation.ActivityMonitor(IntentFilter(action).apply { addDataType("image/*") },
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                server(scenario); photos(); tag("intro_gallery").performClick(); awaitTag("intro_notifications")
                tag("intro_not_now").performClick(); tag("tab_video").assertIsDisplayed()
                scenario.onActivity {
                    assertEquals(IntroPhotoChoice.CANCELLED, it.model.snapshot.value!!.preferences.introPhotoChoice)
                    assertTrue(it.model.backendPhotos.state.value!!.draft.photos.isEmpty())
                }
                assertEquals(1, monitor.hits)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun backFromNotificationsThenSkipDiscardsPreviouslySelectedSample() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            server(scenario); photos(); tag("intro_sample").performClick(); awaitTag("intro_notifications")
            tag("intro_back").performClick(); tag("intro_photo_skip").assertIsDisplayed()
            tag("intro_photo_skip").performClick(); tag("intro_not_now").performClick()
            tag("tab_video").assertIsDisplayed()
            scenario.onActivity {
                assertNull(it.model.serverIntroPhoto.state.value!!.reference)
                assertTrue(it.model.backendPhotos.state.value!!.draft.photos.isEmpty())
                assertEquals(IntroPhotoChoice.SKIPPED, it.model.snapshot.value!!.preferences.introPhotoChoice)
            }
        }
    }
    @Test fun invalidPhotoStaysOnSelectionAndCanBeSkipped() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            server(scenario); photos()
            scenario.onActivity { it.model.importServerIntroPhoto(android.net.Uri.parse("content://missing-intro-photo/image")) }
            await(scenario) { it.serverIntroPhoto.state.value!!.failed }
            text(R.string.server_intro_photo_failed).assertIsDisplayed(); text(R.string.okay).performClick()
            tag("intro_photo_skip").performClick(); tag("intro_not_now").performClick(); tag("tab_video").assertIsDisplayed()
        }
    }
    @Test fun committedPhotoImportRecoversIfProcessStopsBeforeIntroStepIsAdvanced() {
        lateinit var importer: ServerIntroPhoto
        instrumentation.runOnMainSync {
            importer = ServerIntroPhoto(context)
            importer.import(android.net.Uri.parse("android.resource://${context.packageName}/${R.drawable.demo_good_1}"), null, IntroPhotoChoice.SAMPLE) {}
        }
        ui.waitUntil(12000) { importer.state.value?.reference != null }
        references = references + importer.state.value!!.reference!!
        instrumentation.runOnMainSync { importer.close() }
        PreferencesDemoStore(context.getSharedPreferences("demo_state_v1", 0)).save(DemoSnapshot(preferences = DemoPreferences(introStep = IntroStep.PHOTOS)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            server(scenario); awaitTag("intro_notifications"); tag("intro_not_now").performClick()
            await(scenario) { it.backendPhotos.state.value!!.draft.photos == references.toList() }
        }
    }
    @Test fun pendingPhotoOwnershipSurvivesRestartAndPreventsDeliveryToAnotherAccount() {
        val name = "intro-owner-${UUID.randomUUID()}"
        lateinit var first: ServerIntroPhoto
        var restored: ServerIntroPhoto? = null
        instrumentation.runOnMainSync {
            first = ServerIntroPhoto(context, name)
            first.import(android.net.Uri.parse("android.resource://${context.packageName}/${R.drawable.demo_good_1}"), null, IntroPhotoChoice.SAMPLE) {}
        }
        ui.waitUntil(12000) { first.state.value?.reference != null }
        references = references + first.state.value!!.reference!!
        try {
            instrumentation.runOnMainSync {
                first.deliver(user) { false }; first.close()
                val second = ServerIntroPhoto(context, name); restored = second
                assertEquals(user, second.state.value!!.owner)
                second.deliver("another-account") { fail("Photo must not cross accounts"); true }
                assertNotNull(second.state.value!!.reference)
                second.deliver(user) { assertEquals(references.single(), it); true }
                assertNull(second.state.value!!.reference)
                second.deliver(user) { fail("Consumed photo must not be replayed"); true }
            }
        } finally {
            instrumentation.runOnMainSync { first.close(); restored?.close() }
            context.getSharedPreferences(name, 0).edit().clear().commit()
        }
    }
    @Test fun previouslyCompletedIntroSkipsWelcomeAndKeepsExistingDraft() {
        PreferencesDemoStore(context.getSharedPreferences("demo_state_v1", 0)).save(DemoSnapshot(preferences = DemoPreferences(introStep = IntroStep.DONE)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            server(scenario); tag("intro_welcome").assertDoesNotExist(); tag("tab_video").assertIsDisplayed()
            scenario.onActivity {
                it.model.backendPhotos.edit { PhotoDraft(prompt = "Keep this draft", photos = listOf("file:existing.jpg")) }
                assertFalse(it.model.backendPhotos.acceptOnboardingPhoto("file:other.jpg"))
                assertEquals("Keep this draft", it.model.backendPhotos.state.value!!.draft.prompt)
                assertEquals(listOf("file:existing.jpg"), it.model.backendPhotos.state.value!!.draft.photos)
            }
        }
    }
    @Test fun permanentlyDeniedNotificationsCanBeSkippedWithoutDemoNavigator() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        instrumentation.uiAutomation.executeShellCommand("pm revoke ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        PreferencesDemoStore(context.getSharedPreferences("demo_state_v1", 0)).save(DemoSnapshot(preferences = DemoPreferences(introStep = IntroStep.NOTIFICATIONS, notificationAsked = true)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            server(scenario); awaitTag("intro_notifications"); tag("intro_next").performClick()
            text(R.string.notification_blocked).assertIsDisplayed(); scenario.recreate()
            text(R.string.notification_blocked).assertIsDisplayed()
            ui.onNode(hasText(context.getString(R.string.not_now)) and hasAnyAncestor(isDialog())).performClick()
            tag("tab_video").assertIsDisplayed()
            scenario.onActivity { assertFalse(it.model.snapshot.value!!.preferences.notificationsEnabled) }
        }
    }
}
