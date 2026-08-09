package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoScrollControlScreenTest {
    @Test
    fun formatAutoScrollSpeed_keepsOneDecimalPlace() {
        assertEquals("0.5", formatAutoScrollSpeed(0.5f))
        assertEquals("2.0", formatAutoScrollSpeed(2f))
        assertEquals("10.0", formatAutoScrollSpeed(10f))
    }

    @Test
    fun autoScroll_stopsOnlyWhenListCannotScrollForward() {
        assertEquals(false, shouldStopReaderAutoScroll(canScrollForward = true))
        assertEquals(true, shouldStopReaderAutoScroll(canScrollForward = false))
    }

    @Test
    fun autoScroll_accumulatesSubPixelMovementBeforeScrolling() {
        assertEquals(0f, wholePixelAutoScrollDelta(0.4f))
        assertEquals(1f, wholePixelAutoScrollDelta(1.2f))
        assertEquals(3f, wholePixelAutoScrollDelta(3.9f))
    }
}
