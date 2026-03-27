package com.lightningstudio.watchrss.data.bili

import org.json.JSONArray
import org.json.JSONObject

data class BiliInteractionState(
    val isLiked: Boolean = false,
    val isCoined: Boolean = false,
    val isFavorited: Boolean = false
) {
    val hasAnyInteraction: Boolean
        get() = isLiked || isCoined || isFavorited
}

internal data class BiliInteractionRecord(
    val aid: Long? = null,
    val bvid: String? = null,
    val state: BiliInteractionState = BiliInteractionState(),
    val updatedAtMillis: Long = 0L
)

internal const val BILI_INTERACTION_STATE_LIMIT = 50

internal fun parseBiliInteractionRecords(raw: String): List<BiliInteractionRecord> {
    return runCatching {
        val array = JSONArray(raw)
        val records = mutableListOf<BiliInteractionRecord>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val aid = obj.optLong("aid", -1L).takeIf { it > 0 }
            val bvid = normalizedBvid(obj.optString("bvid", ""))
            val state = BiliInteractionState(
                isLiked = obj.optBoolean("isLiked", false),
                isCoined = obj.optBoolean("isCoined", false),
                isFavorited = obj.optBoolean("isFavorited", false)
            )
            if ((aid == null && bvid.isNullOrBlank()) || !state.hasAnyInteraction) {
                continue
            }
            records += BiliInteractionRecord(
                aid = aid,
                bvid = bvid,
                state = state,
                updatedAtMillis = obj.optLong("updatedAt", 0L).coerceAtLeast(0L)
            )
        }
        records.sortedByDescending { it.updatedAtMillis }
    }.getOrDefault(emptyList())
}

internal fun buildBiliInteractionRecordsJson(records: List<BiliInteractionRecord>): String {
    val array = JSONArray()
    records.forEach { record ->
        val obj = JSONObject()
        record.aid?.let { obj.put("aid", it) }
        record.bvid?.let { obj.put("bvid", it) }
        obj.put("isLiked", record.state.isLiked)
        obj.put("isCoined", record.state.isCoined)
        obj.put("isFavorited", record.state.isFavorited)
        obj.put("updatedAt", record.updatedAtMillis)
        array.put(obj)
    }
    return array.toString()
}

internal fun findBiliInteractionState(
    records: List<BiliInteractionRecord>,
    aid: Long?,
    bvid: String?
): BiliInteractionState {
    val safeBvid = normalizedBvid(bvid)
    records.firstOrNull { !safeBvid.isNullOrBlank() && it.bvid.equals(safeBvid, ignoreCase = true) }
        ?.let { return it.state }
    records.firstOrNull { aid != null && it.aid == aid }
        ?.let { return it.state }
    return BiliInteractionState()
}

internal fun upsertBiliInteractionState(
    records: List<BiliInteractionRecord>,
    aid: Long?,
    bvid: String?,
    state: BiliInteractionState,
    updatedAtMillis: Long = System.currentTimeMillis(),
    limit: Int = BILI_INTERACTION_STATE_LIMIT
): List<BiliInteractionRecord> {
    val safeBvid = normalizedBvid(bvid)
    val remaining = records.filterNot { matchesInteractionIdentity(it, aid, safeBvid) }
    if (!state.hasAnyInteraction || (aid == null && safeBvid.isNullOrBlank())) {
        return remaining.sortedByDescending { it.updatedAtMillis }.take(limit)
    }
    return (listOf(
        BiliInteractionRecord(
            aid = aid,
            bvid = safeBvid,
            state = state,
            updatedAtMillis = updatedAtMillis
        )
    ) + remaining)
        .sortedByDescending { it.updatedAtMillis }
        .take(limit)
}

private fun matchesInteractionIdentity(record: BiliInteractionRecord, aid: Long?, bvid: String?): Boolean {
    val sameAid = aid != null && record.aid == aid
    val sameBvid = !bvid.isNullOrBlank() && record.bvid.equals(bvid, ignoreCase = true)
    return sameAid || sameBvid
}

private fun normalizedBvid(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
