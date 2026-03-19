package com.lightningstudio.watchrss.ui.input

import android.os.SystemClock

internal object PointerWheelScrollTracker {
    @Volatile
    private var lastScrollEventAtUptimeMillis: Long = 0L

    fun onScrollEvent(eventTime: Long) {
        lastScrollEventAtUptimeMillis = if (eventTime > 0L) {
            eventTime
        } else {
            SystemClock.uptimeMillis()
        }
    }

    fun isRecentPointerWheelScroll(nowUptimeMillis: Long = SystemClock.uptimeMillis()): Boolean {
        return shouldConsumePointerWheelOverscroll(
            lastPointerWheelEventAtUptimeMillis = lastScrollEventAtUptimeMillis,
            nowUptimeMillis = nowUptimeMillis
        )
    }
}

internal fun shouldConsumePointerWheelOverscroll(
    lastPointerWheelEventAtUptimeMillis: Long,
    nowUptimeMillis: Long,
    thresholdMillis: Long = POINTER_WHEEL_OVERSCROLL_GUARD_WINDOW_MS
): Boolean {
    if (lastPointerWheelEventAtUptimeMillis <= 0L) return false
    if (nowUptimeMillis < lastPointerWheelEventAtUptimeMillis) return false
    return nowUptimeMillis - lastPointerWheelEventAtUptimeMillis <= thresholdMillis
}

private const val POINTER_WHEEL_OVERSCROLL_GUARD_WINDOW_MS = 200L
