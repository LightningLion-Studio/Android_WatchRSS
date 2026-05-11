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
            sessionCapVolume = 3,
            previousState = DigitalCrownVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        assertEquals(3f, first.targetVolume, 0.001f)

        val second = applyDigitalCrownVolumeGuard(
            currentVolume = first.targetVolume,
            requestedDeltaVolume = 10f,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3,
            previousState = first.nextState,
            eventUptimeMs = 1_200L
        )

        assertEquals(3f, second.targetVolume, 0.001f)
    }

    @Test
    fun applyDigitalCrownVolumeGuard_allowsFurtherRaiseAfterPause() {
        val first = applyDigitalCrownVolumeGuard(
            currentVolume = 1f,
            requestedDeltaVolume = 10f,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3,
            previousState = DigitalCrownVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        val second = applyDigitalCrownVolumeGuard(
            currentVolume = first.targetVolume,
            requestedDeltaVolume = 10f,
            minVolume = 0,
            maxVolume = 20,
            guardEnabled = true,
            sessionCapVolume = 3,
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
            sessionCapVolume = 3,
            previousState = DigitalCrownVolumeGuardState(),
            eventUptimeMs = 1_000L
        )

        assertEquals(11f, result.targetVolume, 0.001f)
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
