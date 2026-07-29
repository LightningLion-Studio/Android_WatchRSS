package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec

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
        val privateKey = requireNotNull(
            keyStore().getKey(alias(userId, deviceId), null) as? PrivateKey
        ) { "手表设备私钥不存在" }
        return unwrapWatchDeviceEnvelope(envelope, userId, deviceId, privateKey).also { key ->
            saveAccountKey(userId, envelope.keyVersion, key)
        }
    }

    private fun getOrCreateDeviceKey(userId: String, deviceId: String): KeyPair {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "云中继需要 Android 12 及以上硬件密钥支持"
        }
        val alias = alias(userId, deviceId)
        val keyStore = keyStore()
        val private = keyStore.getKey(alias, null) as? PrivateKey
        val public = keyStore.getCertificate(alias)?.publicKey
        if (private != null && public != null) return KeyPair(public, private)
        return KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        ).run {
            initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generateKeyPair()
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
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
