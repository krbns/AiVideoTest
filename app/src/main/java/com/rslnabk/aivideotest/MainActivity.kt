package com.rslnabk.aivideotest

import android.os.Bundle
import android.net.Uri
import android.content.ActivityNotFoundException
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.util.UUID
import com.rslnabk.aivideotest.ui.generator.*
import com.rslnabk.aivideotest.ui.result.*
import com.rslnabk.aivideotest.ui.library.*
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rslnabk.aivideotest.databinding.ActivityMainBinding
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.catalog.BrowserFragment
import com.rslnabk.aivideotest.ui.effect.EffectFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var selectedTab = AppTab.VIDEO
    private var imeVisible = false
    private var pendingPhotoKey: String? = null
    private var cameraFile: String? = null
    private val model get() = ViewModelProvider(this)[AppViewModel::class.java]
    private val document = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val job = model.snapshot.value!!.jobs.find { it.id == model.exports.state.value?.jobId }
        model.exports.documentChosen(job, if (result.resultCode == RESULT_OK) result.data?.data else null)
    }
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.exports.galleryPermission(model.snapshot.value!!.jobs.find { it.id == model.exports.state.value?.jobId },granted)
    }
    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = pendingPhotoKey; pendingPhotoKey = null
        if (uri != null && key != null) model.loadPhoto(key, uri.toString())
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val key = pendingPhotoKey; val path = cameraFile; pendingPhotoKey = null; cameraFile = null
        if (success && key != null && path != null) model.loadPhoto(key, FileProvider.getUriForFile(this, "$packageName.fileprovider", File(path)).toString())
        else path?.let { File(it).delete() }
    }
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStackImmediate()
            else selectTab(AppTab.VIDEO)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            updateNavigation()
            WindowInsetsCompat.CONSUMED
        }
        pendingPhotoKey = savedInstanceState?.getString("pending_photo_key")
        cameraFile = savedInstanceState?.getString("camera_file")
        selectedTab = savedInstanceState?.getString("tab")?.let { AppTab.valueOf(it) } ?: AppTab.VIDEO
        binding.navigation.selectedItemId = selectedTab.menuId
        binding.navigation.setOnItemSelectedListener { item ->
            selectTab(AppTab.entries.first { it.menuId == item.itemId }); true
        }
        onBackPressedDispatcher.addCallback(this, back)
        supportFragmentManager.addOnBackStackChangedListener { updateNavigation() }
        if (savedInstanceState == null) {
            selectTab(selectedTab)
            // A cold launch has no saved Activity Result request to deliver this choice.
            if (model.exports.state.value?.phase == ExportPhase.CHOOSING) model.exports.documentChosen(null,null)
        }
        model.exports.state.observe(this) { showExportStatus() }
        updateNavigation()
    }
    override fun onResumeFragments() {
        super.onResumeFragments()
        binding.root.post { showExportStatus() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", selectedTab.name)
        outState.putString("pending_photo_key", pendingPhotoKey)
        outState.putString("camera_file", cameraFile)
        super.onSaveInstanceState(outState)
    }
    fun selectTab(tab: AppTab) {
        hideKeyboard()
        supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val tag = "root_${tab.name}"
        val target = supportFragmentManager.findFragmentByTag(tag) ?: BrowserFragment.root(tab)
        supportFragmentManager.beginTransaction().apply {
            supportFragmentManager.fragments.filter { it.isAdded && it !== target }.forEach { hide(it) }
            if (target.isAdded) show(target) else add(R.id.content, target, tag)
        }.commitNow()
        selectedTab = tab
        binding.navigation.menu.findItem(tab.menuId).isChecked = true
        updateNavigation()
    }
    fun openCategory(kind: MediaKind, category: Category) = push(BrowserFragment.category(kind, category))
    fun openEffect(id: String) = push(EffectFragment.newInstance(id))
    fun openLibrary(kind: MediaKind) {
        selectTab(AppTab.LIBRARY)
        (supportFragmentManager.findFragmentByTag("root_LIBRARY") as? BrowserFragment)?.showMediaKind(kind)
    }
    fun openGenerator(key: String) = push(GeneratorFragment.newInstance(key))
    fun openJob(id: String) {
        val job = model.snapshot.value!!.jobs.find { it.id == id } ?: return
        if (job.status == JobStatus.SUCCEEDED) push(ResultFragment.newInstance(id)) else push(CreatingFragment.newInstance(id))
    }
    fun showFailedActions(id: String) {
        supportFragmentManager.executePendingTransactions()
        if (supportFragmentManager.findFragmentByTag("creation_actions") == null)
            CreationActionsDialog.newInstance(id).show(supportFragmentManager,"creation_actions")
    }
    fun confirmDelete(id: String) {
        supportFragmentManager.executePendingTransactions()
        if (supportFragmentManager.findFragmentByTag("delete_generation") == null)
            DeleteGenerationDialog.newInstance(id).show(supportFragmentManager,"delete_generation")
    }
    fun deleteGeneration(id: String) {
        val job = model.snapshot.value!!.jobs.find { it.id == id } ?: return
        if (model.deleteJob(id)) {
            val result = supportFragmentManager.fragments.filterIsInstance<ResultFragment>().any { !it.isHidden && it.arguments?.getString("job") == id }
            if (result) openLibrary(job.draft.kind)
            Toast.makeText(this,R.string.generation_deleted,Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this,R.string.action_busy,Toast.LENGTH_SHORT).show()
    }
    fun retryJob(id: String, replaceCreating: Boolean = false) {
        when (val result = model.retryJob(id)) {
            is SubmitResult.Accepted -> { if (replaceCreating) supportFragmentManager.popBackStackImmediate(); openJob(id) }
            is SubmitResult.InsufficientBalance -> MaterialAlertDialogBuilder(this).setTitle(R.string.insufficient_title)
                .setMessage(resources.getQuantityString(R.plurals.insufficient_body,result.required,result.required))
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.demo_credits) { _,_ -> model.addDemoCredits(); retryJob(id,replaceCreating) }.show()
            SubmitResult.InvalidDraft -> Unit
        }
    }
    fun exportResult(id: String, destination: ExportDestination) {
        val job = model.snapshot.value!!.jobs.find { it.id == id } ?: return
        val permission = destination == ExportDestination.GALLERY && Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (!model.exports.begin(job,destination,permission)) return
        try {
            if (permission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else if (destination == ExportDestination.FILES) document.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = model.exports.media.mime(job)
                putExtra(Intent.EXTRA_TITLE,model.exports.media.name(job))
            })
        } catch (_: ActivityNotFoundException) { model.exports.fail(R.string.export_handler_missing) }
    }
    private fun showExportStatus() {
        if (supportFragmentManager.isStateSaved || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        val value = model.exports.state.value ?: return
        when (value.phase) {
            ExportPhase.SHARE_READY -> {
                val operation = model.exports.claimShare() ?: return
                val job = model.snapshot.value!!.jobs.find { it.id == operation.jobId } ?: return
                try { startActivity(Intent.createChooser(model.exports.media.shareIntent(job,operation.uri!!),getString(R.string.share))) }
                catch (_: ActivityNotFoundException) { Toast.makeText(this,R.string.export_handler_missing,Toast.LENGTH_LONG).show() }
            }
            ExportPhase.SUCCEEDED, ExportPhase.FAILED -> {
                supportFragmentManager.executePendingTransactions()
                if (supportFragmentManager.findFragmentByTag("export_status") == null) ExportStatusDialog.newInstance(value).show(supportFragmentManager,"export_status")
            }
            ExportPhase.CANCELLED -> { model.exports.acknowledge(value.id); Toast.makeText(this,R.string.export_cancelled,Toast.LENGTH_SHORT).show() }
            else -> Unit
        }
    }
    fun showReadyResult(id: String) {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.popBackStackImmediate()
        push(ResultFragment.newInstance(id))
    }
    fun generate(key: String, replaceCreating: Boolean = false) {
        hideKeyboard()
        when (val result = model.submit(key)) {
            is SubmitResult.Accepted -> {
                if (replaceCreating) supportFragmentManager.popBackStackImmediate()
                push(CreatingFragment.newInstance(result.jobId))
            }
            is SubmitResult.InsufficientBalance -> MaterialAlertDialogBuilder(this).setTitle(R.string.insufficient_title)
                .setMessage(resources.getQuantityString(R.plurals.insufficient_body, result.required, result.required)).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.demo_credits) { _, _ -> model.addDemoCredits(); generate(key, replaceCreating) }.show()
            SubmitResult.InvalidDraft -> Unit
        }
    }
    fun requestPhoto(key: String) {
        hideKeyboard()
        supportFragmentManager.executePendingTransactions()
        if (supportFragmentManager.findFragmentByTag("photo_dialog") != null) return
        PhotoDialog.newInstance(key, if (model.snapshot.value!!.instructionSeen) "source" else "instruction").show(supportFragmentManager, "photo_dialog")
    }
    fun showPhotoSource(key: String) { PhotoDialog.newInstance(key, "source").show(supportFragmentManager,"photo_dialog") }
    fun pickGallery(key: String) {
        pendingPhotoKey = key
        try { gallery.launch("image/*") } catch (_: ActivityNotFoundException) {
            pendingPhotoKey = null; Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_LONG).show()
        }
    }
    fun takePhoto(key: String) {
        pendingPhotoKey = key
        val file = File(File(cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        cameraFile = file.path
        try { camera.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", file)) }
        catch (_: ActivityNotFoundException) {
            file.delete(); pendingPhotoKey = null; cameraFile = null
            Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_LONG).show()
        }
    }
    private fun hideKeyboard() { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.root.windowToken, 0) }
    private fun push(fragment: Fragment) {
        supportFragmentManager.executePendingTransactions()
        supportFragmentManager.beginTransaction().apply {
            supportFragmentManager.fragments.filter { it.isAdded && !it.isHidden }.forEach { hide(it) }
            add(R.id.content, fragment)
            addToBackStack(null)
        }.commit()
    }
    private fun updateNavigation() {
        val nested = supportFragmentManager.backStackEntryCount > 0
        binding.navigation.visibility = if (nested || imeVisible) View.GONE else View.VISIBLE
        back.isEnabled = nested || selectedTab != AppTab.VIDEO
    }
}
