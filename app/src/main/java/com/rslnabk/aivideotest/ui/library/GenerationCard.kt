package com.rslnabk.aivideotest.ui.library

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.*
import com.rslnabk.aivideotest.ui.common.*

@SuppressLint("ViewConstructor")
class GenerationCard(context: Context, val job: GenerationJob, name: String, open: () -> Unit, actions: () -> Unit) : MaterialCardView(context) {
    init {
        radius = context.dp(20).toFloat(); cardElevation = 0f; strokeWidth = 0
        setCardBackgroundColor(context.color(R.color.ds_background_secondary))
        isClickable = true; isFocusable = true
        val status = when (job.status) { JobStatus.RUNNING -> R.string.job_running; JobStatus.SUCCEEDED -> R.string.job_ready; JobStatus.FAILED -> R.string.job_failed }
        contentDescription = context.getString(R.string.creation_named_status,name,context.getString(status))
        setOnClickListener { open() }
        if (job.status != JobStatus.RUNNING) {
            setOnLongClickListener { actions(); true }
        }
        val frame = FrameLayout(context)
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (job.status == JobStatus.SUCCEEDED) setImageResource(job.resultImage)
            else setImageBitmap(BitmapFactory.decodeResource(context.resources,job.resultImage,BitmapFactory.Options().apply { inSampleSize = 32 }))
        }
        frame.addView(image,FrameLayout.LayoutParams(-1,-1))
        val shade = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0,context.color(R.color.ds_background_primary_alpha)))
        }
        frame.addView(shade,FrameLayout.LayoutParams(-1,context.dp(64),Gravity.BOTTOM))
        val title = context.text(name,R.style.TextAppearance_AiVideoTest_Caption1_Regular).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(title,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply { setMargins(context.dp(12),0,context.dp(12),context.dp(14)) })
        if (job.status != JobStatus.SUCCEEDED) {
            frame.addView(View(context).apply { setBackgroundColor(context.color(R.color.ds_label_primary_inverted_invariably)) },FrameLayout.LayoutParams(-1,-1))
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(context.dp(8),0,context.dp(8),0) }
            if (job.status == JobStatus.RUNNING) column.addView(CircularProgressIndicator(context).apply {
                isIndeterminate = true; setIndicatorColor(context.color(R.color.ds_accent_primary)); indicatorSize = context.dp(28)
            },LinearLayout.LayoutParams(context.dp(40),context.dp(40)).apply { bottomMargin = context.dp(8) })
            column.addView(context.text(context.getString(if (job.status == JobStatus.RUNNING) R.string.loading_generation else R.string.generation_error),R.style.TextAppearance_AiVideoTest_Caption1_Regular).apply { gravity = Gravity.CENTER })
            if (job.status == JobStatus.FAILED) column.addView(context.button(context.getString(R.string.read_more)).apply {
                setTextAppearance(R.style.TextAppearance_AiVideoTest_Caption1_Regular); setTextColor(context.color(R.color.ds_label_secondary))
                setOnClickListener { open() }; contentDescription = context.getString(R.string.creation_actions_accessibility,name)
            },LinearLayout.LayoutParams(-2,context.dp(48)))
            frame.addView(column,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        }
        addView(frame)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec((width * 1.78f).toInt(),View.MeasureSpec.EXACTLY))
    }
}
