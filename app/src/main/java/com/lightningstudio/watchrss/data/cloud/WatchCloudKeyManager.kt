package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyPair
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class WatchCloudKeyManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "watchrss_cloud_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun accountKey(userId: String): ByteArray? =
        accountKey(userId, currentKeyVersion(userId))

    fun accountKey(userId: String, keyVersion: Int): ByteArray? =
        preferences.getString(accountName(userId, keyVersion), null)?.b64Bytes()
            ?: if (keyVersion == 1) {
                preferences.getString(legacyAccountName(userId), null)
                    ?.b64Bytes()
                    ?.also { saveAccountKey(userId, 1, it) }
            } else {
                null
            }

    fun keyVersions(userId: String): List<Int> {
        val prefix = accountPrefix(userId)
        val versions = preferences.all.keys.mapNotNull { key ->
            key.removePrefix(prefix).takeIf { key.startsWith(prefix) }?.toIntOrNull()
        }.sorted()
        if (versions.isNotEmpty()) return versions
        return if (preferences.contains(legacyAccountName(userId))) listOf(1) else emptyList()
    }

    fun currentKeyVersion(userId: String): Int =
        preferences.getInt(currentName(userId), 0)
            .takeIf { it > 0 }
            ?: keyVersions(userId).maxOrNull()
            ?: 1

    fun publicKeySpki(userId: String, deviceId: String): String =
        getOrCreateDeviceKey(userId, deviceId).public.encoded.b64()

    fun acceptEnvelope(
        userId: String,
        deviceId: String,
        envelope: WatchDeviceKeyEnvelope
    ): ByteArray {
        val privateKey = getOrCreateDeviceKey(userId, deviceId).private
        val key = unwrapWatchDeviceEnvelope(envelope, userId, deviceId, privateKey)
        val existing = accountKey(userId, envelope.keyVersion)
        try {
            require(existing == null || java.security.MessageDigest.isEqual(existing, key)) { "同版本云密钥冲突" }
            saveAccountKey(userId, envelope.keyVersion, key)
            return key
        } catch (error: Exception) {
            key.fill(0)
            throw error
        } finally { existing?.fill(0) }
    }

    /** Decrypt and validate the entire delivered version chain before one durable key-store write. */
    internal fun acceptTrustedEnvelopes(userId: String, deviceId: String, bootstrap: WatchCloudBootstrap,
        envelopes: List<WatchDeviceKeyEnvelope> = bootstrap.envelopes, attestation: org.json.JSONObject? = null) {
        val epoch = requireNotNull(bootstrap.libraryEpoch)
        val privateKey = getOrCreateDeviceKey(userId, deviceId).private
        val candidates = linkedMapOf<Int, ByteArray>()
        try {
            for (envelope in envelopes) {
                require(envelope.keyVersion !in candidates) { "重复的云密钥版本" }
                candidates[envelope.keyVersion] = unwrapWatchDeviceEnvelope(envelope, userId, deviceId, privateKey)
            }
            for (version in keyVersions(userId)) {
                val existing = accountKey(userId, version) ?: continue
                val candidate = candidates[version]
                if (candidate != null) {
                    try { require(java.security.MessageDigest.isEqual(existing, candidate)) { "同版本云密钥冲突" } }
                    finally { existing.fill(0) }
                } else candidates[version] = existing
            }
            WatchCloudProtocolV2.validateKeys(userId, epoch, bootstrap.keyVersionFloor, bootstrap.keyCommitments, candidates)
            val required = (0 until bootstrap.keyCommitments.length()).map {
                bootstrap.keyCommitments.getJSONObject(it).getInt("keyVersion")
            }.toSet()
            require(candidates.keys == required) { "缺少历史云密钥，请在手机 App 或小程序重新授权" }
            attestation?.let {
                val version = it.getInt("keyVersion")
                require(version == required.max()) { "设备证明版本无效" }
                require(WatchCloudProtocolV2.equal(it.getString("mac"), WatchCloudProtocolV2.attestation(
                    candidates.getValue(version), userId, epoch, deviceId, publicKeySpki(userId, deviceId)))) { "设备密钥证明不匹配" }
            }
            val editor = preferences.edit()
            candidates.forEach { (version, key) -> editor.putString(accountName(userId, version), key.b64()) }
            editor.putInt(currentName(userId), required.max())
            check(editor.commit()) { "云密钥保存失败" }
        } finally { candidates.values.forEach { it.fill(0) } }
    }

    fun deviceKeyAlgorithm(userId: String, deviceId: String): String =
        if (getOrCreateDeviceKey(userId, deviceId).public.algorithm == "RSA") "RSA-2048" else "P-256"

    private fun getOrCreateDeviceKey(userId: String, deviceId: String): KeyPair = synchronized(deviceKeyLock) {
        val alias = alias(userId, deviceId)
        val keyStore = keyStore()
        val rsaAlias = alias.replace("_ecdh", "_rsa")
        val rsaPrivate = keyStore.getKey(rsaAlias, null) as? PrivateKey
        val rsaPublic = keyStore.getCertificate(rsaAlias)?.publicKey
        if (rsaPrivate != null && rsaPublic != null) return@synchronized KeyPair(rsaPublic, rsaPrivate)
        val private = keyStore.getKey(alias, null) as? PrivateKey
        val public = keyStore.getCertificate(alias)?.publicKey
        if (private != null && public != null) return@synchronized KeyPair(public, private)

        // API 30 cannot perform ECDH with AndroidKeyStore keys. Keep a software
        // P-256 key encrypted by the existing Keystore-backed AES master key.
        // Prefer a saved pair even after an OS upgrade so issued envelopes remain valid.
        val privateName = "device_private_$alias"
        val publicName = "device_public_$alias"
        val savedPrivate = preferences.getString(privateName, null)
        val savedPublic = preferences.getString(publicName, null)
        if (savedPrivate != null || savedPublic != null) {
            check(savedPrivate != null && savedPublic != null) { "设备加密凭据不完整" }
            val encoded = savedPrivate.b64Bytes()
            return@synchronized try {
                KeyFactory.getInstance("EC").let { factory ->
                    KeyPair(factory.generatePublic(X509EncodedKeySpec(savedPublic.b64Bytes())),
                        factory.generatePrivate(PKCS8EncodedKeySpec(encoded)))
                }
            } finally { encoded.fill(0) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return@synchronized KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
                generateKeyPair()
            }
        }
        KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }.also { pair ->
            val encoded = pair.private.encoded
            try {
                check(preferences.edit().putString(privateName, encoded.b64())
                    .putString(publicName, pair.public.encoded.b64()).commit()) { "无法保存设备加密凭据" }
            } finally { encoded.fill(0) }
        }
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun saveAccountKey(userId: String, keyVersion: Int, key: ByteArray) {
        val editor = preferences.edit()
            .putString(accountName(userId, keyVersion), key.b64())
        if (keyVersion >= currentKeyVersion(userId)) {
            editor.putInt(currentName(userId), keyVersion)
        }
        check(editor.commit())
    }

    private fun accountPrefix(userId: String) =
        "account_${WatchCloudCodec.sha256(userId.toByteArray()).take(24)}_v"

    private fun accountName(userId: String, keyVersion: Int) =
        accountPrefix(userId) + keyVersion

    private fun currentName(userId: String) =
        "account_current_${WatchCloudCodec.sha256(userId.toByteArray()).take(24)}"

    private fun legacyAccountName(userId: String) =
        "account_${WatchCloudCodec.sha256(userId.toByteArray()).take(24)}"

    private fun alias(userId: String, deviceId: String) =
        "watchrss_cloud_ecdh_${WatchCloudCodec.sha256("$userId:$deviceId".toByteArray()).take(24)}"

    private companion object {
        private val deviceKeyLock = Any()
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
