package com.rslnabk.aivideotest.ui.generator

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.databinding.FragmentCreatingBinding
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.bindBalance

class CreatingFragment : Fragment() {
    private var binding: FragmentCreatingBinding? = null
    private var navigating = false
    private val id get() = requireArguments().getString("job")!!
    private val model get() = ViewModelProvider(requireActivity())[AppViewModel::class.java]
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentCreatingBinding.inflate(inflater, container, false).also { binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        val host = requireActivity() as MainActivity; val b = binding!!
        b.header.title.text = ""; b.header.back.setOnClickListener { host.onBackPressedDispatcher.onBackPressed() }
        model.snapshot.observe(viewLifecycleOwner) { snapshot ->
            val job = snapshot.jobs.find { it.id == id }
            if (job == null) {
                view.post { if (isAdded && !parentFragmentManager.isStateSaved) host.selectTab(AppTab.VIDEO) }
                return@observe
            }
            b.header.balance.bindBalance(model)
            val failed = job.status == JobStatus.FAILED
            b.creatingTitle.setText(if (failed) R.string.generation_failed else R.string.creating)
            b.message.setText(if (failed) R.string.generation_failed_body else if (job.draft.kind == MediaKind.VIDEO) R.string.creating_video else R.string.creating_photo)
            b.progress.visibility = if (failed) View.GONE else View.VISIBLE
            b.retry.visibility = if (failed) View.VISIBLE else View.GONE
            b.retry.setOnClickListener { host.retryJob(job.id, replaceCreating = true) }
            b.okay.setText(if (failed) R.string.back_to_input else R.string.okay)
            b.okay.setOnClickListener { if (failed) host.onBackPressedDispatcher.onBackPressed() else host.openLibrary(job.draft.kind) }
            checkCompletion()
        }
    }
    override fun onResume() { super.onResume(); checkCompletion() }
    private fun checkCompletion() {
        if (navigating || !isResumed || isHidden) return
        if (model.snapshot.value!!.jobs.find { it.id == id }?.status == JobStatus.SUCCEEDED) {
            navigating = true
            binding?.root?.post {
                if (isAdded && !isHidden && !parentFragmentManager.isStateSaved) (requireActivity() as MainActivity).showReadyResult(id)
                else navigating = false
            }
        }
    }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
    companion object { fun newInstance(id: String) = CreatingFragment().apply { arguments = Bundle().apply { putString("job",id) } } }
}
