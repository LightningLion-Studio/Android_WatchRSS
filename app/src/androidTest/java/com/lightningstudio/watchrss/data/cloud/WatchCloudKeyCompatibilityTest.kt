package com.lightningstudio.watchrss.data.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic identity only; never reads or changes the signed-in account. */
@RunWith(AndroidJUnit4::class)
class WatchCloudKeyCompatibilityTest {
    @Test fun deviceKeySurvivesManagerRecreationAndDecryptsStandardEnvelope() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".bletest"))
        val user = "cloud-api30-compatibility-fixture"
        val device = "isolated-compatibility-device"
        val first = WatchCloudKeyManager(context).publicKeySpki(user, device)
        val manager = WatchCloudKeyManager(context)
        assertEquals(first, manager.publicKeySpki(user, device))
        val ephemeral = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1")); generateKeyPair()
        }
        val recipient = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(first.b64Bytes()))
        val secret = KeyAgreement.getInstance("ECDH").run {
            init(ephemeral.private); doPhase(recipient, true); generateSecret()
        }
        val saltHex = WatchCloudCodec.sha256(user.toByteArray())
        val salt = ByteArray(32) { saltHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val key = manager.accountKey(user, 1) ?: ByteArray(32).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val wrapped = WatchCloudCodec.aes(true,
            WatchCloudCodec.hkdf(secret, salt, "watchrss/device-envelope/v1/$device".toByteArray()),
            nonce, key, "watchrss-account-key:$user:1:$device".toByteArray())
        secret.fill(0)
        val envelope = WatchDeviceKeyEnvelope("P-256+HKDF-SHA256+A256GCM", 1,
            wrapped.b64(), nonce.b64(), ephemeral.public.encoded.b64())
        assertArrayEquals(key, manager.acceptEnvelope(user, device, envelope))
        assertArrayEquals(key, WatchCloudKeyManager(context).accountKey(user, 1))
        val storedXml = java.io.File(context.applicationInfo.dataDir, "shared_prefs/watchrss_cloud_keys.xml").readText()
        assertFalse(storedXml.contains(first))
        assertFalse(storedXml.contains(key.b64()))
        key.fill(0)
    }
}
