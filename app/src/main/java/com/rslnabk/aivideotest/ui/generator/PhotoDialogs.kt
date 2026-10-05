package com.rslnabk.aivideotest.ui.generator

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.ui.common.*

class PhotoDialog : BottomSheetDialogFragment() {
    override fun getTheme() = R.style.ThemeOverlay_AiVideoTest_PhotoSheet
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val host = requireActivity() as MainActivity
        val model = ViewModelProvider(host)[AppViewModel::class.java]
        val key = requireArguments().getString("key")!!
        val stage = requireArguments().getString("stage")!!
        val body = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL; setPadding(host.dp(16), host.dp(24), host.dp(16), host.dp(24)) }
        fun title(resource: Int) { body.addView(host.text(getString(resource), R.style.TextAppearance_AiVideoTest_Title2_Emphasized).apply { if (resource == R.string.instruction) gravity = android.view.Gravity.CENTER }, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = host.dp(16) }) }
        fun action(resource: Int, primary: Boolean = false, block: () -> Unit) {
            body.addView(host.button(getString(resource), primary).apply { setOnClickListener { dismiss(); block() } }, LinearLayout.LayoutParams(-1,host.dp(52)).apply { topMargin = host.dp(12) })
        }
        fun photos(images: List<Int>, choose: ((Int) -> Unit)? = null) {
            val row = LinearLayout(host)
            images.forEachIndexed { index, image ->
                val card = MaterialCardView(host).apply {
                    radius = host.dp(24).toFloat(); strokeWidth = 0
                    addView(ImageView(host).apply { setImageResource(image); scaleType = ImageView.ScaleType.CENTER_CROP }, LinearLayout.LayoutParams(-1,-1))
                    if (choose != null) {
                        isFocusable = true; isClickable = true; contentDescription = getString(if (index == 0) R.string.sample_one else R.string.sample_two)
                        setOnClickListener { dismiss(); choose(index) }
                    }
                }
                row.addView(card,LinearLayout.LayoutParams(0,host.dp(if (stage == "instruction") 145 else 240),1f).apply { if (index > 0) marginStart = host.dp(12) })
            }
            body.addView(row)
        }
        when (stage) {
            "instruction" -> {
                title(R.string.instruction); title(R.string.good_choice)
                photos(listOf(R.drawable.demo_good_1, R.drawable.demo_good_2))
                body.addView(host.text(getString(R.string.good_choice_body)), LinearLayout.LayoutParams(-1,-2).apply { topMargin = host.dp(12); bottomMargin = host.dp(20) })
                title(R.string.bad_choice); photos(listOf(R.drawable.demo_bad_1, R.drawable.demo_bad_2))
                body.addView(host.text(getString(R.string.bad_choice_body)), LinearLayout.LayoutParams(-1,-2).apply { topMargin = host.dp(12) })
                action(R.string.continue_action, true) { model.markInstructionSeen(); host.showPhotoSource(key) }
            }
            "source" -> {
                title(R.string.photo_source)
                action(R.string.gallery) { host.pickGallery(key) }
                action(R.string.camera) { host.takePhoto(key) }
                action(R.string.sample_photo, true) { newInstance(key,"samples").show(parentFragmentManager,"photo_dialog") }
                action(R.string.cancel) {}
            }
            "samples" -> {
                title(R.string.sample_photos); photos(listOf(R.drawable.demo_good_1,R.drawable.demo_good_2)) { index -> model.loadPhoto(key, if (index == 0) "asset:good1" else "asset:good2") }
                action(R.string.cancel) {}
            }
        }
        return ScrollView(host).apply { addView(body) }
    }
    override fun onStart() {
        super.onStart()
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior?.apply {
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }
    companion object {
        fun newInstance(key: String, stage: String) = PhotoDialog().apply { arguments = Bundle().apply { putString("key", key); putString("stage", stage) } }
    }
}
