package com.rslnabk.aivideotest

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rslnabk.aivideotest.data.media.PhotoThumbnail
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoThumbnailTest {
    @Test fun thumbnailUsesLessMemoryAndKeepsSourceBytesAndCropResolution() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "thumbnail-test.jpg")
        val source = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            val before = file.readBytes()
            val thumbnail = PhotoThumbnail.decode(file, 200, 200)!!
            try {
                assertTrue(thumbnail.width >= 200 && thumbnail.height >= 200)
                assertTrue(thumbnail.allocationByteCount <= source.allocationByteCount / 16)
                assertArrayEquals(before, file.readBytes())
            } finally { thumbnail.recycle() }
            file.writeText("Not a photo")
            assertNull(PhotoThumbnail.decode(file, 200, 200))
        } finally { source.recycle(); file.delete() }
    }
}
