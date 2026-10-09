package com.rslnabk.aivideotest.data.backend

/** Remote identity and data never enter DemoSnapshot or DemoSession. */
enum class DataSource { DEMO, SERVER }
enum class BackendSection(val path: String) {
    PHOTOS("/v1/media/templates/images"), VIDEOS("/v1/media/templates/videos"),
    MODELS("/v1/media/models"), POLICY("/v1/policy/effective"), WALLET("/v1/wallet"),
    PROFILE("/v1/profile"), PRODUCTS("/v1/tokens/products"), JOBS("/v1/media/jobs?limit=20")
}
data class AuthSession(val userId: String, val deviceId: String, val accessToken: String,
    val refreshToken: String, val expiresAt: Long, val refreshExpiresAt: Long, val refreshPending: Boolean = false) {
    override fun toString() = "AuthSession(userId=" + userId + ", credentials=[redacted])"
}
data class RemoteTemplate(val id: String, val title: String, val cover: String?, val videoCover: String?,
    val categories: List<String>, val tokens: Int, val requiredImages: Int, val pipeline: String,
    val isNew: Boolean, val trending: Boolean, val model: String = "", val mode: String = "",
    val parameters: String? = null, val steps: List<RemoteTemplateStep> = emptyList(), val section: String? = null)
data class RemoteTemplateStep(val index: Int, val kind: String, val model: String, val requiredImages: Int)
data class RemoteMode(val name: String, val resolutions: List<String>, val durations: List<String>,
    val params: List<String> = emptyList(), val aspectRatios: List<String> = emptyList(),
    val outputFormats: List<String> = emptyList(), val required: List<String> = emptyList(), val defaults: Map<String, String> = emptyMap())
data class RemoteModel(val id: String, val title: String, val kind: String, val modes: List<RemoteMode>,
    val credits: Int = 0, val resolutionCredits: Map<String, Int> = emptyMap(), val maxImages: Int = 0,
    val minImages: Int = 0, val baseDuration: Int? = null, val resolutionMultipliers: Map<String, Int> = emptyMap(),
    val audioMultiplier: Double? = null, val supportsAudio: Boolean = false)
data class RemotePolicy(val subscribed: Boolean, val credits: Int, val trial: Int, val canGenerate: Boolean,
    val plan: String?, val expiresAt: String?, val willRenew: Boolean?, val reasons: List<String>)
data class RemoteProfile(val accountId: String, val name: String?)
data class RemoteJob(val id: String, val kind: String, val prompt: String, val status: String,
    val thumbnail: String?, val errorCode: String?, val assets: List<RemoteAsset> = emptyList(),
    val progress: Float = 0f, val charged: Int = 0, val refunded: Boolean = false, val model: String = "",
    val mode: String? = null, val parameters: String? = null, val templateId: String? = null,
    val inputImageUrls: List<String>? = null, val createdAt: String? = null, val pipelineStage: String? = null)
data class RemoteAsset(val url: String, val mime: String?, val name: String?, val expiresAt: String?)
data class RemoteProduct(val id: String, val title: String?, val credits: Int?)
data class BackendData(val photos: List<RemoteTemplate> = emptyList(), val videos: List<RemoteTemplate> = emptyList(),
    val models: List<RemoteModel> = emptyList(), val policy: RemotePolicy? = null, val wallet: Int? = null,
    val profile: RemoteProfile? = null, val products: List<RemoteProduct> = emptyList(),
    val jobs: List<RemoteJob> = emptyList(), val nextCursor: String? = null)
data class BackendState(val userId: String? = null, val connecting: Boolean = false, val authError: BackendFailure? = null,
    val data: BackendData = BackendData(), val loading: Set<BackendSection> = emptySet(),
    val loaded: Set<BackendSection> = emptySet(), val cached: Set<BackendSection> = emptySet(),
    val errors: Map<BackendSection, BackendFailure> = emptyMap(), val favorites: Set<String> = emptySet(),
    val loadingMore: Boolean = false, val historyError: BackendFailure? = null)
class BackendFailure(val status: Int = 0, val code: String = "network", val requestId: String? = null) :
    Exception(code) // Raw responses, bearer tokens and result URLs never appear in exception messages.
