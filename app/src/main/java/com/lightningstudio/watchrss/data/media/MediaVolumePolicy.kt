package com.lightningstudio.watchrss.data.media

import kotlin.math.roundToInt

private const val DIGITAL_CROWN_SESSION_IDLE_TIMEOUT_MS = 600L

internal data class DigitalCrownVolumeGuardState(
    val sessionCapVolume: Float? = null,
    val lastEventUptimeMs: Long = Long.MIN_VALUE,
    val guardNotificationShown: Boolean = false
)

internal data class DigitalCrownVolumeGuardResult(
    val targetVolume: Float,
    val nextState: DigitalCrownVolumeGuardState,
    val shouldNotifyGuardTriggered: Boolean = false
)

internal fun applyDigitalCrownVolumeGuard(
    currentVolume: Float,
    requestedDeltaVolume: Float,
    minVolume: Int,
    maxVolume: Int,
    guardEnabled: Boolean,
    sessionCapVolume: Float,
    previousState: DigitalCrownVolumeGuardState,
    eventUptimeMs: Long
): DigitalCrownVolumeGuardResult {
    val minVolumeFloat = minVolume.toFloat()
    val maxVolumeFloat = maxVolume.toFloat()
    val clampedCurrent = currentVolume.coerceIn(minVolumeFloat, maxVolumeFloat)
    if (requestedDeltaVolume == 0f) {
        return DigitalCrownVolumeGuardResult(
            targetVolume = clampedCurrent,
            nextState = previousState
        )
    }

    val isNewSession = previousState.lastEventUptimeMs == Long.MIN_VALUE ||
        eventUptimeMs - previousState.lastEventUptimeMs > DIGITAL_CROWN_SESSION_IDLE_TIMEOUT_MS
    val direction = requestedDeltaVolume.compareTo(0f)
    var activeCapVolume = if (isNewSession || direction <= 0 || !guardEnabled) {
        null
    } else {
        previousState.sessionCapVolume
    }
    var guardNotificationShown = if (activeCapVolume == null) {
        false
    } else {
        previousState.guardNotificationShown
    }
    var targetVolume = (clampedCurrent + requestedDeltaVolume).coerceIn(minVolumeFloat, maxVolumeFloat)
    var wasLimitedByGuard = false

    if (guardEnabled && direction > 0) {
        val capActivationVolume = volumeGuardActivationVolume(
            sessionCapVolume = sessionCapVolume,
            minVolume = minVolume,
            maxVolume = maxVolume
        )
        val effectiveCapVolume = activeCapVolume ?: if (clampedCurrent < capActivationVolume) {
            sessionCapVolume
        } else {
            null
        }
        if (effectiveCapVolume != null) {
            activeCapVolume = effectiveCapVolume
            val cappedTargetVolume = targetVolume.coerceAtMost(effectiveCapVolume)
            wasLimitedByGuard = cappedTargetVolume < targetVolume
            targetVolume = cappedTargetVolume
        }
    }
    val shouldNotifyGuardTriggered = wasLimitedByGuard && !guardNotificationShown
    if (shouldNotifyGuardTriggered) {
        guardNotificationShown = true
    }

    return DigitalCrownVolumeGuardResult(
        targetVolume = targetVolume,
        nextState = DigitalCrownVolumeGuardState(
            sessionCapVolume = activeCapVolume,
            lastEventUptimeMs = eventUptimeMs,
            guardNotificationShown = guardNotificationShown
        ),
        shouldNotifyGuardTriggered = shouldNotifyGuardTriggered
    )
}

internal fun shouldEnforcePlaybackStartGuard(
    playbackStartVolumeLimitPercent: Int?,
    dismissedByUser: Boolean,
    currentVolume: Int,
    minVolume: Int,
    maxVolume: Int
): Boolean {
    if (playbackStartVolumeLimitPercent == null || dismissedByUser) return false
    val targetVolume = playbackStartVolumeForPercent(
        targetPercent = playbackStartVolumeLimitPercent,
        minVolume = minVolume,
        maxVolume = maxVolume
    )
    return currentVolume.coerceIn(minVolume, maxVolume).toFloat() > targetVolume
}

internal fun hasOutOfBandVolumeChange(
    observedVolume: Int,
    actualVolume: Int
): Boolean {
    return observedVolume != actualVolume
}

internal fun volumeProgress(
    currentVolume: Int,
    minVolume: Int,
    maxVolume: Int
): Float {
    if (maxVolume <= minVolume) return 0f
    val clampedVolume = currentVolume.coerceIn(minVolume, maxVolume)
    val range = (maxVolume - minVolume).coerceAtLeast(1)
    return ((clampedVolume - minVolume).toFloat() / range.toFloat()).coerceIn(0f, 1f)
}

internal fun nearestPositiveVolumeForPercent(
    targetPercent: Float,
    minVolume: Int,
    maxVolume: Int
): Int {
    if (maxVolume <= minVolume) return minVolume
    if (targetPercent <= 0f) return minVolume
    val range = (maxVolume - minVolume).coerceAtLeast(1)
    val target = minVolume + (range * targetPercent).roundToInt()
    return target.coerceIn(minVolume + 1, maxVolume)
}

internal fun playbackStartVolumeForPercent(
    targetPercent: Int,
    minVolume: Int,
    maxVolume: Int
): Float {
    return volumeForPercent(
        targetPercent = targetPercent.coerceIn(0, 100) / 100f,
        minVolume = minVolume,
        maxVolume = maxVolume
    )
}

internal fun volumeForPercent(
    targetPercent: Float,
    minVolume: Int,
    maxVolume: Int
): Float {
    if (maxVolume <= minVolume) return minVolume.toFloat()
    if (targetPercent <= 0f) return minVolume.toFloat()
    val range = (maxVolume - minVolume).coerceAtLeast(1)
    val target = minVolume + (range * targetPercent)
    return target.coerceIn((minVolume + 1).toFloat(), maxVolume.toFloat())
}

internal fun volumeGuardActivationVolume(
    sessionCapVolume: Float,
    minVolume: Int,
    maxVolume: Int
): Float {
    if (maxVolume <= minVolume) return minVolume.toFloat()
    return sessionCapVolume
        .roundToInt()
        .coerceIn(minVolume, maxVolume)
        .toFloat()
}
