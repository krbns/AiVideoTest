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
        commerce = CommerceJson.decode(preferences.getString("commerce_v1", null))
    ))
    override fun save(snapshot: DemoSnapshot) {
        val commerce = CommerceJson.encode(snapshot.commerce)
        // Commit account, receipts, operation and accepted job together on purchase transitions.
        preferences.edit(commit = commerce != preferences.getString("commerce_v1", null)) {
            putStringSet("favorites", snapshot.favorites.toSet())
            putInt("tokens", snapshot.account.tokens)
            putBoolean("pro", snapshot.account.isPro)
            putString("plan", snapshot.account.plan?.name)
            putString("commerce_v1", commerce)
            putString("generation_v1", GenerationJson.encode(snapshot))
        }
    }
}
