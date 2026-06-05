package com.lightningstudio.watchrss.data.bili

import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.ExternalSavedItem
import com.lightningstudio.watchrss.data.rss.RssPreviewItem
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class BiliVideoTarget(
    val aid: Long?,
    val bvid: String?,
    val cid: Long?
)

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

fun buildBiliPlaybackWebUrl(
    aid: Long?,
    bvid: String?,
    cid: Long?,
    fallbackUrl: String?
): String? {
    buildBiliSavedLink(bvid, aid, cid)?.let { return it }
    val safeFallback = fallbackUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return safeFallback.takeIf(::isBiliDetailPageUrl)
}

fun parseBiliVideoTarget(link: String?): BiliVideoTarget? {
    val raw = link?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (raw.startsWith("BV", ignoreCase = true)) {
        return BiliVideoTarget(aid = null, bvid = raw.canonicalBvid(), cid = null)
    }
    if (raw.startsWith("av", ignoreCase = true)) {
        return BiliVideoTarget(aid = raw.drop(2).toLongOrNull(), bvid = null, cid = null)
            .takeIf { it.aid != null }
    }

    val parsedUrl = parseBiliUrl(raw) ?: return null
    if (!isBiliHost(parsedUrl.host)) return null
    val cid = parsedUrl.queryParameters["cid"]?.firstOrNull()?.toLongOrNull()
    val segments = parsedUrl.pathSegments
    val videoIndex = segments.indexOf("video")
    val rawId = segments.getOrNull(videoIndex + 1)
        ?: parsedUrl.queryParameters["bvid"]?.firstOrNull()
        ?: parsedUrl.queryParameters["aid"]?.firstOrNull()?.let { "av$it" }
        ?: return null
    return when {
        rawId.startsWith("BV", ignoreCase = true) -> {
            BiliVideoTarget(aid = null, bvid = rawId.canonicalBvid(), cid = cid)
        }
        rawId.startsWith("av", ignoreCase = true) -> {
            BiliVideoTarget(aid = rawId.drop(2).toLongOrNull(), bvid = null, cid = cid)
        }
        else -> null
    }?.takeIf { it.aid != null || !it.bvid.isNullOrBlank() }
}

internal fun isBiliWebUrl(url: String): Boolean {
    val parsedUrl = parseBiliUrl(url) ?: return false
    return isBiliHost(parsedUrl.host)
}

internal fun isBiliDetailPageUrl(url: String?): Boolean {
    val parsedUrl = parseBiliUrl(url) ?: return false
    if (!isBiliHost(parsedUrl.host)) return false
    val segments = parsedUrl.pathSegments
    val videoIndex = segments.indexOf("video")
    return videoIndex >= 0 && videoIndex < segments.lastIndex
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
            videoUrl = link
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

private fun parseBiliUrl(url: String?): ParsedBiliUrl? {
    val safeUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { URI(safeUrl) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    val pathSegments = uri.path
        ?.split('/')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    val queryParameters = uri.rawQuery
        ?.split('&')
        ?.mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val separatorIndex = pair.indexOf('=')
            val rawKey = if (separatorIndex >= 0) pair.substring(0, separatorIndex) else pair
            val rawValue = if (separatorIndex >= 0) pair.substring(separatorIndex + 1) else ""
            val key = decodeUrlComponent(rawKey)
            key.takeIf { it.isNotBlank() }?.let { decodedKey ->
                decodedKey to decodeUrlComponent(rawValue)
            }
        }
        ?.groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .orEmpty()
    return ParsedBiliUrl(
        host = host,
        pathSegments = pathSegments,
        queryParameters = queryParameters
    )
}

private fun isBiliHost(host: String): Boolean {
    return host == "bilibili.com" || host.endsWith(".bilibili.com")
}

private fun decodeUrlComponent(value: String): String {
    return runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}

private fun String.canonicalBvid(): String {
    return take(2).uppercase() + drop(2)
}

private data class ParsedBiliUrl(
    val host: String,
    val pathSegments: List<String>,
    val queryParameters: Map<String, List<String>>
)
