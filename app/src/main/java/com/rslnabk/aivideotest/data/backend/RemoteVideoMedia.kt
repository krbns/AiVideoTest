package com.rslnabk.aivideotest.data.backend

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** Video bytes are streamed to disk, validated locally, and exported without transcoding. */
class RemoteVideoMedia(context: Context, private val fetch: (String, File) -> Unit = ::fetchVideo) {
    private val root = File(context.cacheDir, "backend_video_results")
    fun load(user: String, job: RemoteJob): LocalPhoto {
        require(job.kind == "video" && job.status == "completed")
        UUID.fromString(user); UUID.fromString(job.id)
        val asset = job.assets.firstOrNull() ?: throw BackendFailure(code = "no_output")
        val key = MessageDigest.getInstance("SHA-256").digest(asset.url.toByteArray()).joinToString("") { "%02x".format(it) }
        val target = File(root, "$user-${job.id}-$key")
        if (target.exists()) return verify(target, job.id)
        if (RemotePhotoMedia.expired(asset.expiresAt)) throw BackendFailure(410, "result_expired")
        root.mkdirs(); val temporary = File(root, UUID.randomUUID().toString() + ".tmp")
        try {
            fetch(asset.url, temporary)
            val checked = verify(temporary, job.id, makePoster = false)
            check(temporary.renameTo(target))
            return verify(target, job.id).copy(mime = checked.mime)
        } catch (error: BackendFailure) { throw error }
        catch (_: Exception) { throw BackendFailure(code = "invalid_video") }
        finally { temporary.delete() }
    }
    private fun verify(file: File, job: String, makePoster: Boolean = true): LocalPhoto {
        if (file.length() !in 12..MAX_VIDEO_BYTES) throw BackendFailure(code = "invalid_video")
        val header = file.inputStream().use { input -> ByteArray(12).also { require(input.read(it) == it.size) } }
        if (String(header, 4, 4, Charsets.US_ASCII) != "ftyp") throw BackendFailure(code = "unsupported_video")
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            require(width in 1..8192 && height in 1..8192 && duration > 0)
            val quickTime = String(header, 8, 4, Charsets.US_ASCII) == "qt  "
            val mime = if (quickTime) "video/quicktime" else "video/mp4"
            val poster = File(file.path + ".jpg")
            if (makePoster && !poster.exists() && Build.VERSION.SDK_INT >= 27) runCatching {
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512)?.let { bitmap ->
                    try { poster.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } } finally { bitmap.recycle() }
                }
            }.onFailure { poster.delete() }
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val ratio = if (rotation in setOf(90, 270)) height.toFloat() / width else width.toFloat() / height
            return LocalPhoto(file, mime, "AiVideoTest-$job.${if (quickTime) "mov" else "mp4"}", poster.takeIf { it.exists() }, ratio)
        } catch (error: BackendFailure) { throw error }
        catch (_: Exception) { throw BackendFailure(code = "invalid_video") }
        finally { retriever.release() }
    }
}
private const val MAX_VIDEO_BYTES = 512L * 1024 * 1024
private fun fetchVideo(value: String, target: File) {
    var url = URL(value)
    for (attempt in 0..3) {
        if (url.protocol != "https") throw BackendFailure(code = "invalid_media_url")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000; connection.readTimeout = 30000; connection.instanceFollowRedirects = false
            val status = connection.responseCode
            if (status in 300..399) { url = URL(url, connection.getHeaderField("Location") ?: throw BackendFailure(code = "invalid_media_url")); continue }
            if (status != 200) throw BackendFailure(status, if (status == 410) "result_expired" else "download")
            if (connection.contentLengthLong > MAX_VIDEO_BYTES) throw BackendFailure(code = "result_too_large")
            connection.inputStream.use { input -> target.outputStream().use { output ->
                val chunk = ByteArray(16384); var total = 0L
                while (true) {
                    val count = input.read(chunk); if (count < 0) break
                    total += count; if (total > MAX_VIDEO_BYTES) throw BackendFailure(code = "result_too_large")
                    output.write(chunk, 0, count)
                }
            } }
            return
        } catch (error: BackendFailure) { throw error }
        catch (_: Exception) { throw BackendFailure() }
        finally { connection.disconnect() }
    }
    throw BackendFailure(code = "invalid_media_url")
}
