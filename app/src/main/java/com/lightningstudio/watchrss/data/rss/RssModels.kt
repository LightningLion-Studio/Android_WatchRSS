package com.lightningstudio.watchrss.data.rss

data class RssChannel(
    val id: Long,
    val url: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val lastFetchedAt: Long?,
    val sortOrder: Long,
    val isPinned: Boolean,
    val useOriginalContent: Boolean,
    val unreadCount: Int
)

data class RssItem(
    val id: Long,
    val channelId: Long,
    val title: String,
    val description: String?,
    val content: String?,
    val originalContent: String?,
    val link: String?,
    val pubDate: String?,
    val imageUrl: String?,
    val audioUrl: String?,
    val videoUrl: String?,
    val summary: String?,
    val previewImageUrl: String?,
    val isRead: Boolean,
    val isLiked: Boolean,
    val readingProgress: Float,
    val fetchedAt: Long
)

fun RssItem.effectiveContent(useOriginalContent: Boolean): String? {
    val safeOriginal = originalContent?.trim()?.ifEmpty { null }
    val safeContent = content?.trim()?.ifEmpty { null }
    val safeDescription = description?.trim()?.ifEmpty { null }
    return if (useOriginalContent) {
        safeOriginal ?: safeContent ?: safeDescription
    } else {
        safeContent ?: safeDescription
    }
}

fun RssItem.isOriginalContentMissing(): Boolean = originalContent.isNullOrBlank()
