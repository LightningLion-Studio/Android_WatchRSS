package com.lightningstudio.watchrss.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import kotlin.math.min

class WatchMaskLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val maskPath = Path()
    private var radius = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var shouldDrawFallbackMask = false
    private val roundOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val diameter = min(view.width, view.height)
            if (diameter <= 0) return
            val left = (view.width - diameter) / 2
            val top = (view.height - diameter) / 2
            outline.setOval(left, top, left + diameter, top + diameter)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = min(w, h) / 2f
        maskPath.reset()
        maskPath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        maskPath.addCircle(centerX, centerY, radius, Path.Direction.CW)
        maskPath.fillType = Path.FillType.EVEN_ODD
        val canUseOutlineClip = resources.configuration.isScreenRound &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        if (canUseOutlineClip) {
            clipToOutline = true
            outlineProvider = roundOutlineProvider
            shouldDrawFallbackMask = false
        } else {
            clipToOutline = false
            shouldDrawFallbackMask = resources.configuration.isScreenRound
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (shouldDrawFallbackMask && radius > 0f) {
            canvas.drawPath(maskPath, maskPaint)
        }
    }
}
