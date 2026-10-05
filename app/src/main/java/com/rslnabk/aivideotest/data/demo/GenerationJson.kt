package com.rslnabk.aivideotest.data.demo

import com.rslnabk.aivideotest.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Names and IDs, rather than persisted Android resource numbers, survive future resource changes. */
object GenerationJson {
    fun draft(value: GenerationDraft) = JSONObject().apply {
        put("key", value.key); put("kind", value.kind.name); put("effect", value.effectId)
        put("prompt", value.prompt); put("resolution", value.resolution); put("style", value.style.name)
        put("photo", value.photo); put("photoStatus", value.photoStatus.name)
        put("pendingPhoto", value.pendingPhoto); put("activeJobId", value.activeJobId)
    }
    fun draft(json: JSONObject): GenerationDraft {
        fun optional(key: String) = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotEmpty() }
        return GenerationDraft(json.getString("key"), MediaKind.valueOf(json.getString("kind")), optional("effect"),
            json.optString("prompt"), json.optInt("resolution", 720), PhotoStyle.valueOf(json.optString("style", "NONE")),
            optional("photo"), PhotoStatus.valueOf(json.optString("photoStatus", "ABSENT")), optional("pendingPhoto"), optional("activeJobId"))
    }
    fun encode(snapshot: DemoSnapshot) = JSONObject().apply {
        put("instructionSeen", snapshot.instructionSeen)
        put("drafts", JSONArray(snapshot.drafts.values.map(::draft)))
        put("jobs", JSONArray(snapshot.jobs.map { job -> JSONObject().apply {
            put("id", job.id); put("draft", draft(job.draft)); put("cost", job.tokenCost)
            put("created", job.createdAt); put("ready", job.readyAt); put("fail", job.willFail); put("status", job.status.name)
        } }))
    }.toString()
    fun decode(value: String?, initial: DemoSnapshot): DemoSnapshot {
        if (value == null) return initial
        return runCatching {
            val root = JSONObject(value); val catalog = DemoCatalogRepository()
            val drafts = root.optJSONArray("drafts") ?: JSONArray()
            val jobs = root.optJSONArray("jobs") ?: JSONArray()
            initial.copy(instructionSeen = root.optBoolean("instructionSeen"),
                drafts = (0 until drafts.length()).map { draft(drafts.getJSONObject(it)) }.associateBy { it.key },
                jobs = (0 until jobs.length()).map { index ->
                    val json = jobs.getJSONObject(index); val input = draft(json.getJSONObject("draft"))
                    val image = DemoResultFixtures.image(input, catalog)
                    GenerationJob(json.getString("id"), input, json.getInt("cost"), image,
                        json.getLong("created"), json.getLong("ready"), json.optBoolean("fail"), JobStatus.valueOf(json.getString("status")))
                })
        }.getOrDefault(initial)
    }
}
