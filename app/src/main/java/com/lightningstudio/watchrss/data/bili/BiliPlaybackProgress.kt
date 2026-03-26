package com.lightningstudio.watchrss.data.bili

import org.json.JSONArray
import org.json.JSONObject

data class BiliPlaybackProgress(
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val hasVideoIdentity: Boolean
        get() = aid != null || !bvid.isNullOrBlank()
}

internal const val BILI_PLAYBACK_PROGRESS_LIMIT = 100

internal fun parseBiliPlaybackProgressRecords(raw: String): List<BiliPlaybackProgress> {
    return runCatching {
        val array = JSONArray(raw)
        val records = mutableListOf<BiliPlaybackProgress>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val cid = obj.optLong("cid", -1L).takeIf { it > 0L } ?: continue
            records += BiliPlaybackProgress(
                aid = obj.optLong("aid", -1L).takeIf { it > 0L },
                bvid = normalizePlaybackProgressBvid(obj.optString("bvid", "")),
                cid = cid,
                positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
                updatedAtMillis = obj.optLong("updatedAt", 0L).coerceAtLeast(0L)
            )
        }
        records.sortedByDescending { it.updatedAtMillis }
    }.getOrDefault(emptyList())
}

internal fun buildBiliPlaybackProgressRecordsJson(records: List<BiliPlaybackProgress>): String {
    val array = JSONArray()
    records.forEach { record ->
        val obj = JSONObject()
        record.aid?.let { obj.put("aid", it) }
        record.bvid?.let { obj.put("bvid", it) }
        obj.put("cid", record.cid)
        obj.put("positionMs", record.positionMs.coerceAtLeast(0L))
        obj.put("durationMs", record.durationMs.coerceAtLeast(0L))
        obj.put("updatedAt", record.updatedAtMillis.coerceAtLeast(0L))
        array.put(obj)
    }
    return array.toString()
}

internal fun findLatestBiliPlaybackProgress(
    records: List<BiliPlaybackProgress>,
    aid: Long?,
    bvid: String?
): BiliPlaybackProgress? {
    val safeBvid = normalizePlaybackProgressBvid(bvid)
    if (safeBvid.isNullOrBlank() && aid == null) return null
    return records
        .filter { matchesPlaybackVideo(it, aid, safeBvid) }
        .maxByOrNull { it.updatedAtMillis }
}

internal fun findBiliPlaybackProgress(
    records: List<BiliPlaybackProgress>,
    aid: Long?,
    bvid: String?,
    cid: Long
): BiliPlaybackProgress? {
    val safeBvid = normalizePlaybackProgressBvid(bvid)
    return records
        .filter { matchesPlaybackIdentity(it, aid, safeBvid, cid) }
        .maxByOrNull { it.updatedAtMillis }
}

internal fun upsertBiliPlaybackProgress(
    records: List<BiliPlaybackProgress>,
    progress: BiliPlaybackProgress,
    updatedAtMillis: Long = System.currentTimeMillis(),
    limit: Int = BILI_PLAYBACK_PROGRESS_LIMIT
): List<BiliPlaybackProgress> {
    val normalized = progress.copy(
        bvid = normalizePlaybackProgressBvid(progress.bvid),
        positionMs = progress.positionMs.coerceAtLeast(0L),
        durationMs = progress.durationMs.coerceAtLeast(0L),
        updatedAtMillis = updatedAtMillis.coerceAtLeast(0L)
    )
    if (normalized.cid <= 0L) {
        return records.sortedByDescending { it.updatedAtMillis }.take(limit)
    }
    val remaining = records.filterNot {
        matchesPlaybackIdentity(it, normalized.aid, normalized.bvid, normalized.cid)
    }
    return (listOf(normalized) + remaining)
        .sortedByDescending { it.updatedAtMillis }
        .take(limit)
}

internal fun removeBiliPlaybackProgress(
    records: List<BiliPlaybackProgress>,
    aid: Long?,
    bvid: String?,
    cid: Long,
    limit: Int = BILI_PLAYBACK_PROGRESS_LIMIT
): List<BiliPlaybackProgress> {
    if (cid <= 0L) {
        return records.sortedByDescending { it.updatedAtMillis }.take(limit)
    }
    val safeBvid = normalizePlaybackProgressBvid(bvid)
    return records.filterNot { matchesPlaybackIdentity(it, aid, safeBvid, cid) }
        .sortedByDescending { it.updatedAtMillis }
        .take(limit)
}

private fun matchesPlaybackIdentity(
    record: BiliPlaybackProgress,
    aid: Long?,
    bvid: String?,
    cid: Long
): Boolean {
    if (record.cid != cid) return false
    return matchesPlaybackVideo(record, aid, bvid) ||
        (aid == null && bvid.isNullOrBlank() && !record.hasVideoIdentity)
}

private fun matchesPlaybackVideo(record: BiliPlaybackProgress, aid: Long?, bvid: String?): Boolean {
    val sameBvid = !bvid.isNullOrBlank() && record.bvid.equals(bvid, ignoreCase = true)
    val sameAid = aid != null && record.aid == aid
    return sameBvid || sameAid
}

private fun normalizePlaybackProgressBvid(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
