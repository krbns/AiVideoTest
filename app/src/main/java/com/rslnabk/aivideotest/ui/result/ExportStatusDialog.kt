package com.rslnabk.aivideotest.ui.result

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.model.*

class ExportStatusDialog : DialogFragment() {
    private val model get() = ViewModelProvider(requireActivity())[AppViewModel::class.java]
    override fun onCreateDialog(state: Bundle?): Dialog {
        val args = requireArguments()
        val failed = args.getBoolean("failed")
        val destination = ExportDestination.valueOf(args.getString("destination")!!)
        val host = requireActivity() as MainActivity
        val builder = MaterialAlertDialogBuilder(host)
        if (failed) {
            builder.setTitle(when (destination) { ExportDestination.GALLERY -> R.string.gallery_error; ExportDestination.FILES -> R.string.files_error; ExportDestination.SHARE -> R.string.share_error })
                .setMessage(args.getInt("message",R.string.export_error_body)).setNegativeButton(R.string.okay,null)
                .setPositiveButton(R.string.refresh) { _,_ ->
                    model.exports.acknowledge(args.getString("operation")!!)
                    host.exportResult(args.getString("job")!!,destination)
                }
        } else builder.setMessage(if (destination == ExportDestination.GALLERY) R.string.saved_gallery else R.string.saved_files).setPositiveButton(R.string.okay,null)
        return builder.create()
    }
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        model.exports.acknowledge(requireArguments().getString("operation")!!)
    }
    companion object {
        fun newInstance(value: ExportState) = ExportStatusDialog().apply { arguments = Bundle().apply {
            putString("operation",value.id); putString("job",value.jobId); putString("destination",value.destination.name)
            putBoolean("failed",value.phase == ExportPhase.FAILED); putInt("message",value.message ?: R.string.export_error_body)
        } }
    }
}
