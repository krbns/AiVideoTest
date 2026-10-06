package com.rslnabk.aivideotest

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.navigation.*
import com.rslnabk.aivideotest.ui.theme.AiVideoTheme
import java.io.File
import java.util.UUID

/** Only Android platform integrations live here; all application UI is Compose. */
class MainActivity : ComponentActivity() {
    lateinit var navigator: AppNavigator
        private set
    val model get() = ViewModelProvider(this)[AppViewModel::class.java]
    private var pendingPhotoKey: String? = null
    private var cameraFile: String? = null
    private val document = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val job = model.snapshot.value!!.jobs.find { it.id == model.exports.state.value?.jobId }
        model.exports.documentChosen(job, if (result.resultCode == RESULT_OK) result.data?.data else null)
    }
    private val storage = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.exports.galleryPermission(model.snapshot.value!!.jobs.find { it.id == model.exports.state.value?.jobId }, granted)
    }
    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = pendingPhotoKey; pendingPhotoKey = null
        if (uri != null && key != null) model.loadPhoto(key, uri.toString())
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val key = pendingPhotoKey; val path = cameraFile; pendingPhotoKey = null; cameraFile = null
        if (success && key != null && path != null)
            model.loadPhoto(key, FileProvider.getUriForFile(this, "$packageName.fileprovider", File(path)).toString())
        else path?.let { File(it).delete() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
        }
        pendingPhotoKey = savedInstanceState?.getString("pending_photo_key")
        cameraFile = savedInstanceState?.getString("camera_file")
        if (savedInstanceState == null && model.exports.state.value?.phase == ExportPhase.CHOOSING)
            model.exports.documentChosen(null, null)
        setContent { AiVideoTheme { AiVideoApp(this, model) { navigator = it } } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_photo_key", pendingPhotoKey); outState.putString("camera_file", cameraFile)
        super.onSaveInstanceState(outState)
    }
    fun selectTab(tab: AppTab) = navigator.selectTab(tab)
    fun openCategory(kind: MediaKind, category: Category) = navigator.openCategory(kind, category)
    fun openEffect(id: String) = navigator.openEffect(id)
    fun openGenerator(key: String) = navigator.openGenerator(key)
    fun openLibrary(kind: MediaKind) = navigator.openLibrary(kind)
    fun openJob(id: String) {
        val job = model.snapshot.value!!.jobs.find { it.id == id } ?: return
        navigator.openJob(job)
    }
    fun generate(key: String) = handleSubmit(model.submit(key), "generate", key)
    fun retryJob(id: String) = handleSubmit(model.retryJob(id), "retry", id)
    private fun handleSubmit(result: SubmitResult, action: String, key: String) {
        when (result) {
            is SubmitResult.Accepted -> openJob(result.jobId)
            is SubmitResult.InsufficientBalance -> navigator.openOffers(OfferKind.TOKENS, CreationIntent(if (action == "retry") CreationAction.RETRY else CreationAction.GENERATE, key))
            SubmitResult.InvalidDraft -> Unit
        }
    }
    fun finishPurchase(id: String) {
        val operation = model.snapshot.value!!.commerce.operation?.takeIf { it.id == id && it.phase == PurchasePhase.SUCCEEDED } ?: return
        val continuation = operation.continuation
        if (continuation == null) { model.acknowledgePurchase(id); navigator.closeOffers(); return }
        val restoreOrigin = navigator.nav.currentDestination?.route?.startsWith("offers/") != true
        val result = model.resumePurchase(id) ?: return
        navigator.closeOffers()
        if (restoreOrigin) {
            // A cold process has the durable continuation but no previous navigation stack.
            if (continuation.action == CreationAction.RETRY) {
                model.snapshot.value!!.jobs.find { it.id == continuation.target }?.let { navigator.openLibrary(it.draft.kind) }
            } else {
                val draft = model.draft(continuation.target)
                if (draft.effectId == null) navigator.openPrompt(draft.kind)
                else { navigator.selectTab(if (draft.kind == MediaKind.VIDEO) AppTab.VIDEO else AppTab.PHOTO); navigator.openGenerator(continuation.target) }
            }
        }
        handleSubmit(result, if (continuation.action == CreationAction.RETRY) "retry" else "generate", continuation.target)
    }
    fun requestPhoto(key: String) = navigator.show(if (model.snapshot.value!!.instructionSeen) "source" else "instruction", key)
    fun confirmDelete(id: String) = navigator.show("delete", id)
    fun showFailedActions(id: String) = navigator.show("failed", id)
    fun deleteGeneration(id: String) {
        val job = model.snapshot.value!!.jobs.find { it.id == id } ?: return
        if (model.deleteJob(id)) {
            if (navigator.currentJobId == id) openLibrary(job.draft.kind)
            toast(R.string.generation_deleted)
        } else toast(R.string.action_busy)
    }
    fun exportResult(id: String, destination: ExportDestination) {
        val job = model.snapshot.value!!.jobs.find { it.id == id } ?: return
        val permission = destination == ExportDestination.GALLERY && Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (!model.exports.begin(job, destination, permission)) return
        try {
            if (permission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else if (destination == ExportDestination.FILES) document.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = model.exports.media.mime(job)
                putExtra(Intent.EXTRA_TITLE, model.exports.media.name(job))
            })
        } catch (_: ActivityNotFoundException) { model.exports.fail(R.string.export_handler_missing) }
    }
    fun shareReady() {
        val operation = model.exports.claimShare() ?: return
        val job = model.snapshot.value!!.jobs.find { it.id == operation.jobId } ?: return
        try { startActivity(Intent.createChooser(model.exports.media.shareIntent(job, operation.uri!!), getString(R.string.share))) }
        catch (_: ActivityNotFoundException) { toast(R.string.export_handler_missing) }
    }
    fun pickGallery(key: String) {
        pendingPhotoKey = key
        try { gallery.launch("image/*") } catch (_: ActivityNotFoundException) { pendingPhotoKey = null; toast(R.string.camera_unavailable) }
    }
    fun takePhoto(key: String) {
        pendingPhotoKey = key
        val file = File(File(cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        cameraFile = file.path
        try { camera.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", file)) }
        catch (_: ActivityNotFoundException) { file.delete(); pendingPhotoKey = null; cameraFile = null; toast(R.string.camera_unavailable) }
    }
    fun toast(message: Int) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}
