package com.lightningstudio.watchrss.data.rss

enum class SaveType {
    FAVORITE,
    WATCH_LATER
}

data class SavedState(
    val isFavorite: Boolean,
    val isWatchLater: Boolean
)

data class SavedItem(
    val item: RssItem,
    val channelTitle: String,
    val savedAt: Long,
    val saveType: SaveType
)

enum class OfflineMediaType {
    IMAGE,
    VIDEO
}

data class OfflineMedia(
    val itemId: Long,
    val type: OfflineMediaType,
    val originUrl: String,
    val localPath: String?
)
