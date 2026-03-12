package com.lightningstudio.watchrss.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Trace
import android.util.AttributeSet
import android.view.View
import com.lightningstudio.watchrss.BuildConfig
import kotlin.math.min

class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val strokeWidthPx = 12f
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = 0xFF202124.toInt()
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = 0xFF476CFF.toInt()
    }
    private val arcBounds = RectF()
    private var progress = 0f
    private var showBase = true

    fun setProgress(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (clamped == progress) return
        progress = clamped
        postInvalidateOnAnimation()
    }

    fun setShowBase(value: Boolean) {
        if (showBase == value) return
        showBase = value
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        if (BuildConfig.DEBUG) {
            Trace.beginSection("ProgressRingView#onDraw")
        }
        try {
            super.onDraw(canvas)
            val size = min(width, height).toFloat()
            if (size <= 0f) return
            val radius = size / 2f - strokeWidthPx / 2f
            val cx = width / 2f
            val cy = height / 2f
            if (showBase) {
                canvas.drawCircle(cx, cy, radius, basePaint)
            }
            if (progress <= 0f) return
            arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arcBounds, -90f, progress * 360f, false, progressPaint)
        } finally {
            if (BuildConfig.DEBUG) {
                Trace.endSection()
            }
        }
    }
}
