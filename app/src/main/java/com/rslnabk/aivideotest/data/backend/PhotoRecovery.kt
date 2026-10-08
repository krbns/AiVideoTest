package com.rslnabk.aivideotest.data.backend

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** The exact request is local recovery evidence, not a server idempotency key. */
data class PhotoSubmission(val sentAt: Long, val body: String, val knownJobIds: Set<String>) {
    fun json() = JSONObject().put("sentAt", sentAt).put("body", JSONObject(body)).put("knownJobIds", JSONArray(knownJobIds.toList()))
    override fun toString() = "PhotoSubmission(sentAt=$sentAt, request=[redacted])"
    companion object {
        fun restore(json: JSONObject) = PhotoSubmission(json.getLong("sentAt"), json.getJSONObject("body").toString(),
            json.getJSONArray("knownJobIds").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() })
    }
}
data class PhotoRecoveryState(val loading: Boolean = false, val searched: Boolean = false,
    val candidates: List<RemoteJob> = emptyList(), val nextCursor: String? = null,
    val scanned: Int = 0, val failure: BackendFailure? = null)

object PhotoRecovery {
    private val requestFields = setOf("model", "mode", "prompt", "templateId", "imageUrl", "imageUrls")
    /** Missing legacy metadata weakens evidence; a conflicting value always excludes a job. */
    fun matches(job: RemoteJob, draft: PhotoDraft, submission: PhotoSubmission?): Boolean = runCatching {
        java.util.UUID.fromString(job.id)
        if (job.kind != draft.kind || job.model != draft.modelId || job.prompt != draft.prompt) return false
        val request = submission?.let { JSONObject(it.body) } ?: JSONObject().put("model", draft.modelId)
            .put("mode", draft.mode).put("prompt", draft.prompt).put("resolution", draft.resolution)
            .put("aspectRatio", draft.aspectRatio).put("outputFormat", draft.outputFormat).put("duration", draft.duration)
            .put("generateAudio", draft.generateAudio).put("templateId", draft.templateId)
        if (job.templateId != request.optString("templateId").takeIf { it.isNotBlank() && it != "null" }) return false
        if (job.mode != null && request.has("mode") && job.mode != request.getString("mode")) return false
        if (submission != null) {
            if (job.id in submission.knownJobIds) return false
            val created = time(job.createdAt)
            // A bounded window allows clock skew. It is evidence, never proof of identity.
            if (created != null && (created < submission.sentAt - 5 * 60000L || created > submission.sentAt + 10 * 60000L)) return false
            val expected = request.optJSONArray("imageUrls")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                ?: request.optString("imageUrl").takeIf { it.isNotBlank() }?.let(::listOf) ?: emptyList()
            // Templates can add hidden references or use the first pipeline result as video input.
            if (draft.templateId == null && job.inputImageUrls != null && job.inputImageUrls != expected) return false
        }
        val actual = job.parameters?.let(::JSONObject)
        if (actual != null) for (key in request.keys()) {
            if (key !in requestFields && (!actual.has(key) || !equal(request.get(key), actual.get(key)))) return false
        }
        true
    }.getOrDefault(false)
    fun incomplete(job: RemoteJob, submission: PhotoSubmission?) = submission == null || job.mode == null ||
        job.parameters == null || time(job.createdAt) == null || job.inputImageUrls == null ||
        JSONObject(submission.body).has("templateId")

    fun time(raw: String?): Long? {
        if (raw == null) return null
        val normalized = Regex("\\.(\\d+)").replace(raw) { "." + it.groupValues[1].take(3).padEnd(3, '0') }
        return listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ")
            .firstNotNullOfOrNull { pattern ->
                val position = ParsePosition(0)
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(normalized, position)
                    ?.time?.takeIf { position.index == normalized.length }
            }
    }
    private fun equal(a: Any, b: Any): Boolean = when {
        a is Number && b is Number -> BigDecimal(a.toString()).compareTo(BigDecimal(b.toString())) == 0
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { equal(a.get(it), b.get(it)) }
        a is JSONObject && b is JSONObject -> a.length() == b.length() && a.keys().asSequence().all { b.has(it) && equal(a.get(it), b.get(it)) }
        else -> a == b
    }
}
