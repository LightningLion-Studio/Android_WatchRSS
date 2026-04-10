package com.lightningstudio.watchrss.data.douyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DouyinCodecRuntimePolicyTest {
    @Before
    fun setUp() {
        DouyinCodecRuntimePolicy.resetForTests()
    }

    @Test
    fun autoModeDoesNotForceH264BeforeEnoughSamples() {
        DouyinCodecRuntimePolicy.recordAutoHevcAttempt()
        DouyinCodecRuntimePolicy.recordAutoHevcFailure()

        assertFalse(DouyinCodecRuntimePolicy.shouldPreferH264InAutoMode())
        assertEquals(
            DouyinCodecRuntimeSnapshot(
                autoHevcAttempts = 1,
                autoHevcFailures = 1,
                autoForceH264 = false
            ),
            DouyinCodecRuntimePolicy.snapshot()
        )
    }

    @Test
    fun autoModeForcesH264WhenMoreThanHalfOfHevcAttemptsFail() {
        DouyinCodecRuntimePolicy.recordAutoHevcAttempt()
        DouyinCodecRuntimePolicy.recordAutoHevcFailure()
        DouyinCodecRuntimePolicy.recordAutoHevcAttempt()
        DouyinCodecRuntimePolicy.recordAutoHevcFailure()

        assertTrue(DouyinCodecRuntimePolicy.shouldPreferH264InAutoMode())
    }

    @Test
    fun autoModeKeepsForcingH264AfterThresholdEvenIfLaterSuccessesArrive() {
        DouyinCodecRuntimePolicy.recordAutoHevcAttempt()
        DouyinCodecRuntimePolicy.recordAutoHevcFailure()
        DouyinCodecRuntimePolicy.recordAutoHevcAttempt()
        DouyinCodecRuntimePolicy.recordAutoHevcFailure()

        assertTrue(DouyinCodecRuntimePolicy.shouldPreferH264InAutoMode())

        DouyinCodecRuntimePolicy.recordAutoHevcAttempt()
        DouyinCodecRuntimePolicy.recordAutoHevcAttempt()

        assertTrue(DouyinCodecRuntimePolicy.shouldPreferH264InAutoMode())
        assertEquals(
            DouyinCodecRuntimeSnapshot(
                autoHevcAttempts = 4,
                autoHevcFailures = 2,
                autoForceH264 = true
            ),
            DouyinCodecRuntimePolicy.snapshot()
        )
    }
}
