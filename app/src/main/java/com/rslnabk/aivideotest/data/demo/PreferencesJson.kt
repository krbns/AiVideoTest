package com.rslnabk.aivideotest.data.demo

import com.rslnabk.aivideotest.model.*
import org.json.JSONArray
import org.json.JSONObject

object PreferencesJson {
    fun encode(p: DemoPreferences) = JSONObject().apply {
        put("intro", p.introStep.name); put("introOfferPending", p.introOfferPending); put("photoChoice", p.introPhotoChoice.name)
        put("notifications", p.notificationsEnabled); put("notificationAsked", p.notificationAsked)
        put("notified", JSONArray(p.notifiedJobIds.toList()))
        put("ratingDraft", p.ratingDraft); put("reviewName", p.reviewName); put("reviewText", p.reviewText)
        put("ratingDecision", p.ratingDecision.name)
        p.review?.let { put("review", JSONObject().apply { put("id", it.id); put("rating", it.rating); put("name", it.name); put("text", it.text) }) }
        put("reportDraft", p.reportDraft); put("letterDraft", p.letterDraft)
        put("messages", JSONArray(p.messages.map { JSONObject().apply { put("id", it.id); put("kind", it.kind.name); put("text", it.text) } }))
    }.toString()
    fun decode(raw: String?, legacy: Boolean = false): DemoPreferences {
        val fallback = DemoPreferences(introStep = if (legacy) IntroStep.DONE else IntroStep.WELCOME)
        if (raw == null) return fallback
        return runCatching {
            val p = JSONObject(raw)
            val notified = p.optJSONArray("notified") ?: JSONArray()
            val messages = p.optJSONArray("messages") ?: JSONArray()
            fallback.copy(introStep = runCatching { IntroStep.valueOf(p.getString("intro")) }.getOrDefault(fallback.introStep),
                introOfferPending = p.optBoolean("introOfferPending"),
                introPhotoChoice = runCatching { IntroPhotoChoice.valueOf(p.getString("photoChoice")) }.getOrDefault(IntroPhotoChoice.NONE),
                notificationsEnabled = p.optBoolean("notifications"), notificationAsked = p.optBoolean("notificationAsked"),
                notifiedJobIds = (0 until notified.length()).map { notified.getString(it) }.toSet(),
                ratingDraft = p.optInt("ratingDraft").coerceIn(0, 5), reviewName = p.optString("reviewName"), reviewText = p.optString("reviewText"),
                ratingDecision = runCatching { RatingDecision.valueOf(p.getString("ratingDecision")) }.getOrDefault(RatingDecision.NONE),
                review = p.optJSONObject("review")?.let { runCatching { DemoReview(it.getString("id"), it.getInt("rating").coerceIn(1, 5), it.optString("name"), it.optString("text")) }.getOrNull() },
                reportDraft = p.optString("reportDraft"), letterDraft = p.optString("letterDraft"),
                messages = (0 until messages.length()).mapNotNull { runCatching { messages.getJSONObject(it).let { item ->
                    DemoMessage(item.getString("id"), MessageKind.valueOf(item.getString("kind")), item.getString("text"))
                } }.getOrNull() }.takeLast(20))
        }.getOrDefault(fallback)
    }
}
