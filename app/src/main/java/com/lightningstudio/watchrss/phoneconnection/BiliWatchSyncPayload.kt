package com.lightningstudio.watchrss.phoneconnection

import com.lightningstudio.watchrss.data.bili.BiliPlaybackProgress
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryItem
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryPage
import org.json.JSONArray
import org.json.JSONObject

data class BiliWatchHistoryCursorPayload(
    val max: Long? = null,
    val viewAt: Long? = null,
    val business: String? = null,
    val ps: Int? = null
)

data class BiliWatchHistoryItemPayload(
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long? = null,
    val page: Int? = null,
    val part: String? = null,
    val business: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val viewAt: Long? = null,
    val durationSeconds: Long? = null,
    val progressSeconds: Long? = null,
    val authorName: String? = null,
    val authorMid: Long? = null,
    val link: String? = null
)

data class BiliWatchHistoryPayload(
    val cursor: BiliWatchHistoryCursorPayload? = null,
    val items: List<BiliWatchHistoryItemPayload> = emptyList()
)

data class BiliPlaybackProgressPayload(
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMillis: Long,
    val link: String? = null
)

object BiliWatchSyncPayload {
    fun buildHistoryPayload(
        page: BiliHistoryPage,
        buildLink: (bvid: String?, aid: Long?, cid: Long?) -> String?
    ): BiliWatchHistoryPayload {
        return BiliWatchHistoryPayload(
            cursor = page.cursor?.let {
                BiliWatchHistoryCursorPayload(
                    max = it.max,
                    viewAt = it.viewAt,
                    business = it.business,
                    ps = it.ps
                )
            },
            items = page.items.map { item ->
                buildHistoryItemPayload(item, buildLink)
            }
        )
    }

    fun buildPlaybackProgressPayload(
        records: List<BiliPlaybackProgress>,
        buildLink: (bvid: String?, aid: Long?, cid: Long?) -> String?
    ): List<BiliPlaybackProgressPayload> {
        return records.map { record ->
            BiliPlaybackProgressPayload(
                aid = record.aid,
                bvid = record.bvid,
                cid = record.cid,
                positionMs = record.positionMs,
                durationMs = record.durationMs,
                updatedAtMillis = record.updatedAtMillis,
                link = buildLink(record.bvid, record.aid, record.cid)
            )
        }
    }

    private fun buildHistoryItemPayload(
        item: BiliHistoryItem,
        buildLink: (bvid: String?, aid: Long?, cid: Long?) -> String?
    ): BiliWatchHistoryItemPayload {
        val entry = item.history
        return BiliWatchHistoryItemPayload(
            aid = entry?.oid,
            bvid = entry?.bvid,
            cid = entry?.cid,
            page = entry?.page,
            part = entry?.part,
            business = entry?.business,
            title = item.title,
            cover = item.cover,
            viewAt = item.viewAt,
            durationSeconds = item.duration,
            progressSeconds = item.progress,
            authorName = item.authorName,
            authorMid = item.authorMid,
            link = buildLink(entry?.bvid, entry?.oid, entry?.cid)
        )
    }
}

fun BiliWatchHistoryPayload.toJsonObject(): JSONObject {
    return JSONObject().apply {
        put("cursor", cursor?.toJsonObject() ?: JSONObject.NULL)
        put(
            "items",
            JSONArray().apply {
                items.forEach { item ->
                    put(item.toJsonObject())
                }
            }
        )
    }
}

fun List<BiliPlaybackProgressPayload>.toJsonArray(): JSONArray {
    return JSONArray().apply {
        this@toJsonArray.forEach { item ->
            put(item.toJsonObject())
        }
    }
}

private fun BiliWatchHistoryCursorPayload.toJsonObject(): JSONObject {
    return JSONObject().apply {
        max?.let { put("max", it) }
        viewAt?.let { put("viewAt", it) }
        business?.takeIf { it.isNotBlank() }?.let { put("business", it) }
        ps?.let { put("ps", it) }
    }
}

private fun BiliWatchHistoryItemPayload.toJsonObject(): JSONObject {
    return JSONObject().apply {
        aid?.let { put("aid", it) }
        bvid?.takeIf { it.isNotBlank() }?.let { put("bvid", it) }
        cid?.let { put("cid", it) }
        page?.let { put("page", it) }
        part?.takeIf { it.isNotBlank() }?.let { put("part", it) }
        business?.takeIf { it.isNotBlank() }?.let { put("business", it) }
        title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
        cover?.takeIf { it.isNotBlank() }?.let { put("cover", it) }
        viewAt?.let { put("viewAt", it) }
        durationSeconds?.let { put("durationSeconds", it) }
        progressSeconds?.let { put("progressSeconds", it) }
        authorName?.takeIf { it.isNotBlank() }?.let { put("authorName", it) }
        authorMid?.let { put("authorMid", it) }
        link?.takeIf { it.isNotBlank() }?.let { put("link", it) }
    }
}

private fun BiliPlaybackProgressPayload.toJsonObject(): JSONObject {
    return JSONObject().apply {
        aid?.let { put("aid", it) }
        bvid?.takeIf { it.isNotBlank() }?.let { put("bvid", it) }
        put("cid", cid)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("updatedAtMillis", updatedAtMillis)
        link?.takeIf { it.isNotBlank() }?.let { put("link", it) }
    }
}
