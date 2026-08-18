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
    val unreadCount: Int,
    val continuePlaybackInBackground: Boolean = false
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
    val fetchedAt: Long,
    val contentHash: String = ""
)

fun RssItem.effectiveContent(useOriginalContent: Boolean): String? {
    val safeOriginal = originalContent.contentOrNull()
    val safeContent = content.contentOrNull()
    val safeDescription = description.contentOrNull()
    return if (useOriginalContent) {
        safeOriginal ?: safeContent ?: safeDescription
    } else {
        safeContent ?: safeDescription
    }
}

fun RssItem.isOriginalContentMissing(): Boolean = originalContent.isNullOrBlank()

private fun String?.contentOrNull(): String? {
    val value = this ?: return null
    if (value.isBlank()) return null
    return if (value.length <= SAFE_TRIM_COPY_LIMIT_CHARS) {
        value.trim()
    } else {
        value
    }
}

private const val SAFE_TRIM_COPY_LIMIT_CHARS = 16_384
