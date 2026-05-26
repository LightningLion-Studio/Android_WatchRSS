package com.lightningstudio.watchrss.data.rss

data class SyncedSavedArticle(
    val articleId: String,
    val sourceDeviceId: String,
    val url: String,
    val title: String,
    val siteName: String,
    val excerpt: String,
    val contentHtml: String?,
    val contentText: String,
    val imageUrl: String?,
    val contentHash: String,
    val importedAt: Long,
    val updatedAt: Long,
    val independentSaved: Boolean = false,
    val independentChangedAt: Long = 0L,
    val independentSortOrder: Long = 0L,
    val rssSourceUrl: String? = null,
    val rssSourceTitle: String? = null,
    val favoriteSaved: Boolean,
    val favoriteChangedAt: Long,
    val favoriteSortOrder: Long,
    val watchLaterSaved: Boolean,
    val watchLaterChangedAt: Long,
    val watchLaterSortOrder: Long,
    val deleted: Boolean,
    val deletedAt: Long
)

data class SyncedSavedArticleMergeStats(
    val received: Int,
    val applied: Int
)

data class SyncedRssSource(
    val url: String,
    val sourceDeviceId: String,
    val title: String,
    val description: String,
    val siteUrl: String?,
    val imageUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Long,
    val isPinned: Boolean = false,
    val deleted: Boolean,
    val deletedAt: Long
)

data class SyncedRssSourceMergeStats(
    val received: Int,
    val applied: Int
)
