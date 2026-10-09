package com.rslnabk.aivideotest.data.backend

import org.json.JSONObject

interface BackendAuthStore {
    fun deviceId(): String
    fun load(): AuthSession?
    fun save(session: AuthSession)
}
/** All access/refresh decisions share one lock, including concurrent 401 recovery. */
class BackendClient(private val transport: BackendTransport, private val store: BackendAuthStore,
    private val now: () -> Long = System::currentTimeMillis) {
    private val authLock = Any()
    fun cachedSession(): AuthSession? = synchronized(authLock) { store.load() }
    fun initialize(reconnect: Boolean = false): AuthSession = synchronized(authLock) {
        val session = store.load()
        if (reconnect || session == null) {
            val path = if (session == null) "/v1/auth/register" else "/v1/auth/token"
            val fresh = BackendJson.session(BackendJson.response(transport.request("POST", path, null,
                JSONObject().put("deviceId", store.deviceId()).toString())), now())
            if (fresh.deviceId != store.deviceId()) throw BackendFailure(code = "identity_mismatch")
            if (session != null && fresh.userId != session.userId) throw BackendFailure(code = "identity_mismatch")
            store.save(fresh); fresh
        } else validSession(session)
    }
    private fun validSession(session: AuthSession): AuthSession {
        if (session.refreshPending) throw BackendFailure(401, "refresh_uncertain")
        if (session.expiresAt > now() + 30000) return session
        if (session.refreshExpiresAt <= now()) throw BackendFailure(401, "session_expired")
        // Commit before the exchange: a timeout or process death must not replay a rotated token.
        store.save(session.copy(refreshPending = true))
        val fresh = try { BackendJson.session(BackendJson.response(transport.request("POST", "/v1/auth/refresh", null,
            JSONObject().put("refreshToken", session.refreshToken).toString())), now()) }
        catch (failure: BackendFailure) {
            if (failure.status == 0) throw BackendFailure(401, "refresh_uncertain")
            throw failure
        }
        if (fresh.userId != session.userId || fresh.deviceId != session.deviceId) throw BackendFailure(code = "identity_mismatch")
        // Persist the whole new pair before any caller receives the new access token.
        store.save(fresh); return fresh
    }
    fun get(path: String): JSONObject {
        var session = synchronized(authLock) { validSession(store.load() ?: throw BackendFailure(401, "session_required")) }
        var response = transport.request("GET", path, session.accessToken, null)
        if (response.status == 401) {
            session = synchronized(authLock) {
                val current = store.load() ?: throw BackendFailure(401, "session_required")
                if (current.accessToken != session.accessToken) validSession(current)
                else validSession(current.copy(expiresAt = 0))
            }
            // Only read requests are retried. No generation or payment request is sent here.
            response = transport.request("GET", path, session.accessToken, null)
        }
        return BackendJson.response(response)
    }
    /** Mutations are sent once. A 401/timeout never replays this request. */
    fun postOnce(path: String, body: JSONObject): JSONObject {
        val session = synchronized(authLock) { validSession(store.load() ?: throw BackendFailure(401, "session_required")) }
        return BackendJson.response(transport.request("POST", path, session.accessToken, body.toString()))
    }
    fun deleteOnce(path: String): JSONObject {
        val session = synchronized(authLock) { validSession(store.load() ?: throw BackendFailure(401, "session_required")) }
        return BackendJson.response(transport.request("DELETE", path, session.accessToken, null))
    }
}
