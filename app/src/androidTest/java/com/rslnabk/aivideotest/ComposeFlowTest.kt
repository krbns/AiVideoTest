package com.rslnabk.aivideotest

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.rslnabk.aivideotest.data.demo.PreferencesJson
import com.rslnabk.aivideotest.model.*
import org.junit.Before
import org.junit.Rule

/** Activity is launched after fixture seeding, avoiding state carried between tests. */
abstract class ComposeFlowTest {
    @get:Rule val ui = createEmptyComposeRule()
    protected val context get() = ApplicationProvider.getApplicationContext<Context>()
    protected fun text(id: Int) = ui.onNodeWithText(context.getString(id))
    protected fun tag(value: String) = ui.onNodeWithTag(value)
    protected fun await(scenario: ActivityScenario<MainActivity>, predicate: (AppViewModel) -> Boolean) {
        ui.waitUntil(12000) {
            var ready = false
            scenario.onActivity { ready = predicate(it.model) }
            ready
        }
        ui.waitForIdle()
    }
    protected fun back(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
    }
    @Before fun resetComposeState() {
        context.getSharedPreferences("backend_source_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("demo_state_v1", Context.MODE_PRIVATE).edit().clear().putString("preferences_v1", PreferencesJson.encode(DemoPreferences(introStep = IntroStep.DONE))).commit()
        context.getSharedPreferences("result_export_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
