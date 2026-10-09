package com.rslnabk.aivideotest.data.backend

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.rslnabk.aivideotest.data.demo.PhotoImporter
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PhotoGenerationController(private val context: Context, private val backend: BackendController,
    private val media: RemotePhotoMedia = RemotePhotoMedia(context), private val pollMillis: Long = 3000,
    val kind: String = "image", private val videoMedia: RemoteVideoMedia = RemoteVideoMedia(context)) {
    private val mutableState = MutableLiveData(PhotoState(draft = PhotoDraft(kind = kind)))
    val state: LiveData<PhotoState> = mutableState
    private val journal = PhotoJournal(context, kind)
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var user: String? = null
    private var jobJson: JSONObject? = null
    private var polling: ScheduledFuture<*>? = null
    private var pollEpoch = 0
    private var visible = false
    @Volatile private var closed = false
    private var pollFailures = 0
    private var recoveryEpoch = 0
    private val recoveryCursors = mutableSetOf<String>()
    fun activate(account: String?) {
        visible = true
        if (account == null) return
        if (user != account) {
            polling?.cancel(false); user = account; recoveryEpoch++; recoveryCursors.clear()
            try {
                val saved = journal.load(account); jobJson = saved.optJSONObject("job")
                mutableState.value = PhotoJournal.restore(saved, kind)
            } catch (_: Exception) { mutableState.value = PhotoState(draft = PhotoDraft(kind = kind), phase = PhotoPhase.UNKNOWN, failure = BackendFailure(code = "photo_journal")) }
        }
        schedulePoll(0)
        if (mutableState.value!!.phase == PhotoPhase.UNKNOWN && !mutableState.value!!.recovery.searched) searchRecovery()
    }
    fun pause() { visible = false; pollEpoch++; polling?.cancel(false); polling = null }
    private fun publish(value: PhotoState, persist: Boolean = true) {
        if (persist && user != null) journal.save(user!!, PhotoJournal.encode(value.draft, value.phase, jobJson, value.submission))
        mutableState.value = value
    }
    fun edit(change: (PhotoDraft) -> PhotoDraft) {
        val state = mutableState.value!!
        if (!state.canEdit) return
        if (state.phase in listOf(PhotoPhase.COMPLETE, PhotoPhase.REJECTED)) jobJson = null
        try { publish(state.copy(draft = change(state.draft), phase = PhotoPhase.EDITING, failure = null,
            submissionJobId = null, job = null, local = null, submission = null, recovery = PhotoRecoveryState(), actionError = null, deletedJobId = null)) }
        catch (_: Exception) { mutableState.value = state.copy(phase = PhotoPhase.UNKNOWN, failure = BackendFailure(code = "photo_journal")) }
    }
    fun importPhoto(uri: Uri) {
        val current = mutableState.value!!; val account = user ?: return
        if (!current.canEdit) return
        if (current.draft.templateId != null && current.draft.photos.size >= (PhotoRequest.template(backend.state.value!!.data, current.draft)?.requiredImages ?: 0)) return
        jobJson = null
        mutableState.value = current.copy(importing = true, failure = null, submissionJobId = null, job = null, local = null,
            submission = null, recovery = PhotoRecoveryState())
        worker.execute {
            val result = runCatching { PhotoImporter(context, "backend_reference_photos").import(uri) }
            main.post {
                if (closed || user != account) return@post
                val state = mutableState.value!!.copy(importing = false)
                result.fold({ reference ->
                    val draft = state.draft.copy(photos = state.draft.photos + reference)
                    val model = if (draft.templateId != null) null else {
                        val models = PhotoRequest.models(backend.state.value!!.data, draft)
                        models.find { it.id == draft.modelId } ?: models.firstOrNull()
                    }
                    runCatching { publish(state.copy(draft = model?.let { PhotoRequest.defaults(draft, it) } ?: draft, phase = PhotoPhase.EDITING)) }
                        .onFailure { mutableState.value = state.copy(phase = PhotoPhase.UNKNOWN, failure = BackendFailure(code = "photo_journal")) }
                }, { mutableState.value = state.copy(failure = BackendFailure(code = "photo_import")) })
            }
        }
    }
    fun removePhoto(reference: String) = edit { draft ->
        val changed = draft.copy(photos = draft.photos - reference)
        if (draft.templateId != null) return@edit changed
        val model = PhotoRequest.models(backend.state.value!!.data, changed).find { it.id == draft.modelId }
            ?: PhotoRequest.models(backend.state.value!!.data, changed).firstOrNull()
        model?.let { PhotoRequest.defaults(changed, it) } ?: changed
    }
    fun configureTemplate(template: RemoteTemplate): Boolean {
        if (!mutableState.value!!.canEdit || kind != if (template.pipeline == "image") "image" else "video") return false
        if (!PhotoRequest.templateSupported(template, backend.state.value!!.data)) return false
        edit { PhotoDraft(modelId = template.model, kind = kind, templateId = template.id) }
        return mutableState.value!!.phase == PhotoPhase.EDITING
    }
    fun usePrompt() { if (mutableState.value!!.canEdit && mutableState.value!!.draft.templateId != null) edit { PhotoDraft(kind = kind) } }
    fun reviewNewGeneration(): Boolean {
        val state = mutableState.value!!; val job = state.job ?: return false
        if (!state.canEdit || job.status !in setOf("completed", "failed")) return false
        val draft = if (job.id == state.submissionJobId) state.draft else {
            val template = (backend.state.value!!.data.photos + backend.state.value!!.data.videos).find { it.id == job.templateId }
            if (template != null) PhotoDraft(modelId = template.model, templateId = template.id, kind = kind)
            else PhotoDraft(prompt = job.prompt, modelId = job.model, kind = kind)
        }
        edit { draft }; return mutableState.value!!.phase == PhotoPhase.EDITING
    }
    /** The persisted SENDING marker is committed before the single, non-replayed POST. */
    fun submit() {
        val current = mutableState.value!!; val account = user ?: return
        val catalog = backend.state.value!!
        if (!current.canEdit || !PhotoRequest.allowed(catalog.data) || backend.source.value != DataSource.SERVER || catalog.userId != account ||
            BackendSection.MODELS !in catalog.loaded || BackendSection.MODELS in catalog.cached || catalog.authError != null) return
        if (current.draft.kind != kind || !PhotoRequest.ready(catalog.data, current.draft)) return
        val template = PhotoRequest.template(catalog.data, current.draft)
        val section = if (kind == "image") BackendSection.PHOTOS else BackendSection.VIDEOS
        if (current.draft.templateId != null && (section !in catalog.loaded || section in catalog.cached)) return
        val model = catalog.data.models.find { it.id == current.draft.modelId }
        val starting = current.copy(phase = PhotoPhase.UPLOADING, failure = null, job = null, submissionJobId = null, local = null,
            downloadError = null, submission = null, recovery = PhotoRecoveryState(), actionError = null, deletedJobId = null)
        jobJson = null
        try { publish(starting) } catch (_: Exception) { mutableState.value = current.copy(phase = PhotoPhase.UNKNOWN, failure = BackendFailure(code = "photo_journal")); return }
        worker.execute {
            var sent = false
            var submission: PhotoSubmission? = null
            try {
                val urls = starting.draft.photos.map { reference ->
                    require(reference.startsWith("file:"))
                    val filename = reference.removePrefix("file:"); require(File(filename).name == filename)
                    val file = File(context.filesDir, "backend_reference_photos/" + filename)
                    val bytes = file.inputStream().use { it.readBytesLimited(10 * 1000 * 1000) }
                    val uploaded = backend.client.postOnce("/v1/media/uploads", JSONObject().put("type", "image")
                        .put("mediaType", "image/jpeg").put("filename", filename).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
                    require(uploaded.getString("mediaType") == "image/jpeg")
                    if (RemotePhotoMedia.expired(if (uploaded.isNull("expiresAt")) null else uploaded.getString("expiresAt"))) throw BackendFailure(code = "upload_expired")
                    uploaded.getString("url")
                }
                val request = if (template == null) PhotoRequest.build(requireNotNull(model), starting.draft, urls)
                    else PhotoRequest.buildTemplate(template, catalog.data, starting.draft, urls)
                submission = PhotoSubmission(System.currentTimeMillis(), request.toString(), catalog.data.jobs.map { it.id }.toSet())
                journal.save(account, PhotoJournal.encode(starting.draft, PhotoPhase.SENDING, submission = submission))
                sent = true
                val sending = starting.copy(phase = PhotoPhase.SENDING, submission = submission)
                main.post { if (!closed && user == account) mutableState.value = sending }
                val response = backend.client.postOnce(if (kind == "video") "/v1/media/videos" else "/v1/media/images", request)
                val job = BackendJson.job(response)
                java.util.UUID.fromString(job.id)
                require(job.kind == kind)
                val phase = if (job.status in listOf("completed", "failed")) PhotoPhase.COMPLETE else PhotoPhase.ACTIVE
                journal.save(account, PhotoJournal.encode(starting.draft, phase, response, submission))
                main.post {
                    if (closed || user != account) return@post
                    jobJson = response; mutableState.value = sending.copy(phase = phase, job = job, submissionJobId = job.id)
                    refreshBackend(); schedulePoll(0)
                }
            } catch (error: Exception) {
                val failure = error as? BackendFailure ?: BackendFailure(code = "invalid_response")
                val rejected = sent && failure.status in setOf(400, 401, 403, 409, 413, 422, 429, 503)
                val phase = if (sent && !rejected) PhotoPhase.UNKNOWN else PhotoPhase.REJECTED
                runCatching { journal.save(account, PhotoJournal.encode(starting.draft, phase, submission = submission)) }
                val uncertain = starting.copy(phase = phase, failure = failure, submission = submission)
                main.post { if (!closed && user == account) {
                    mutableState.value = uncertain; refreshBackend()
                    if (phase == PhotoPhase.UNKNOWN) searchRecovery()
                } }
            }
        }
    }
    /** User has inspected history and explicitly accepts that another launch may be charged. */
    fun acknowledgeUnknown() {
        if (mutableState.value!!.phase != PhotoPhase.UNKNOWN || mutableState.value!!.recovery.loading) return
        recoveryEpoch++
        jobJson = null
        try { publish(mutableState.value!!.copy(phase = PhotoPhase.EDITING, failure = null, job = null, submissionJobId = null,
            local = null, submission = null, recovery = PhotoRecoveryState())) }
        catch (_: Exception) { mutableState.value = mutableState.value!!.copy(failure = BackendFailure(code = "photo_journal")) }
    }
    /** History is read fresh and separately from the Library cache. A match never binds itself. */
    fun searchRecovery(more: Boolean = false) {
        val current = mutableState.value!!; val account = user ?: return
        if (closed || !visible || current.phase != PhotoPhase.UNKNOWN || current.recovery.loading ||
            backend.source.value != DataSource.SERVER || backend.state.value!!.userId != account) return
        val cursor = if (more) current.recovery.nextCursor ?: return else null
        if (!more) recoveryCursors.clear()
        val epoch = ++recoveryEpoch
        val previous = if (more) current.recovery else PhotoRecoveryState()
        mutableState.value = current.copy(recovery = previous.copy(loading = true, searched = true, failure = null))
        worker.execute {
            val result = runCatching {
                val path = "/v1/media/jobs?kind=" + kind + "&limit=20" + (cursor?.let { "&cursor=" + URLEncoder.encode(it, "UTF-8") } ?: "")
                BackendJson.update(BackendData(), BackendSection.JOBS, backend.client.get(path))
            }
            main.post {
                if (closed || user != account || backend.state.value!!.userId != account || recoveryEpoch != epoch || mutableState.value!!.phase != PhotoPhase.UNKNOWN) return@post
                val state = mutableState.value!!
                mutableState.value = state.copy(recovery = result.fold({ page ->
                    cursor?.let(recoveryCursors::add)
                    val loop = page.nextCursor != null && page.nextCursor in recoveryCursors
                    val candidates = page.jobs.filter { PhotoRecovery.matches(it, state.draft, state.submission) }
                    previous.copy(loading = false, searched = true, candidates = (previous.candidates + candidates).distinctBy { it.id },
                        nextCursor = page.nextCursor.takeUnless { loop }, scanned = previous.scanned + page.jobs.size,
                        failure = if (loop) BackendFailure(code = "recovery_cursor") else null)
                }, { previous.copy(loading = false, searched = true, failure = it as? BackendFailure ?: BackendFailure()) }))
            }
        }
    }
    /** Called only after the user selects and confirms a candidate; detail GET revalidates it. */
    fun recoverJob(id: String) {
        val current = mutableState.value!!; val account = user ?: return
        if (closed || !visible || current.phase != PhotoPhase.UNKNOWN || current.recovery.loading ||
            backend.source.value != DataSource.SERVER || backend.state.value!!.userId != account ||
            current.recovery.candidates.none { it.id == id } || runCatching { java.util.UUID.fromString(id) }.isFailure) return
        val epoch = ++recoveryEpoch
        mutableState.value = current.copy(recovery = current.recovery.copy(loading = true, failure = null))
        worker.execute {
            val result = runCatching {
                val json = backend.client.get("/v1/media/jobs/" + id); val job = BackendJson.job(json)
                if (job.id != id || !PhotoRecovery.matches(job, current.draft, current.submission)) throw BackendFailure(code = "recovery_changed")
                json to job
            }
            main.post {
                if (closed || user != account || backend.state.value!!.userId != account || recoveryEpoch != epoch || mutableState.value!!.phase != PhotoPhase.UNKNOWN) return@post
                result.fold({ (json, job) ->
                    val phase = if (job.status in listOf("completed", "failed")) PhotoPhase.COMPLETE else PhotoPhase.ACTIVE
                    val recovered = current.copy(phase = phase, job = job, submissionJobId = id, failure = null,
                        local = null, downloading = false, downloadError = null, recovery = PhotoRecoveryState())
                    try {
                        journal.save(account, PhotoJournal.encode(recovered.draft, phase, json, recovered.submission))
                        jobJson = json; mutableState.value = recovered; refreshBackend(); schedulePoll(pollMillis)
                    } catch (_: Exception) {
                        mutableState.value = current.copy(recovery = current.recovery.copy(failure = BackendFailure(code = "photo_journal")))
                    }
                }, { error -> mutableState.value = current.copy(recovery = current.recovery.copy(failure = error as? BackendFailure ?: BackendFailure())) })
            }
        }
    }
    fun openJob(job: RemoteJob) {
        if (job.kind != kind) return
        if (runCatching { java.util.UUID.fromString(job.id) }.isFailure) {
            mutableState.value = mutableState.value!!.copy(failure = BackendFailure(code = "invalid_response")); return
        }
        mutableState.value = mutableState.value!!.copy(job = job, local = null, downloadError = null, downloading = false, actionError = null, deletedJobId = null)
        schedulePoll(0)
    }
    fun refreshJob() { pollFailures = 0; schedulePoll(0, force = true) }
    private fun schedulePoll(delay: Long, force: Boolean = false) {
        val cycle = ++pollEpoch
        polling?.cancel(false); polling = null
        val current = mutableState.value!!; val account = user ?: return
        if (!visible || closed || current.actionBusy || backend.source.value != DataSource.SERVER) return
        val ids = listOfNotNull(current.submissionJobId.takeIf { current.phase == PhotoPhase.ACTIVE },
            current.job?.id.takeIf { current.job != null && (force || current.job.status !in listOf("completed", "failed")) }).distinct()
        if (ids.isEmpty()) return
        polling = worker.schedule({
            val results = ids.associateWith { id -> runCatching {
                val json = backend.client.get("/v1/media/jobs/" + id)
                val job = BackendJson.job(json); require(job.id == id && job.kind == kind)
                json to job
            } }
            main.post {
                if (closed || !visible || user != account || cycle != pollEpoch) return@post
                var state = mutableState.value!!; var terminal = false; var hardFailure = false
                results.forEach { (id, result) -> result.fold({ (json, job) ->
                    pollFailures = 0
                    if (state.submissionJobId == id) {
                        jobJson = json
                        if (job.status in listOf("completed", "failed")) { state = state.copy(phase = PhotoPhase.COMPLETE); terminal = true }
                    }
                    if (state.job?.id == id) state = state.copy(job = job, failure = null, actionError = if (force) null else state.actionError)
                }, { error ->
                    val failure = error as? BackendFailure ?: BackendFailure(code = "invalid_response")
                    state = state.copy(failure = failure); pollFailures++
                    if (failure.status == 404 && state.submissionJobId == id) {
                        state = state.copy(phase = PhotoPhase.UNKNOWN, submissionJobId = null)
                        jobJson = null
                    }
                    if (failure.status in listOf(401, 403, 404)) hardFailure = true
                }) }
                runCatching { publish(state) }.onFailure { mutableState.value = state.copy(failure = BackendFailure(code = "photo_journal")) }
                if (terminal) refreshBackend()
                if (!hardFailure) schedulePoll((pollMillis * (1L shl pollFailures.coerceAtMost(3))).coerceAtMost(30000))
            }
        }, delay, TimeUnit.MILLISECONDS)
    }
    fun cancelJob() = jobAction(delete = false)
    fun deleteJob() = jobAction(delete = true)
    private fun jobAction(delete: Boolean) {
        val account = user ?: return; val current = mutableState.value!!; val job = current.job ?: return
        if (closed || !visible || current.actionBusy || job.kind != kind || backend.source.value != DataSource.SERVER ||
            backend.state.value!!.userId != account || runCatching { java.util.UUID.fromString(job.id) }.isFailure) return
        if (delete != (job.status in setOf("completed", "failed")) || (!delete && job.status !in setOf("queued", "running"))) return
        polling?.cancel(false); polling = null
        mutableState.value = current.copy(actionBusy = true, actionError = null)
        worker.execute {
            val result = runCatching {
                val json = if (delete) backend.client.deleteOnce("/v1/media/jobs/" + job.id)
                    else backend.client.postOnce("/v1/media/jobs/" + job.id + "/cancel", JSONObject())
                if (delete) require(json.getBoolean("deleted")) else {
                    val changed = BackendJson.job(json); require(changed.id == job.id && changed.kind == kind)
                }
                json
            }
            // A lost cancel/delete response is reconciled by GET, never by replaying the mutation.
            val detail = if (result.isFailure) runCatching { backend.client.get("/v1/media/jobs/" + job.id) } else null
            main.post {
                if (closed || user != account || backend.state.value!!.userId != account) return@post
                var state = mutableState.value!!.copy(actionBusy = false)
                val removed = delete && (result.isSuccess || (detail?.exceptionOrNull() as? BackendFailure)?.status == 404)
                if (removed) {
                    if (state.submissionJobId == job.id) {
                        jobJson = null; state = state.copy(phase = PhotoPhase.EDITING, submissionJobId = null, submission = null)
                    }
                    if (state.job?.id == job.id) state = state.copy(job = null, local = null, deletedJobId = job.id)
                } else {
                    val json = if (!delete && result.isSuccess) result.getOrNull() else detail?.getOrNull()
                    val changed = json?.let { runCatching { BackendJson.job(it) }.getOrNull() }?.takeIf { it.id == job.id && it.kind == kind }
                    if (changed != null) {
                        if (state.submissionJobId == job.id) {
                            jobJson = json
                            if (changed.status in setOf("completed", "failed")) state = state.copy(phase = PhotoPhase.COMPLETE)
                        }
                        if (state.job?.id == job.id) state = state.copy(job = changed, failure = null)
                    }
                    if (result.isFailure && !(changed?.status == "failed" && changed.errorCode == "canceled" && !delete))
                        state = state.copy(actionError = result.exceptionOrNull() as? BackendFailure ?: BackendFailure(code = "job_action"))
                }
                runCatching { publish(state) }.onFailure { mutableState.value = state.copy(actionError = BackendFailure(code = "photo_journal")) }
                refreshBackend(); schedulePoll(pollMillis)
            }
        }
    }
    fun download() {
        val account = user ?: return; val current = mutableState.value!!; val job = current.job ?: return
        if (current.downloading || job.status != "completed" || job.kind != kind) return
        mutableState.value = current.copy(downloading = true, downloadError = null)
        worker.execute {
            val result = runCatching { if (kind == "video") videoMedia.load(account, job) else media.load(account, job) }
            main.post {
                if (closed || user != account || mutableState.value!!.job?.id != job.id) return@post
                val state = mutableState.value!!
                mutableState.value = result.fold({ state.copy(downloading = false, local = it) },
                    { state.copy(downloading = false, downloadError = it as? BackendFailure ?: BackendFailure()) })
            }
        }
    }
    private fun refreshBackend() {
        if (backend.source.value == DataSource.SERVER) backend.refreshAfterMutation()
    }
    fun close() { closed = true; pause(); worker.shutdown(); main.removeCallbacksAndMessages(null) }
}
