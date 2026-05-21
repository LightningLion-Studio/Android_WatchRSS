package com.lightningstudio.watchrss.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerVolumeOverlayPolicyTest {
    @Test
    fun applyDigitalCrownVolumeGuard_capsFirstContinuousRaiseFromLowVolume() {
        val first = applyDigitalCrownVolumeGuard(
            currentVolume = 1f,
            requestedDeltaVolume = 10f,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3f,
            previousState = DigitalCrownVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        assertEquals(3f, first.targetVolume, 0.001f)
        assertEquals(true, first.shouldNotifyGuardTriggered)

        val second = applyDigitalCrownVolumeGuard(
            currentVolume = first.targetVolume,
            requestedDeltaVolume = 10f,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3f,
            previousState = first.nextState,
            eventUptimeMs = 1_200L
        )

        assertEquals(3f, second.targetVolume, 0.001f)
        assertEquals(false, second.shouldNotifyGuardTriggered)
    }

    @Test
    fun applyDigitalCrownVolumeGuard_allowsFurtherRaiseAfterPause() {
        val first = applyDigitalCrownVolumeGuard(
            currentVolume = 1f,
            requestedDeltaVolume = 10f,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3f,
            previousState = DigitalCrownVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        val second = applyDigitalCrownVolumeGuard(
            currentVolume = first.targetVolume,
            requestedDeltaVolume = 10f,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3f,
            previousState = first.nextState,
            eventUptimeMs = 1_700L
        )

        assertEquals(13f, second.targetVolume, 0.001f)
    }

    @Test
    fun applyDigitalCrownVolumeGuard_doesNotCapWhenGuardDisabled() {
        val result = applyDigitalCrownVolumeGuard(
            currentVolume = 1f,
            requestedDeltaVolume = 10f,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = false,
            sessionCapVolume = 3f,
            previousState = DigitalCrownVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        assertEquals(11f, result.targetVolume, 0.001f)
        assertEquals(false, result.shouldNotifyGuardTriggered)
    }

    @Test
    fun applyDigitalCrownVolumeGuard_capsFirstRaiseAtTwentyOnePercentOnSixteenStepStreams() {
        val result = applyDigitalCrownVolumeGuard(
            currentVolume = 1f,
            requestedDeltaVolume = 10f,
            minVolume = 0,
            maxVolume = 16,
            guardEnabled = true,
            sessionCapVolume = volumeForPercent(0.21f, minVolume = 0, maxVolume = 16),
            previousState = DigitalCrownVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        assertEquals(3.36f, result.targetVolume, 0.001f)
        assertEquals(true, result.shouldNotifyGuardTriggered)
    }

    @Test
    fun percentVolumeHelpers_keepTargetsAboveMuteWhenPossible() {
        assertEquals(1, nearestPositiveVolumeForPercent(0.06f, minVolume = 0, maxVolume = 10))
        assertEquals(2.1f, volumeForPercent(0.21f, minVolume = 0, maxVolume = 10), 0.001f)
    }

    @Test
    fun volumeForPercent_keepsTwentyOnePercentOnSixteenStepStreams() {
        assertEquals(3.36f, volumeForPercent(0.21f, minVolume = 0, maxVolume = 16), 0.001f)
    }

    @Test
    fun shouldEnforcePlaybackStartGuard_returnsFalseAfterUserDismissal() {
        val result = shouldEnforcePlaybackStartGuard(
            playbackStartVolumeLimitPercent = 10,
            dismissedByUser = true,
            currentVolume = 5,
            minVolume = 0,
            maxVolume = 10
        )

        assertEquals(false, result)
    }

    @Test
    fun shouldEnforcePlaybackStartGuard_returnsTrueForLoudPlaybackWithoutUserOverride() {
        val result = shouldEnforcePlaybackStartGuard(
            playbackStartVolumeLimitPercent = 10,
            dismissedByUser = false,
            currentVolume = 5,
            minVolume = 0,
            maxVolume = 10
        )

        assertEquals(true, result)
    }

    @Test
    fun shouldEnforcePlaybackStartGuard_returnsFalseWhenUnlimited() {
        val result = shouldEnforcePlaybackStartGuard(
            playbackStartVolumeLimitPercent = null,
            dismissedByUser = false,
            currentVolume = 5,
            minVolume = 0,
            maxVolume = 10
        )

        assertEquals(false, result)
    }

    @Test
    fun shouldEnforcePlaybackStartGuard_allowsMuteLimit() {
        val result = shouldEnforcePlaybackStartGuard(
            playbackStartVolumeLimitPercent = 0,
            dismissedByUser = false,
            currentVolume = 1,
            minVolume = 0,
            maxVolume = 10
        )

        assertEquals(true, result)
        assertEquals(0f, playbackStartVolumeForPercent(0, minVolume = 0, maxVolume = 10), 0.001f)
    }

    @Test
    fun shouldEnforcePlaybackStartGuard_returnsFalseWhenCurrentIsNotAboveTargetStep() {
        val result = shouldEnforcePlaybackStartGuard(
            playbackStartVolumeLimitPercent = 10,
            dismissedByUser = false,
            currentVolume = 1,
            minVolume = 0,
            maxVolume = 10
        )

        assertEquals(false, result)
    }

    @Test
    fun playbackStartVolumeForPercent_mapsTenPercentOnSixteenStepStreams() {
        assertEquals(1.6f, playbackStartVolumeForPercent(10, minVolume = 0, maxVolume = 16), 0.001f)
    }

    @Test
    fun shouldEnforcePlaybackStartGuard_comparesAgainstFloatTargetOnSixteenStepStreams() {
        val belowFloatTarget = shouldEnforcePlaybackStartGuard(
            playbackStartVolumeLimitPercent = 10,
            dismissedByUser = false,
            currentVolume = 1,
            minVolume = 0,
            maxVolume = 16
        )
        val aboveFloatTarget = shouldEnforcePlaybackStartGuard(
            playbackStartVolumeLimitPercent = 10,
            dismissedByUser = false,
            currentVolume = 2,
            minVolume = 0,
            maxVolume = 16
        )

        assertEquals(false, belowFloatTarget)
        assertEquals(true, aboveFloatTarget)
    }

    @Test
    fun playbackStartVolumeForPercent_usesFloatPercentTargets() {
        assertEquals(1f, playbackStartVolumeForPercent(5, minVolume = 0, maxVolume = 16), 0.001f)
        assertEquals(2.4f, playbackStartVolumeForPercent(15, minVolume = 0, maxVolume = 16), 0.001f)
        assertEquals(16f, playbackStartVolumeForPercent(100, minVolume = 0, maxVolume = 16), 0.001f)
    }

    @Test
    fun hasOutOfBandVolumeChange_returnsTrueWhenSystemVolumeChangedExternally() {
        val result = hasOutOfBandVolumeChange(
            observedVolume = 1,
            actualVolume = 5
        )

        assertEquals(true, result)
    }

    @Test
    fun hasOutOfBandVolumeChange_returnsFalseWhenObservedVolumeMatchesSystem() {
        val result = hasOutOfBandVolumeChange(
            observedVolume = 5,
            actualVolume = 5
        )

        assertEquals(false, result)
    }
}
