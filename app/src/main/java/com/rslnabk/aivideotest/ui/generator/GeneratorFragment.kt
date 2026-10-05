package com.rslnabk.aivideotest.ui.generator

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.databinding.FragmentGeneratorBinding
import com.rslnabk.aivideotest.ui.common.bindBalance

class GeneratorFragment : Fragment() {
    private var binding: FragmentGeneratorBinding? = null
    private var editor: PromptEditor? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentGeneratorBinding.inflate(inflater, container, false).also { binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        val host = requireActivity() as MainActivity
        val model = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        val key = requireArguments().getString("key")!!; val b = binding!!
        b.header.title.text = model.catalog.effect(key)?.let { getString(it.title) } ?: getString(R.string.prepare_photo)
        b.header.back.setOnClickListener { host.onBackPressedDispatcher.onBackPressed() }
        editor = PromptEditor(host, model, key); b.body.addView(editor!!.view)
        model.snapshot.observe(viewLifecycleOwner) { b.header.balance.bindBalance(model); editor?.bind() }
        if (state == null && model.draft(key).photo == null) view.post {
            if (isAdded && !isHidden && !parentFragmentManager.isStateSaved) host.requestPhoto(key)
        }
    }
    override fun onDestroyView() { binding = null; editor = null; super.onDestroyView() }
    companion object { fun newInstance(key: String) = GeneratorFragment().apply { arguments = Bundle().apply { putString("key", key) } } }
}
