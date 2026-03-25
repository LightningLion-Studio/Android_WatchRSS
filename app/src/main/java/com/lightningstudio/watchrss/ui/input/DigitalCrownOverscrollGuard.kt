package com.lightningstudio.watchrss.ui.input

import android.os.SystemClock

internal object DigitalCrownScrollTracker {
    @Volatile
    private var lastScrollEventAtUptimeMillis: Long = 0L

    fun onScrollEvent(eventTime: Long) {
        lastScrollEventAtUptimeMillis = if (eventTime > 0L) {
            eventTime
        } else {
            SystemClock.uptimeMillis()
        }
    }

    fun isRecentDigitalCrownInput(nowUptimeMillis: Long = SystemClock.uptimeMillis()): Boolean {
        return shouldConsumeDigitalCrownOverscroll(
            lastDigitalCrownEventAtUptimeMillis = lastScrollEventAtUptimeMillis,
            nowUptimeMillis = nowUptimeMillis
        )
    }
}

internal fun shouldConsumeDigitalCrownOverscroll(
    lastDigitalCrownEventAtUptimeMillis: Long,
    nowUptimeMillis: Long,
    thresholdMillis: Long = DIGITAL_CROWN_OVERSCROLL_GUARD_WINDOW_MS
): Boolean {
    if (lastDigitalCrownEventAtUptimeMillis <= 0L) return false
    if (nowUptimeMillis < lastDigitalCrownEventAtUptimeMillis) return false
    return nowUptimeMillis - lastDigitalCrownEventAtUptimeMillis <= thresholdMillis
}

private const val DIGITAL_CROWN_OVERSCROLL_GUARD_WINDOW_MS = 200L
