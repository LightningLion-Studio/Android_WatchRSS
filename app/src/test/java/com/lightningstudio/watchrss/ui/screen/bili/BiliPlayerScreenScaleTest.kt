package com.lightningstudio.watchrss.ui.screen.bili

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliPlayerScreenScaleTest {

    @Test
    fun standardModeKeepsFitScalingForWideVideo() {
        val scale = calculatePlayerScale(
            viewWidth = 400f,
            viewHeight = 400f,
            videoWidth = 1600f,
            videoHeight = 900f,
            scaleMode = PlayerScaleMode.Standard
        )

        assertEquals(1.0, scale.scaleX.toDouble(), 0.0001)
        assertEquals(0.5625, scale.scaleY.toDouble(), 0.0001)
    }

    @Test
    fun expandedModeKeepsFillScalingForWideVideo() {
        val scale = calculatePlayerScale(
            viewWidth = 400f,
            viewHeight = 400f,
            videoWidth = 1600f,
            videoHeight = 900f,
            scaleMode = PlayerScaleMode.Expanded
        )

        assertEquals(1.7778, scale.scaleX.toDouble(), 0.0001)
        assertEquals(1.0, scale.scaleY.toDouble(), 0.0001)
    }

    @Test
    fun shrunkModeMatchesScreenWidthDiagonal() {
        val standardScale = calculatePlayerScale(
            viewWidth = 400f,
            viewHeight = 400f,
            videoWidth = 1600f,
            videoHeight = 900f,
            scaleMode = PlayerScaleMode.Standard
        )
        val shrunkScale = calculatePlayerScale(
            viewWidth = 400f,
            viewHeight = 400f,
            videoWidth = 1600f,
            videoHeight = 900f,
            scaleMode = PlayerScaleMode.Shrunk
        )

        val contentWidth = 400f * shrunkScale.scaleX
        val contentHeight = 400f * shrunkScale.scaleY
        val diagonal = sqrt(contentWidth * contentWidth + contentHeight * contentHeight)

        assertEquals(400.0, diagonal.toDouble(), 0.001)
        assertTrue(shrunkScale.scaleX < standardScale.scaleX)
        assertTrue(shrunkScale.scaleY < standardScale.scaleY)
    }
}
