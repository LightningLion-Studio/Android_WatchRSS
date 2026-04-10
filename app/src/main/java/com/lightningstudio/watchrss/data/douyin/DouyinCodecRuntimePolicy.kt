package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.util.AppLogger

internal object DouyinCodecRuntimePolicy {
    private const val MIN_AUTO_HEVC_SAMPLES = 2

    private var autoHevcAttempts = 0
    private var autoHevcFailures = 0
    private var autoForceH264 = false

    @Synchronized
    fun shouldPreferH264InAutoMode(): Boolean = autoForceH264

    @Synchronized
    fun recordAutoHevcAttempt(): Boolean {
        autoHevcAttempts += 1
        return updateAutoForceStateLocked()
    }

    @Synchronized
    fun recordAutoHevcFailure(): Boolean {
        autoHevcFailures = (autoHevcFailures + 1).coerceAtMost(autoHevcAttempts.coerceAtLeast(1))
        return updateAutoForceStateLocked()
    }

    @Synchronized
    fun snapshot(): DouyinCodecRuntimeSnapshot {
        return DouyinCodecRuntimeSnapshot(
            autoHevcAttempts = autoHevcAttempts,
            autoHevcFailures = autoHevcFailures,
            autoForceH264 = autoForceH264
        )
    }

    @Synchronized
    fun resetForTests() {
        autoHevcAttempts = 0
        autoHevcFailures = 0
        autoForceH264 = false
    }

    private fun updateAutoForceStateLocked(): Boolean {
        val previous = autoForceH264
        autoForceH264 = autoForceH264 ||
            (
                autoHevcAttempts >= MIN_AUTO_HEVC_SAMPLES &&
                    autoHevcFailures * 2 > autoHevcAttempts
                )
        if (previous != autoForceH264) {
            AppLogger.d(
                TAG,
                "auto codec policy forceH264=$autoForceH264 attempts=$autoHevcAttempts failures=$autoHevcFailures"
            )
        }
        return previous != autoForceH264
    }

    private const val TAG = "DouyinCodecPolicy"
}

internal data class DouyinCodecRuntimeSnapshot(
    val autoHevcAttempts: Int,
    val autoHevcFailures: Int,
    val autoForceH264: Boolean
)
