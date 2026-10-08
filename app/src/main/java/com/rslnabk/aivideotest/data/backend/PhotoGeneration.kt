package com.rslnabk.aivideotest.data.backend

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class PhotoPhase { EDITING, UPLOADING, SENDING, ACTIVE, COMPLETE, REJECTED, UNKNOWN }
data class PhotoDraft(val prompt: String = "", val modelId: String = "", val photos: List<String> = emptyList(),
    val resolution: String? = null, val aspectRatio: String? = null, val outputFormat: String? = null) {
    val mode get() = if (photos.isEmpty()) "textToImage" else "imageToImage"
}
data class PhotoState(val draft: PhotoDraft = PhotoDraft(), val phase: PhotoPhase = PhotoPhase.EDITING,
    val submissionJobId: String? = null, val job: RemoteJob? = null, val failure: BackendFailure? = null,
    val importing: Boolean = false, val downloading: Boolean = false, val local: LocalPhoto? = null,
    val downloadError: BackendFailure? = null, val submission: PhotoSubmission? = null,
    val recovery: PhotoRecoveryState = PhotoRecoveryState()) {
    val busy get() = importing || recovery.loading || phase in listOf(PhotoPhase.UPLOADING, PhotoPhase.SENDING, PhotoPhase.ACTIVE)
    val canEdit get() = !busy && phase != PhotoPhase.UNKNOWN
}
/** Only creator modes belong to Photo Prompt; processing modes have different required inputs. */
object PhotoRequest {
    fun allowed(data: BackendData) = data.policy?.let { it.canGenerate || it.trial > 0 } ?: true
    fun models(data: BackendData, draft: PhotoDraft) = data.models.filter { model ->
        model.kind == "image" && model.modes.any { it.name == draft.mode } &&
            draft.photos.size <= model.maxImages && draft.photos.size >= model.minImages
    }
    fun mode(model: RemoteModel, draft: PhotoDraft) = model.modes.find { it.name == draft.mode }
    fun defaults(draft: PhotoDraft, model: RemoteModel): PhotoDraft {
        val mode = mode(model, draft) ?: return draft.copy(modelId = model.id)
        fun selected(value: String?, key: String, options: List<String>) =
            value?.takeIf { it in options } ?: mode.defaults[key]?.takeIf { it in options } ?: options.firstOrNull()
        return draft.copy(modelId = model.id, resolution = selected(draft.resolution, "resolution", mode.resolutions),
            aspectRatio = selected(draft.aspectRatio, "aspectRatio", mode.aspectRatios),
            outputFormat = selected(draft.outputFormat, "outputFormat", mode.outputFormats))
    }
    fun cost(model: RemoteModel, draft: PhotoDraft): Int? =
        if (model.resolutionCredits.isNotEmpty()) model.resolutionCredits[draft.resolution ?: mode(model, draft)?.defaults?.get("resolution")]
        else model.credits.takeIf { it >= 0 }
    fun build(model: RemoteModel, draft: PhotoDraft, urls: List<String>): JSONObject {
        val mode = requireNotNull(mode(model, draft))
        require(draft.prompt.isNotBlank() && draft.prompt.codePointCount(0, draft.prompt.length) <= 10000)
        require(urls.size == draft.photos.size && urls.size <= model.maxImages && urls.size >= model.minImages)
        require(urls.all { java.net.URL(it).protocol == "https" })
        val body = JSONObject().put("model", model.id).put("mode", mode.name).put("prompt", draft.prompt)
        if (urls.isNotEmpty()) body.put("imageUrls", JSONArray(urls))
        fun option(key: String, selected: String?, allowed: List<String>) {
            if (allowed.isEmpty()) { require(selected == null); return }
            require(key in mode.params && selected in allowed); body.put(key, selected)
        }
        option("resolution", draft.resolution, mode.resolutions)
        option("aspectRatio", draft.aspectRatio, mode.aspectRatios)
        option("outputFormat", draft.outputFormat, mode.outputFormats)
        if ("numImages" in mode.params) body.put("numImages", 1)
        // An unsupported new mandatory setting disables submission instead of guessing.
        require(mode.required.all { it in setOf("imageUrl", "imageUrls") && urls.isNotEmpty() || body.has(it) })
        return body
    }
    fun ready(model: RemoteModel?, draft: PhotoDraft): Boolean = model != null && runCatching {
        build(model, draft, draft.photos.map { "https://validation.invalid/reference" })
        require((cost(model, draft) ?: -1) >= 0)
    }.isSuccess
}
class PhotoJournal(context: Context) {
    private val root = File(context.filesDir, "backend_photo")
    private fun file(user: String): AtomicFile { UUID.fromString(user); return AtomicFile(File(root, user + ".json")) }
    @Synchronized fun load(user: String): JSONObject {
        val target = file(user)
        if (!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists()) return JSONObject()
        try { return target.openRead().use { JSONObject(it.readBytesLimited(8 * 1024 * 1024).toString(Charsets.UTF_8)) } }
        catch (_: Exception) { throw BackendFailure(code = "photo_journal") }
    }
    @Synchronized fun save(user: String, value: JSONObject) {
        root.mkdirs(); val target = file(user); val stream = target.startWrite()
        try { stream.write(value.toString().toByteArray()); target.finishWrite(stream) }
        catch (_: Exception) { target.failWrite(stream); throw BackendFailure(code = "photo_journal") }
    }
    companion object {
        fun encode(draft: PhotoDraft, phase: PhotoPhase, job: JSONObject? = null, submission: PhotoSubmission? = null) = JSONObject()
            .put("prompt", draft.prompt).put("model", draft.modelId).put("photos", JSONArray(draft.photos))
            .put("resolution", draft.resolution).put("aspectRatio", draft.aspectRatio).put("outputFormat", draft.outputFormat)
            .put("phase", phase.name).put("job", job).put("submission", submission?.json())
        fun restore(value: JSONObject): PhotoState {
            fun optional(key: String) = if (value.isNull(key)) null else value.optString(key).takeIf { it.isNotEmpty() }
            val photos = value.optJSONArray("photos")
            val draft = PhotoDraft(value.optString("prompt"), value.optString("model"),
                photos?.let { (0 until it.length()).map { i -> it.getString(i) } }.orEmpty(),
                optional("resolution"), optional("aspectRatio"), optional("outputFormat"))
            val original = value.optString("phase", "EDITING")
            val phase = when (original) {
                "SENDING", "UNKNOWN" -> PhotoPhase.UNKNOWN
                "UPLOADING" -> PhotoPhase.EDITING
                else -> runCatching { PhotoPhase.valueOf(original) }.getOrDefault(PhotoPhase.UNKNOWN)
            }
            val job = value.optJSONObject("job")?.let(BackendJson::job)
            return PhotoState(draft, if (phase == PhotoPhase.ACTIVE && job == null) PhotoPhase.UNKNOWN else phase,
                job?.id, job, if (original == "UPLOADING") BackendFailure(code = "interrupted") else null,
                submission = value.optJSONObject("submission")?.let(PhotoSubmission::restore))
        }
    }
}
