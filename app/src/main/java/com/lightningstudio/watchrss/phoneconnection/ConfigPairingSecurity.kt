package com.lightningstudio.watchrss.phoneconnection

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Per-screen pairing session for configuration endpoints.
 *
 * The 256-bit secret is shown only in the QR URL fragment, so browsers and HTTP servers do not
 * receive it as part of the URL. Requests prove possession with HMAC-SHA256 and carry sensitive
 * JSON inside an AES-256-GCM envelope. A session is invalidated when the first valid write is claimed
 * or when its LocalHttpServer is stopped.
 */
internal class ConfigPairingSession private constructor(
    val pairingToken: String,
    private val random: SecureRandom
) {
    private val active = AtomicBoolean(true)
    private val acceptedNonces = ConcurrentHashMap.newKeySet<String>()

    fun authenticate(
        method: String,
        uri: String,
        nonce: String?,
        requestBody: String,
        requestProof: String?
    ): Boolean {
        if (!active.get() || nonce == null || requestProof == null) return false
        if (!ConfigPairingProtocol.isValidNonce(nonce)) return false
        if (acceptedNonces.size >= MAX_REQUESTS_PER_SESSION) return false

        val expected = ConfigPairingProtocol.createRequestProof(
            pairingToken = pairingToken,
            method = method,
            uri = uri,
            nonce = nonce,
            requestBody = requestBody
        )
        if (!ConfigPairingProtocol.constantTimeEquals(expected, requestProof)) return false

        return acceptedNonces.add(nonce)
    }

    fun decryptRequest(method: String, uri: String, encryptedBody: String): String {
        check(active.get()) { "Pairing session is no longer active" }
        return ConfigPairingProtocol.decrypt(
            pairingToken = pairingToken,
            method = method,
            uri = uri,
            envelope = encryptedBody
        )
    }

    fun encryptResponse(method: String, uri: String, plaintext: String): String {
        check(active.get()) { "Pairing session is no longer active" }
        return ConfigPairingProtocol.encrypt(
            pairingToken = pairingToken,
            method = method,
            uri = uri,
            plaintext = plaintext,
            random = random
        )
    }

    /** Atomically reserves the only permitted configuration write for this QR session. */
    fun claimWrite(): Boolean {
        val claimed = active.compareAndSet(true, false)
        if (claimed) acceptedNonces.clear()
        return claimed
    }

    fun invalidate() {
        active.set(false)
        acceptedNonces.clear()
    }

    companion object {
        private const val MAX_REQUESTS_PER_SESSION = 512

        fun create(random: SecureRandom = SecureRandom()): ConfigPairingSession {
            return ConfigPairingSession(
                pairingToken = ConfigPairingProtocol.generatePairingToken(random),
                random = random
            )
        }
    }
}

/** Wire-level primitives shared by the watch server and protocol tests. */
internal object ConfigPairingProtocol {
    const val PROTOCOL_ID = "watchrss-config-pairing-v1"
    const val NONCE_HEADER = "X-WatchRSS-Pairing-Nonce"
    const val AUTH_HEADER = "X-WatchRSS-Pairing-Auth"

    private const val TOKEN_BYTES = 32
    private const val NONCE_BYTES = 16
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val ENCRYPTION_CONTEXT = "watchrss-config-encryption-v1"
    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()
    private val base64Decoder = Base64.getUrlDecoder()

    fun generatePairingToken(random: SecureRandom = SecureRandom()): String {
        return ByteArray(TOKEN_BYTES)
            .also(random::nextBytes)
            .let(base64Encoder::encodeToString)
    }

    fun generateRequestNonce(random: SecureRandom = SecureRandom()): String {
        return ByteArray(NONCE_BYTES)
            .also(random::nextBytes)
            .let(base64Encoder::encodeToString)
    }

    fun createRequestProof(
        pairingToken: String,
        method: String,
        uri: String,
        nonce: String,
        requestBody: String
    ): String {
        val bodyDigest = MessageDigest.getInstance("SHA-256")
            .digest(requestBody.toByteArray(StandardCharsets.UTF_8))
        val canonicalRequest = buildString {
            append(method.uppercase())
            append('\n')
            append(uri)
            append('\n')
            append(nonce)
            append('\n')
            append(base64Encoder.encodeToString(bodyDigest))
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(decodePairingToken(pairingToken), "HmacSHA256"))
        return base64Encoder.encodeToString(
            mac.doFinal(canonicalRequest.toByteArray(StandardCharsets.UTF_8))
        )
    }

    fun encrypt(
        pairingToken: String,
        method: String,
        uri: String,
        plaintext: String,
        random: SecureRandom = SecureRandom()
    ): String {
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(pairingToken), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(additionalAuthenticatedData(method, uri))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return JSONObject().apply {
            put("protocol", PROTOCOL_ID)
            put("iv", base64Encoder.encodeToString(iv))
            put("ciphertext", base64Encoder.encodeToString(ciphertext))
        }.toString()
    }

    fun decrypt(
        pairingToken: String,
        method: String,
        uri: String,
        envelope: String
    ): String {
        val json = JSONObject(envelope)
        require(json.optString("protocol") == PROTOCOL_ID) { "Unsupported pairing protocol" }
        val iv = base64Decoder.decode(json.getString("iv"))
        require(iv.size == GCM_IV_BYTES) { "Invalid encryption IV" }
        val ciphertext = base64Decoder.decode(json.getString("ciphertext"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(pairingToken), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(additionalAuthenticatedData(method, uri))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    fun isValidPairingToken(token: String): Boolean {
        return runCatching { decodePairingToken(token).size == TOKEN_BYTES }.getOrDefault(false)
    }

    fun isValidNonce(nonce: String): Boolean {
        return runCatching { base64Decoder.decode(nonce).size == NONCE_BYTES }.getOrDefault(false)
    }

    fun constantTimeEquals(expected: String, actual: String): Boolean {
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            actual.toByteArray(StandardCharsets.US_ASCII)
        )
    }

    private fun encryptionKey(pairingToken: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ENCRYPTION_CONTEXT.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(decodePairingToken(pairingToken))
        return SecretKeySpec(digest.digest(), "AES")
    }

    private fun additionalAuthenticatedData(method: String, uri: String): ByteArray {
        return "${method.uppercase()}\n$uri".toByteArray(StandardCharsets.UTF_8)
    }

    private fun decodePairingToken(token: String): ByteArray = base64Decoder.decode(token)
}
