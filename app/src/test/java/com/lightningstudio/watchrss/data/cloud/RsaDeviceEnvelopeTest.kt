package com.lightningstudio.watchrss.data.cloud

import java.security.KeyPairGenerator
import java.security.SecureRandom
import org.junit.Assert.*
import org.junit.Test

class RsaDeviceEnvelopeTest {
    private val pair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }
    private val key = ByteArray(32) { it.toByte() }
    private fun envelope(): WatchDeviceKeyEnvelope {
        val wrapping = ByteArray(32).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val capsule = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(javax.crypto.Cipher.ENCRYPT_MODE, pair.public, javax.crypto.spec.OAEPParameterSpec("SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA1, javax.crypto.spec.PSource.PSpecified.DEFAULT))
            doFinal(wrapping)
        }
        val ciphertext = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").run {
            init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(wrapping, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
            updateAAD("watchrss-account-key:user:2:device".toByteArray())
            doFinal(key)
        }
        return WatchDeviceKeyEnvelope(RsaDeviceEnvelope.ALGORITHM, 2, (capsule + ciphertext).b64(), nonce.b64(), "")
    }

    @Test fun rsaDeviceEnvelopeRoundTripsAndBindsAccountDeviceAndVersion() {
        val envelope = envelope()
        assertEquals(RsaDeviceEnvelope.ALGORITHM, envelope.algorithm)
        assertEquals("", envelope.ephemeralPublicKeySpki)
        assertArrayEquals(key, unwrapWatchDeviceEnvelope(envelope, "user", "device", pair.private))
        rejects { unwrapWatchDeviceEnvelope(envelope, "other", "device", pair.private) }
        rejects { unwrapWatchDeviceEnvelope(envelope, "user", "other", pair.private) }
        rejects { unwrapWatchDeviceEnvelope(envelope.copy(keyVersion = 3), "user", "device", pair.private) }
    }

    @Test fun rsaEnvelopeRejectsTamperingMalformedCapsulesAndKeyTypes() {
        val envelope = envelope()
        val bytes = envelope.wrappedKeyBase64.b64Bytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        rejects { unwrapWatchDeviceEnvelope(envelope.copy(wrappedKeyBase64 = bytes.b64()), "user", "device", pair.private) }
        rejects { unwrapWatchDeviceEnvelope(envelope.copy(nonceBase64 = ByteArray(8).b64()), "user", "device", pair.private) }
        rejects { unwrapWatchDeviceEnvelope(envelope.copy(wrappedKeyBase64 = ByteArray(48).b64()), "user", "device", pair.private) }
        rejects { unwrapWatchDeviceEnvelope(envelope.copy(ephemeralPublicKeySpki = "unexpected"), "user", "device", pair.private) }
        rejects { unwrapWatchDeviceEnvelope(envelope, "user", "device", java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair().private) }
        val weak = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        rejects { RsaDeviceEnvelope.wrap(key, byteArrayOf(), weak.public, SecureRandom()) }
    }

    private fun rejects(action: () -> Any?) {
        var failed = false
        try { action() } catch (_: Exception) { failed = true }
        assertTrue("Invalid envelope must be rejected", failed)
    }
}
