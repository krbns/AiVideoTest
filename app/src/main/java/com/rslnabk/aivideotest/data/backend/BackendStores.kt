package com.rslnabk.aivideotest.data.backend

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Installation identity and the entire token pair are one encrypted, committed record. */
class EncryptedBackendAuthStore(context: Context, prefsName: String = "backend_auth_v1") : BackendAuthStore {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val alias = "aivideotest_backend_auth_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) return store.getKey(alias, null) as SecretKey
        // A restored ciphertext without its installation key must not create a new account silently.
        if (prefs.contains("ciphertext")) throw BackendFailure(code = "session_storage")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun read(): JSONObject {
        val encrypted = prefs.getString("ciphertext", null) ?: return JSONObject().put("deviceId", UUID.randomUUID().toString()).also(::write)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", null), Base64.NO_WRAP)))
        return JSONObject(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8))
    }
    private fun write(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        if (!prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) throw BackendFailure(code = "session_storage")
    }
    @Synchronized override fun deviceId(): String = protected { read().getString("deviceId") }
    @Synchronized override fun load(): AuthSession? = protected {
        val value = read(); if (!value.has("accessToken")) null else AuthSession(value.getString("userId"), value.getString("deviceId"),
            value.getString("accessToken"), value.getString("refreshToken"), value.getLong("expiresAt"), value.getLong("refreshExpiresAt"), value.optBoolean("refreshPending"))
    }
    @Synchronized override fun save(session: AuthSession) = protected {
        write(JSONObject().put("userId", session.userId).put("deviceId", session.deviceId).put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken).put("expiresAt", session.expiresAt).put("refreshExpiresAt", session.refreshExpiresAt).put("refreshPending", session.refreshPending))
    }
    private inline fun <T> protected(block: () -> T): T = try { block() } catch (_: Exception) { throw BackendFailure(code = "session_storage") }
}
class BackendReadCache(context: Context) {
    private val root = File(context.cacheDir, "backend_read")
    private fun file(user: String): AtomicFile { UUID.fromString(user); return AtomicFile(File(root, user + ".json")) }
    fun read(user: String): JSONObject = runCatching { file(user).openRead().use { JSONObject(it.readBytesLimited(8 * 1024 * 1024).toString(Charsets.UTF_8)) } }.getOrDefault(JSONObject())
    fun write(user: String, section: BackendSection, value: JSONObject) {
        root.mkdirs(); val record = read(user).put(section.name, value)
        val target = file(user); val stream = target.startWrite()
        try { stream.write(record.toString().toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
        catch (error: Exception) { target.failWrite(stream); throw error }
    }
}
