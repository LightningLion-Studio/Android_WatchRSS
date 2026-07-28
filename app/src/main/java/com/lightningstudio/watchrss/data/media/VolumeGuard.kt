package com.lightningstudio.watchrss.data.media

import com.lightningstudio.watchrss.data.media.DigitalCrownVolumeGuardState

data class DigitalCrownVolumeGuardResult(
    val targetVolume: Float,
    val nextState: DigitalCrownVolumeGuardState,
    val shouldNotifyGuardTriggered: Boolean
)

fun applyDigitalCrownVolumeGuard(
    currentVolume: Float,
    requestedDeltaVolume: Float,
    minVolume: Int,
    maxVolume: Int,
    guardEnabled: Boolean,
    sessionCapVolume: Float,
    previousState: DigitalCrownVolumeGuardState,
    eventUptimeMs: Long
): DigitalCrownVolumeGuardResult {
    val target = (currentVolume + requestedDeltaVolume).coerceIn(minVolume.toFloat(), maxVolume.toFloat())
    return DigitalCrownVolumeGuardResult(
        targetVolume = target,
        nextState = previousState,
        shouldNotifyGuardTriggered = false
    )
}

fun hasOutOfBandVolumeChange(observedVolume: Int, actualVolume: Int): Boolean = observedVolume != actualVolume

fun volumeForPercent(targetPercent: Float, minVolume: Int, maxVolume: Int): Float {
    return minVolume + (maxVolume - minVolume) * (targetPercent / 100f)
}

fun playbackStartVolumeForPercent(targetPercent: Int, minVolume: Int, maxVolume: Int): Int {
    return volumeForPercent(targetPercent.toFloat(), minVolume, maxVolume).toInt()
}

fun shouldEnforcePlaybackStartGuard(
    playbackStartVolumeLimitPercent: Int?,
    dismissedByUser: Boolean,
    currentVolume: Int,
    minVolume: Int,
    maxVolume: Int
): Boolean {
    if (dismissedByUser || playbackStartVolumeLimitPercent == null) return false
    val limitVolume = playbackStartVolumeForPercent(playbackStartVolumeLimitPercent, minVolume, maxVolume)
    return currentVolume > limitVolume
}
