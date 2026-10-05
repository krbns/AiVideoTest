package com.rslnabk.aivideotest.ui.generator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import com.rslnabk.aivideotest.*
import com.rslnabk.aivideotest.databinding.ViewPromptEditorBinding
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*
import java.io.File

class PromptEditor(private val host: MainActivity, private val model: AppViewModel, private val key: String) {
    val binding = ViewPromptEditorBinding.inflate(LayoutInflater.from(host))
    val view get() = binding.root
    private val resolutions = mutableMapOf<Int, View>()
    private val styles = mutableMapOf<PhotoStyle, MaterialCardView>()
    private var changing = false
    private var displayedPhoto: String? = "unbound"
    init {
        val draft = model.draft(key); val b = binding
        b.promptInput.doAfterTextChanged { if (!changing) model.editDraft(key) { draft -> draft.copy(prompt = it?.toString().orEmpty()) } }
        b.clearPrompt.setOnClickListener { model.editDraft(key) { it.copy(prompt = "") } }
        b.copyPrompt.setOnClickListener {
            (host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(host.getString(R.string.prompt), model.draft(key).prompt))
            Toast.makeText(host, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        b.photoCard.setOnClickListener { host.requestPhoto(key) }
        b.removePhoto.setOnClickListener { model.removePhoto(key) }
        b.retryPhoto.setOnClickListener { model.retryPhoto(key) }
        b.chooseAnother.setOnClickListener { host.requestPhoto(key) }
        b.generate.setOnClickListener { host.generate(key) }
        b.resolutionGroup.visibility = if (draft.kind == MediaKind.VIDEO) View.VISIBLE else View.GONE
        b.styleGroup.visibility = if (draft.kind == MediaKind.PHOTO && draft.effectId == null) View.VISIBLE else View.GONE
        if (draft.effectId != null) {
            b.editorHeading.setText(R.string.prepare_photo); b.promptInput.visibility = View.GONE
            b.copyPrompt.visibility = View.GONE; b.clearPrompt.visibility = View.GONE; b.counter.visibility = View.GONE
        }
        listOf(720, 1080).forEach { resolution ->
            val button = host.button(resolution.toString()).apply {
                contentDescription = "$resolution"; setOnClickListener { model.editDraft(key) { it.copy(resolution = resolution) } }
            }
            b.resolutionOptions.addView(button, LinearLayout.LayoutParams(host.dp(80), host.dp(44)).apply { marginEnd = host.dp(8); topMargin = host.dp(8) })
            resolutions[resolution] = button
        }
        val titles = listOf(R.string.style_none, R.string.style_ghibli, R.string.style_3d, R.string.style_simpsons, R.string.style_fantasy)
        val images = listOf(R.drawable.ic_sparkle, R.drawable.demo_style_ghibli, R.drawable.demo_style_3d, R.drawable.demo_style_simpsons, R.drawable.demo_style_fantasy)
        PhotoStyle.entries.forEachIndexed { index, style ->
            val column = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
            val card = MaterialCardView(host).apply {
                radius = host.dp(16).toFloat(); isFocusable = true; isClickable = true
                contentDescription = host.getString(titles[index]); setOnClickListener { model.editDraft(key) { it.copy(style = style) } }
                addView(ImageView(host).apply {
                    setImageResource(images[index]); scaleType = ImageView.ScaleType.CENTER_CROP
                    if (style == PhotoStyle.NONE) { setPadding(host.dp(18), host.dp(18), host.dp(18), host.dp(18)); imageTintList = ColorStateList.valueOf(host.color(R.color.ds_accent_primary)) }
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-1, -1))
            }
            column.addView(card, LinearLayout.LayoutParams(host.dp(64), host.dp(64)))
            column.addView(host.text(host.getString(titles[index]), R.style.TextAppearance_AiVideoTest_Caption1_Regular).apply {
                gravity = android.view.Gravity.CENTER; setTextColor(host.color(R.color.ds_label_tertiary))
            }, LinearLayout.LayoutParams(host.dp(72), -2).apply { topMargin = host.dp(6) })
            b.styleOptions.addView(column, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = host.dp(8) })
            styles[style] = card
        }
        bind()
    }
    fun bind() {
        val draft = model.draft(key); val b = binding
        if (b.promptInput.text?.toString() != draft.prompt) {
            changing = true; b.promptInput.setText(draft.prompt); b.promptInput.setSelection(draft.prompt.length); changing = false
        }
        b.counter.text = host.getString(R.string.counter_format, draft.characterCount)
        b.counter.setTextColor(host.color(if (draft.characterCount > 300) R.color.ds_accent_red else R.color.ds_label_tertiary))
        b.copyPrompt.visibility = if (draft.effectId == null && draft.prompt.isNotEmpty()) View.VISIBLE else View.GONE
        b.clearPrompt.visibility = if (draft.effectId == null && draft.prompt.isNotEmpty()) View.VISIBLE else View.GONE
        b.photoCard.contentDescription = host.getString(if (draft.photo == null) R.string.choose_photo else R.string.replace_photo)
        b.removePhoto.visibility = if (draft.photo != null || draft.photoStatus == PhotoStatus.FAILED) View.VISIBLE else View.GONE
        if (displayedPhoto != draft.photo) {
            displayedPhoto = draft.photo
            val inset = if (draft.photo == null) host.dp(18) else 0; b.photoPreview.setPadding(inset, inset, inset, inset)
            when {
                draft.photo == "asset:good1" -> b.photoPreview.setImageResource(R.drawable.demo_good_1)
                draft.photo == "asset:good2" -> b.photoPreview.setImageResource(R.drawable.demo_good_2)
                draft.photo?.startsWith("file:") == true -> b.photoPreview.setImageURI(Uri.fromFile(File(host.filesDir, "reference_photos/${draft.photo.removePrefix("file:")}")))
                else -> b.photoPreview.setImageResource(R.drawable.ic_photo)
            }
        }
        b.photoProgress.visibility = if (draft.photoStatus == PhotoStatus.LOADING) View.VISIBLE else View.GONE
        b.photoRecovery.visibility = if (draft.photoStatus == PhotoStatus.FAILED) View.VISIBLE else View.GONE
        val error = when {
            draft.characterCount > 300 -> R.string.prompt_too_long
            draft.photoStatus == PhotoStatus.FAILED -> R.string.photo_failed
            else -> null
        }
        b.error.visibility = if (error == null) View.GONE else View.VISIBLE
        error?.let { b.error.setText(it) }
        resolutions.forEach { (resolution, view) ->
            (view as com.google.android.material.button.MaterialButton).apply {
                isSelected = resolution == draft.resolution; strokeWidth = host.dp(1)
                strokeColor = ColorStateList.valueOf(host.color(if (isSelected) R.color.ds_accent_primary else R.color.ds_separator_primary))
                backgroundTintList = ColorStateList.valueOf(host.color(R.color.ds_background_primary))
                setTextColor(host.color(if (isSelected) R.color.ds_accent_primary else R.color.ds_label_tertiary))
            }
        }
        styles.forEach { (style, card) ->
            card.isSelected = style == draft.style; card.strokeWidth = host.dp(if (card.isSelected) 2 else 0)
            card.strokeColor = host.color(R.color.ds_accent_primary)
        }
        val running = model.snapshot.value!!.jobs.any { it.id == draft.activeJobId && it.status == JobStatus.RUNNING }
        b.generate.isEnabled = draft.isValid || running
        b.generate.text = if (running) host.getString(R.string.view_creating) else host.resources.getQuantityString(R.plurals.generate_cost, model.cost(key), model.cost(key))
    }
}
