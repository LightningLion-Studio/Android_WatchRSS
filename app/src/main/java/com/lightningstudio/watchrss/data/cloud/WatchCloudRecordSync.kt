package com.lightningstudio.watchrss.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal class WatchCloudRecordCursorInvalid : IOException("cloud_record_cursor_invalid")
internal class WatchCloudRecordConflict : IOException("cloud_record_conflict")

/** Transport/merge callbacks keep the cursor and CAS protocol independently testable. */
internal class WatchCloudRecordSync(
    private val userId: String,
    private val scope: String,
    private val store: WatchRecordStore,
    private val keyVersion: () -> Int,
    private val keyForVersion: (Int) -> ByteArray?,
    private val checkOwner: () -> Unit,
    private val readPage: suspend (String) -> JSONObject?,
    private val writeBatch: suspend (JSONObject) -> JSONObject,
    private val streamLocal: suspend (suspend (String, JSONObject) -> Unit) -> Unit,
    private val apply: suspend (List<WatchCloudRecordValue>) -> Unit,
    private val localProjection: suspend (JSONObject) -> WatchRecordProjection? = { null }
) {
    suspend fun sync(writable: Boolean): Boolean {
        if (!pull()) return false // GET only; this client can never grant consent.
        if (!writable) return true
        repeat(3) {
            try { push(); return true }
            catch (_: WatchCloudRecordConflict) {
                // A different writer won: apply its LWW state and re-enumerate
                // the local library before constructing any replacement CAS.
                checkOwner()
                if (!pull()) return false
            }
        }
        throw IOException("云同步变更较多，请稍后重试")
    }

    private suspend fun pull(): Boolean {
        var cursor = store.cursor(scope)
        var reset = false
        var pages = 0
        while (true) {
            require(++pages <= 2000) { "云状态分页过多，请稍后继续同步" }
            checkOwner()
            val page = try { readPage(cursor) } catch (failure: WatchCloudRecordCursorInvalid) {
                checkOwner()
                if (reset) throw failure
                store.reset(scope)
                reset = true
                cursor = "0"
                continue
            } ?: return false // 404, not 403.
            checkOwner()
            WatchCloudProtocolV2.features(page)
            val rows = page.getJSONArray("items")
            when (page.getInt("formatVersion")) {
                0 -> { require(rows.length() == 0); checkOwner(); store.reset(scope); return false }
                1 -> Unit
                else -> throw WatchCloudUpgradeRequired()
            }
            val next = page.getString("cursor")
            val head = page.getString("headRevision")
            require(revision(next) >= revision(cursor) && revision(head) >= revision(next))
            require(rows.length() <= 50)
            val hasMore = page.getBoolean("hasMore")
            if (hasMore) require(rows.length() > 0 && revision(next) > revision(cursor)) { "云同步游标未前进" }
            else require(next == head) { "云同步终页不完整" }
            val decoded = ArrayList<WatchCloudRecordValue>(rows.length())
            val stamps = ArrayList<WatchRecordStoredRow>(rows.length())
            var last = revision(cursor)
            val seen = hashSetOf<String>()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val id = row.getString("recordId")
                val rev = row.getString("revision")
                require(seen.add(id) && revision(rev) > last && revision(rev) <= revision(next))
                last = revision(rev)
                val value = WatchCloudRecordCodec.decrypt(userId, id, row.getJSONObject("envelope"), keyForVersion)
                decoded.add(value)
                stamps.add(WatchRecordStoredRow(id, WatchRecordStamp(rev,
                    WatchCloudRecordCodec.fingerprint(value.kind, value.value))))
            }
            checkOwner()
            apply(decoded)
            checkOwner()
            // If merge, integrity or owner checks fail, replay this page next time.
            val baselines = stamps.mapIndexed { index, stamp ->
                val row = decoded[index]
                if (row.kind != "article") stamp else {
                    checkOwner()
                    val local = localProjection(row.value)
                    stamp.copy(stamp = stamp.stamp.copy(remoteContentHash = row.value.optString("contentHash"),
                        localContentHash = local?.contentHash, remoteImportedAt = row.value.optLong("importedAt"),
                        localImportedAt = local?.importedAt, metadataOnly = local?.metadataOnly == true || row.value.optBoolean("deleted")))
                }
            }
            checkOwner()
            store.commitPage(scope, next, baselines)
            if (!hasMore) return true
            cursor = next
        }
    }

    private suspend fun push() {
        val version = keyVersion()
        val key = keyForVersion(version)?.copyOf() ?: error("云同步密钥尚未授权")
        val pending = ArrayList<Pair<JSONObject, WatchRecordStoredRow>>(16)
        var pendingBytes = 64
        suspend fun flush() {
            if (pending.isEmpty()) return
            checkOwner()
            val body = JSONObject().put("formatVersion", 1).put("items", JSONArray().apply {
                pending.forEach { put(it.first) }
            })
            val response = writeBatch(body)
            checkOwner()
            val result = response.getJSONArray("items")
            require(result.length() == pending.size)
            val returned = (0 until result.length()).associate { index ->
                val item = result.getJSONObject(index)
                item.getString("recordId") to item.getString("revision")
            }
            require(returned.size == pending.size)
            val saved = pending.map { (_, row) ->
                val rev = returned[row.recordId] ?: error("云记录提交响应不完整")
                require(revision(rev) > revision(row.stamp.revision))
                row.copy(stamp = row.stamp.copy(revision = rev))
            }
            store.commitWrites(scope, saved)
            pending.clear()
            pendingBytes = 64
        }
        try {
            streamLocal { kind, raw ->
                checkOwner()
                val value = WatchCloudRecordCodec.metadata(kind, raw)
                val id = WatchCloudRecordCodec.recordId(key, userId, kind, value)
                // Multiple local channel rows can share one canonical article ID.
                if (pending.any { it.second.recordId == id }) flush()
                val known = store.record(scope, id)
                val localHash = value.optString("contentHash")
                val localImportedAt = value.optLong("importedAt")
                if (kind == "article" && known?.metadataOnly == true) {
                    val deletedWithoutLocalBody = value.optBoolean("deleted") && known.localContentHash == null
                    if (known.remoteContentHash != null && (known.localContentHash == localHash || deletedWithoutLocalBody))
                        value.put("contentHash", known.remoteContentHash)
                    if (known.remoteImportedAt != null && (known.localImportedAt == localImportedAt || deletedWithoutLocalBody))
                        value.put("importedAt", known.remoteImportedAt)
                }
                val hash = WatchCloudRecordCodec.fingerprint(kind, value)
                if (known?.fingerprint != hash) {
                    val (_, envelope) = WatchCloudRecordCodec.encrypt(key, userId, version, kind, value)
                    val item = JSONObject().put("recordId", id)
                        .put("expectedRevision", known?.revision ?: "0").put("envelope", envelope)
                    val bytes = item.toString().toByteArray(Charsets.UTF_8).size + 1
                    if (pending.isNotEmpty() && (pending.size >= 16 || pendingBytes + bytes > 900 * 1024)) flush()
                    pending.add(item to WatchRecordStoredRow(id, WatchRecordStamp(known?.revision ?: "0", hash,
                        if (kind == "article") value.optString("contentHash") else null,
                        if (kind == "article") localHash else null,
                        if (kind == "article") value.optLong("importedAt") else null,
                        if (kind == "article") localImportedAt else null,
                        known?.metadataOnly == true && localHash == known.localContentHash)))
                    pendingBytes += bytes
                }
            }
            flush()
        } finally { key.fill(0) }
    }

    private fun revision(value: String): Long {
        require(value.matches(Regex("0|[1-9][0-9]*"))) { "云记录版本无效" }
        return value.toLong().also { require(it >= 0) }
    }
}
