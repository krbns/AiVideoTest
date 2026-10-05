package com.rslnabk.aivideotest.ui.result

import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.*
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.data.demo.DemoResultFixtures
import com.rslnabk.aivideotest.databinding.FragmentResultBinding
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.color

class ResultFragment : Fragment() {
    private var binding: FragmentResultBinding? = null
    private var position = 0
    private var paused = false
    private val model get() = ViewModelProvider(requireActivity())[AppViewModel::class.java]
    private val job get() = model.snapshot.value!!.jobs.find { it.id == requireArguments().getString("job") }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        position = state?.getInt("position") ?: 0
        paused = state?.getBoolean("paused") ?: false
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentResultBinding.inflate(inflater, container, false).also { binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        val b = binding!!
        val result = job ?: return
        val host = requireActivity() as MainActivity
        b.image.setImageResource(result.resultImage)
        b.back.setOnClickListener { host.onBackPressedDispatcher.onBackPressed() }
        b.share.setOnClickListener { host.exportResult(result.id, ExportDestination.SHARE) }
        b.options.setOnClickListener {
            PopupMenu(requireContext(), b.options).apply {
                fun menuTitle(resource: Int, color: Int) = SpannableString(getString(resource)).apply {
                    setSpan(ForegroundColorSpan(requireContext().color(color)),0,length,0)
                }
                menu.add(0, R.id.action_save, 0, menuTitle(R.string.save_gallery,R.color.ds_accent_primary))
                menu.add(0, R.id.action_files, 1, menuTitle(R.string.save_files,R.color.ds_accent_primary))
                menu.add(0, R.id.action_delete, 2, menuTitle(R.string.delete_generation,R.color.ds_accent_red))
                setOnMenuItemClickListener { item ->
                    if (model.exports.state.value?.busy != true) when (item.itemId) {
                        R.id.action_save -> host.exportResult(result.id, ExportDestination.GALLERY)
                        R.id.action_files -> host.exportResult(result.id, ExportDestination.FILES)
                        R.id.action_delete -> host.confirmDelete(result.id)
                    }
                    true
                }
                show()
            }
        }
        model.exports.state.observe(viewLifecycleOwner) { operation ->
            val busy = operation?.busy == true
            b.share.isEnabled = !busy
            b.options.isEnabled = !busy
            val mine = busy && operation?.jobId == result.id
            b.exportProgress.visibility = if (mine) View.VISIBLE else View.GONE
            b.exportMessage.visibility = if (mine) View.VISIBLE else View.GONE
            b.exportMessage.setText(when (operation?.phase) {
                ExportPhase.CHOOSING -> R.string.choose_file_location
                ExportPhase.PERMISSION -> R.string.awaiting_permission
                else -> R.string.exporting
            })
        }
        if (result.draft.kind == MediaKind.VIDEO) {
            b.video.visibility = View.VISIBLE
            b.playback.visibility = View.VISIBLE
            b.playback.setOnClickListener {
                paused = !paused
                if (paused) b.video.pause() else b.video.start()
                b.playback.setText(if (paused) R.string.play else R.string.pause)
            }
            b.video.setOnPreparedListener { player ->
                player.isLooping = true
                b.video.seekTo(position)
                b.image.visibility = View.GONE
                if (!paused) b.video.start()
                b.playback.setText(if (paused) R.string.play else R.string.pause)
            }
            b.video.setOnErrorListener { _, _, _ ->
                b.image.visibility = View.VISIBLE
                MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.video_unavailable)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.retry) { _, _ -> startVideo() }.show()
                true
            }
        }
    }
    private fun startVideo() {
        job?.let { binding?.video?.setVideoURI("android.resource://${requireContext().packageName}/${DemoResultFixtures.video(it.draft)}".toUri()) }
    }
    override fun onStart() { super.onStart(); if (job?.draft?.kind == MediaKind.VIDEO) startVideo() }
    override fun onStop() {
        binding?.video?.let { if (it.currentPosition > 0) position = it.currentPosition; it.stopPlayback() }
        super.onStop()
    }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("position", binding?.video?.currentPosition?.takeIf { it > 0 } ?: position)
        out.putBoolean("paused", paused)
    }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
    companion object {
        fun newInstance(id: String) = ResultFragment().apply { arguments = Bundle().apply { putString("job", id) } }
    }
}
