package com.rslnabk.aivideotest.model

enum class IntroStep { WELCOME, PROMPT, SHARE, REVIEWS, PHOTOS, NOTIFICATIONS, DONE }
enum class IntroPhotoChoice { NONE, PICKED, SAMPLE, SKIPPED, CANCELLED }
enum class RatingDecision { NONE, DECLINED, SAVED }
enum class MessageKind { REPORT, LETTER }
enum class SubscriptionCard { FREE, PRO, LOW_BALANCE }
data class DemoReview(val id: String, val rating: Int, val name: String, val text: String)
data class DemoMessage(val id: String, val kind: MessageKind, val text: String)
data class DemoPreferences(
    val introStep: IntroStep = IntroStep.WELCOME,
    val introPhotoChoice: IntroPhotoChoice = IntroPhotoChoice.NONE,
    val notificationsEnabled: Boolean = false,
    val notificationAsked: Boolean = false,
    val notifiedJobIds: Set<String> = emptySet(),
    val ratingDraft: Int = 0,
    val reviewName: String = "",
    val reviewText: String = "",
    val ratingDecision: RatingDecision = RatingDecision.NONE,
    val review: DemoReview? = null,
    val reportDraft: String = "",
    val letterDraft: String = "",
    val messages: List<DemoMessage> = emptyList(),
    val introOfferPending: Boolean = false
)
val DemoAccount.subscriptionCard get() = when {
    tokens < 10 -> SubscriptionCard.LOW_BALANCE
    isPro -> SubscriptionCard.PRO
    else -> SubscriptionCard.FREE
}
