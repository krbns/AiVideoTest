package com.rslnabk.aivideotest.data.backend

import android.content.Context
import android.graphics.BitmapFactory
import com.rslnabk.aivideotest.data.media.ExportContent
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class LocalPhoto(val file: File, val mime: String, val name: String) {
    fun export(jobId: String) = ExportContent(jobId, mime, name) { output -> file.inputStream().use { it.copyTo(output) } }
}
/** Results are downloaded as their original bytes. References and demo fixtures never enter this path. */
class RemotePhotoMedia(context: Context, private val fetch: (String) -> Pair<ByteArray, String?> = ::fetchPhoto) {
    private val root = File(context.cacheDir, "backend_results")
    fun load(user: String, job: RemoteJob): LocalPhoto {
        require(job.kind == "image" && job.status == "completed")
        UUID.fromString(user); UUID.fromString(job.id)
        val asset = job.assets.firstOrNull() ?: throw BackendFailure(code = "no_output")
        val key = MessageDigest.getInstance("SHA-256").digest(asset.url.toByteArray()).joinToString("") { "%02x".format(it) }
        val target = File(root, user + "-" + job.id + "-" + key)
        if (target.exists()) return verify(target, job.id)
        if (expired(asset.expiresAt)) throw BackendFailure(410, "result_expired")
        root.mkdirs(); val temporary = File(root, UUID.randomUUID().toString() + ".tmp")
        try {
            val bytes = fetch(asset.url).first
            require(bytes.isNotEmpty() && bytes.size <= 32 * 1024 * 1024)
            temporary.writeBytes(bytes)
            val checked = verify(temporary, job.id)
            check(temporary.renameTo(target))
            return checked.copy(file = target)
        } catch (error: BackendFailure) { throw error }
        catch (_: Exception) { throw BackendFailure(code = "invalid_image") }
        finally { temporary.delete() }
    }
    private fun verify(file: File, job: String): LocalPhoto {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..16000 || bounds.outHeight !in 1..16000) throw BackendFailure(code = "invalid_image")
        val ext = when (bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> throw BackendFailure(code = "unsupported_image") }
        return LocalPhoto(file, bounds.outMimeType, "AiVideoTest-" + job + "." + ext)
    }
    companion object {
        fun expired(raw: String?): Boolean {
            if (raw == null) return false
            val normalized = Regex("\\.(\\d+)").replace(raw) { "." + it.groupValues[1].take(3).padEnd(3, '0') }
            val formats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")
            val time = formats.firstNotNullOfOrNull { pattern ->
                runCatching { SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }.parse(normalized)?.time }.getOrNull()
            }
            return time != null && time <= System.currentTimeMillis()
        }
    }
}
private fun fetchPhoto(value: String): Pair<ByteArray, String?> {
    var url = URL(value)
    for (attempt in 0..3) {
        if (url.protocol != "https") throw BackendFailure(code = "invalid_media_url")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000; connection.readTimeout = 30000; connection.instanceFollowRedirects = false
            // Result tokens are already in the public URL; no API credentials are attached.
            val status = connection.responseCode
            if (status in 300..399) { url = URL(url, connection.getHeaderField("Location") ?: throw BackendFailure(code = "invalid_media_url")); continue }
            if (status != 200) throw BackendFailure(status, if (status == 410) "result_expired" else "download")
            return connection.inputStream.use { it.readBytesLimited(32 * 1024 * 1024) } to connection.contentType
        } catch (error: BackendFailure) { throw error }
        catch (_: Exception) { throw BackendFailure() }
        finally { connection.disconnect() }
    }
    throw BackendFailure(code = "invalid_media_url")
}
