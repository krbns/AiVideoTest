package com.rslnabk.aivideotest.data.media

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.rslnabk.aivideotest.data.demo.DemoResultFixtures
import com.rslnabk.aivideotest.model.*
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** Exports exactly the local fixture displayed by Result, never the reference photo. */
class ResultMedia(private val context: Context) {
    fun mime(job: GenerationJob) = if (job.draft.kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg"
    fun name(job: GenerationJob) = "AiVideoTest-${job.id}.${if (job.draft.kind == MediaKind.VIDEO) "mp4" else "jpg"}"
    private fun write(job: GenerationJob, output: OutputStream) {
        if (job.draft.kind == MediaKind.VIDEO) {
            context.resources.openRawResource(DemoResultFixtures.video(job.draft)).use { it.copyTo(output) }
        } else {
            val bitmap = BitmapFactory.decodeResource(context.resources, job.resultImage) ?: error("Missing result image")
            try { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 93, output)) } finally { bitmap.recycle() }
        }
    }
    fun document(job: GenerationJob, uri: Uri, fail: Boolean = false) {
        try {
            context.contentResolver.openOutputStream(uri, "w").use { stream ->
                checkNotNull(stream); if (fail) error("Demo export failure"); write(job, stream)
            }
        } catch (error: Exception) {
            // Only the newly created document returned by our create request is removed.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }
    fun gallery(job: GenerationJob, onInserted: (Uri) -> Unit, fail: Boolean = false): Uri {
        val video = job.draft.kind == MediaKind.VIDEO
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val folder = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name(job).replace(".${if (video) "mp4" else "jpg"}", "-${UUID.randomUUID()}.${if (video) "mp4" else "jpg"}"))
            put(MediaStore.MediaColumns.MIME_TYPE, mime(job))
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/AiVideoTest")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(collection, values))
        try {
            onInserted(uri)
            resolver.openOutputStream(uri, "w").use { stream ->
                checkNotNull(stream); if (fail) error("Demo export failure"); write(job, stream)
            }
            if (Build.VERSION.SDK_INT >= 29) check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0)
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }; throw error
        }
    }
    fun share(job: GenerationJob, fail: Boolean = false): Uri {
        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        val target = File(folder, name(job))
        val temporary = File(folder, "${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { if (fail) error("Demo export failure"); write(job, it) }
            check(temporary.renameTo(target))
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        } finally { temporary.delete() }
    }
    fun shareIntent(job: GenerationJob, uri: Uri) = Intent(Intent.ACTION_SEND).apply {
        type = mime(job); putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, name(job), uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
