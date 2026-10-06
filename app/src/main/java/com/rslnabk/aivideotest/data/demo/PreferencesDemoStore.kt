package com.rslnabk.aivideotest.data.demo

import android.content.SharedPreferences
import androidx.core.content.edit
import com.rslnabk.aivideotest.data.DemoStore
import com.rslnabk.aivideotest.model.*

class PreferencesDemoStore(private val preferences: SharedPreferences) : DemoStore {
    override fun load() = GenerationJson.decode(preferences.getString("generation_v1", null), DemoSnapshot(
        preferences.getStringSet("favorites", emptySet())!!.toSet(),
        DemoAccount(preferences.getInt("tokens", 5).coerceAtLeast(0), preferences.getBoolean("pro", false),
            preferences.getString("plan", null)?.let { runCatching { SubscriptionPlan.valueOf(it) }.getOrNull() }),
        commerce = CommerceJson.decode(preferences.getString("commerce_v1", null)),
        preferences = PreferencesJson.decode(preferences.getString("preferences_v1", null),
            legacy = preferences.contains("tokens") || preferences.contains("generation_v1") || preferences.contains("favorites"))
    ))
    override fun save(snapshot: DemoSnapshot) {
        val commerce = CommerceJson.encode(snapshot.commerce)
        val settings = PreferencesJson.encode(snapshot.preferences)
        fun DemoPreferences.durableState() = copy(ratingDraft = 0, reviewName = "", reviewText = "", reportDraft = "", letterDraft = "")
        val previousSettings = preferences.getString("preferences_v1", null)
        val durableSettingsChanged = previousSettings == null || snapshot.preferences.durableState() != PreferencesJson.decode(previousSettings).durableState()
        // Accepted purchases, intro steps, notification claims and saved feedback are durable transitions.
        // Text editing remains asynchronous; a following accepted action commits its full snapshot.
        preferences.edit(commit = commerce != preferences.getString("commerce_v1", null) || durableSettingsChanged) {
            putStringSet("favorites", snapshot.favorites.toSet())
            putInt("tokens", snapshot.account.tokens)
            putBoolean("pro", snapshot.account.isPro)
            putString("plan", snapshot.account.plan?.name)
            putString("commerce_v1", commerce)
            putString("preferences_v1", settings)
            putString("generation_v1", GenerationJson.encode(snapshot))
        }
    }
}
