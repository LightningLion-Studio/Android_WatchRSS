package com.lightningstudio.watchrss.cloud

import com.lightningstudio.watchrss.data.cloud.*
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.data.account.workspace.AccountDataContext
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceKeyCompatibilityRuntimeTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun defaultP256DeviceKeySurvivesReopenWithApiCompatibleStorage() {
        val user = "compat-${UUID.randomUUID()}"
        val device = "fixture-device"
        val context = AccountDataContext(target, user, false)
        try {
            val manager = WatchCloudKeyManager(context)
            val public = manager.publicKeySpki(user, device)
            assertEquals("P-256", manager.deviceKeyAlgorithm(user, device))
            assertEquals(public, WatchCloudKeyManager(context).publicKeySpki(user, device))
            val alias = "watchrss_cloud_ecdh_" + WatchCloudCodec.sha256("$user:$device".toByteArray()).take(24)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (Build.VERSION.SDK_INT >= 31) {
                val private = store.getKey(alias, null)
                assertNotNull(private)
                assertNull(private.encoded)
            } else {
                // The v2 backend accepts P-256. API 30 stores the software pair in
                // EncryptedSharedPreferences rather than registering an RSA identity.
                assertNull(store.getKey(alias, null))
                val raw = context.getSharedPreferences("watchrss_cloud_keys", 0)
                assertFalse(raw.contains("device_private_$alias"))
                assertFalse(raw.contains("device_public_$alias"))
                assertNull(store.getKey(alias.replace("_ecdh_", "_rsa_"), null))
            }
        } finally { cleanup(context, user, device) }
    }

    @Test fun existingRsaIdentitySurvivesUpgradeAndApprovesAuthenticatedEnvelope() {
        val user = "compat-${UUID.randomUUID()}"
        val device = "fixture-device"
        val context = AccountDataContext(target, user, false)
        val alias = "watchrss_cloud_rsa_" + WatchCloudCodec.sha256("$user:$device".toByteArray()).take(24)
        try {
            val pair = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").run {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP).build())
                generateKeyPair()
            }
            val manager = WatchCloudKeyManager(context)
            assertEquals("RSA-2048", manager.deviceKeyAlgorithm(user, device))
            assertArrayEquals(pair.public.encoded, Base64.getDecoder().decode(manager.publicKeySpki(user, device)))
            val key = ByteArray(32).also(SecureRandom()::nextBytes)
            val public = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(pair.public.encoded))
            val (wrapped, nonce) = RsaDeviceEnvelope.wrap(key, "watchrss-account-key:$user:2:$device".toByteArray(), public, SecureRandom())
            val envelope = WatchDeviceKeyEnvelope(RsaDeviceEnvelope.ALGORITHM, 2, Base64.getEncoder().encodeToString(wrapped), Base64.getEncoder().encodeToString(nonce), "")
            assertArrayEquals(key, manager.acceptEnvelope(user, device, envelope))
            assertArrayEquals(key, WatchCloudKeyManager(context).accountKey(user, 2))
            var rejected = false
            try { manager.acceptEnvelope(user, device, envelope.copy(keyVersion = 3)) } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
            assertNull(manager.accountKey(user, 3))
            assertArrayEquals(key, manager.accountKey(user, 2))
            assertNull(pair.private.encoded)
        } finally { cleanup(context, user, device) }
    }

    private fun cleanup(context: AccountDataContext, user: String, device: String) {
        val hash = WatchCloudCodec.sha256("$user:$device".toByteArray()).take(24)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("watchrss_cloud_ecdh_$hash"); deleteEntry("watchrss_cloud_rsa_$hash") }
        context.deleteSharedPreferences("watchrss_cloud_keys")
        context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
    }
}
