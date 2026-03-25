package com.lightningstudio.watchrss.ui.input

import org.junit.Assert.assertEquals
import org.junit.Test

class DigitalCrownInputTest {
    @Test
    fun normalizeDigitalCrownScrollDelta_preservesUnitDelta() {
        assertEquals(1f, normalizeDigitalCrownScrollDelta(1f), 0f)
    }

    @Test
    fun normalizeDigitalCrownScrollDelta_clampsLargeDelta() {
        assertEquals(1f, normalizeDigitalCrownScrollDelta(4f), 0f)
        assertEquals(-1f, normalizeDigitalCrownScrollDelta(-4f), 0f)
    }

    @Test
    fun normalizeDigitalCrownVolumeDelta_keepsDirectionByDefault() {
        assertEquals(1f, normalizeDigitalCrownVolumeDelta(1f, reverseDirection = false), 0f)
    }

    @Test
    fun normalizeDigitalCrownVolumeDelta_reversesDirectionWhenRequested() {
        assertEquals(-1f, normalizeDigitalCrownVolumeDelta(1f, reverseDirection = true), 0f)
    }

    @Test
    fun digitalCrownVolumeDirection_returnsNeutralForZeroDelta() {
        assertEquals(0, digitalCrownVolumeDirection(0f))
    }
}
