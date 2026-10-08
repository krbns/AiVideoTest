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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PhotoGenerationController(private val context: Context, private val backend: BackendController,
    private val media: RemotePhotoMedia = RemotePhotoMedia(context), private val pollMillis: Long = 3000) {
    private val mutableState = MutableLiveData(PhotoState())
    val state: LiveData<PhotoState> = mutableState
    private val journal = PhotoJournal(context)
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var user: String? = null
    private var jobJson: JSONObject? = null
    private var polling: ScheduledFuture<*>? = null
    private var visible = false
    @Volatile private var closed = false
    private var pollFailures = 0
    fun activate(account: String?) {
        visible = true
        if (account == null) return
        if (user != account) {
            polling?.cancel(false); user = account
            try {
                val saved = journal.load(account); jobJson = saved.optJSONObject("job")
                mutableState.value = PhotoJournal.restore(saved)
            } catch (_: Exception) { mutableState.value = PhotoState(phase = PhotoPhase.UNKNOWN, failure = BackendFailure(code = "photo_journal")) }
        }
        schedulePoll(0)
    }
    fun pause() { visible = false; polling?.cancel(false); polling = null }
    private fun publish(value: PhotoState, persist: Boolean = true) {
        if (persist && user != null) journal.save(user!!, PhotoJournal.encode(value.draft, value.phase, jobJson))
        mutableState.value = value
    }
    fun edit(change: (PhotoDraft) -> PhotoDraft) {
        val state = mutableState.value!!
        if (!state.canEdit) return
        if (state.phase in listOf(PhotoPhase.COMPLETE, PhotoPhase.REJECTED)) jobJson = null
        try { publish(state.copy(draft = change(state.draft), phase = PhotoPhase.EDITING, failure = null,
            submissionJobId = null, job = null, local = null)) }
        catch (_: Exception) { mutableState.value = state.copy(phase = PhotoPhase.UNKNOWN, failure = BackendFailure(code = "photo_journal")) }
    }
    fun importPhoto(uri: Uri) {
        val current = mutableState.value!!; val account = user ?: return
        if (!current.canEdit) return
        jobJson = null
        mutableState.value = current.copy(importing = true, failure = null, submissionJobId = null, job = null, local = null)
        worker.execute {
            val result = runCatching { PhotoImporter(context, "backend_reference_photos").import(uri) }
            main.post {
                if (closed || user != account) return@post
                val state = mutableState.value!!.copy(importing = false)
                result.fold({ reference ->
                    val draft = state.draft.copy(photos = state.draft.photos + reference)
                    val model = PhotoRequest.models(backend.state.value!!.data, draft).find { it.id == draft.modelId }
                        ?: PhotoRequest.models(backend.state.value!!.data, draft).firstOrNull()
                    runCatching { publish(state.copy(draft = model?.let { PhotoRequest.defaults(draft, it) } ?: draft, phase = PhotoPhase.EDITING)) }
                        .onFailure { mutableState.value = state.copy(phase = PhotoPhase.UNKNOWN, failure = BackendFailure(code = "photo_journal")) }
                }, { mutableState.value = state.copy(failure = BackendFailure(code = "photo_import")) })
            }
        }
    }
    fun removePhoto(reference: String) = edit { draft ->
        val changed = draft.copy(photos = draft.photos - reference)
        val model = PhotoRequest.models(backend.state.value!!.data, changed).find { it.id == draft.modelId }
            ?: PhotoRequest.models(backend.state.value!!.data, changed).firstOrNull()
        model?.let { PhotoRequest.defaults(changed, it) } ?: changed
    }
    /** The persisted SENDING marker is committed before the single, non-replayed POST. */
    fun submit() {
        val current = mutableState.value!!; val account = user ?: return
        val catalog = backend.state.value!!
        if (!current.canEdit || !PhotoRequest.allowed(catalog.data) || backend.source.value != DataSource.SERVER || catalog.userId != account ||
            BackendSection.MODELS !in catalog.loaded || BackendSection.MODELS in catalog.cached || catalog.authError != null) return
        val model = catalog.data.models.find { it.id == current.draft.modelId } ?: return
        if (!PhotoRequest.ready(model, current.draft)) return
        val starting = current.copy(phase = PhotoPhase.UPLOADING, failure = null, job = null, submissionJobId = null, local = null, downloadError = null)
        jobJson = null
        try { publish(starting) } catch (_: Exception) { mutableState.value = current.copy(phase = PhotoPhase.UNKNOWN, failure = BackendFailure(code = "photo_journal")); return }
        worker.execute {
            var sent = false
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
                val request = PhotoRequest.build(model, starting.draft, urls)
                journal.save(account, PhotoJournal.encode(starting.draft, PhotoPhase.SENDING))
                sent = true
                main.post { if (!closed && user == account) mutableState.value = starting.copy(phase = PhotoPhase.SENDING) }
                val response = backend.client.postOnce("/v1/media/images", request)
                val job = BackendJson.job(response)
                java.util.UUID.fromString(job.id)
                require(job.kind == "image")
                val phase = if (job.status in listOf("completed", "failed")) PhotoPhase.COMPLETE else PhotoPhase.ACTIVE
                journal.save(account, PhotoJournal.encode(starting.draft, phase, response))
                main.post {
                    if (closed || user != account) return@post
                    jobJson = response; mutableState.value = starting.copy(phase = phase, job = job, submissionJobId = job.id)
                    refreshBackend(); schedulePoll(0)
                }
            } catch (error: Exception) {
                val failure = error as? BackendFailure ?: BackendFailure(code = "invalid_response")
                val rejected = sent && failure.status in setOf(400, 401, 403, 409, 413, 422, 429, 503)
                val phase = if (sent && !rejected) PhotoPhase.UNKNOWN else PhotoPhase.REJECTED
                runCatching { journal.save(account, PhotoJournal.encode(starting.draft, phase)) }
                main.post { if (!closed && user == account) { mutableState.value = starting.copy(phase = phase, failure = failure); refreshBackend() } }
            }
        }
    }
    /** User has inspected history and explicitly accepts that another launch may be charged. */
    fun acknowledgeUnknown() {
        if (mutableState.value!!.phase != PhotoPhase.UNKNOWN) return
        try { publish(mutableState.value!!.copy(phase = PhotoPhase.EDITING, failure = null)) }
        catch (_: Exception) { mutableState.value = mutableState.value!!.copy(failure = BackendFailure(code = "photo_journal")) }
    }
    fun openJob(job: RemoteJob) {
        if (job.kind != "image") return
        if (runCatching { java.util.UUID.fromString(job.id) }.isFailure) {
            mutableState.value = mutableState.value!!.copy(failure = BackendFailure(code = "invalid_response")); return
        }
        mutableState.value = mutableState.value!!.copy(job = job, local = null, downloadError = null, downloading = false)
        schedulePoll(0)
    }
    fun refreshJob() { pollFailures = 0; schedulePoll(0) }
    private fun schedulePoll(delay: Long) {
        polling?.cancel(false); polling = null
        val current = mutableState.value!!; val account = user ?: return
        if (!visible || closed || backend.source.value != DataSource.SERVER) return
        val ids = listOfNotNull(current.submissionJobId.takeIf { current.phase == PhotoPhase.ACTIVE },
            current.job?.id.takeIf { current.job != null && current.job.status !in listOf("completed", "failed") }).distinct()
        if (ids.isEmpty()) return
        polling = worker.schedule({
            val results = ids.associateWith { id -> runCatching {
                val json = backend.client.get("/v1/media/jobs/" + id)
                val job = BackendJson.job(json); require(job.id == id && job.kind == "image")
                json to job
            } }
            main.post {
                if (closed || user != account) return@post
                var state = mutableState.value!!; var terminal = false; var hardFailure = false
                results.forEach { (id, result) -> result.fold({ (json, job) ->
                    pollFailures = 0
                    if (state.submissionJobId == id) {
                        jobJson = json
                        if (job.status in listOf("completed", "failed")) { state = state.copy(phase = PhotoPhase.COMPLETE); terminal = true }
                    }
                    if (state.job?.id == id) state = state.copy(job = job, failure = null)
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
    fun download() {
        val account = user ?: return; val current = mutableState.value!!; val job = current.job ?: return
        if (current.downloading || job.status != "completed" || job.kind != "image") return
        mutableState.value = current.copy(downloading = true, downloadError = null)
        worker.execute {
            val result = runCatching { media.load(account, job) }
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
