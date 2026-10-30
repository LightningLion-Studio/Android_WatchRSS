package com.lightningstudio.watchrss.ui.components

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class ReadAloudFloatingControlsTest {
    @Test
    fun roundDock_keepsAllButtonCornersInsideCircularScreen() {
        for (width in listOf(192f, 320f, 466f)) {
            for (fraction in listOf(-2f, 0.2f, 0.5f, 0.8f, 2f)) {
                for (right in listOf(false, true)) {
                    val size = 40f
                    val offset = readAloudDockPosition(width, width, size, fraction, right, true)
                    for (x in listOf(offset.x, offset.x + size)) {
                        for (y in listOf(offset.y, offset.y + size)) {
                            assertTrue(hypot(x - width / 2f, y - width / 2f) <= width / 2f)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun rectangularDock_snapsToSelectedEdgeAndClampsSmallViewports() {
        assertEquals(4f, readAloudDockPosition(320f, 240f, 40f, 0.5f, false, false).x)
        assertEquals(276f, readAloudDockPosition(320f, 240f, 40f, 0.5f, true, false).x)
        val small = readAloudDockPosition(20f, 20f, 40f, 1f, true, true)
        assertEquals(0f, small.x)
        assertEquals(0f, small.y)
    }
}
