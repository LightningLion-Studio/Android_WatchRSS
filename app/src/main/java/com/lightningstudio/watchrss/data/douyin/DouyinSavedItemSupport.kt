package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.ExternalSavedItem
import com.lightningstudio.watchrss.data.rss.RssPreviewItem
import com.lightningstudio.watchrss.data.rss.SavedItem

fun buildDouyinShareLink(awemeId: String): String? {
    val safeAwemeId = awemeId.trim()
    return safeAwemeId.takeIf { it.isNotEmpty() }?.let { "https://www.douyin.com/video/$it" }
}

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
