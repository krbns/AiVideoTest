package com.rslnabk.aivideotest.data.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID

/** Imports downsampled photos into app-owned storage, so temporary picker grants need not persist. */
class PhotoImporter(private val context: Context) {
    fun import(uri: Uri): String {
        val directory = File(context.filesDir, "reference_photos").apply { mkdirs() }
        val raw = File(directory, "${UUID.randomUUID()}.tmp")
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                raw.outputStream().use { stream ->
                    val buffer = ByteArray(8192); var total = 0
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 32 * 1024 * 1024) { "Photo too large" }; stream.write(buffer, 0, count)
                    }
                }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.path, options)
            require(options.outWidth > 0 && options.outHeight > 0) { "Invalid image" }
            options.inJustDecodeBounds = false; options.inSampleSize = 1
            while (options.outWidth / options.inSampleSize > 1600 || options.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2
            val decoded = requireNotNull(BitmapFactory.decodeFile(raw.path, options))
            val orientation = runCatching { ExifInterface(raw.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val transform = Matrix().apply { when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            } }
            val bitmap = if (transform.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true).also { decoded.recycle() }
            try { output.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
            finally { bitmap.recycle() }
            return "file:${output.name}"
        } catch (error: Exception) { output.delete(); throw error }
        finally { raw.delete() }
    }
}
