package com.lightningstudio.watchrss.data.cloud

import org.junit.Assert.*
import org.junit.Test

class WatchCloudControlsTest {
    @Test fun smallStatePolicyDefersBodiesOnMeteredNetwork() {
        assertEquals(true to false, cloudTransferAllowed(WatchCloudNetworkPolicy.WIFI_BODIES_ANY_STATE, false, true, false, false))
        assertEquals(true to true, cloudTransferAllowed(WatchCloudNetworkPolicy.WIFI_BODIES_ANY_STATE, false, true, true, false))
    }
    @Test fun chargingPolicyWaitsForBothConditionsButExplicitManualCanOverride() {
        val policy = WatchCloudNetworkPolicy.WIFI_AND_CHARGING
        assertEquals(false to false, cloudTransferAllowed(policy, false, true, true, false))
        assertEquals(false to false, cloudTransferAllowed(policy, false, true, false, true))
        assertEquals(true to true, cloudTransferAllowed(policy, false, true, true, true))
        assertEquals(true to true, cloudTransferAllowed(policy, true, true, false, false))
    }
    @Test fun noPolicyCanTransferWithoutNetworkAndAnyNetworkIsExplicit() {
        WatchCloudNetworkPolicy.entries.forEach { assertEquals(false to false, cloudTransferAllowed(it, true, false, true, true)) }
        assertEquals(true to true, cloudTransferAllowed(WatchCloudNetworkPolicy.ANY_NETWORK, false, true, false, false))
        assertFalse(WatchCloudConfiguration().automatic)
    }
}
