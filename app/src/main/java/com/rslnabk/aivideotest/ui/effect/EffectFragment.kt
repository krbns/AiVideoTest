package com.rslnabk.aivideotest.ui.effect

import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.databinding.FragmentEffectBinding
import com.rslnabk.aivideotest.model.MediaKind
import com.rslnabk.aivideotest.ui.common.bindBalance

class EffectFragment : Fragment() {
    private var binding: FragmentEffectBinding? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentEffectBinding.inflate(inflater, container, false).also { binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        val model = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        val id = requireArguments().getString("effect")!!
        val effect = requireNotNull(model.catalog.effect(id)) { "Unknown demo effect: $id" }
        val b = binding!!
        b.title.setText(effect.title)
        b.preview.setImageResource(effect.image)
        b.preview.contentDescription = getString(effect.title)
        b.previewLabel.visibility = if (effect.kind == MediaKind.VIDEO) View.VISIBLE else View.GONE
        b.back.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        b.like.setOnClickListener { model.toggleFavorite(id) }
        b.useEffect.setOnClickListener { (requireActivity() as MainActivity).explainCreation() }
        model.snapshot.observe(viewLifecycleOwner) { snapshot ->
            b.balance.bindBalance(model)
            val selected = id in snapshot.favorites
            b.like.isSelected = selected
            b.like.setImageResource(if (selected) R.drawable.ic_heart else R.drawable.ic_heart_outline)
            b.like.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (selected) R.color.ds_accent_primary else R.color.ds_label_primary))
            b.like.contentDescription = getString(if (selected) R.string.remove_favorite else R.string.add_favorite)
        }
    }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
    companion object {
        fun newInstance(id: String) = EffectFragment().apply { arguments = Bundle().apply { putString("effect", id) } }
    }
}
