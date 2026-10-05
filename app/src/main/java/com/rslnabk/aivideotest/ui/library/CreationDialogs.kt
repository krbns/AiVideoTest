package com.rslnabk.aivideotest.ui.library

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.model.JobStatus
import com.rslnabk.aivideotest.ui.common.*

class CreationActionsDialog : BottomSheetDialogFragment() {
    override fun getTheme() = R.style.ThemeOverlay_AiVideoTest_PhotoSheet
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val host = requireActivity() as MainActivity
        val model = ViewModelProvider(host)[AppViewModel::class.java]
        val id = requireArguments().getString("job")!!
        val job = model.snapshot.value!!.jobs.find { it.id == id }
        return LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL; setPadding(host.dp(16),host.dp(24),host.dp(16),host.dp(24))
            addView(host.text(getString(R.string.select_action),R.style.TextAppearance_AiVideoTest_Headline_Emphasized).apply { gravity = Gravity.CENTER },LinearLayout.LayoutParams(-1,-2))
            addView(host.text(getString(R.string.failed_action_body)).apply { gravity = Gravity.CENTER; setTextColor(host.color(R.color.ds_label_tertiary)) },LinearLayout.LayoutParams(-1,-2).apply { topMargin = host.dp(12); bottomMargin = host.dp(16) })
            fun action(text: Int, danger: Boolean = false, click: () -> Unit) {
                addView(host.button(getString(text)).apply {
                    setTextColor(host.color(if (danger) R.color.ds_accent_red else R.color.ds_accent_primary))
                    setOnClickListener { dismiss(); click() }
                },LinearLayout.LayoutParams(-1,host.dp(52)).apply { topMargin = host.dp(8) })
            }
            if (job?.status == JobStatus.FAILED) action(R.string.refresh) { host.retryJob(id) }
            if (job?.status != JobStatus.RUNNING) action(R.string.delete_generation, true) { host.confirmDelete(id) }
            action(R.string.cancel) {}
        }
    }
    companion object { fun newInstance(id: String) = CreationActionsDialog().apply { arguments = Bundle().apply { putString("job",id) } } }
}

class DeleteGenerationDialog : DialogFragment() {
    override fun onCreateDialog(state: Bundle?): Dialog {
        val host = requireActivity() as MainActivity
        val id = requireArguments().getString("job")!!
        return MaterialAlertDialogBuilder(host).setTitle(R.string.delete_title).setMessage(R.string.delete_body)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.delete_generation) { _,_ -> host.deleteGeneration(id) }.create().apply {
                setOnShowListener { getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(host.color(R.color.ds_accent_red)) }
            }
    }
    companion object { fun newInstance(id: String) = DeleteGenerationDialog().apply { arguments = Bundle().apply { putString("job",id) } } }
}
