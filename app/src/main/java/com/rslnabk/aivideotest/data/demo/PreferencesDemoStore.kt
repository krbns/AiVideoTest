package com.rslnabk.aivideotest.data.demo

import android.content.SharedPreferences
import androidx.core.content.edit
import com.rslnabk.aivideotest.data.DemoStore
import com.rslnabk.aivideotest.model.*

class PreferencesDemoStore(private val preferences: SharedPreferences) : DemoStore {
    override fun load() = GenerationJson.decode(preferences.getString("generation_v1", null), DemoSnapshot(
        preferences.getStringSet("favorites", emptySet())!!.toSet(),
        DemoAccount(preferences.getInt("tokens", 5).coerceAtLeast(0), preferences.getBoolean("pro", false))
    ))
    override fun save(snapshot: DemoSnapshot) {
        preferences.edit {
            putStringSet("favorites", snapshot.favorites.toSet())
            putInt("tokens", snapshot.account.tokens)
            putBoolean("pro", snapshot.account.isPro)
            putString("generation_v1", GenerationJson.encode(snapshot))
        }
    }
}
