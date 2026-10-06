package com.rslnabk.aivideotest

import android.app.NotificationManager
import android.content.Intent
import com.rslnabk.aivideotest.data.demo.*
import android.os.Build
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationFlowTest : ComposeFlowTest() {
    private fun notificationSwitch(): SemanticsNodeInteraction {
        tag("settings_list").performScrollToKey("notifications")
        return tag("notification_switch")
    }
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
    }
    @Test fun optInPreviewFutureReadyTapAndRotationNeverDuplicateNotification() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tag("tab_settings").performClick(); notificationSwitch().performClick()
            tag("notification_switch").assertIsOn(); assertEquals(1, manager.activeNotifications.size)
            scenario.recreate(); assertEquals(1, manager.activeNotifications.size)
            scenario.onActivity { it.model.setAccount(DemoAccount(100)); it.model.editDraft("prompt_photo") { d -> d.copy(prompt = "A morning") }; it.model.submit("prompt_photo") }
            await(scenario) { it.snapshot.value!!.jobs.singleOrNull()?.status == JobStatus.SUCCEEDED && manager.activeNotifications.size == 2 }
            val notification = manager.activeNotifications.first { it.id != 41 }
            notification.notification.contentIntent.send()
            ui.waitUntil(12000) { ui.onAllNodesWithTag("share").fetchSemanticsNodes().isNotEmpty() }; tag("share").assertIsDisplayed()
            manager.cancel(notification.id); scenario.recreate(); assertEquals(1, manager.activeNotifications.size)
            back(scenario); notificationSwitch().performClick().assertIsOff()
            assertEquals(0, manager.activeNotifications.size)
            scenario.onActivity { assertEquals(90, it.model.snapshot.value!!.account.tokens); assertEquals(1, it.model.snapshot.value!!.jobs.size) }
        } } finally { manager.cancelAll() }
    }
    @Test fun coldNotificationIntentOpensReadyResultOnceAndNormalLaunchStaysCatalog() {
        val store = PreferencesDemoStore(context.getSharedPreferences("demo_state_v1", 0))
        val session = DemoSession(store, DemoCatalogRepository())
        session.setAccount(DemoAccount(100)); session.updateDraft("prompt_photo") { it.copy(prompt = "Cold result") }
        session.submit("prompt_photo", "cold-notification", 0); session.reconcile(4000)
        val intent = Intent(context, MainActivity::class.java).putExtra("notification_job", "cold-notification")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            ui.waitUntil(12000) { ui.onAllNodesWithTag("share").fetchSemanticsNodes().isNotEmpty() }
            tag("share").assertIsDisplayed(); scenario.recreate(); tag("share").assertIsDisplayed()
            back(scenario); tag("tab_video").assertIsDisplayed()
            scenario.onActivity { assertEquals(90, it.model.snapshot.value!!.account.tokens); assertEquals(1, it.model.snapshot.value!!.jobs.size) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { tag("tab_video").assertIsDisplayed(); tag("share").assertDoesNotExist() }
    }
}
