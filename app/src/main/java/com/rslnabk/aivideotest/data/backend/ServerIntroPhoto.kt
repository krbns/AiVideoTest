package com.rslnabk.aivideotest.data.backend

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.rslnabk.aivideotest.data.demo.PhotoImporter
import com.rslnabk.aivideotest.model.IntroPhotoChoice
import java.io.File
import java.util.concurrent.Executors

data class ServerIntroPhotoState(val reference: String? = null, val owner: String? = null,
    val choice: IntroPhotoChoice = IntroPhotoChoice.NONE, val importing: Boolean = false, val failed: Boolean = false,
    val advancePending: Boolean = false)

/** Local handoff survives a cold offline launch; no photo is uploaded by onboarding. */
class ServerIntroPhoto(private val context: Context, prefsName: String = "server_intro_photo_v1") {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val mutableState = MutableLiveData(ServerIntroPhotoState(prefs.getString("reference", null), prefs.getString("owner", null),
        runCatching { IntroPhotoChoice.valueOf(prefs.getString("choice", "NONE")!!) }.getOrDefault(IntroPhotoChoice.NONE),
        advancePending = prefs.getBoolean("advance_pending", false)))
    val state: LiveData<ServerIntroPhotoState> = mutableState
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    fun import(uri: Uri, owner: String?, choice: IntroPhotoChoice, completed: () -> Unit) {
        val current = mutableState.value!!
        if (closed || current.importing) return
        mutableState.value = current.copy(importing = true, failed = false)
        worker.execute {
            val result = runCatching {
                val reference = PhotoImporter(context, "backend_reference_photos").import(uri)
                try {
                    check(prefs.edit().putString("reference", reference).putString("owner", owner).putString("choice", choice.name).putBoolean("advance_pending", true).commit())
                    reference
                } catch (failure: Exception) {
                    File(context.filesDir, "backend_reference_photos/${reference.removePrefix("file:")}").delete()
                    throw failure
                }
            }
            main.post {
                if (!closed) result.fold({ reference ->
                    mutableState.value = ServerIntroPhotoState(reference, owner, choice, advancePending = true)
                    completed()
                }, { mutableState.value = current.copy(failed = true) })
            }
        }
    }

    /** Commit ownership before touching the account journal, then clear only after durable acceptance. */
    fun deliver(account: String, accept: (String) -> Boolean) {
        val current = mutableState.value!!
        val reference = current.reference ?: return
        if (current.importing || current.owner != null && current.owner != account) return
        if (current.owner == null) {
            if (!prefs.edit().putString("owner", account).commit()) return
            mutableState.value = current.copy(owner = account)
        }
        if (accept(reference) && prefs.edit().remove("reference").remove("owner").remove("choice").remove("advance_pending").commit())
            mutableState.value = ServerIntroPhotoState()
    }

    fun markAdvanced() {
        if (prefs.edit().remove("advance_pending").commit()) mutableState.value = mutableState.value!!.copy(advancePending = false)
    }

    fun discard(): Boolean {
        val current = mutableState.value!!
        if (current.importing) return false
        if (!prefs.edit().clear().commit()) { mutableState.value = current.copy(failed = true); return false }
        current.reference?.takeIf { it.startsWith("file:") && File(it.removePrefix("file:")).name == it.removePrefix("file:") }?.let {
            File(context.filesDir, "backend_reference_photos/${it.removePrefix("file:")}").delete()
        }
        mutableState.value = ServerIntroPhotoState()
        return true
    }

    fun acknowledgeError() { mutableState.value = mutableState.value!!.copy(failed = false) }
    fun close() { closed = true; worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
