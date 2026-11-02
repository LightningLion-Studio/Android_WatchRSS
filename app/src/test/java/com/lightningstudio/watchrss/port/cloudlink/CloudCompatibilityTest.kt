package com.lightningstudio.watchrss.port.cloudlink

import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.cloud.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CloudCompatibilityTest {
    private val epoch = "11111111-1111-4111-8111-111111111111"
    private val generation = "22222222-2222-4222-8222-222222222222"
    private val account = WatchAccountState(userId = "fixture-user", phoneMasked = "fixture", installId = "fixture-install",
        watchDeviceToken = "fixture-token", tokenExpiresAtMillis = Long.MAX_VALUE,
        backendBaseUrl = "https://fixture.invalid", posthogHost = "", posthogProjectApiKey = "")
    private fun bootstrap(v2: Boolean = true) = JSONObject().put("member", JSONObject()).put("devices", JSONArray())
        .put("keyEnvelopes", JSONArray()).apply { if (v2) { put("libraryEpoch", epoch); put("contentGeneration", generation) } }

    @Test fun currentRegistrationUsesNegotiatedFencesAndGenerationChangesStopTheSession() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        var body = bootstrap()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(body.toString().toResponseBody()).build()
        }.build()
        val client = WatchCloudClient(http)
        client.bootstrap(account)
        client.register(account, "watch", "spki", "fixture", 1)
        assertEquals(epoch, requests.last().header("x-watchrss-library-epoch"))
        assertEquals(generation, requests.last().header("x-watchrss-content-generation"))
        body = bootstrap().put("contentGeneration", "33333333-3333-4333-8333-333333333333")
        try { client.bootstrap(account); fail("must stop") } catch (_: IllegalArgumentException) { }
        assertEquals(3, requests.size)
    }

    @Test fun legacyRegistrationDoesNotInventV2Headers() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val client = WatchCloudClient(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(bootstrap(false).toString().toResponseBody()).build()
        }.build())
        client.bootstrap(account); client.register(account, "watch", "spki", "fixture", 1)
        assertNull(requests.last().header("x-watchrss-library-epoch"))
    }

    @Test fun unknownRequiredFeaturesAreNeverSilentlyIgnored() {
        WatchCloudProtocolV2.features(JSONObject().put("requiredFeatures", JSONArray()))
        assertThrows(WatchCloudUpgradeRequired::class.java) {
            WatchCloudProtocolV2.features(JSONObject().put("requiredFeatures", JSONArray().put("future-format")))
        }
        assertThrows(WatchCloudUpgradeRequired::class.java) {
            WatchCloudProtocolV2.features(JSONObject().put("requiredFeatures", JSONObject()))
        }
        assertThrows(WatchCloudUpgradeRequired::class.java) {
            WatchCloudProtocolV2.features(JSONObject().put("features", JSONObject().put("incompat", JSONArray().put("r48.private-overlay.v1"))))
        }
        assertThrows(WatchCloudUpgradeRequired::class.java) {
            WatchCloudProtocolV2.features(JSONObject().put("features", JSONObject().put("roCompat", JSONArray().put("future-title"))))
        }
    }

    @Test fun unknownManifestObjectCannotBeAcknowledgedOrReplaced() {
        val codec = WatchCloudCodec()
        val key = ByteArray(32) { 7 }
        val snapshot = codec.create(key, 1, "fixture", 1, listOf(WatchCloudLogicalObject("books/book/package.json", byteArrayOf(1))), emptyMap(), emptyMap())
        assertThrows(WatchCloudUpgradeRequired::class.java) { codec.decryptManifest(key, snapshot.manifest.snapshotId, snapshot.encryptedManifest) }
    }

    @Test fun commitmentChainRejectsReplacementMissingHistoryAndBadRotation() {
        val key = ByteArray(32) { 7 }; val second = ByteArray(32) { 9 }
        val first = JSONObject().put("keyVersion", 1).put("commitment", WatchCloudProtocolV2.commitment(key, "user", epoch, 1)).put("rotationProof", JSONObject.NULL)
        val commitment = WatchCloudProtocolV2.commitment(second, "user", epoch, 2)
        val next = JSONObject().put("keyVersion", 2).put("commitment", commitment)
            .put("rotationProof", WatchCloudProtocolV2.mac(key, "watchrss/key-rotate/v2", "user", epoch, 2, commitment))
        val rows = JSONArray().put(first).put(next)
        WatchCloudProtocolV2.validateKeys("user", epoch, 0, rows, mapOf(1 to key, 2 to second))
        assertThrows(IllegalArgumentException::class.java) { WatchCloudProtocolV2.validateKeys("user", epoch, 0, rows, mapOf(2 to second)) }
        assertThrows(IllegalArgumentException::class.java) { WatchCloudProtocolV2.validateKeys("user", epoch, 0, rows, mapOf(1 to second, 2 to second)) }
        next.put("rotationProof", "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { WatchCloudProtocolV2.validateKeys("user", epoch, 0, rows, mapOf(1 to key, 2 to second)) }
        assertArrayEquals(ByteArray(32) { 7 }, key)
    }

    @Test fun receiptsSurviveRestartAndOnlyHeadAckCanReachOneHundredPercent() {
        val root = Files.createTempDirectory("restore-journal").toFile()
        try {
            var journal = WatchCloudRestoreJournal(root)
            assertEquals(0, journal.begin("owner-a", "head", "manifest-hash", setOf("article", "notes")).percent)
            journal.commit("owner-a", "head", "article")
            journal = WatchCloudRestoreJournal(root)
            assertTrue(journal.received("owner-a", "head", "article"))
            assertFalse(journal.received("owner-a", "head", "notes"))
            assertEquals(33, journal.latest("owner-a")!!.percent)
            assertThrows(IllegalArgumentException::class.java) { journal.acknowledge("owner-a", "head") }
            assertEquals(66, journal.commit("owner-a", "head", "notes").percent)
            assertEquals(100, journal.acknowledge("owner-a", "head").percent)
            assertEquals(0, journal.begin("owner-b", "head", "manifest-hash", setOf("article", "notes")).percent)
            assertThrows(IllegalArgumentException::class.java) { journal.begin("owner-a", "head", "different-manifest", setOf("article", "notes")) }
        } finally { root.deleteRecursively() }
    }

    @Test fun repeatedInterruptedWindowsAreClassifiedFromReceiptsAndProgressResetsTheCount() {
        val root = Files.createTempDirectory("restore-journal").toFile()
        try {
            val journal = WatchCloudRestoreJournal(root)
            repeat(3) { journal.begin("owner", "head", "manifest", setOf("article", "notes")); journal.failed("owner", "head") }
            journal.begin("owner", "head", "manifest", setOf("article", "notes"))
            assertEquals(4, journal.failed("owner", "head"))
            journal.begin("owner", "head", "manifest", setOf("article", "notes"))
            journal.commit("owner", "head", "article")
            assertEquals(0, journal.failed("owner", "head"))
            assertTrue(journal.reusable("owner", "head", "manifest", "article"))
            assertFalse(journal.reusable("different-epoch", "head", "manifest", "article"))
        } finally { root.deleteRecursively() }
    }

    @Test fun corruptReceiptsNeverResetHistoricalProgress() {
        val root = Files.createTempDirectory("restore-journal").toFile()
        try {
            val journal = WatchCloudRestoreJournal(root)
            journal.begin("owner", "head", "manifest", setOf("article"))
            val receipt = root.walkTopDown().first { it.isFile && it.readText().contains("manifest") }
            receipt.writeText("invalid")
            assertThrows(Exception::class.java) { journal.begin("owner", "head", "manifest", setOf("article")) }
            assertEquals("invalid", receipt.readText())
        } finally { root.deleteRecursively() }
    }
}
