package com.lightningstudio.watchrss.data.bili

import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.ExternalSavedItem
import com.lightningstudio.watchrss.data.rss.RssPreviewItem
import com.lightningstudio.watchrss.sdk.bili.BiliItem

fun buildBiliShareLink(bvid: String?, aid: Long?): String? {
    val safeBvid = bvid?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        safeBvid != null -> "https://www.bilibili.com/video/$safeBvid"
        aid != null -> "https://www.bilibili.com/video/av$aid"
        else -> null
    }
}

fun buildBiliSavedLink(bvid: String?, aid: Long?, cid: Long?): String? {
    val base = buildBiliShareLink(bvid, aid) ?: return null
    val safeCid = cid ?: return base
    return "$base?cid=$safeCid"
}

fun buildBiliExternalSavedItem(
    aid: Long?,
    bvid: String?,
    cid: Long?,
    title: String?,
    owner: String?,
    coverUrl: String?,
    description: String? = null
): ExternalSavedItem {
    val safeBvid = bvid?.trim()?.takeIf { it.isNotEmpty() }
    val safeTitle = title?.trim().takeUnless { it.isNullOrBlank() }
        ?: safeBvid?.let { "BV号 $it" }
        ?: aid?.let { "av$it" }
        ?: "哔哩哔哩视频"
    val link = buildBiliSavedLink(safeBvid, aid, cid)
    val guid = when {
        safeBvid != null -> "bili:$safeBvid"
        aid != null -> "bili:av$aid"
        !link.isNullOrBlank() -> "bili:$link"
        else -> null
    }
    val safeOwner = owner?.trim()?.takeIf { it.isNotEmpty() }
    val safeDescription = description?.trim()?.takeIf { it.isNotEmpty() }
        ?: safeOwner?.let { "UP主：$it" }

    return ExternalSavedItem(
        channelUrl = BuiltinChannelType.BILI.url,
        item = RssPreviewItem(
            title = safeTitle,
            description = safeDescription,
            content = safeDescription,
            link = link,
            guid = guid,
            pubDate = null,
            imageUrl = coverUrl?.trim()?.takeIf { it.isNotEmpty() },
            audioUrl = null,
            videoUrl = null
        )
    )
}

fun buildBiliExternalSavedItem(item: BiliItem): ExternalSavedItem {
    return buildBiliExternalSavedItem(
        aid = item.aid,
        bvid = item.bvid,
        cid = item.cid,
        title = item.title,
        owner = item.owner?.name,
        coverUrl = item.cover
    )
}
