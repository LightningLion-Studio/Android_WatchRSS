package com.lightningstudio.watchrss.data.cloud

import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/** Android 11 uses a non-exportable RSA Keystore key to wrap a fresh AES envelope key. */
internal object RsaDeviceEnvelope {
    const val ALGORITHM = "RSA-2048-OAEP-SHA256-MGF1-SHA1+A256GCM"
    private const val CAPSULE_BYTES = 256
    private const val KEY_BYTES = 32
    private const val TAG_BYTES = 16

    fun wrap(accountKey: ByteArray, aad: ByteArray, publicKey: PublicKey, random: SecureRandom): Pair<ByteArray, ByteArray> {
        require(accountKey.size == KEY_BYTES)
        require(publicKey is RSAPublicKey && publicKey.modulus.bitLength() == 2048) { "设备 RSA 公钥必须为2048位" }
        val wrappingKey = ByteArray(KEY_BYTES).also(random::nextBytes)
        return try {
            val capsule = rsa(Cipher.ENCRYPT_MODE, publicKey, wrappingKey)
            require(capsule.size == CAPSULE_BYTES)
            val nonce = ByteArray(12).also(random::nextBytes)
            val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad)
                doFinal(accountKey)
            }
            (capsule + ciphertext) to nonce
        } finally { wrappingKey.fill(0) }
    }

    fun unwrap(wrapped: ByteArray, nonce: ByteArray, aad: ByteArray, privateKey: PrivateKey): ByteArray {
        require(privateKey.algorithm == "RSA") { "设备密钥类型与信封不符" }
        require(wrapped.size == CAPSULE_BYTES + KEY_BYTES + TAG_BYTES && nonce.size == 12) { "设备密钥信封长度无效" }
        val wrappingKey = rsa(Cipher.DECRYPT_MODE, privateKey, wrapped.copyOfRange(0, CAPSULE_BYTES))
        return try {
            require(wrappingKey.size == KEY_BYTES)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad)
                doFinal(wrapped, CAPSULE_BYTES, wrapped.size - CAPSULE_BYTES)
            }.also { require(it.size == KEY_BYTES) }
        } finally { wrappingKey.fill(0) }
    }

    private fun rsa(mode: Int, key: java.security.Key, bytes: ByteArray): ByteArray =
        Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            // Android Keystore before API 35 fixes MGF1 to SHA-1; specify both digests on every provider.
            init(mode, key, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))
            doFinal(bytes)
        }
}
