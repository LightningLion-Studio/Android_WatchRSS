package com.lightningstudio.watchrss.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.account.workspace.AccountDataContext
import com.lightningstudio.watchrss.data.cloud.WatchCloudControls
import com.lightningstudio.watchrss.data.cloud.WatchCloudNetworkPolicy
import com.lightningstudio.watchrss.data.cloud.WatchCloudPhase
import com.lightningstudio.watchrss.data.cloud.WatchCloudSyncService
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchCloudControlsRuntimeTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun optingInAndNetworkPolicyArePrivateToAccountAndSurviveReopen() {
        val a = AccountDataContext(target, "verification_cloud_a_${UUID.randomUUID()}", false)
        val b = AccountDataContext(target, "verification_cloud_b_${UUID.randomUUID()}", false)
        try {
            val controls = WatchCloudControls(a)
            assertFalse(controls.state.value.automatic)
            controls.setAutomatic(true)
            controls.setNetwork(WatchCloudNetworkPolicy.WIFI_AND_CHARGING)
            assertTrue(WatchCloudControls(a).state.value.automatic)
            assertEquals(WatchCloudNetworkPolicy.WIFI_AND_CHARGING, WatchCloudControls(a).state.value.network)
            assertFalse(WatchCloudControls(b).state.value.automatic)
            controls.setAutomatic(false)
            assertFalse(WatchCloudControls(a).state.value.automatic)
        } finally {
            a.deleteSharedPreferences("watchrss_cloud_controls"); b.deleteSharedPreferences("watchrss_cloud_controls")
            a.filesDir.deleteRecursively(); b.filesDir.deleteRecursively()
        }
    }

    @Test fun disabledAutoAndMissingConsentReturnBeforeReadingCredentialsOrConnecting() = runBlocking {
        val context = AccountDataContext(target, "verification_cloud_guard_${UUID.randomUUID()}", false)
        val store = object : AccountStore {
            override val state = MutableStateFlow<WatchAccountState?>(null)
            override fun read(): WatchAccountState? = error("Cloud must not read credentials before opt-in/consent")
            override fun save(state: WatchAccountState) = error("No credentials may be saved")
            override fun clear() = Unit
        }
        val repository = (target.applicationContext as WatchRssApplication).container.rssRepository
        try {
            val automatic = WatchCloudSyncService(context, store, repository)
            assertFalse(automatic.syncNow())
            assertEquals(WatchCloudPhase.IDLE, automatic.status.value.phase)
            automatic.controls.setAutomatic(true)
            val noConsent = WatchCloudSyncService(context, store, repository, canSync = { false })
            assertFalse(noConsent.syncNow(manual = true))
            assertEquals(WatchCloudPhase.IDLE, noConsent.status.value.phase)
            assertTrue(noConsent.status.value.message.contains("隐私政策"))
        } finally {
            context.deleteSharedPreferences("watchrss_cloud_controls")
            context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
        }
    }
    @Test fun approvedReadOnlyAccountDecryptsItsEnvelopeButNeverRegistersOrUploads() = runBlocking {
        val context = AccountDataContext(target, "verification_cloud_readonly_${UUID.randomUUID()}", false)
        val userId = "verification-${UUID.randomUUID()}"
        val deviceId = "fixture-watch"
        val paths = mutableListOf<String>()
        val keys = com.lightningstudio.watchrss.data.cloud.WatchCloudKeyManager(context)
        val keyAlias = "watchrss_cloud_ecdh_" + com.lightningstudio.watchrss.data.cloud.WatchCloudCodec.sha256("$userId:$deviceId".toByteArray()).take(24)
        try {
            val publicBytes = java.util.Base64.getDecoder().decode(keys.publicKeySpki(userId, deviceId))
            val recipient = java.security.KeyFactory.getInstance("EC").generatePublic(java.security.spec.X509EncodedKeySpec(publicBytes))
            val ephemeral = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            val secret = javax.crypto.KeyAgreement.getInstance("ECDH").run { init(ephemeral.private); doPhase(recipient, true); generateSecret() }
            val key = ByteArray(32) { (it + 1).toByte() }
            val nonce = ByteArray(12) { (it + 3).toByte() }
            val salt = java.security.MessageDigest.getInstance("SHA-256").digest(userId.toByteArray())
            // Independent RFC 5869/JCA issuer fixture; do not reuse the decoder's helpers.
            val extract = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(javax.crypto.spec.SecretKeySpec(salt, "HmacSHA256")) }.doFinal(secret)
            val wrapping = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(javax.crypto.spec.SecretKeySpec(extract, "HmacSHA256")) }
                .doFinal("watchrss/device-envelope/v1/$deviceId".toByteArray() + byteArrayOf(1))
            val encrypted = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").run {
                init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(wrapping, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
                updateAAD("watchrss-account-key:$userId:1:$deviceId".toByteArray())
                doFinal(key)
            }
            secret.fill(0); extract.fill(0); wrapping.fill(0)
            val encode: (ByteArray) -> String = { java.util.Base64.getEncoder().withoutPadding().encodeToString(it) }
            val envelope = org.json.JSONObject().put("recipientType", "device").put("algorithm", "P-256+HKDF-SHA256+A256GCM")
                .put("keyVersion", 1).put("wrappedKeyBase64", encode(encrypted)).put("nonceBase64", encode(nonce))
                .put("ephemeralPublicKeySpki", encode(ephemeral.public.encoded))
            val bootstrap = org.json.JSONObject()
                .put("member", org.json.JSONObject().put("readable", true).put("writable", false).put("quotaBytes", 1048576))
                .put("devices", org.json.JSONArray()).put("keyEnvelopes", org.json.JSONArray().put(envelope))
            val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                paths += path
                val json = when (path) {
                    "/functions/v1/cloud/bootstrap" -> bootstrap
                    "/functions/v1/cloud/snapshots/heads" -> org.json.JSONObject().put("heads", org.json.JSONArray())
                    else -> error("Read-only sync must never issue mutation request: $path")
                }
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(json.toString().toResponseBody("application/json".toMediaType())).build()
            }.build()
            val account = WatchAccountState(userId, "fixture", "fixture", "fixture-token", System.currentTimeMillis() + 3600000,
                backendBaseUrl = "https://invalid.example", posthogHost = "", posthogProjectApiKey = "")
            val store = object : AccountStore {
                override val state = MutableStateFlow<WatchAccountState?>(account)
                override fun read() = account
                override fun save(state: WatchAccountState) = error("Read-only sync must not mutate credentials")
                override fun clear() = Unit
            }
            val repository = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
                arrayOf(com.lightningstudio.watchrss.data.rss.RssRepository::class.java)) { _, method, _ ->
                when (method.name) {
                    "exportSyncedRssSources", "exportSyncedSavedArticles", "exportCloudRssStateArticles" -> emptyList<Any>()
                    else -> error("Unexpected local repository call: ${method.name}")
                }
            } as com.lightningstudio.watchrss.data.rss.RssRepository
            val service = WatchCloudSyncService(context, store, repository,
                client = com.lightningstudio.watchrss.data.cloud.WatchCloudClient(http), deviceId = deviceId)
            service.controls.setAutomatic(true)
            assertTrue(service.status.value.message, service.syncNow())
            assertEquals(WatchCloudPhase.COMPLETE, service.status.value.phase)
            assertTrue(service.status.value.message.contains("只读"))
            assertArrayEquals(key, keys.accountKey(userId, 1))
            assertTrue(paths.isNotEmpty())
            assertTrue(paths.all { it.endsWith("/bootstrap") || it.endsWith("/snapshots/heads") })
        } finally {
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(keyAlias) }
            listOf("watchrss_cloud_controls", "watchrss_cloud_keys", "watchrss_cloud_state").forEach(context::deleteSharedPreferences)
            context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
        }
    }
    @Test fun cancellingAutomaticTransferReportsStoppedAndStillPropagatesCancellation() = runBlocking {
        val context = AccountDataContext(target, "verification_cloud_cancel_${UUID.randomUUID()}", false)
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val account = object : AccountStore {
            override val state = MutableStateFlow<WatchAccountState?>(null)
            override fun read(): WatchAccountState? = error("Cancelled before credential read")
            override fun save(state: WatchAccountState) = Unit
            override fun clear() = Unit
        }
        try {
            val service = WatchCloudSyncService(context, account,
                (target.applicationContext as WatchRssApplication).container.rssRepository,
                canSync = { entered.complete(Unit); kotlinx.coroutines.awaitCancellation() })
            service.controls.setAutomatic(true)
            val job = launch { service.syncNow() }
            entered.await()
            job.cancel(); job.join()
            assertTrue(job.isCancelled)
            assertEquals(WatchCloudPhase.IDLE, service.status.value.phase)
            assertTrue(service.status.value.message.contains("停止"))
            assertEquals(0L, service.status.value.lastCompletedAt)
        } finally {
            context.deleteSharedPreferences("watchrss_cloud_controls")
            context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
        }
    }
}
