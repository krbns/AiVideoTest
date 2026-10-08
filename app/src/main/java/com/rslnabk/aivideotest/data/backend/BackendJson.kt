package com.rslnabk.aivideotest.data.backend

import org.json.JSONArray
import org.json.JSONObject

object BackendJson {
    private fun JSONObject.optional(key: String) = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.strings(key: String) = optJSONArray(key)?.let { array ->
        (0 until array.length()).map { array.getString(it) }
    }.orEmpty()
    fun parse(body: String): JSONObject = try { JSONObject(body) } catch (_: Exception) { throw BackendFailure(code = "invalid_response") }
    fun response(response: BackendResponse): JSONObject {
        if (response.status !in 200..299) {
            val error = runCatching { JSONObject(response.body).optJSONObject("error") }.getOrNull()
            val code = error?.optional("code") ?: if (response.status == 422) "validation" else "http_error"
            throw BackendFailure(response.status, code, error?.optional("requestId"))
        }
        val value = parse(response.body)
        if (value.optString("status") == "blocked") throw BackendFailure(code = "blocked")
        return value
    }
    fun session(value: JSONObject, now: Long): AuthSession = checked {
        val user = value.getString("userId"); java.util.UUID.fromString(user)
        val access = value.getString("accessToken"); val refresh = value.getString("refreshToken")
        require(access.isNotBlank() && refresh.isNotBlank() && value.getString("tokenType") == "Bearer")
        val expires = value.getLong("expiresIn"); val refreshExpires = value.getLong("refreshExpiresIn")
        require(expires in 1..315360000 && refreshExpires in 1..315360000)
        AuthSession(user, value.getString("deviceId").also { require(it.isNotBlank()) }, access, refresh,
            now + expires * 1000, now + refreshExpires * 1000)
    }
    fun update(data: BackendData, section: BackendSection, value: JSONObject): BackendData = checked {
        when (section) {
            BackendSection.PHOTOS, BackendSection.VIDEOS -> {
                val items = value.getJSONArray("templates").objects().map { item ->
                    val video = item.getString("coverMediaType").startsWith("video/")
                    RemoteTemplate(item.getString("id"), item.getString("title"),
                        if (video) item.optional("coverPosterUrl") else item.optional("coverUrl"),
                        if (video) item.optional("coverPreviewUrl") ?: item.optional("coverUrl") else null,
                        item.strings("categories"), item.getInt("tokens"), item.getInt("requiredInputImages"),
                        item.getString("pipeline"), item.optBoolean("isNew"), item.optBoolean("isTrending"))
                }.distinctBy { it.id }
                if (section == BackendSection.PHOTOS) data.copy(photos = items) else data.copy(videos = items)
            }
            BackendSection.MODELS -> data.copy(models = value.getJSONArray("models").objects().map { item ->
                RemoteModel(item.getString("id"), item.getString("title"), item.getString("kind"),
                    item.getJSONArray("modes").objects().map { mode -> RemoteMode(mode.getString("mode"), mode.strings("resolutions"), mode.strings("durations"),
                        mode.strings("params"), mode.strings("aspectRatios"), mode.strings("outputFormats"), mode.strings("requiredParams"),
                        mode.optJSONObject("defaults")?.let { d -> d.keys().asSequence().associateWith { d.get(it).toString() } }.orEmpty()) },
                    item.getInt("credits"), item.optJSONObject("resolutionCredits")?.let { prices -> prices.keys().asSequence().associateWith { prices.getInt(it) } }.orEmpty(),
                    item.getInt("maxInputImages"), item.optInt("minInputImages"))
            })
            BackendSection.POLICY -> data.copy(policy = RemotePolicy(value.getBoolean("isSubscribed"), value.getInt("creditsBalance"),
                value.getInt("trialRemaining"), value.getBoolean("canGenerateCreditsMode"), value.optional("plan"),
                value.optional("subscriptionExpiresAt"), if (value.isNull("willRenew")) null else value.getBoolean("willRenew"), value.strings("reasons")))
            BackendSection.WALLET -> data.copy(wallet = value.getInt("balance"))
            BackendSection.PROFILE -> data.copy(profile = RemoteProfile(value.getString("accountId"), value.optional("displayName")))
            BackendSection.PRODUCTS -> data.copy(products = value.getJSONArray("products").objects().map { item ->
                RemoteProduct(item.getString("productId"), item.optional("title"), if (item.isNull("credits")) null else item.getInt("credits"))
            })
            BackendSection.JOBS -> data.copy(jobs = value.getJSONArray("jobs").objects().map { item ->
                job(item)
            }.distinctBy { it.id }, nextCursor = value.optional("nextCursor"))
        }
    }
    fun job(item: JSONObject): RemoteJob = checked {
        val first = item.optJSONArray("assets")?.optJSONObject(0)
        RemoteJob(item.getString("jobId"), item.getString("kind"), item.getString("prompt"), item.getString("status"),
            first?.optional("thumbnailUrl") ?: first?.optional("url")?.takeIf { item.getString("kind") == "image" }, item.optional("errorCode"),
            item.optJSONArray("assets")?.objects()?.map { RemoteAsset(it.getString("url"), it.optional("contentType"), it.optional("fileName"), it.optional("expiresAt")) }.orEmpty(),
            item.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f), item.optInt("creditsCharged"), item.optBoolean("creditsRefunded"), item.optString("model"),
            item.optional("mode"), item.optJSONObject("parameters")?.toString(), item.optional("templateId"),
            if (item.isNull("inputImageUrls")) null else item.strings("inputImageUrls"), item.optional("createdAt"))
    }
    private inline fun <T> checked(block: () -> T): T = try { block() } catch (_: Exception) { throw BackendFailure(code = "invalid_response") }
}
