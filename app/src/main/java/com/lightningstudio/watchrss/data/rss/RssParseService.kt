package com.lightningstudio.watchrss.data.rss

import android.net.Uri
import com.lightningstudio.watchrss.data.db.RssItemEntity
import com.prof18.rssparser.model.RssChannel as ParsedChannel
import com.prof18.rssparser.model.RssItem as ParsedItem

class RssParseService {
    fun channelTitle(parsed: ParsedChannel, url: String): String {
        val title = parsed.title?.trim().orEmpty()
        if (title.isNotEmpty()) return title
        val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
        return host.ifEmpty { url }
    }

    fun toPreviewItem(item: ParsedItem): RssPreviewItem = RssPreviewItem(
        title = item.title?.trim()?.ifEmpty { null },
        description = item.description?.trim()?.ifEmpty { null },
        content = item.content?.trim()?.ifEmpty { null },
        link = item.link?.trim()?.ifEmpty { null },
        guid = item.guid?.trim()?.ifEmpty { null },
        pubDate = item.pubDate?.trim()?.ifEmpty { null },
        imageUrl = item.image?.trim()?.ifEmpty { null },
        audioUrl = item.audio?.trim()?.ifEmpty { null },
        videoUrl = item.video?.trim()?.ifEmpty { null }
    )

    fun toEntityFromParsedItem(
        item: ParsedItem,
        channelId: Long,
        isRead: Boolean,
        fetchedAt: Long,
        contentOverride: String? = null
    ): RssItemEntity {
        val safeTitle = item.title?.trim().takeUnless { it.isNullOrEmpty() }
            ?: item.link?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "未命名内容"
        val safeDescription = item.description?.trim()?.ifEmpty { null }
        val safeContent = contentOverride?.trim()?.ifEmpty { null }
            ?: item.content?.trim()?.ifEmpty { null }
        val safeLink = item.link?.trim()?.ifEmpty { null }
        val safeGuid = item.guid?.trim()?.ifEmpty { null }
        val safePubDate = item.pubDate?.trim()?.ifEmpty { null }
        val safeImage = item.image?.trim()?.ifEmpty { null }
        val safeAudio = item.audio?.trim()?.ifEmpty { null }
        val safeVideo = item.video?.trim()?.ifEmpty { null }
        val preview = RssPreviewFormatter.buildPreview(
            description = safeDescription,
            content = safeContent,
            imageUrl = safeImage,
            link = safeLink
        )
        val dedupKey = RssDedupKey.compute(safeGuid, safeLink, safeTitle)

        return RssItemEntity(
            channelId = channelId,
            title = safeTitle,
            description = safeDescription,
            content = safeContent,
            link = safeLink,
            guid = safeGuid,
            pubDate = safePubDate,
            imageUrl = safeImage,
            audioUrl = safeAudio,
            videoUrl = safeVideo,
            summary = preview.summary,
            previewImageUrl = preview.previewImageUrl,
            isRead = isRead,
            isLiked = false,
            readingProgress = 0f,
            dedupKey = dedupKey,
            fetchedAt = fetchedAt,
            contentSizeBytes = estimateContentSize(
                safeTitle,
                safeDescription,
                safeContent,
                safeLink,
                safeImage,
                safeAudio,
                safeVideo
            )
        )
    }

    fun toEntityFromPreviewItem(
        item: RssPreviewItem,
        channelId: Long,
        isRead: Boolean,
        fetchedAt: Long
    ): RssItemEntity {
        val safeTitle = item.title?.trim().takeUnless { it.isNullOrEmpty() }
            ?: item.link?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "未命名内容"
        val safeDescription = item.description?.trim()?.ifEmpty { null }
        val safeContent = item.content?.trim()?.ifEmpty { null }
        val safeLink = item.link?.trim()?.ifEmpty { null }
        val safeGuid = item.guid?.trim()?.ifEmpty { null }
        val safePubDate = item.pubDate?.trim()?.ifEmpty { null }
        val safeImage = item.imageUrl?.trim()?.ifEmpty { null }
        val safeAudio = item.audioUrl?.trim()?.ifEmpty { null }
        val safeVideo = item.videoUrl?.trim()?.ifEmpty { null }
        val preview = RssPreviewFormatter.buildPreview(
            description = safeDescription,
            content = safeContent,
            imageUrl = safeImage,
            link = safeLink
        )
        val dedupKey = RssDedupKey.compute(safeGuid, safeLink, safeTitle)

        return RssItemEntity(
            channelId = channelId,
            title = safeTitle,
            description = safeDescription,
            content = safeContent,
            link = safeLink,
            guid = safeGuid,
            pubDate = safePubDate,
            imageUrl = safeImage,
            audioUrl = safeAudio,
            videoUrl = safeVideo,
            summary = preview.summary,
            previewImageUrl = preview.previewImageUrl,
            isRead = isRead,
            isLiked = false,
            readingProgress = 0f,
            dedupKey = dedupKey,
            fetchedAt = fetchedAt,
            contentSizeBytes = estimateContentSize(
                safeTitle,
                safeDescription,
                safeContent,
                safeLink,
                safeImage,
                safeAudio,
                safeVideo
            )
        )
    }

    private fun estimateContentSize(vararg parts: String?): Long {
        var total = 0L
        for (part in parts) {
            if (!part.isNullOrEmpty()) {
                total += part.toByteArray(Charsets.UTF_8).size
            }
        }
        return total
    }
}
