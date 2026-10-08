package com.rslnabk.aivideotest

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.rslnabk.aivideotest.data.CatalogRepository
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.data.media.ExportController
import com.rslnabk.aivideotest.data.settings.*
import com.rslnabk.aivideotest.model.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val backend = com.rslnabk.aivideotest.data.backend.BackendController(application)
    val backendPhotos = com.rslnabk.aivideotest.data.backend.PhotoGenerationController(application, backend)
    val backendExports = ExportController(application, "backend_export_v1")
    val catalog: CatalogRepository = DemoCatalogRepository()
    val exports = ExportController(application)
    private val session = DemoSession(PreferencesDemoStore(application.getSharedPreferences("demo_state_v1", Context.MODE_PRIVATE)), catalog)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val photoOperations = mutableMapOf<String, Int>()
    private val mutableSnapshot = MutableLiveData<DemoSnapshot>()
    val snapshot: LiveData<DemoSnapshot> = mutableSnapshot
    val notifications = DemoNotifications(application)
    val notificationsAllowed = MutableLiveData(notifications.allowed())
    private val cache = PreviewCache(application.cacheDir)
    val cacheBytes = MutableLiveData(0L)
    val cacheBusy = MutableLiveData(false)
    val cacheResult = MutableLiveData<Boolean?>(null)
    var failNextCache = false
    @Volatile private var disposed = false
    var failNextPhoto = false
    var failNextGeneration = false
    var nextPurchaseOutcome = DemoPurchaseOutcome.SUCCESS
    private val tick = object : Runnable {
        override fun run() {
            session.reconcile(System.currentTimeMillis())
            if (backend.source.value == com.rslnabk.aivideotest.data.backend.DataSource.DEMO && notifications.allowed()) session.claimReadyNotifications().forEach(notifications::ready)
            publish()
            if (session.snapshot.jobs.any { it.status == JobStatus.RUNNING } || session.snapshot.commerce.operation?.phase == PurchasePhase.LOADING) handler.postDelayed(this, 250)
        }
    }
    init {
        session.snapshot.drafts.values.filter { it.photoStatus == PhotoStatus.LOADING }.forEach { draft ->
            session.updateDraft(draft.key) { it.copy(photoStatus = PhotoStatus.FAILED) }
        }
        tick.run()
    }
    private fun publish() { if (mutableSnapshot.value != session.snapshot) mutableSnapshot.value = session.snapshot }
    fun toggleFavorite(id: String) { session.toggleFavorite(id); publish() }
    fun setAccount(account: DemoAccount) { session.setAccount(account); publish() }
    fun reset() {
        photoOperations.keys.toList().forEach { photoOperations[it] = photoOperations.getValue(it) + 1 }
        handler.removeCallbacks(tick); failNextPhoto = false; failNextGeneration = false
        nextPurchaseOutcome = DemoPurchaseOutcome.SUCCESS
        failNextCache = false
        notifications.cancel(); cacheResult.value = null
        session.reset(); publish()
        exports.reset()
    }
    fun draft(key: String) = session.draft(key)
    fun cost(key: String) = session.cost(draft(key))
    fun editDraft(key: String, change: (GenerationDraft) -> GenerationDraft) { session.updateDraft(key, change); publish() }
    fun markInstructionSeen() { session.markInstructionSeen(); publish() }
    fun advanceIntro(expected: IntroStep) { session.advanceIntro(expected); publish() }
    fun finishIntroPhoto(choice: IntroPhotoChoice) { session.finishIntroPhoto(choice); publish() }
    fun backIntro() { session.backIntro(); publish() }
    fun completeIntro(): Boolean { val changed = session.completeIntro(); publish(); return changed }
    fun markIntroOfferShown() { session.markIntroOfferShown(); publish() }
    fun replayIntro() { session.replayIntro(); publish() }
    fun refreshNotifications() { notificationsAllowed.value = notifications.allowed() }
    fun markNotificationAsked() { session.markNotificationAsked(); publish() }
    fun setNotifications(enabled: Boolean) {
        val previously = session.snapshot.preferences.notificationsEnabled
        session.setNotifications(enabled); refreshNotifications(); publish()
        if (!enabled) notifications.cancel()
        if (enabled && !previously && notifications.allowed()) notifications.preview()
    }
    fun editReview(rating: Int? = null, name: String? = null, text: String? = null) { session.editReview(rating, name, text); publish() }
    fun declineRating() { session.declineRating(); publish() }
    fun saveReview(): Boolean { val accepted = session.saveReview(UUID.randomUUID().toString()); publish(); return accepted }
    fun editMessage(kind: MessageKind, text: String) { session.editMessage(kind, text); publish() }
    fun saveMessage(kind: MessageKind): Boolean { val accepted = session.saveMessage(kind, UUID.randomUUID().toString()); publish(); return accepted }
    fun refreshCache() {
        if (cacheBusy.value == true) return
        worker.execute { val bytes = runCatching(cache::bytes).getOrDefault(0); handler.post { if (!disposed) cacheBytes.value = bytes } }
    }
    fun acknowledgeCache() { cacheResult.value = null }
    fun clearCache() {
        if (cacheBusy.value == true) return
        val fail = failNextCache; failNextCache = false; cacheBusy.value = true; cacheResult.value = null
        worker.execute {
            val result = runCatching { if (fail) error("Demo cache failure"); cache.clear(); cache.bytes() }
            handler.post { if (!disposed) { cacheBytes.value = result.getOrDefault(cacheBytes.value ?: 0); cacheBusy.value = false; cacheResult.value = result.isSuccess } }
        }
    }
    fun removePhoto(key: String) {
        photoOperations[key] = (photoOperations[key] ?: 0) + 1
        editDraft(key) { it.copy(photo = null, pendingPhoto = null, photoStatus = PhotoStatus.ABSENT) }
    }
    fun loadPhoto(key: String, source: String) {
        val operation = (photoOperations[key] ?: 0) + 1; photoOperations[key] = operation
        val fail = failNextPhoto; failNextPhoto = false
        editDraft(key) { it.copy(pendingPhoto = source, photoStatus = PhotoStatus.LOADING) }
        val started = android.os.SystemClock.uptimeMillis()
        worker.execute {
            val result = runCatching {
                if (fail) error("Demo photo loading error")
                if (source.startsWith("asset:") || (source.startsWith("file:") && !source.startsWith("file:/"))) source
                else PhotoImporter(getApplication()).import(source.toUri())
            }
            handler.postDelayed({
                if (disposed || photoOperations[key] != operation) {
                    result.getOrNull()?.takeIf { it.startsWith("file:") && it != source }?.let {
                        File(getApplication<Application>().filesDir, "reference_photos/${it.removePrefix("file:")}").delete()
                    }
                    return@postDelayed
                }
                editDraft(key) { draft -> result.fold(
                    { draft.copy(photo = it, pendingPhoto = null, photoStatus = PhotoStatus.READY) },
                    { draft.copy(photoStatus = PhotoStatus.FAILED) }
                ) }
            }, (600 - (android.os.SystemClock.uptimeMillis() - started)).coerceAtLeast(0))
        }
    }
    fun retryPhoto(key: String) { draft(key).pendingPhoto?.let { loadPhoto(key, it) } }
    fun submit(key: String): SubmitResult {
        val result = session.submit(key, UUID.randomUUID().toString(), System.currentTimeMillis(), failNextGeneration)
        if (result is SubmitResult.Accepted) {
            failNextGeneration = false; publish(); handler.removeCallbacks(tick); handler.post(tick)
        }
        return result
    }
    fun retryJob(id: String): SubmitResult {
        val result = session.retryJob(id, System.currentTimeMillis(), failNextGeneration)
        if (result is SubmitResult.Accepted) {
            failNextGeneration = false; publish(); handler.removeCallbacks(tick); handler.post(tick)
        }
        return result
    }
    fun deleteJob(id: String): Boolean {
        if (exports.state.value?.let { it.jobId == id && it.busy } == true) return false
        val removed = session.deleteJob(id); publish(); return removed
    }
    fun purchase(product: DemoProduct, continuation: CreationIntent? = null) = beginPurchase(product, continuation, PurchaseAction.BUY)
    fun restorePurchases(continuation: CreationIntent? = null) = beginPurchase(null, continuation, PurchaseAction.RESTORE)
    private fun beginPurchase(product: DemoProduct?, continuation: CreationIntent?, action: PurchaseAction): Boolean {
        val accepted = session.beginPurchase(UUID.randomUUID().toString(), product, System.currentTimeMillis(), nextPurchaseOutcome, continuation, action)
        if (accepted) { nextPurchaseOutcome = DemoPurchaseOutcome.SUCCESS; schedulePurchase() }
        return accepted
    }
    private fun schedulePurchase() { publish(); handler.removeCallbacks(tick); handler.post(tick) }
    fun cancelPurchase(id: String) { if (session.cancelPurchase(id)) publish() }
    fun acknowledgePurchase(id: String) { if (session.acknowledgePurchase(id)) publish() }
    fun retryPurchase(id: String) {
        if (session.retryPurchase(id, UUID.randomUUID().toString(), System.currentTimeMillis(), nextPurchaseOutcome)) {
            nextPurchaseOutcome = DemoPurchaseOutcome.SUCCESS; schedulePurchase()
        }
    }
    fun resumePurchase(id: String): SubmitResult? {
        val result = session.resumePurchase(id, UUID.randomUUID().toString(), System.currentTimeMillis(), failNextGeneration)
        if (result is SubmitResult.Accepted) { failNextGeneration = false; schedulePurchase() } else publish()
        return result
    }
    override fun onCleared() { disposed = true; backendPhotos.close(); backendExports.close(); backend.close(); exports.close(); handler.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onCleared() }
}
