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
