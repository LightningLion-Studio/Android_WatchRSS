package com.lightningstudio.watchrss.phoneconnection.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSyncSessionDecisionTest {
    @Test
    fun successfulLibraryExchange_continuesPersistentSession() {
        assertTrue(
            shouldContinuePersistentSessionAfterLibraryExchange(terminateSession = false)
        )
    }

    @Test
    fun failedLibraryExchange_terminatesPersistentSession() {
        assertFalse(
            shouldContinuePersistentSessionAfterLibraryExchange(terminateSession = true)
        )
    }
}
