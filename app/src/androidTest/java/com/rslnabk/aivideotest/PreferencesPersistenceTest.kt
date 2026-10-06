package com.rslnabk.aivideotest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesPersistenceTest {
    @Test fun preferencesRoundTripPreservesDraftsAndAcceptedEvents() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("p6_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = PreferencesDemoStore(prefs)
            val data = DemoPreferences(IntroStep.PHOTOS, IntroPhotoChoice.SAMPLE, true, true, setOf("ready"), 4, "Имя", "😀",
                RatingDecision.SAVED, DemoReview("r", 5, "Name", "Good"), "Problem", "Letter", listOf(DemoMessage("m", MessageKind.REPORT, "Report")))
            store.save(DemoSnapshot(preferences = data)); assertEquals(data, PreferencesDemoStore(prefs).load().preferences)
            assertEquals(IntroStep.WELCOME, PreferencesJson.decode("broken").introStep)
            assertEquals(IntroStep.DONE, PreferencesJson.decode("{\"intro\":\"FUTURE\"}", true).introStep)
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun legacyUpgradeKeepsAccountAndSkipsIntroButFreshInstallStartsWelcome() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("p6_upgrade_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            assertEquals(IntroStep.WELCOME, PreferencesDemoStore(prefs).load().preferences.introStep)
            prefs.edit().putInt("tokens", 105).putBoolean("pro", true).putStringSet("favorites", setOf("photo_gold")).commit()
            val snapshot = PreferencesDemoStore(prefs).load()
            assertEquals(IntroStep.DONE, snapshot.preferences.introStep); assertEquals(DemoAccount(105, true), snapshot.account)
            assertEquals(setOf("photo_gold"), snapshot.favorites)
        } finally { prefs.edit().clear().commit() }
    }
}
