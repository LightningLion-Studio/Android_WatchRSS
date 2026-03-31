package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.ExternalSavedItem
import com.lightningstudio.watchrss.data.rss.RssPreviewItem
import com.lightningstudio.watchrss.data.rss.SavedItem
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class DouyinSavedOpenTarget(
    val awemeId: String?,
    val webUrl: String?,
    val title: String?,
    val author: String?,
    val summary: String?,
    val playUrl: String?,
    val coverUrl: String?
)

fun buildDouyinShareLink(awemeId: String): String? {
    val safeAwemeId = awemeId.trim()
    return safeAwemeId.takeIf { it.isNotEmpty() }?.let { "https://www.douyin.com/video/$it" }
}

fun buildDouyinPlaybackWebUrl(
    awemeId: String?,
    fallbackUrl: String?
): String? {
    val shareLink = awemeId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::buildDouyinShareLink)
    if (!shareLink.isNullOrBlank()) {
        return shareLink
    }
    val safeFallback = fallbackUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return safeFallback.takeIf(::isDouyinWebUrl)
}

fun resolveDouyinSavedOpenTarget(item: SavedItem): DouyinSavedOpenTarget? {
    val link = item.item.link?.trim()?.takeIf { it.isNotEmpty() }
    val awemeId = parseDouyinAwemeId(link)
    val playUrl = item.item.videoUrl?.trim()?.takeIf { it.isNotEmpty() }
    val webUrl = buildDouyinPlaybackWebUrl(awemeId = awemeId, fallbackUrl = link)
    if (awemeId == null && playUrl == null && webUrl == null) {
        return null
    }

    val description = item.item.description?.trim()?.takeIf { it.isNotEmpty() }
        ?: item.item.summary?.trim()?.takeIf { it.isNotEmpty() }

    return DouyinSavedOpenTarget(
        awemeId = awemeId,
        webUrl = webUrl,
        title = item.item.title.trim().takeIf { it.isNotEmpty() },
        author = extractDouyinAuthor(description),
        summary = extractDouyinSummary(description),
        playUrl = playUrl,
        coverUrl = item.item.previewImageUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: item.item.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    )
}

internal fun parseDouyinAwemeId(link: String?): String? {
    val parsedUrl = parseDouyinUrl(link) ?: return null
    if (!isDouyinHost(parsedUrl.host)) {
        return null
    }

    val segments = parsedUrl.pathSegments
    val videoIndex = segments.indexOf("video")
    if (videoIndex >= 0 && videoIndex < segments.lastIndex) {
        return segments[videoIndex + 1].trim().takeIf { it.isNotEmpty() }
    }

    return parsedUrl.queryParameters["aweme_id"]
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

internal fun isDouyinWebUrl(url: String): Boolean {
    val parsedUrl = parseDouyinUrl(url) ?: return false
    return isDouyinHost(parsedUrl.host)
}

internal fun isDouyinDetailPageUrl(url: String?): Boolean {
    val parsedUrl = parseDouyinUrl(url) ?: return false
    if (!isDouyinHost(parsedUrl.host)) {
        return false
    }
    val segments = parsedUrl.pathSegments
    val videoIndex = segments.indexOf("video")
    return videoIndex >= 0 && videoIndex < segments.lastIndex
}

internal fun shouldRefreshDouyinPlayback(playUrl: String?): Boolean {
    val safePlayUrl = playUrl?.trim().orEmpty()
    return safePlayUrl.isEmpty() || isDouyinDetailPageUrl(safePlayUrl)
}

private fun extractDouyinAuthor(description: String?): String? {
    val safeDescription = description?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!safeDescription.startsWith("作者：")) {
        return null
    }
    return safeDescription
        .removePrefix("作者：")
        .substringBefore("·")
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun extractDouyinSummary(description: String?): String? {
    val safeDescription = description?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!safeDescription.startsWith("作者：")) {
        return safeDescription
    }
    val remainder = safeDescription.substringAfter("·", "").trim()
    return remainder.takeIf { it.isNotEmpty() }
}

