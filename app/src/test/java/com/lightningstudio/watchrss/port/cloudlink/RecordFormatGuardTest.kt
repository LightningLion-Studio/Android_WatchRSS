package com.lightningstudio.watchrss.port.cloudlink

import com.lightningstudio.watchrss.data.cloud.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class RecordFormatGuardTest {
    @Test fun futureFieldStopsTheEntirePageBeforeMergeCursorOrUpload() = runBlocking {
        val user = "fixture-user"; val key = ByteArray(32) { 7 }
        val (firstId, first) = WatchCloudRecordCodec.encrypt(key, user, 1, "source", JSONObject().put("url", "https://fixture.invalid/a"))
        val (id, envelope) = WatchCloudRecordCodec.encrypt(key, user, 1, "source", JSONObject().put("url", "https://fixture.invalid/b"))
        val nonce = Base64.getDecoder().decode(envelope.getString("nonceBase64"))
        val derived = WatchCloudCodec.hkdf(key, user.toByteArray(), "watchrss/cloud/record/v1".toByteArray())
        val aad = "watchrss-cloud-record-v1:$user:$id:1".toByteArray()
        val plain = WatchCloudCodec.aes(false, derived, nonce, Base64.getDecoder().decode(envelope.getString("ciphertextBase64")), aad)
        val future = JSONObject(plain.toString(Charsets.UTF_8)).apply { getJSONObject("value").put("futureOverlay", JSONObject().put("version", 2)) }
        envelope.put("ciphertextBase64", Base64.getEncoder().encodeToString(WatchCloudCodec.aes(true, derived, nonce, future.toString().toByteArray(), aad)))
        var merged = 0; var writes = 0; var enumerated = 0
        val store = Store()
        val sync = WatchCloudRecordSync(user, "scope", store, { 1 }, { key }, {}, readPage = {
            JSONObject().put("formatVersion", 1).put("cursor", "9").put("headRevision", "9").put("hasMore", false)
                .put("items", JSONArray().put(row(firstId, first, "8")).put(row(id, envelope, "9")))
        }, writeBatch = { writes++; error("unexpected write") }, streamLocal = { enumerated++ }, apply = { merged++ })
        try { sync.sync(true); fail("future page must stop") } catch (_: WatchCloudUpgradeRequired) { }
        assertEquals(0, merged); assertEquals(0, writes); assertEquals(0, enumerated)
        assertEquals("7", store.cursor("scope")); assertEquals(0, store.commits)
        derived.fill(0); plain.fill(0)
    }
    private fun row(id: String, envelope: JSONObject, revision: String) = JSONObject().put("recordId", id).put("revision", revision).put("envelope", envelope)
    private class Store : WatchRecordStore {
        var commits = 0
        override fun reset(scope: String) { error("must retain cursor") }
        override fun cursor(scope: String) = "7"
        override fun record(scope: String, id: String): WatchRecordStamp? = null
        override fun commitPage(scope: String, cursor: String, rows: List<WatchRecordStoredRow>) { commits++ }
        override fun commitWrites(scope: String, rows: List<WatchRecordStoredRow>) { commits++ }
    }
}
