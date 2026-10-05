package com.rslnabk.aivideotest.ui.common

import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rslnabk.aivideotest.AppViewModel
import com.rslnabk.aivideotest.BuildConfig
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.DemoAccount

fun MaterialButton.bindBalance(model: AppViewModel) {
    val account = model.snapshot.value!!.account
    text = context.getString(if (account.isPro) R.string.pro_balance_value else R.string.balance_value, account.tokens)
    contentDescription = context.getString(R.string.balance_accessibility, account.tokens)
    setOnClickListener {
        MaterialAlertDialogBuilder(context).setTitle(R.string.tokens_title)
            .setMessage(context.getString(R.string.tokens_body, model.snapshot.value!!.account.tokens))
            .setPositiveButton(R.string.got_it, null).show()
    }
    if (BuildConfig.DEBUG) setOnLongClickListener {
        showDemoControls(this, model); true
    }
}
private fun showDemoControls(view: View, model: AppViewModel) {
    val context = view.context
    val options = intArrayOf(R.string.debug_free, R.string.debug_pro, R.string.debug_zero, R.string.debug_reset, R.string.debug_photo_error, R.string.debug_generation_error, R.string.debug_export_error).map(context::getString).toTypedArray()
    MaterialAlertDialogBuilder(context).setTitle(R.string.debug_title).setItems(options) { _, index ->
        when (index) {
            0 -> model.setAccount(DemoAccount())
            1 -> model.setAccount(DemoAccount(100, true))
            2 -> model.setAccount(DemoAccount(0))
            3 -> MaterialAlertDialogBuilder(context).setTitle(R.string.debug_reset_title).setMessage(R.string.debug_reset_body)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.reset) { _, _ -> model.reset() }.show()
            4 -> { model.failNextPhoto = true; android.widget.Toast.makeText(context, R.string.debug_error_ready, android.widget.Toast.LENGTH_SHORT).show() }
            5 -> { model.failNextGeneration = true; android.widget.Toast.makeText(context, R.string.debug_error_ready, android.widget.Toast.LENGTH_SHORT).show() }
            6 -> { model.exports.failNext = true; android.widget.Toast.makeText(context, R.string.debug_error_ready, android.widget.Toast.LENGTH_SHORT).show() }
        }
    }.setNegativeButton(R.string.close, null).show()
}
