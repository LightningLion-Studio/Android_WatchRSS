package com.lightningstudio.watchrss.phoneconnection

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigPairingSecurityTest {
    @Test
    fun generatedPairingTokensContain256BitsOfRandomMaterial() {
        val first = ConfigPairingProtocol.generatePairingToken()
        val second = ConfigPairingProtocol.generatePairingToken()

        assertEquals(32, Base64.getUrlDecoder().decode(first).size)
        assertEquals(32, Base64.getUrlDecoder().decode(second).size)
        assertNotEquals(first, second)
        assertTrue(ConfigPairingProtocol.isValidPairingToken(first))
    }

    @Test
    fun validRequestProofIsAcceptedOnlyOnce() {
        val session = ConfigPairingSession.create()
        val nonce = ConfigPairingProtocol.generateRequestNonce()
        val body = ""
        val proof = ConfigPairingProtocol.createRequestProof(
            session.pairingToken,
            "GET",
            "/getLLMSummaryConfig",
            nonce,
            body
        )

        assertTrue(session.authenticate("GET", "/getLLMSummaryConfig", nonce, body, proof))
        assertFalse(session.authenticate("GET", "/getLLMSummaryConfig", nonce, body, proof))
    }

    @Test
    fun requestProofBindsMethodPathAndEncryptedBody() {
        val session = ConfigPairingSession.create()
        val nonce = ConfigPairingProtocol.generateRequestNonce()
        val proof = ConfigPairingProtocol.createRequestProof(
            session.pairingToken,
            "POST",
            "/setTtsConfig",
            nonce,
            "ciphertext-a"
        )

        assertFalse(session.authenticate("POST", "/setTtsConfig", nonce, "ciphertext-b", proof))
        assertFalse(session.authenticate("POST", "/setLLMSummaryConfig", nonce, "ciphertext-a", proof))
    }

    @Test
    fun aesGcmEnvelopeRoundTripsWithoutExposingApiKey() {
        val token = ConfigPairingProtocol.generatePairingToken()
        val plaintext = """{"provider":"openai","apiKey":"sk-sensitive-value"}"""
        val envelope = ConfigPairingProtocol.encrypt(
            pairingToken = token,
            method = "POST",
            uri = "/setLLMSummaryConfig",
            plaintext = plaintext
        )

        assertFalse(envelope.contains("sk-sensitive-value"))
        assertEquals(
            plaintext,
            ConfigPairingProtocol.decrypt(token, "POST", "/setLLMSummaryConfig", envelope)
        )
        assertThrows(Exception::class.java) {
            ConfigPairingProtocol.decrypt(token, "POST", "/setTtsConfig", envelope)
        }
    }

    @Test
    fun invalidatedSessionRejectsFreshProof() {
        val session = ConfigPairingSession.create()
        session.invalidate()
        val nonce = ConfigPairingProtocol.generateRequestNonce()
        val proof = ConfigPairingProtocol.createRequestProof(
            session.pairingToken,
            "GET",
            "/getTtsConfig",
            nonce,
            ""
        )

        assertFalse(session.authenticate("GET", "/getTtsConfig", nonce, "", proof))
    }

    @Test
    fun configurationWriteCanBeClaimedOnlyOnce() {
        val session = ConfigPairingSession.create()

        assertTrue(session.claimWrite())
        assertFalse(session.claimWrite())
    }
}
