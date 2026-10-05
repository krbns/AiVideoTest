package com.rslnabk.aivideotest.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.Effect

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()
fun Context.color(id: Int) = ContextCompat.getColor(this, id)
fun Context.text(value: String, appearance: Int = R.style.TextAppearance_AiVideoTest_Subheadline_Regular) =
    MaterialTextView(this).apply { text = value; setTextAppearance(appearance); includeFontPadding = false }
fun Context.button(value: String, primary: Boolean = false) = MaterialButton(this).apply {
    text = value
    setTextAppearance(R.style.TextAppearance_AiVideoTest_Subheadline_Regular)
    isAllCaps = false
    minHeight = dp(44); minimumHeight = dp(44); minWidth = 0; minimumWidth = 0
    insetTop = 0; insetBottom = 0
    cornerRadius = dp(28)
    backgroundTintList = ColorStateList.valueOf(color(if (primary) R.color.ds_accent_primary else R.color.ds_background_secondary))
    setTextColor(color(if (primary) R.color.ds_label_primary_inverted else R.color.ds_label_primary))
    elevation = 0f
}
// Cards are constructed with effect data; they are never inflated from XML.
@SuppressLint("ViewConstructor")
class EffectCard(context: Context, val effect: Effect, open: () -> Unit, like: () -> Unit) : MaterialCardView(context) {
    private val heart = AppCompatImageButton(context)
    init {
        radius = context.dp(20).toFloat(); cardElevation = 0f; strokeWidth = 0
        isClickable = true; isFocusable = true
        contentDescription = context.getString(R.string.effect_accessibility, context.getString(effect.title))
        setOnClickListener { open() }
        val frame = FrameLayout(context)
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(effect.image); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        val shade = View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0, context.color(R.color.ds_background_primary_alpha))) }
        frame.addView(shade, FrameLayout.LayoutParams(-1, context.dp(52), Gravity.BOTTOM))
        val title = context.text(context.getString(effect.title), R.style.TextAppearance_AiVideoTest_Caption1_Regular).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(title, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(context.dp(12), 0, context.dp(8), context.dp(14))
        })
        heart.apply {
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
            setBackgroundResource(R.drawable.bg_circle)
            setOnClickListener { like() }
        }
        frame.addView(heart, FrameLayout.LayoutParams(context.dp(48), context.dp(48), Gravity.TOP or Gravity.END).apply {
            setMargins(0, context.dp(6), context.dp(6), 0)
        })
        addView(frame)
    }
    fun bindFavorite(selected: Boolean) {
        heart.setImageResource(if (selected) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        heart.imageTintList = ColorStateList.valueOf(context.color(if (selected) R.color.ds_accent_primary else R.color.ds_label_primary))
        heart.contentDescription = context.getString(if (selected) R.string.remove_favorite else R.string.add_favorite) + ": " + context.getString(effect.title)
        heart.isSelected = selected
    }
}

fun Context.segmented(labels: List<String>, selected: Int, icons: List<Int> = emptyList(), onSelect: (Int) -> Unit): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(16), 0, dp(16), dp(16))
        labels.forEachIndexed { index, label ->
            val item = button(label, index == selected).apply {
                isSelected = index == selected
                if (icons.isNotEmpty()) {
                    setCompoundDrawablesWithIntrinsicBounds(0, icons[index], 0, 0)
                    TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(color(if (index == selected) R.color.ds_label_primary_inverted else R.color.ds_label_tertiary)))
                }
                setOnClickListener { onSelect(index) }
            }
            addView(item, LinearLayout.LayoutParams(0, dp(if (icons.isEmpty()) 44 else 58), 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
    }
}
