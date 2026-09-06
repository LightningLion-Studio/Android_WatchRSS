package com.lightningstudio.watchrss.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.ceil

/** Android 11 fallback; only transient decoded pixels change, never the received resource. */
@Suppress("DEPRECATION")
internal class ReaderBackgroundBlurTransformation(
    private val context: Context,
    private val radiusPx: Float
) : Transformation {
    override val cacheKey = "reader-blur-v1:$radiusPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // ScriptIntrinsicBlur accepts a radius up to 25. Downsampling keeps wider blur bounded.
        val sample = ceil(radiusPx / 25f).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, (input.width / sample).coerceAtLeast(1),
            (input.height / sample).coerceAtLeast(1), true)
        val software = small.copy(Bitmap.Config.ARGB_8888, true)
        if (small !== input) small.recycle()
        val rs = RenderScript.create(context.applicationContext)
        var allocation: Allocation? = null
        var output: Allocation? = null
        var blur: ScriptIntrinsicBlur? = null
        try {
            allocation = Allocation.createFromBitmap(rs, software)
            output = Allocation.createTyped(rs, allocation.type)
            blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius((radiusPx / sample).coerceIn(.1f, 25f))
            blur.setInput(allocation)
            blur.forEach(output)
            output.copyTo(software)
            return Bitmap.createScaledBitmap(software, input.width, input.height, true).let {
                if (it !== software) software.recycle()
                it
            }
        } catch (failure: Throwable) {
            software.recycle()
            throw failure
        } finally {
            blur?.destroy()
            output?.destroy()
            allocation?.destroy()
            rs.destroy()
        }
    }
}
