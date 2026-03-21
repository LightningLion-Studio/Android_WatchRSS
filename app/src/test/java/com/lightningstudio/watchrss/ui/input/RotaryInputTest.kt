package com.lightningstudio.watchrss.ui.input

import org.junit.Assert.assertEquals
import org.junit.Test

class RotaryInputTest {
    @Test
    fun normalizePointerWheelScrollDelta_preservesUnitDelta() {
        assertEquals(1f, normalizePointerWheelScrollDelta(1f), 0f)
    }

    @Test
    fun normalizePointerWheelScrollDelta_clampsLargeDelta() {
        assertEquals(1f, normalizePointerWheelScrollDelta(4f), 0f)
        assertEquals(-1f, normalizePointerWheelScrollDelta(-4f), 0f)
    }

    @Test
    fun normalizeRotaryVolumeDelta_keepsDirectionByDefault() {
        assertEquals(1f, normalizeRotaryVolumeDelta(1f, reverseDirection = false), 0f)
    }

    @Test
    fun normalizeRotaryVolumeDelta_reversesDirectionWhenRequested() {
        assertEquals(-1f, normalizeRotaryVolumeDelta(1f, reverseDirection = true), 0f)
    }

    @Test
    fun rotaryVolumeDirection_returnsNeutralForZeroDelta() {
        assertEquals(0, rotaryVolumeDirection(0f))
    }
}
