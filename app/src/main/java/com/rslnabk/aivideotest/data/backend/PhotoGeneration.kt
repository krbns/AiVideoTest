package com.rslnabk.aivideotest.data.backend

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class PhotoPhase { EDITING, UPLOADING, SENDING, ACTIVE, COMPLETE, REJECTED, UNKNOWN }
data class PhotoDraft(val prompt: String = "", val modelId: String = "", val photos: List<String> = emptyList(),
    val resolution: String? = null, val aspectRatio: String? = null, val outputFormat: String? = null,
    val kind: String = "image", val duration: String? = null, val generateAudio: Boolean? = null, val templateId: String? = null) {
    val mode get() = if (kind == "video") { if (photos.isEmpty()) "textToVideo" else "imageToVideo" }
        else if (photos.isEmpty()) "textToImage" else "imageToImage"
}
data class PhotoState(val draft: PhotoDraft = PhotoDraft(), val phase: PhotoPhase = PhotoPhase.EDITING,
    val submissionJobId: String? = null, val job: RemoteJob? = null, val failure: BackendFailure? = null,
    val importing: Boolean = false, val downloading: Boolean = false, val local: LocalPhoto? = null,
    val downloadError: BackendFailure? = null, val submission: PhotoSubmission? = null,
    val recovery: PhotoRecoveryState = PhotoRecoveryState(), val actionBusy: Boolean = false,
    val actionError: BackendFailure? = null, val deletedJobId: String? = null) {
    val busy get() = importing || recovery.loading || actionBusy || phase in listOf(PhotoPhase.UPLOADING, PhotoPhase.SENDING, PhotoPhase.ACTIVE)
    val canEdit get() = !busy && phase != PhotoPhase.UNKNOWN
}
/** Shared image/video creator requests; processing modes have different required inputs. */
object PhotoRequest {
    fun allowed(data: BackendData) = data.policy?.let { it.canGenerate || it.trial > 0 } ?: true
    fun models(data: BackendData, draft: PhotoDraft) = data.models.filter { model ->
        model.kind == draft.kind && model.modes.any { it.name == draft.mode } &&
            draft.photos.size <= model.maxImages && draft.photos.size >= model.minImages
    }
    fun mode(model: RemoteModel, draft: PhotoDraft) = model.modes.find { it.name == draft.mode }
    fun defaults(draft: PhotoDraft, model: RemoteModel): PhotoDraft {
        val mode = mode(model, draft) ?: return draft.copy(modelId = model.id)
        fun selected(value: String?, key: String, options: List<String>) =
            value?.takeIf { it in options } ?: mode.defaults[key]?.takeIf { it in options } ?: options.firstOrNull()
        return draft.copy(modelId = model.id, resolution = selected(draft.resolution, "resolution", mode.resolutions),
            aspectRatio = selected(draft.aspectRatio, "aspectRatio", mode.aspectRatios),
            outputFormat = selected(draft.outputFormat, "outputFormat", mode.outputFormats),
            duration = selected(draft.duration, "duration", mode.durations),
            generateAudio = if (model.supportsAudio && "generateAudio" in mode.params)
                draft.generateAudio ?: mode.defaults["generateAudio"]?.toBooleanStrictOrNull() ?: false else null)
    }
    fun cost(model: RemoteModel, draft: PhotoDraft): Int? {
        if (draft.kind == "image") return if (model.resolutionCredits.isNotEmpty())
            model.resolutionCredits[draft.resolution ?: mode(model, draft)?.defaults?.get("resolution")]
            else model.credits.takeIf { it >= 0 }
        return runCatching {
            val seconds = java.math.BigDecimal(requireNotNull(draft.duration).removeSuffix("s"))
            require(seconds.signum() > 0 && model.credits >= 0 && (model.baseDuration ?: 0) > 0)
            val batches = seconds.divide(java.math.BigDecimal(model.baseDuration!!), 0, java.math.RoundingMode.CEILING)
            val resolution = if (model.resolutionMultipliers.isEmpty()) 1 else requireNotNull(model.resolutionMultipliers[draft.resolution])
            val audio = if (draft.generateAudio == true) model.audioMultiplier ?: 1.0 else 1.0
            require(resolution > 0 && audio.isFinite() && audio > 0)
            java.math.BigDecimal(model.credits).multiply(batches).multiply(java.math.BigDecimal(resolution))
                .multiply(java.math.BigDecimal.valueOf(audio)).setScale(0, java.math.RoundingMode.CEILING).intValueExact()
        }.getOrNull()
    }
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
        option("duration", draft.duration, mode.durations)
        draft.generateAudio?.let { require(model.supportsAudio && "generateAudio" in mode.params); body.put("generateAudio", it) }
        if (draft.kind == "image" && "numImages" in mode.params) body.put("numImages", 1)
        // An unsupported new mandatory setting disables submission instead of guessing.
        require(mode.required.all { it in setOf("imageUrl", "imageUrls") && urls.isNotEmpty() || body.has(it) })
        return body
    }
    fun ready(model: RemoteModel?, draft: PhotoDraft): Boolean = model != null && runCatching {
        build(model, draft, draft.photos.map { "https://validation.invalid/reference" })
        require((cost(model, draft) ?: -1) >= 0)
    }.isSuccess
    fun template(data: BackendData, draft: PhotoDraft) = (if (draft.kind == "image") data.photos else data.videos).find { it.id == draft.templateId }
    fun templateSupported(template: RemoteTemplate, data: BackendData): Boolean {
        val kind = if (template.pipeline == "image") "image" else "video"
        val model = data.models.find { it.id == template.model && it.kind == kind } ?: return false
        val creatorModes = if (kind == "image") setOf("textToImage", "imageToImage") else setOf("textToVideo", "imageToVideo")
        return template.pipeline in setOf("image", "video", "image_video") && template.tokens >= 0 &&
            template.requiredImages in 0..14 && (template.mode.isEmpty() || template.mode in creatorModes) &&
            model.modes.any { it.name in creatorModes && (template.mode.isEmpty() || it.name == template.mode) } &&
            template.steps.all { step ->
                step.kind in setOf("image", "video") && step.requiredImages in 0..14 &&
                    data.models.any { it.id == step.model && it.kind == step.kind && it.modes.any { mode ->
                        mode.name in if (step.kind == "image") setOf("textToImage", "imageToImage") else setOf("textToVideo", "imageToVideo")
                    } }
            }
    }
    fun buildTemplate(template: RemoteTemplate, data: BackendData, draft: PhotoDraft, urls: List<String>): JSONObject {
        require(templateSupported(template, data) && template.id == draft.templateId && template.model == draft.modelId)
        require(draft.kind == if (template.pipeline == "image") "image" else "video")
        require(urls.size == template.requiredImages && urls.size == draft.photos.size && urls.all { java.net.URL(it).protocol == "https" })
        // Hidden prompts, defaults and image_video steps belong to the server. Send only user inputs.
        return JSONObject().put("templateId", template.id).also { if (urls.isNotEmpty()) it.put("imageUrls", JSONArray(urls)) }
    }
    fun ready(data: BackendData, draft: PhotoDraft): Boolean = if (draft.templateId == null) ready(data.models.find { it.id == draft.modelId }, draft)
        else runCatching { buildTemplate(requireNotNull(template(data, draft)), data, draft, draft.photos.map { "https://validation.invalid/reference" }) }.isSuccess
    fun cost(data: BackendData, draft: PhotoDraft): Int? = if (draft.templateId != null) template(data, draft)?.tokens
        else data.models.find { it.id == draft.modelId }?.let { cost(it, draft) }
}
class PhotoJournal(context: Context, kind: String = "image") {
    private val root = File(context.filesDir, if (kind == "video") "backend_video" else "backend_photo")
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
            .put("kind", draft.kind).put("duration", draft.duration).put("generateAudio", draft.generateAudio).put("templateId", draft.templateId)
            .put("phase", phase.name).put("job", job).put("submission", submission?.json())
        fun restore(value: JSONObject, kind: String = "image"): PhotoState {
            fun optional(key: String) = if (value.isNull(key)) null else value.optString(key).takeIf { it.isNotEmpty() }
            val photos = value.optJSONArray("photos")
            val draft = PhotoDraft(value.optString("prompt"), value.optString("model"),
                photos?.let { (0 until it.length()).map { i -> it.getString(i) } }.orEmpty(),
                optional("resolution"), optional("aspectRatio"), optional("outputFormat"), value.optString("kind", kind),
                optional("duration"), if (value.isNull("generateAudio")) null else value.getBoolean("generateAudio"), optional("templateId"))
            require(draft.kind == kind && kind in setOf("image", "video"))
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
