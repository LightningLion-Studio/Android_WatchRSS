package com.lightningstudio.watchrss.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerVolumeOverlayPolicyTest {
    @Test
    fun applyRotaryVolumeGuard_capsFirstContinuousRaiseFromLowVolume() {
        val first = applyRotaryVolumeGuard(
            currentVolume = 1,
            requestedSteps = 10,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3,
            previousState = RotaryVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        assertEquals(3, first.targetVolume)

        val second = applyRotaryVolumeGuard(
            currentVolume = first.targetVolume,
            requestedSteps = 10,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3,
            previousState = first.nextState,
            eventUptimeMs = 1_200L
        )

        assertEquals(3, second.targetVolume)
    }

    @Test
    fun applyRotaryVolumeGuard_allowsFurtherRaiseAfterPause() {
        val first = applyRotaryVolumeGuard(
            currentVolume = 1,
            requestedSteps = 10,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3,
            previousState = RotaryVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        val second = applyRotaryVolumeGuard(
            currentVolume = first.targetVolume,
            requestedSteps = 10,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3,
            previousState = first.nextState,
            eventUptimeMs = 1_700L
        )

        assertEquals(13, second.targetVolume)
    }

    @Test
    fun applyRotaryVolumeGuard_doesNotCapWhenGuardDisabled() {
        val result = applyRotaryVolumeGuard(
            currentVolume = 1,
            requestedSteps = 10,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = false,
            sessionCapVolume = 3,
            previousState = RotaryVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        assertEquals(11, result.targetVolume)
    }

    @Test
    fun percentVolumeHelpers_keepTargetsAboveMuteWhenPossible() {
        assertEquals(1, nearestPositiveVolumeForPercent(0.07f, minVolume = 0, maxVolume = 10))
        assertEquals(1, highestSafeVolumeForPercent(0.16f, minVolume = 0, maxVolume = 10))
    }

    @Test
    fun shouldEnforcePlaybackStartGuard_returnsFalseAfterUserDismissal() {
        val result = shouldEnforcePlaybackStartGuard(
            guardEnabled = true,
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
            guardEnabled = true,
            dismissedByUser = false,
            currentVolume = 5,
            minVolume = 0,
            maxVolume = 10
        )

        assertEquals(true, result)
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
