package com.lightningstudio.watchrss.ui.screen.douyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinImmersiveScreenScaleTest {

    @Test
    fun scaleModeCyclesLikeBiliPlayer() {
        assertEquals(DouyinPlayerScaleMode.Expanded, DouyinPlayerScaleMode.Standard.next())
        assertEquals(DouyinPlayerScaleMode.Shrunk, DouyinPlayerScaleMode.Expanded.next())
        assertEquals(DouyinPlayerScaleMode.Standard, DouyinPlayerScaleMode.Shrunk.next())
    }

    @Test
    fun shrunkModeMatchesStandardDiagonalConstraintForWideVideo() {
        val shrinkFactor = calculateDouyinPlayerShrinkFactor(
            viewWidth = 400f,
            viewHeight = 400f,
            videoWidth = 1600f,
            videoHeight = 900f
        )

        val standardContentWidth = 400f
        val standardContentHeight = 225f
        val shrunkContentWidth = standardContentWidth * shrinkFactor
        val shrunkContentHeight = standardContentHeight * shrinkFactor
        val diagonal = kotlin.math.sqrt(
            shrunkContentWidth * shrunkContentWidth +
                shrunkContentHeight * shrunkContentHeight
        )

        assertEquals(400.0, diagonal.toDouble(), 0.001)
        assertTrue(shrinkFactor < 1f)
    }
}
