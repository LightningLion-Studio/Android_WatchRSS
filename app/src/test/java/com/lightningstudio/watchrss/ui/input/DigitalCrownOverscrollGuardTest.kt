package com.lightningstudio.watchrss.ui.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalCrownOverscrollGuardTest {
    @Test
    fun shouldConsumeDigitalCrownOverscroll_returnsTrueWithinGuardWindow() {
        assertTrue(
            shouldConsumeDigitalCrownOverscroll(
                lastDigitalCrownEventAtUptimeMillis = 1_000L,
                nowUptimeMillis = 1_150L
            )
        )
    }

    @Test
    fun shouldConsumeDigitalCrownOverscroll_returnsFalseAfterGuardWindow() {
        assertFalse(
            shouldConsumeDigitalCrownOverscroll(
                lastDigitalCrownEventAtUptimeMillis = 1_000L,
                nowUptimeMillis = 1_250L
            )
        )
    }

    @Test
    fun shouldConsumeDigitalCrownOverscroll_returnsFalseForInvalidTimestamps() {
        assertFalse(
            shouldConsumeDigitalCrownOverscroll(
                lastDigitalCrownEventAtUptimeMillis = 0L,
                nowUptimeMillis = 1_000L
            )
        )
        assertFalse(
            shouldConsumeDigitalCrownOverscroll(
                lastDigitalCrownEventAtUptimeMillis = 1_000L,
                nowUptimeMillis = 999L
            )
        )
    }
}
