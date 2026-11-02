package com.lightningstudio.watchrss.port.cloudlink

import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.cloud.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class ApprovalV2Test {
    @Test fun deliveredKeysWaitForLocalConfirmationAndValidSignature() = runBlocking {
        val fixture = Fixture()
        val request = fixture.protocol
        request.begin()
        val code = request.poll()!!
        fixture.delivered = true
        assertEquals(code, request.poll())
        assertEquals(0, fixture.installs)
        assertTrue(request.confirm(code))
        assertEquals(1, fixture.installs)
        assertTrue(request.delivered)
        request.cancel()
        assertFalse(fixture.paths.any { it.endsWith("/cancel") })
    }
    @Test fun mismatchedCodeAndChangedApproverCannotInstall() = runBlocking {
        val fixture = Fixture(); fixture.protocol.begin(); val code = fixture.protocol.poll()!!
        fixture.delivered = true
        try { fixture.protocol.confirm(if (code == "00000000") "11111111" else "00000000"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, fixture.installs)
        fixture.approver = "different-device"
        try { fixture.protocol.poll(); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, fixture.installs)
    }
    @Test fun invalidSignatureCannotInstallEvenAfterLocalConfirmation() = runBlocking {
        val fixture = Fixture(); fixture.protocol.begin(); val code = fixture.protocol.poll()!!
        assertFalse(fixture.protocol.confirm(code))
        fixture.delivered = true; fixture.badSignature = true
        try { fixture.protocol.poll(); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(0, fixture.installs)
    }
    private class Fixture {
        val paths = mutableListOf<String>()
        var installs = 0
        var delivered = false
        var badSignature = false
        var approver = "phone"
        private val id = "11111111-1111-4111-8111-111111111111"
        private val epoch = "22222222-2222-4222-8222-222222222222"
        private val key = KeyPairGenerator.getInstance("EC").run { initialize(ECGenParameterSpec("secp256r1")); generateKeyPair() }
        private val spki = Base64.getEncoder().encodeToString(key.public.encoded)
        private val phoneNonce = ByteArray(32) { 4 }
        private var nonce: ByteArray? = null
        private val account = WatchAccountState(userId = "fixture-user", phoneMasked = "fixture", installId = "fixture-install",
            watchDeviceToken = "fixture-token", tokenExpiresAtMillis = Long.MAX_VALUE,
            backendBaseUrl = "https://fixture.invalid", posthogHost = "", posthogProjectApiKey = "")
        val protocol = WatchCloudApprovalV2(account, "watch", spki, epoch,
            get = { remote() }, post = { path, body ->
                paths += path
                if (path.endsWith("/reveal")) nonce = Base64.getDecoder().decode(body.getString("requesterNonce"))
                JSONObject().put("requestId", id)
            }, checkOwner = {}, install = { assertEquals("encrypted-payload", it); installs++ })
        private fun remote(): JSONObject = JSONObject().put("status", if (delivered) "delivered" else "accepted")
            .put("approverDeviceId", approver).put("approverSpki", spki)
            .put("approverNonce", Base64.getEncoder().encodeToString(phoneNonce)).apply {
                nonce?.let { put("requesterNonce", Base64.getEncoder().encodeToString(it)) }
                if (delivered) {
                    put("payload", "encrypted-payload")
                    val t = WatchCloudProtocolV2.transcript(account.userId, epoch, id, "watch", key.public.encoded,
                        approver, key.public.encoded, requireNotNull(nonce), phoneNonce)
                    val data = WatchCloudProtocolV2.lp("watchrss/approval-deliver/v2",
                        java.security.MessageDigest.getInstance("SHA-256").digest(t), "encrypted-payload".toByteArray())
                    val der = Signature.getInstance("SHA256withECDSA").run { initSign(key.private); update(data); sign() }
                    var offset = 2
                    fun integer(): ByteArray {
                        require(der[offset++].toInt() == 2)
                        val size = der[offset++].toInt() and 255
                        val value = BigInteger(1, der.copyOfRange(offset, offset + size)).toByteArray(); offset += size
                        return ByteArray(32).also { value.takeLast(32).toByteArray().copyInto(it, 32 - minOf(32, value.size)) }
                    }
                    val raw = integer() + integer()
                    if (badSignature) raw[0] = (raw[0].toInt() xor 1).toByte()
                    put("signature", Base64.getEncoder().encodeToString(raw))
                }
            }
    }
}
