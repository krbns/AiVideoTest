package com.rslnabk.aivideotest.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Decode only enough pixels to cover the thumbnail; the stored reference remains intact. */
object PhotoThumbnail {
    fun decode(file: File, width: Int, height: Int): Bitmap? {
        require(width > 0 && height > 0)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        options.inSampleSize = 1
        while (options.outWidth / options.inSampleSize / 2 >= width && options.outHeight / options.inSampleSize / 2 >= height) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.path, options)
    }
}
