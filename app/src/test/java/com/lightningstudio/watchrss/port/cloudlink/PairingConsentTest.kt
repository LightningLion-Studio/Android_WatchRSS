package com.lightningstudio.watchrss.port.cloudlink

import com.lightningstudio.watchrss.phoneconnection.provisioning.BleSetupCrypto
import com.lightningstudio.watchrss.phoneconnection.provisioning.PairingConsent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PairingConsentTest {
    @Test fun phoneAndWatchMayConfirmInEitherOrderWithoutConfiguringEarly() {
        for (watchFirst in listOf(true, false)) {
            val consent = PairingConsent("user", "device", "install", "12345678", false, { 0L })
            val initial = consent.receipt("spki")
            assertEquals("pending", initial.getJSONObject("confirmation").getString("watch"))
            assertEquals("12345678", initial.getString("sas"))
            assertEquals(15, initial.getInt("messageSequenceMax")) // backend main fence; phone requires 255
            assertFalse(initial.getJSONObject("capabilities").getBoolean("pairingReplaceAccountV1"))
            if (watchFirst) consent.watchDecision(true) else consent.update(request("confirmed"))
            assertFalse(consent.bothConfirmed)
            if (watchFirst) consent.update(request("confirmed")) else consent.watchDecision(true)
            assertTrue(consent.bothConfirmed)
            consent.update(request("confirmed")) // exact retries remain safe
            assertThrows(IllegalArgumentException::class.java) { consent.update(request("pending")) }
        }
    }
    @Test fun rejectionConflictExpiryAndIdentityCannotGrantConsent() {
        val rejected = PairingConsent("user", "device", "install", "12345678", false, { 0L })
        rejected.watchDecision(false); rejected.update(request("confirmed"))
        assertTrue(rejected.rejected); assertFalse(rejected.bothConfirmed)
        assertThrows(IllegalArgumentException::class.java) { rejected.watchDecision(true) }
        val conflict = PairingConsent("user", "device", "install", "12345678", true, { 0L })
        conflict.watchDecision(true); conflict.update(request("confirmed")); assertFalse(conflict.bothConfirmed)
        assertThrows(IllegalArgumentException::class.java) { conflict.update(request("confirmed").put("userId", "other")) }
        var now = 0L
        val expired = PairingConsent("user", "device", "install", "12345678", false, { now })
        now = 90_000
        assertThrows(IllegalStateException::class.java) { expired.receipt("spki") }
    }
    @Test fun sasUsesTheSharedLengthPrefixedHmacDomain() {
        // Independent Python hmac/hashlib vector; no production identity or secret.
        assertEquals("21541826", BleSetupCrypto.sas(ByteArray(32) { it.toByte() }, "fixture-session", "fixture-user"))
        assertNotEquals(BleSetupCrypto.sas(ByteArray(32) { it.toByte() }, "fixture-session", "fixture-user"),
            BleSetupCrypto.sas(ByteArray(32) { it.toByte() }, "fixture-session", "other-user"))
    }
    private fun request(decision: String) = JSONObject().put("userId", "user").put("decision", decision)
}
