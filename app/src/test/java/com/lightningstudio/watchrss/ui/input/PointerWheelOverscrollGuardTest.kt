package com.lightningstudio.watchrss.ui.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerWheelOverscrollGuardTest {
    @Test
    fun shouldConsumePointerWheelOverscroll_returnsTrueWithinGuardWindow() {
        assertTrue(
            shouldConsumePointerWheelOverscroll(
                lastPointerWheelEventAtUptimeMillis = 1_000L,
                nowUptimeMillis = 1_150L
            )
        )
    }

    @Test
    fun shouldConsumePointerWheelOverscroll_returnsFalseAfterGuardWindow() {
        assertFalse(
            shouldConsumePointerWheelOverscroll(
                lastPointerWheelEventAtUptimeMillis = 1_000L,
                nowUptimeMillis = 1_250L
            )
        )
    }

    @Test
    fun shouldConsumePointerWheelOverscroll_returnsFalseForInvalidTimestamps() {
        assertFalse(
            shouldConsumePointerWheelOverscroll(
                lastPointerWheelEventAtUptimeMillis = 0L,
                nowUptimeMillis = 1_000L
            )
        )
        assertFalse(
            shouldConsumePointerWheelOverscroll(
                lastPointerWheelEventAtUptimeMillis = 1_000L,
                nowUptimeMillis = 999L
            )
        )
    }
}
