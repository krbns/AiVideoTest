package com.rslnabk.aivideotest.data.backend

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Phase B1: only auth POSTs and documented read endpoints; demo remains independent. */
class BackendController(context: Context, transport: BackendTransport = HttpsBackendTransport(),
    authStore: BackendAuthStore = EncryptedBackendAuthStore(context)) {
    private val prefs = context.getSharedPreferences("backend_source_v1", Context.MODE_PRIVATE)
    private val mutableSource = MutableLiveData(if (prefs.getString("source", "DEMO") == "SERVER") DataSource.SERVER else DataSource.DEMO)
    val source: LiveData<DataSource> = mutableSource
    private val mutableState = MutableLiveData(BackendState())
    val state: LiveData<BackendState> = mutableState
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val client = BackendClient(transport, authStore)
    private val cache = BackendReadCache(context)
    @Volatile private var epoch = 0
    @Volatile private var closed = false
    init { if (mutableSource.value == DataSource.SERVER) connect() }
    fun useDemo() {
        epoch++; mutableSource.value = DataSource.DEMO
        mutableState.value = mutableState.value!!.copy(connecting = false, loading = emptySet(), loadingMore = false)
        prefs.edit().putString("source", "DEMO").apply()
    }
    fun connect(reconnect: Boolean = false) {
        if (mutableState.value!!.connecting || mutableState.value!!.loading.isNotEmpty()) return
        mutableSource.value = DataSource.SERVER; prefs.edit().putString("source", "SERVER").apply()
        val cycle = ++epoch
        mutableState.value = mutableState.value!!.copy(connecting = true, authError = null)
        worker.execute {
            try {
                val session = client.initialize(reconnect)
                var data = BackendData(); val loaded = mutableSetOf<BackendSection>()
                val record = cache.read(session.userId)
                BackendSection.entries.forEach { section -> record.optJSONObject(section.name)?.let { value ->
                    runCatching { BackendJson.update(data, section, value) }.onSuccess { data = it; loaded.add(section) }
                } }
                val favorites = prefs.getStringSet("favorites_" + session.userId, emptySet())!!.toSet()
                val initial = BackendState(userId = session.userId, data = data, loading = BackendSection.entries.toSet(),
                    loaded = loaded.toSet(), cached = loaded.toSet(), favorites = favorites)
                post(cycle) { initial }
                val order = listOf(BackendSection.POLICY, BackendSection.WALLET, BackendSection.PROFILE,
                    BackendSection.VIDEOS, BackendSection.PHOTOS, BackendSection.MODELS, BackendSection.JOBS, BackendSection.PRODUCTS)
                for (section in order) {
                    if (closed || epoch != cycle) break
                    try {
                        val value = client.get(section.path)
                        data = BackendJson.update(data, section, value)
                        // Cache is optional; a disk/cache failure must not discard valid network data.
                        runCatching { cache.write(session.userId, section, value) }
                        val updated = data
                        post(cycle) { it.copy(data = updated, loading = it.loading - section, loaded = it.loaded + section,
                            cached = it.cached - section, errors = it.errors - section) }
                    } catch (failure: BackendFailure) {
                        post(cycle) { it.copy(loading = it.loading - section, errors = it.errors + (section to failure)) }
                        if (failure.status == 401 || failure.code == "session_storage") throw failure
                    }
                }
            } catch (failure: Exception) {
                val safe = failure as? BackendFailure ?: BackendFailure()
                post(cycle) { it.copy(connecting = false, loading = emptySet(), authError = safe) }
            }
        }
    }
    fun toggleFavorite(id: String) {
        val current = mutableState.value!!; val user = current.userId ?: return
        if ((current.data.photos + current.data.videos).none { it.id == id }) return
        val favorites = current.favorites.toMutableSet().apply { if (!add(id)) remove(id) }.toSet()
        prefs.edit().putStringSet("favorites_" + user, favorites).apply()
        mutableState.value = current.copy(favorites = favorites)
    }
    fun loadMore() {
        val current = mutableState.value!!
        if (current.loadingMore || current.loading.isNotEmpty() || current.authError != null) return
        val cursor = current.data.nextCursor ?: return
        val cycle = epoch; mutableState.value = current.copy(loadingMore = true, historyError = null)
        worker.execute {
            try {
                val value = client.get(BackendSection.JOBS.path + "&cursor=" + URLEncoder.encode(cursor, "UTF-8"))
                val page = BackendJson.update(BackendData(), BackendSection.JOBS, value)
                post(cycle) { it.copy(loadingMore = false, data = it.data.copy(
                    jobs = (it.data.jobs + page.jobs).distinctBy { job -> job.id }, nextCursor = page.nextCursor)) }
            } catch (failure: Exception) {
                post(cycle) { it.copy(loadingMore = false, historyError = failure as? BackendFailure ?: BackendFailure()) }
            }
        }
    }
    private fun post(cycle: Int, change: (BackendState) -> BackendState) = main.post {
        if (!closed && epoch == cycle && mutableSource.value == DataSource.SERVER) mutableState.value = change(mutableState.value!!)
    }
    fun close() { closed = true; epoch++; worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
