package com.rslnabk.aivideotest.data.backend

import java.net.HttpURLConnection
import java.net.URL

fun interface BackendTransport {
    fun request(method: String, path: String, bearer: String?, body: String?): BackendResponse
}
data class BackendResponse(val status: Int, val body: String)
class HttpsBackendTransport(private val origin: String = "https://benvilo.shop") : BackendTransport {
    init { require(URL(origin).protocol == "https") }
    override fun request(method: String, path: String, bearer: String?, body: String?): BackendResponse {
        require(path.startsWith("/v1/") && !path.startsWith("//"))
        val connection = URL(origin + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 15000; connection.readTimeout = 20000
            // Do not forward identity headers through redirects to a different host.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "en")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer)
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                val bytes = body.toByteArray(Charsets.UTF_8)
                // Streaming bodies cannot be buffered and replayed for authentication/redirects.
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use {
                val bytes = it.readBytesLimited(8 * 1024 * 1024)
                bytes.toString(Charsets.UTF_8)
            }.orEmpty()
            return BackendResponse(status, text)
        } catch (error: BackendFailure) { throw error }
        catch (_: Exception) { throw BackendFailure() }
        finally { connection.disconnect() }
    }
}
internal fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val result = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer); if (size < 0) break
        if (result.size() + size > limit) throw BackendFailure(code = "response_too_large")
        result.write(buffer, 0, size)
    }
    return result.toByteArray()
}
