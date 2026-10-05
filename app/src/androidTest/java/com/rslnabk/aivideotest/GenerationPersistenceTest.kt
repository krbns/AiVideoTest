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
class GenerationPersistenceTest {
    @Test fun requestsAndJobsRoundTripThroughPreferencesAndFinishOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("generation_codec_test",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = PreferencesDemoStore(prefs)
            val session = DemoSession(store,DemoCatalogRepository())
            session.setAccount(DemoAccount(100,true));session.markInstructionSeen()
            session.updateDraft("photo_gold") { it.copy(photo="asset:good2",photoStatus=PhotoStatus.READY) }
            session.updateDraft("prompt_video") { it.copy(prompt="Кино 👋",resolution=1080) }
            session.submit("photo_gold","stable-job",1000)
            val restored = DemoSession(PreferencesDemoStore(prefs),DemoCatalogRepository())
            assertEquals(session.snapshot,restored.snapshot)
            restored.reconcile(5000)
            assertEquals(JobStatus.SUCCEEDED,restored.snapshot.jobs.single().status)
            assertEquals(80,restored.snapshot.account.tokens)
            val again = DemoSession(PreferencesDemoStore(prefs),DemoCatalogRepository())
            again.reconcile(6000)
            assertEquals(restored.snapshot,again.snapshot)
            assertEquals("Кино 👋",again.draft("prompt_video").prompt)
        } finally { prefs.edit().clear().commit() }
    }
}
