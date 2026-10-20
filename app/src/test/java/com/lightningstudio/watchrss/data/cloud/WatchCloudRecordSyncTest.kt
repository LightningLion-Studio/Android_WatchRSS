package com.lightningstudio.watchrss.data.cloud

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WatchCloudRecordSyncTest {
    private val user = "record-fixture-user"
    private val key = ByteArray(32) { it.toByte() }
    private val source = JSONObject().put("url", "https://example.com/rss").put("title", "Source")
        .put("updatedAt", 100).put("createdAt", 50).put("sortOrder", 100)
    private fun encrypted(value: JSONObject = source): Pair<String, JSONObject> =
        WatchCloudRecordCodec.encrypt(key, user, 3, "source", value, ByteArray(12) { 7 })

    @Test fun decryptsIndependentNodeFixtureAndChecksAccountBinding() {
        val fixture = JSONObject(requireNotNull(javaClass.getResourceAsStream("/cloud-record-node-v1.json"))
            .bufferedReader().use { it.readText() })
        val value = fixture.getJSONObject("value")
        assertEquals(fixture.getString("recordId"), WatchCloudRecordCodec.recordId(key, user, "article", value))
        val decoded = WatchCloudRecordCodec.decrypt(user, fixture.getString("recordId"), fixture.getJSONObject("envelope")) { key }
        assertEquals("article-fixture", decoded.value.getString("articleId"))
        assertEquals("你好 RSS", decoded.value.getString("title"))
        assertTrue(decoded.value.getBoolean("favoriteSaved"))
        assertThrows(Exception::class.java) {
            WatchCloudRecordCodec.decrypt("different-user", fixture.getString("recordId"), fixture.getJSONObject("envelope")) { key }
        }
        assertArrayEquals(ByteArray(32) { it.toByte() }, key)
    }

    @Test fun codecNeverAcceptsBodyAndBindsLogicalId() {
        assertThrows(IllegalArgumentException::class.java) {
            WatchCloudRecordCodec.encrypt(key, user, 3, "source", JSONObject(source.toString()).put("contentText", "secret"))
        }
        val (id, envelope) = encrypted()
        assertThrows(Exception::class.java) { WatchCloudRecordCodec.decrypt(user, "0".repeat(64), envelope) { key } }
        val decoded = WatchCloudRecordCodec.decrypt(user, id, envelope) { key }
        assertEquals("Source", decoded.value.getString("title"))
        assertFalse(decoded.value.has("contentText"))
    }

    @Test fun formatZeroAndMissingRouteNeverWriteOrConsent() = runBlocking {
        for (page in listOf(null, JSONObject().put("formatVersion", 0).put("items", JSONArray()))) {
            val store = Store()
            var writes = 0
            val sync = engine(store, read = { page }, write = { writes++; error("cannot write") })
            assertFalse(sync.sync(true))
            assertEquals(0, writes)
            assertEquals("0", store.cursor("scope"))
        }
    }

    @Test fun deniedReadPropagatesWithoutDowngrade() = runBlocking {
        val sync = engine(Store(), read = { throw IOException("HTTP 403") })
        try { sync.sync(true); fail() } catch (failure: IOException) { assertEquals("HTTP 403", failure.message) }
    }

    @Test fun cursorAdvancesOnlyAfterSuccessfulMerge() = runBlocking {
        val store = Store()
        val remote = page(listOf(encrypted()), "7")
        val sync = engine(store, read = { remote }, apply = { error("merge failed") })
        try { sync.sync(false); fail() } catch (_: IllegalStateException) { }
        assertEquals("0", store.cursor("scope"))
        assertEquals(0, store.rows.size)
    }

    @Test fun sourceAttributionAndCreationTimeDoNotBounceBackToOtherDevice() = runBlocking {
        val store = Store()
        val remoteValue = JSONObject(source.toString()).put("sourceDeviceId", "phone-A")
        val localValue = JSONObject(source.toString()).put("sourceDeviceId", "watch-B").put("createdAt", 999)
        var posts = 0
        val sync = engine(store, read = { page(listOf(encrypted(remoteValue)), "7") },
            local = { emit -> emit("source", localValue) }, write = { posts++; error("no changes") })
        assertTrue(sync.sync(true))
        assertEquals(0, posts)
        assertEquals("7", store.cursor("scope"))
    }

    @Test fun onlyChangedRowsArePushedAndAcknowledgedWithTheirRevision() = runBlocking {
        val store = Store()
        var posts = 0
        val (id, _) = encrypted()
        val sync = engine(store, read = { page(emptyList(), "0") },
            local = { emit -> emit("source", source) }, write = { body ->
                posts++
                val row = body.getJSONArray("items").getJSONObject(0)
                assertEquals("0", row.getString("expectedRevision"))
                assertEquals(id, row.getString("recordId"))
                ack(id, "1")
            })
        assertTrue(sync.sync(true)); assertTrue(sync.sync(true))
        assertEquals(1, posts)
        assertEquals("1", store.record("scope", id)?.revision)
    }

    @Test fun conflictPullsAndMergesBeforeRecomputingAnyReplacement() = runBlocking {
        val store = Store()
        var local = JSONObject(source.toString()).put("title", "my initial state")
        val winner = JSONObject(source.toString()).put("title", "remote newer state").put("updatedAt", 200)
        var reads = 0
        var posts = 0
        val sync = engine(store, read = { reads++; if (reads == 1) page(emptyList(), "0") else page(listOf(encrypted(winner)), "7") },
            apply = { values -> if (values.isNotEmpty()) local = values.single().value },
            local = { emit -> emit("source", local) }, write = { posts++; throw WatchCloudRecordConflict() })
        assertTrue(sync.sync(true))
        assertEquals(2, reads)
        assertEquals(1, posts)
        assertEquals("remote newer state", local.getString("title"))
        assertEquals("7", store.cursor("scope"))
    }

    @Test fun ownerChangeAfterNetworkDoesNotApplyOrAdvanceCursor() = runBlocking {
        val store = Store()
        var owner = true
        var applied = false
        val sync = engine(store, owner = { check(owner) }, read = { owner = false; page(listOf(encrypted()), "7") },
            apply = { applied = true })
        try { sync.sync(false); fail() } catch (_: IllegalStateException) { }
        assertFalse(applied)
        assertEquals("0", store.cursor("scope"))
    }

    @Test fun serverResetRestartsOnlyThisAccountsRecordCursorAndCasCache() = runBlocking {
        val store = Store()
        val (id, _) = encrypted()
        store.commitPage("scope", "9", listOf(WatchRecordStoredRow(id, WatchRecordStamp("9", "old"))))
        store.commitPage("other-account", "88", emptyList())
        var reads = 0
        var posts = 0
        val sync = engine(store, read = { after ->
            reads++
            if (reads == 1) { assertEquals("9", after); throw WatchCloudRecordCursorInvalid() }
            assertEquals("0", after); page(emptyList(), "0")
        }, local = { emit -> emit("source", source) }, write = { body ->
            assertEquals("0", body.getJSONArray("items").getJSONObject(0).getString("expectedRevision"))
            posts++; ack(id, "1")
        })
        assertTrue(sync.sync(true))
        assertEquals(2, reads)
        assertEquals(1, posts)
        assertEquals("88", store.cursor("other-account"))
    }

    @Test fun floatProgressPromotionDoesNotEchoButPositionsStillSync() {
        val value = JSONObject().put("articleId", "fixture").put("url", "https://example.com/article")
            .put("readingProgress", 0.2).put("readingPositionBytes", 123).put("readingPositionChangedAt", 456)
        val promoted = JSONObject(value.toString()).put("readingProgress", 0.20000000298)
        assertEquals(WatchCloudRecordCodec.fingerprint("article", value), WatchCloudRecordCodec.fingerprint("article", promoted))
        promoted.put("readingPositionBytes", 124)
        assertNotEquals(WatchCloudRecordCodec.fingerprint("article", value), WatchCloudRecordCodec.fingerprint("article", promoted))
    }

    @Test fun badTerminalCursorAndEmptyContinuationCannotAdvanceOrApply() = runBlocking {
        val terminal = page(listOf(encrypted()), "7").put("headRevision", "8")
        val emptyContinuation = page(emptyList(), "0").put("hasMore", true).put("headRevision", "1")
        for (bad in listOf(terminal, emptyContinuation)) {
            val store = Store()
            var applied = false
            try { engine(store, read = { bad }, apply = { applied = true }).sync(false); fail() }
            catch (_: IllegalArgumentException) { }
            assertFalse(applied)
            assertEquals("0", store.cursor("scope"))
        }
    }

    @Test fun pageCursorCommitsAfterEachSuccessfulMerge() = runBlocking {
        val store = Store()
        var pages = 0
        var applied = 0
        val sync = engine(store, read = { after ->
            pages++
            when (pages) {
                1 -> { assertEquals("0", after); page(listOf(encrypted()), "1").put("headRevision", "2").put("hasMore", true) }
                else -> { assertEquals("1", after); page(listOf(encrypted()), "2") }
            }
        }, apply = { applied += it.size })
        assertTrue(sync.sync(false))
        assertEquals(2, applied)
        assertEquals("2", store.cursor("scope"))
    }

    @Test fun formatZeroClearsOnlyThisAccountsOldRecordBaseline() = runBlocking {
        val store = Store()
        val (id, _) = encrypted()
        store.commitPage("scope", "9", listOf(WatchRecordStoredRow(id, WatchRecordStamp("9", "old"))))
        store.commitPage("other-account", "88", emptyList())
        val sync = engine(store, read = { JSONObject().put("formatVersion", 0).put("items", JSONArray()) })
        assertFalse(sync.sync(true))
        assertEquals("0", store.cursor("scope"))
        assertNull(store.record("scope", id))
        assertEquals("88", store.cursor("other-account"))
    }

    @Test fun repeatedLogicalIdsAreSubmittedInSeparateCasBatches() = runBlocking {
        val store = Store()
        val (id, _) = encrypted()
        var posts = 0
        val sync = engine(store, read = { page(emptyList(), "0") }, local = { emit ->
            emit("source", source)
            emit("source", JSONObject(source.toString()).put("title", "later local version").put("updatedAt", 200))
        }, write = { body ->
            val rows = body.getJSONArray("items")
            assertEquals(1, rows.length())
            assertEquals(posts.toString(), rows.getJSONObject(0).getString("expectedRevision"))
            posts++
            ack(id, posts.toString())
        })
        assertTrue(sync.sync(true))
        assertEquals(2, posts)
    }

    @Test fun malformedDecryptedJsonCannotLeakIntoErrorTelemetry() {
        val secret = "KNOWN-PRIVATE-METADATA-SENTINEL"
        val (id, _) = encrypted()
        val nonce = ByteArray(12) { 7 }
        val derived = WatchCloudCodec.hkdf(key, user.toByteArray(), "watchrss/cloud/record/v1".toByteArray())
        val bad = WatchCloudCodec.aes(true, derived, nonce, ("{bad:" + secret).toByteArray(),
            "watchrss-cloud-record-v1:$user:$id:3".toByteArray())
        derived.fill(0)
        val envelope = JSONObject().put("algorithm", "A256GCM").put("keyVersion", 3)
            .put("nonceBase64", java.util.Base64.getEncoder().encodeToString(nonce))
            .put("ciphertextBase64", java.util.Base64.getEncoder().encodeToString(bad))
        val failure = assertThrows(IllegalArgumentException::class.java) {
            WatchCloudRecordCodec.decrypt(user, id, envelope) { key }
        }
        assertEquals("cloud_record_invalid", failure.message)
        assertNull(failure.cause)
        assertFalse(failure.stackTraceToString().contains(secret))
    }

    private fun engine(store: Store, read: suspend (String) -> JSONObject?,
        write: suspend (JSONObject) -> JSONObject = { error("Unexpected write") },
        local: suspend (suspend (String, JSONObject) -> Unit) -> Unit = {},
        apply: suspend (List<WatchCloudRecordValue>) -> Unit = {}, owner: () -> Unit = {}) = WatchCloudRecordSync(
        user, "scope", store, { 3 }, { key }, owner, read, write, local, apply)

    private fun page(rows: List<Pair<String, JSONObject>>, revision: String): JSONObject = JSONObject()
        .put("formatVersion", 1).put("cursor", revision).put("headRevision", revision).put("hasMore", false)
        .put("items", JSONArray().apply { rows.forEach { (id, envelope) -> put(JSONObject()
            .put("recordId", id).put("revision", revision).put("envelope", envelope)) } })
    private fun ack(id: String, revision: String) = JSONObject().put("items", JSONArray().put(
        JSONObject().put("recordId", id).put("revision", revision)))
    private class Store : WatchRecordStore {
        val rows = linkedMapOf<String, WatchRecordStamp>()
        private val cursors = linkedMapOf<String, String>()
        override fun reset(scope: String) {
            rows.keys.removeAll { it.startsWith("$scope:") }; cursors.remove(scope)
        }
        override fun cursor(scope: String) = cursors[scope] ?: "0"
        override fun record(scope: String, id: String) = rows["$scope:$id"]
        override fun commitPage(scope: String, cursor: String, rows: List<WatchRecordStoredRow>) {
            commitWrites(scope, rows); cursors[scope] = cursor
        }
        override fun commitWrites(scope: String, rows: List<WatchRecordStoredRow>) {
            rows.forEach { this.rows["$scope:${it.recordId}"] = it.stamp }
        }
    }
}
