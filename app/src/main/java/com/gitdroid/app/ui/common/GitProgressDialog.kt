package com.gitdroid.app.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.gitdroid.app.R

class GitProgressDialog(context: Context) : Dialog(context, R.style.Theme_GitDroid_ProgressDialog) {

    private val tvTask: TextView
    private val tvTitle: TextView
    private val progressBar: ProgressBar
    private val tvPercent: TextView

    init {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 48, 56, 48)
            background = resolveCardBackground(context)
        }

        tvTitle = TextView(context).apply {
            text = context.getString(R.string.processing)
            setTextColor(context.getColor(R.color.on_surface))
            textSize = 17f
            setPadding(0, 0, 0, 24)
        }
        container.addView(tvTitle)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            container.addView(this, lp)
        }
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
            val lp = LinearLayout.LayoutParams(0, 36, 1f)
            row.addView(this, lp)
        }
        tvPercent = TextView(context).apply {
            text = "0%"
            setTextColor(context.getColor(R.color.primary))
            textSize = 13f
            setPadding(24, 0, 0, 0)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            row.addView(this, lp)
        }

        tvTask = TextView(context).apply {
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 13f
            setPadding(4, 20, 0, 0)
            container.addView(this)
        }

        setContentView(container)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    fun setTitle(text: String) {
        tvTitle.text = text
    }

    fun update(taskName: String, percent: Int) {
        tvPercent.text = "$percent%"
        progressBar.progress = percent.coerceIn(0, 100)
        tvTask.text = if (taskName.isNotEmpty()) taskName else ""
    }

    fun setIndeterminate(indeterminate: Boolean) {
        progressBar.isIndeterminate = indeterminate
        if (indeterminate) tvPercent.text = "…" else if (progressBar.progress == 0) tvPercent.text = "0%"
    }
}

object GitProgressDialogFactory {
    fun show(context: Context, title: String = context.getString(R.string.processing)): GitProgressDialog {
        return GitProgressDialog(context).apply {
            setTitle(title)
            setIndeterminate(true)
            show()
        }
    }
}

private fun resolveCardBackground(context: Context): android.graphics.drawable.Drawable {
    val ta = context.obtainStyledAttributes(
        intArrayOf(com.google.android.material.R.attr.colorSurface)
    )
    val color = ta.getColor(0, Color.WHITE)
    ta.recycle()
    val drawable = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = 28f
        setStroke(1, context.getColor(R.color.divider))
    }
    return drawable
}