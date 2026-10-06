package com.rslnabk.aivideotest.model

/** IDs are local PDF fixtures, never store SKUs. Amounts/prices follow pages 13 and 19. */
enum class OfferKind { PRO, TOKENS }
enum class SubscriptionPlan { YEAR, WEEK }
enum class DemoProduct(val kind: OfferKind, val price: String, val tokens: Int = 0, val plan: SubscriptionPlan? = null, val savings: Boolean = false) {
    PRO_YEAR(OfferKind.PRO, "$29.99", plan = SubscriptionPlan.YEAR, savings = true),
    PRO_WEEK(OfferKind.PRO, "$9.99", plan = SubscriptionPlan.WEEK),
    TOKENS_1(OfferKind.TOKENS, "$19.99", 100),
    TOKENS_2(OfferKind.TOKENS, "$19.99", 100, savings = true),
    TOKENS_3(OfferKind.TOKENS, "$19.99", 100, savings = true),
    TOKENS_4(OfferKind.TOKENS, "$19.99", 100, savings = true)
}
enum class PurchaseAction { BUY, RESTORE }
enum class PurchasePhase { LOADING, SUCCEEDED, CANCELLED, FAILED, EMPTY }
enum class DemoPurchaseOutcome { SUCCESS, CANCEL, ERROR }
enum class CreationAction { GENERATE, RETRY }
data class CreationIntent(val action: CreationAction, val target: String)
data class DemoReceipt(val id: String, val product: DemoProduct)
data class DemoPurchase(
    val id: String, val action: PurchaseAction, val product: DemoProduct?, val readyAt: Long,
    val outcome: DemoPurchaseOutcome, val phase: PurchasePhase = PurchasePhase.LOADING,
    val continuation: CreationIntent? = null
)
data class DemoCommerce(val receipts: List<DemoReceipt> = emptyList(), val operation: DemoPurchase? = null)