private fun parseDouyinUrl(url: String?): ParsedDouyinUrl? {
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
            val rawKey = if (separatorIndex >= 0) {
                pair.substring(0, separatorIndex)
            } else {
                pair
            }
            val rawValue = if (separatorIndex >= 0) {
                pair.substring(separatorIndex + 1)
            } else {
                ""
            }
            val key = decodeUrlComponent(rawKey)
            key.takeIf { it.isNotBlank() }?.let { decodedKey ->
                decodedKey to decodeUrlComponent(rawValue)
            }
        }
        ?.groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .orEmpty()
    return ParsedDouyinUrl(
        host = host,
        pathSegments = pathSegments,
        queryParameters = queryParameters
    )
}

private fun decodeUrlComponent(value: String): String {
    return runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}

private fun isDouyinHost(host: String): Boolean {
    return host.contains("douyin.com") || host.contains("iesdouyin.com")
}

private data class ParsedDouyinUrl(
    val host: String,
    val pathSegments: List<String>,
    val queryParameters: Map<String, List<String>>
)

fun buildDouyinExternalSavedItem(
    awemeId: String,
    title: String?,
    author: String?,
    playUrl: String?,
    coverUrl: String?,
    likeCount: Long
): ExternalSavedItem? {
    val safePlayUrl = playUrl?.trim()?.takeIf { it.isNotEmpty() }
    val link = buildDouyinShareLink(awemeId)
    if (link == null && safePlayUrl == null) return null

    val safeTitle = title?.trim().orEmpty().ifBlank { "抖音视频" }
    val safeAuthor = author?.trim()?.takeIf { it.isNotEmpty() }
    val descriptionParts = buildList {
        safeAuthor?.let { add("作者：$it") }
        if (likeCount > 0L) {
            add("点赞 $likeCount")
        }
    }
    val description = descriptionParts.joinToString(" · ").ifBlank { null }
    val safeAwemeId = awemeId.trim()

    return ExternalSavedItem(
        channelUrl = BuiltinChannelType.DOUYIN.url,
        item = RssPreviewItem(
            title = safeTitle,
            description = description,
            content = description,
            link = link,
            guid = safeAwemeId.takeIf { it.isNotEmpty() }?.let { "douyin:$it" },
            pubDate = null,
            imageUrl = coverUrl?.trim()?.takeIf { it.isNotEmpty() },
            audioUrl = null,
            videoUrl = safePlayUrl
        )
    )
}

fun buildDouyinExternalSavedItem(item: DouyinStreamItem): ExternalSavedItem? {
    return buildDouyinExternalSavedItem(
        awemeId = item.awemeId,
        title = item.title,
        author = item.author,
        playUrl = item.playUrl,
        coverUrl = item.coverUrl,
        likeCount = item.likeCount
    )
}

fun containsDouyinSavedItem(
    items: List<SavedItem>,
    awemeId: String,
    link: String?,
    playUrl: String
): Boolean {
    val safeAwemeId = awemeId.trim()
    val safeLink = link?.trim().orEmpty()
    val safePlayUrl = playUrl.trim()

    return items.any { savedItem ->
        val savedLink = savedItem.item.link?.trim().orEmpty()
        val savedVideoUrl = savedItem.item.videoUrl?.trim().orEmpty()
        when {
            safeLink.isNotEmpty() && savedLink == safeLink -> true
            safePlayUrl.isNotEmpty() && savedVideoUrl == safePlayUrl -> true
            safeAwemeId.isNotEmpty() && savedLink.endsWith("/video/$safeAwemeId") -> true
            else -> false
        }
    }
}

fun containsDouyinSavedItem(
    items: List<SavedItem>,
    item: DouyinStreamItem
): Boolean {
    return containsDouyinSavedItem(
        items = items,
        awemeId = item.awemeId,
        link = buildDouyinShareLink(item.awemeId),
        playUrl = item.playUrl
    )
}
