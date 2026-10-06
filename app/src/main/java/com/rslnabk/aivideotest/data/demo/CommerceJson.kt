package com.rslnabk.aivideotest.data.demo

import com.rslnabk.aivideotest.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Stable enum/fixture names, no Android resource IDs in saved transactions. */
object CommerceJson {
    fun encode(value: DemoCommerce) = JSONObject().apply {
        put("receipts", JSONArray().apply { value.receipts.forEach { receipt -> put(JSONObject().apply {
            put("id", receipt.id); put("product", receipt.product.name)
        }) } })
        value.operation?.let { op -> put("operation", JSONObject().apply {
            put("id", op.id); put("action", op.action.name); put("product", op.product?.name)
            put("readyAt", op.readyAt); put("outcome", op.outcome.name); put("phase", op.phase.name)
            op.continuation?.let { put("continuation", JSONObject().apply { put("action", it.action.name); put("target", it.target) }) }
        }) }
    }.toString()
    fun decode(raw: String?): DemoCommerce {
        if (raw == null) return DemoCommerce()
        return runCatching {
            val value = JSONObject(raw)
            val receipts = value.optJSONArray("receipts") ?: JSONArray()
            DemoCommerce((0 until receipts.length()).mapNotNull { index -> runCatching {
                val receipt = receipts.getJSONObject(index)
                DemoReceipt(receipt.getString("id"), DemoProduct.valueOf(receipt.getString("product")))
            }.getOrNull() }, value.optJSONObject("operation")?.let { op -> runCatching {
                DemoPurchase(op.getString("id"), PurchaseAction.valueOf(op.getString("action")),
                    op.optString("product").takeIf { it.isNotEmpty() && it != "null" }?.let(DemoProduct::valueOf),
                    op.getLong("readyAt"), DemoPurchaseOutcome.valueOf(op.getString("outcome")), PurchasePhase.valueOf(op.getString("phase")),
                    op.optJSONObject("continuation")?.let { CreationIntent(CreationAction.valueOf(it.getString("action")), it.getString("target")) })
            }.getOrNull() })
        }.getOrDefault(DemoCommerce())
    }
}
