package com.rslnabk.aivideotest

import android.content.*
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.data.media.*
import com.rslnabk.aivideotest.model.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import androidx.test.platform.app.InstrumentationRegistry

@RunWith(AndroidJUnit4::class)
class ResultExportTest : ComposeFlowTest() {
    private fun job(kind: MediaKind): GenerationJob {
        val draft = GenerationDraft("prompt_${kind.name.lowercase()}",kind,prompt = "Demo")
        return GenerationJob(UUID.randomUUID().toString(),draft,10,DemoResultFixtures.image(draft,DemoCatalogRepository()),0,0,status = JobStatus.SUCCEEDED)
    }
    @Before fun reset() {
        context.getSharedPreferences("demo_state_v1",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("result_export_v1",Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun photoAndVideoGalleryDocumentAndShareContainActualDisplayedMedia() {
        val media=ResultMedia(context)
        MediaKind.entries.forEach { kind ->
            val job=job(kind);var gallery:Uri?=null
            val file=File(context.cacheDir,"export-test-${job.id}")
            try {
                gallery=media.gallery(job,{})
                val bytes=context.contentResolver.openInputStream(gallery)!!.use { it.readBytes() }
                if (kind==MediaKind.PHOTO) {
                    assertEquals(0xff,bytes[0].toInt() and 0xff);assertEquals(0xd8,bytes[1].toInt() and 0xff)
                    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size);assertNotNull(bitmap);bitmap.recycle()
                } else {
                    val original=context.resources.openRawResource(DemoResultFixtures.video(job.draft)).use { it.readBytes() }
                    assertArrayEquals(original,bytes)
                }
                if (Build.VERSION.SDK_INT>=29) context.contentResolver.query(gallery,arrayOf(MediaStore.MediaColumns.IS_PENDING),null,null,null)!!.use {
                    assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))
                }
                media.document(job,Uri.fromFile(file));assertArrayEquals(bytes,file.readBytes())
                val uri=media.share(job);val intent=media.shareIntent(job,uri)
                assertEquals(media.mime(job),intent.type);assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
                assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertArrayEquals(bytes,context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                // An exported copy survives removal of the history record.
                val store=PreferencesDemoStore(context.getSharedPreferences("demo_state_v1",Context.MODE_PRIVATE))
                store.save(DemoSnapshot(jobs=listOf(job)))
                assertTrue(DemoSession(store,DemoCatalogRepository()).deleteJob(job.id));assertTrue(file.exists())
            } finally { gallery?.let { context.contentResolver.delete(it,null,null) };file.delete() }
        }
    }
    @Test fun failedGalleryWriteRollsBackNewRow() {
        val media=ResultMedia(context);var inserted:Uri?=null
        try {
            assertThrows(Exception::class.java) { media.gallery(job(MediaKind.PHOTO),{inserted=it},true) }
            assertNotNull(inserted)
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns._ID} = ?",arrayOf(ContentUris.parseId(inserted!!).toString()),null)!!.use { assertEquals(0,it.count) }
        } finally { inserted?.let { runCatching { context.contentResolver.delete(it,null,null) } } }
    }
    @Test fun saveErrorRefreshSuccessAndFileChoiceRotationCancellation() {
        val job=job(MediaKind.PHOTO)
        PreferencesDemoStore(context.getSharedPreferences("demo_state_v1",Context.MODE_PRIVATE)).save(DemoSnapshot(jobs=listOf(job)))
        var exported:Uri?=null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openJob(job.id);ViewModelProvider(it)[AppViewModel::class.java].exports.failNext=true }
            tag("options").performClick();text(R.string.save_gallery).performClick()
            await(scenario) { it.exports.state.value?.phase==ExportPhase.FAILED }
            text(R.string.gallery_error).assertIsDisplayed()
            scenario.recreate()
            text(R.string.refresh).performClick()
            await(scenario) { it.exports.state.value?.phase==ExportPhase.SUCCEEDED }
            scenario.onActivity { exported=ViewModelProvider(it)[AppViewModel::class.java].exports.state.value?.uri }
            text(R.string.saved_gallery).assertIsDisplayed()
            text(R.string.okay).performClick()
            scenario.onActivity { assertTrue(ViewModelProvider(it)[AppViewModel::class.java].exports.begin(job,ExportDestination.FILES)) }
            scenario.recreate()
            scenario.onActivity {
                val exports=ViewModelProvider(it)[AppViewModel::class.java].exports
                assertEquals(ExportPhase.CHOOSING,exports.state.value?.phase)
                assertFalse(exports.begin(job,ExportDestination.GALLERY))
                exports.documentChosen(job,null)
            }
            tag("share").assertIsEnabled()
            scenario.onActivity { assertEquals(1,ViewModelProvider(it)[AppViewModel::class.java].snapshot.value!!.jobs.size) }
        }
        exported?.let { context.contentResolver.delete(it,null,null) }
    }
    @Test fun interruptedOperationRecoversWithoutFalseSuccessAndColdChoiceDoesNotBlock() {
        val job=job(MediaKind.PHOTO)
        val preferences=context.getSharedPreferences("result_export_v1",Context.MODE_PRIVATE)
        fun operation(phase:String)=JSONObject().apply {
            put("id","interrupted");put("job",job.id);put("destination","GALLERY");put("phase",phase)
        }
        preferences.edit().putString("operation",operation("WRITING").toString()).commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val controller=ExportController(context)
            assertEquals(ExportPhase.FAILED,controller.state.value?.phase)
            assertEquals(R.string.export_interrupted,controller.state.value?.message)
            controller.acknowledge("interrupted");controller.close()
        }
        preferences.edit().putString("operation",operation("CHOOSING").apply { put("destination","FILES") }.toString()).commit()
        PreferencesDemoStore(context.getSharedPreferences("demo_state_v1",Context.MODE_PRIVATE)).save(DemoSnapshot(jobs=listOf(job)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { it.exports.state.value == null }
            scenario.onActivity {
                assertNull(ViewModelProvider(it)[AppViewModel::class.java].exports.state.value)
                it.openJob(job.id)
            }
            tag("share").assertIsEnabled()
        }
    }
}
