package com.rslnabk.aivideotest.data.backend

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.rslnabk.aivideotest.BuildConfig
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Server is the normal entry; debug can explicitly select the independent demo. */
class BackendController(context: Context, transport: BackendTransport = HttpsBackendTransport(),
    authStore: BackendAuthStore = EncryptedBackendAuthStore(context), private val allowDemo: Boolean = BuildConfig.DEBUG,
    prefsName: String = "backend_source_v1", private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val mutableSource = MutableLiveData(if (allowDemo && prefs.getString("source", null) == "DEMO") DataSource.DEMO else DataSource.SERVER)
    val source: LiveData<DataSource> = mutableSource
    private val mutableState = MutableLiveData(BackendState())
    val state: LiveData<BackendState> = mutableState
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    internal val client = BackendClient(transport, authStore)
    private val cache = BackendReadCache(context)
    @Volatile private var epoch = 0
    @Volatile private var closed = false
    private var lastAttempt = 0L
    init { if (mutableSource.value == DataSource.SERVER) connect() }
    fun useDemo() {
        if (!allowDemo) return
        epoch++; mutableSource.value = DataSource.DEMO
        mutableState.value = mutableState.value!!.copy(connecting = false, loading = emptySet(), loadingMore = false)
        prefs.edit().putString("source", "DEMO").apply()
    }
    fun connect(reconnect: Boolean = false) {
        if (closed || mutableState.value!!.connecting || mutableState.value!!.loading.isNotEmpty()) return
        lastAttempt = now()
        mutableSource.value = DataSource.SERVER; prefs.edit().putString("source", "SERVER").apply()
        val cycle = ++epoch
        mutableState.value = mutableState.value!!.copy(connecting = true, authError = null)
        worker.execute {
            try {
                // Identity comes only from encrypted credentials, never from an arbitrary cache file.
                val stored = client.cachedSession()
                stored?.let { session ->
                    val saved = cachedState(session.userId).copy(connecting = true, loading = BackendSection.entries.toSet())
                    post(cycle) { saved }
                }
                val session = client.initialize(reconnect)
                val initial = cachedState(session.userId).copy(loading = BackendSection.entries.toSet())
                var data = initial.data
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
    private fun cachedState(user: String): BackendState {
        var data = BackendData(); val loaded = mutableSetOf<BackendSection>()
        val record = cache.read(user)
        BackendSection.entries.forEach { section -> record.optJSONObject(section.name)?.let { value ->
            runCatching { BackendJson.update(data, section, value) }.onSuccess { data = it; loaded.add(section) }
        } }
        return BackendState(userId = user, data = data, loaded = loaded, cached = loaded,
            favorites = prefs.getStringSet("favorites_" + user, emptySet())!!.toSet())
    }
    /** Foreground reads recover connectivity and revalidate wallet/policy without replaying creations. */
    fun onForeground() {
        if (source.value != DataSource.SERVER || now() - lastAttempt < 15000) return
        val current = state.value!!
        if (current.authError != null || current.errors.isNotEmpty() || now() - lastAttempt >= 60000) connect()
    }
    fun toggleFavorite(id: String) {
        val current = mutableState.value!!; val user = current.userId ?: return
        if ((current.data.photos + current.data.videos).none { it.id == id }) return
        val favorites = current.favorites.toMutableSet().apply { if (!add(id)) remove(id) }.toSet()
        prefs.edit().putStringSet("favorites_" + user, favorites).apply()
        mutableState.value = current.copy(favorites = favorites)
    }
    fun refreshAfterMutation() {
        if (closed || mutableSource.value != DataSource.SERVER) return
        mutableState.value = mutableState.value!!.copy(cached = mutableState.value!!.cached +
            setOf(BackendSection.WALLET, BackendSection.POLICY, BackendSection.JOBS))
        if (mutableState.value!!.connecting || mutableState.value!!.loading.isNotEmpty())
            main.postDelayed({ refreshAfterMutation() }, 1000)
        else connect()
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
