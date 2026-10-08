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

data class ExportContent(val id: String, val mime: String, val name: String, val write: (OutputStream) -> Unit)

/** Exports displayed demo content or verified server bytes; never the reference photo. */
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
    fun content(job: GenerationJob) = ExportContent(job.id, mime(job), name(job)) { write(job, it) }
    fun document(job: GenerationJob, uri: Uri, fail: Boolean = false) = document(content(job), uri, fail)
    fun document(job: ExportContent, uri: Uri, fail: Boolean = false) {
        try {
            context.contentResolver.openOutputStream(uri, "w").use { stream ->
                checkNotNull(stream); if (fail) error("Demo export failure"); job.write(stream)
            }
        } catch (error: Exception) {
            // Only the newly created document returned by our create request is removed.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }
    fun gallery(job: GenerationJob, onInserted: (Uri) -> Unit, fail: Boolean = false) = gallery(content(job), onInserted, fail)
    fun gallery(job: ExportContent, onInserted: (Uri) -> Unit, fail: Boolean = false): Uri {
        val video = job.mime.startsWith("video/")
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val folder = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, UUID.randomUUID().toString() + "-" + job.name)
            put(MediaStore.MediaColumns.MIME_TYPE, job.mime)
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
                checkNotNull(stream); if (fail) error("Demo export failure"); job.write(stream)
            }
            if (Build.VERSION.SDK_INT >= 29) check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0)
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }; throw error
        }
    }
    fun share(job: GenerationJob, fail: Boolean = false) = share(content(job), fail)
    fun share(job: ExportContent, fail: Boolean = false): Uri {
        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        val target = File(folder, job.name)
        val temporary = File(folder, "${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { if (fail) error("Demo export failure"); job.write(it) }
            check(temporary.renameTo(target))
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        } finally { temporary.delete() }
    }
    fun shareIntent(job: GenerationJob, uri: Uri) = shareIntent(content(job), uri)
    fun shareIntent(job: ExportContent, uri: Uri) = Intent(Intent.ACTION_SEND).apply {
        type = job.mime; putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, job.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
