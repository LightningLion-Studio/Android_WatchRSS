package com.lightningstudio.watchrss.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternetAvailabilityMonitorTest {
    @Test
    fun parseDebugForcedStatusSupportsBluetoothAliases() {
        assertEquals(
            InternetAvailabilityStatus.Bluetooth,
            DefaultInternetAvailabilityMonitor.parseDebugForcedStatus("bluetooth")
        )
        assertEquals(
            InternetAvailabilityStatus.Bluetooth,
            DefaultInternetAvailabilityMonitor.parseDebugForcedStatus(" bt ")
        )
    }

    @Test
    fun parseDebugForcedStatusSupportsAvailableAndUnavailableAliases() {
        assertEquals(
            InternetAvailabilityStatus.Available,
            DefaultInternetAvailabilityMonitor.parseDebugForcedStatus("wifi")
        )
        assertEquals(
            InternetAvailabilityStatus.Unavailable,
            DefaultInternetAvailabilityMonitor.parseDebugForcedStatus("offline")
        )
    }

    @Test
    fun parseDebugForcedStatusIgnoresUnknownValues() {
        assertNull(DefaultInternetAvailabilityMonitor.parseDebugForcedStatus(null))
        assertNull(DefaultInternetAvailabilityMonitor.parseDebugForcedStatus(""))
        assertNull(DefaultInternetAvailabilityMonitor.parseDebugForcedStatus("real"))
    }
}
