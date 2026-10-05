package com.rslnabk.aivideotest.data.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.core.content.edit
import androidx.core.net.toUri
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/** A single operation survives Activity recreation; IO never belongs to a Fragment view. */
class ExportController(private val context: Context) {
    private val preferences = context.getSharedPreferences("result_export_v1", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableLiveData<ExportState?>()
    val state: LiveData<ExportState?> = mutableState
    val media = ResultMedia(context)
    var failNext = false
    @Volatile private var closed = false
    init {
        val restored = runCatching {
            val json = JSONObject(preferences.getString("operation", null) ?: return@runCatching null)
            ExportState(json.getString("id"), json.getString("job"), ExportDestination.valueOf(json.getString("destination")),
                ExportPhase.valueOf(json.getString("phase")), json.optString("uri").takeIf(String::isNotEmpty)?.let(Uri::parse),
                when (json.optString("message")) {
                    "interrupted" -> R.string.export_interrupted
                    "permission" -> R.string.gallery_permission_denied
                    "missing" -> R.string.result_missing
                    "handler" -> R.string.export_handler_missing
                    "error" -> R.string.export_error_body
                    else -> null
                })
        }.getOrNull()
        // A process that died during IO cannot report success. Remove its incomplete gallery row.
        preferences.getString("pending_gallery", null)?.let { raw ->
            worker.execute {
                runCatching {
                    val uri = raw.toUri()
                    val column = if (android.os.Build.VERSION.SDK_INT >= 29) android.provider.MediaStore.MediaColumns.IS_PENDING else android.provider.MediaStore.MediaColumns.SIZE
                    context.contentResolver.query(uri,arrayOf(column),null,null,null)?.use { cursor ->
                        if (cursor.moveToFirst() && (if (android.os.Build.VERSION.SDK_INT >= 29) cursor.getInt(0) == 1 else cursor.getLong(0) == 0L)) context.contentResolver.delete(uri,null,null)
                    }
                }
                preferences.edit(commit = true) { remove("pending_gallery") }
            }
        }
        set(if (restored?.phase in listOf(ExportPhase.WRITING, ExportPhase.PERMISSION)) restored?.copy(phase = ExportPhase.FAILED, message = R.string.export_interrupted) else restored)
    }
    private fun set(value: ExportState?) {
        // Commit the small journal before publishing it: a consumed share must not replay after death.
        preferences.edit(commit = true) {
            if (value == null) remove("operation")
            else putString("operation", JSONObject().apply {
                put("id", value.id); put("job", value.jobId); put("destination", value.destination.name)
                put("phase", value.phase.name); put("uri", value.uri?.toString())
                put("message",when (value.message) {
                    R.string.export_interrupted -> "interrupted"
                    R.string.gallery_permission_denied -> "permission"
                    R.string.result_missing -> "missing"
                    R.string.export_handler_missing -> "handler"
                    R.string.export_error_body -> "error"
                    else -> null
                })
            }.toString())
        }
        mutableState.value = value
    }
    fun begin(job: GenerationJob, destination: ExportDestination, permission: Boolean = false): Boolean {
        if (job.status != JobStatus.SUCCEEDED || mutableState.value?.busy == true) return false
        val phase = when { permission -> ExportPhase.PERMISSION; destination == ExportDestination.FILES -> ExportPhase.CHOOSING; else -> ExportPhase.WRITING }
        set(ExportState(UUID.randomUUID().toString(), job.id, destination, phase))
        if (phase == ExportPhase.WRITING) write(job)
        return true
    }
    fun galleryPermission(job: GenerationJob?, granted: Boolean) {
        val value = mutableState.value ?: return
        if (value.phase != ExportPhase.PERMISSION) return
        if (!granted) set(value.copy(phase = ExportPhase.FAILED, message = R.string.gallery_permission_denied))
        else if (job == null) set(value.copy(phase = ExportPhase.FAILED, message = R.string.result_missing))
        else { set(value.copy(phase = ExportPhase.WRITING)); write(job) }
    }
    fun documentChosen(job: GenerationJob?, uri: Uri?) {
        val value = mutableState.value ?: return
        if (value.phase != ExportPhase.CHOOSING) return
        if (uri == null) { set(value.copy(phase = ExportPhase.CANCELLED)); return }
        if (job == null) { set(value.copy(phase = ExportPhase.FAILED, message = R.string.result_missing)); return }
        set(value.copy(phase = ExportPhase.WRITING, uri = uri)); write(job)
    }
    fun fail(message: Int = R.string.export_error_body) { mutableState.value?.let { set(it.copy(phase = ExportPhase.FAILED, message = message)) } }
    fun acknowledge(id: String) { if (mutableState.value?.id == id && mutableState.value?.busy != true) set(null) }
    fun claimShare(): ExportState? {
        val current = mutableState.value ?: return null
        if (current.phase != ExportPhase.SHARE_READY) return null
        // Mark consumed before launching the system sheet: rotation must not send a second intent.
        set(null); return current
    }
    private fun write(job: GenerationJob) {
        val operation = mutableState.value ?: return
        val fail = failNext; failNext = false
        worker.execute {
            val result = runCatching {
                when (operation.destination) {
                    ExportDestination.GALLERY -> media.gallery(job, { uri -> preferences.edit(commit = true) { putString("pending_gallery", uri.toString()) } }, fail)
                    ExportDestination.FILES -> { media.document(job, checkNotNull(operation.uri), fail); operation.uri }
                    ExportDestination.SHARE -> media.share(job, fail)
                }
            }
            preferences.edit(commit = true) { remove("pending_gallery") }
            handler.post {
                if (!closed && mutableState.value?.id == operation.id) set(result.fold(
                    { operation.copy(phase = if (operation.destination == ExportDestination.SHARE) ExportPhase.SHARE_READY else ExportPhase.SUCCEEDED, uri = it) },
                    { operation.copy(phase = ExportPhase.FAILED, message = R.string.export_error_body) }
                ))
            }
        }
    }
    fun reset() { failNext = false; if (mutableState.value?.busy != true) set(null) }
    fun close() { closed = true; worker.shutdown(); handler.removeCallbacksAndMessages(null) }
}
