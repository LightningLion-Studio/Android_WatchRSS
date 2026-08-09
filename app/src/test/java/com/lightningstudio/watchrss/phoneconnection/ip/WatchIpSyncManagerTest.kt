package com.lightningstudio.watchrss.phoneconnection.ip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchIpSyncManagerTest {
    @Test
    fun backgroundDisplayTimeout_doesNotStopAnActiveTransfer() {
        assertFalse(shouldStopIpTransport(resumedCount = 0, transferInProgress = true))
    }

    @Test
    fun idleBackgroundRoute_canBeStopped() {
        assertTrue(shouldStopIpTransport(resumedCount = 0, transferInProgress = false))
        assertFalse(shouldStopIpTransport(resumedCount = 1, transferInProgress = false))
    }

    @Test
    fun rfcommBootstrap_canCreateIpRouteWhileDisplayIsOff() {
        assertTrue(shouldScheduleIpRefresh(resumedCount = 0, rfcommBootstrap = true))
        assertFalse(shouldScheduleIpRefresh(resumedCount = 0, rfcommBootstrap = false))
        assertTrue(shouldScheduleIpRefresh(resumedCount = 1, rfcommBootstrap = false))
    }

    @Test
    fun mediaKeepAlive_requiresBothSettingAndActiveSyncLease() {
        assertFalse(shouldPlaySyncMediaKeepAlive(enabled = false, activeSessionOwners = 1))
        assertFalse(shouldPlaySyncMediaKeepAlive(enabled = true, activeSessionOwners = 0))
        assertTrue(shouldPlaySyncMediaKeepAlive(enabled = true, activeSessionOwners = 1))
        assertTrue(shouldPlaySyncMediaKeepAlive(enabled = true, activeSessionOwners = 2))
    }
}
