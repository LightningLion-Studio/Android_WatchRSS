package com.lightningstudio.watchrss.data.rss

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class ImportedTextReader(
    val marker: String,
    val byteLength: Long,
    val chunkCount: Int
)

interface RssRepository {
    fun observeCloudSyncRevision(): Flow<Long> = emptyFlow()
    fun observeHomeChannels(): Flow<List<RssChannel>>
    fun observeChannels(): Flow<List<RssChannel>>
    fun observeChannel(channelId: Long): Flow<RssChannel?>
    fun observeItemsPaged(channelId: Long, limit: Int): Flow<List<RssItem>>
    fun observeItemCount(channelId: Long): Flow<Int>
    fun observeChannelHasPlayableMedia(channelId: Long): Flow<Boolean>
    fun observeItem(itemId: Long): Flow<RssItem?>
    fun searchItems(channelId: Long, keyword: String, limit: Int): Flow<List<RssItem>>
    fun observeCacheUsageBytes(): Flow<Long>
    fun observeSavedItems(saveType: SaveType): Flow<List<SavedItem>>
    fun observeSavedState(itemId: Long): Flow<SavedState>
    fun observeOfflineMedia(itemId: Long): Flow<List<OfflineMedia>>
    suspend fun getImportedTextReader(itemId: Long): ImportedTextReader?
    suspend fun loadImportedTextChunk(marker: String, chunkIndex: Int): String?

    suspend fun previewChannel(url: String): Result<AddRssPreview>
    suspend fun confirmAddChannel(preview: RssChannelPreview): Result<RssChannel>
    suspend fun addChannel(url: String): Result<RssChannel>
    suspend fun refreshChannel(channelId: Long, refreshAll: Boolean = false): Result<Unit>
    fun refreshChannelInBackground(channelId: Long, refreshAll: Boolean = false)
    fun requestOriginalContent(itemId: Long, force: Boolean = false)
    fun requestOriginalContents(itemIds: List<Long>, force: Boolean = false)
    fun setOriginalContentUpdatesPaused(channelId: Long, paused: Boolean)
    suspend fun markItemRead(itemId: Long)
    suspend fun toggleFavorite(itemId: Long): Result<SavedState>
    suspend fun toggleWatchLater(itemId: Long): Result<SavedState>
    suspend fun reorderSavedItems(saveType: SaveType, orderedItemIds: List<Long>)
    suspend fun syncExternalSavedItem(
        item: ExternalSavedItem,
        saveType: SaveType,
        saved: Boolean
    ): Result<SavedState>
    suspend fun exportSyncedSavedArticles(deviceId: String): List<SyncedSavedArticle>
    suspend fun exportCloudRssStateArticles(deviceId: String): List<SyncedSavedArticle> =
        exportSyncedSavedArticles(deviceId)
    suspend fun exportSyncedArticleManifests(deviceId: String): List<SyncedArticleManifest>
    suspend fun getLibrarySyncCursor(peerDeviceId: String): WatchLibrarySyncCursorSnapshot
    suspend fun prepareLibrarySyncWindow(
        peerDeviceId: String,
        localDeviceId: String,
        peerAppliedLocalSeq: Long? = null
    ): WatchLibrarySyncWindow
    suspend fun markLibrarySyncSuccess(
        peerDeviceId: String,
        localSeqToInclusive: Long,
        remoteSeqToInclusive: Long,
        remoteProtocolVersion: Int,
        fullSnapshot: Boolean
    )
    suspend fun exportSyncedSavedArticlesForRequests(
        deviceId: String,
        requests: List<SyncedArticleBodyRequest>
    ): List<SyncedSavedArticle>
    suspend fun mergeSyncedSavedArticles(
        articles: List<SyncedSavedArticle>,
        remoteDeviceId: String,
        localDeviceId: String
    ): SyncedSavedArticleMergeStats
    suspend fun mergeSyncedChunkedArticles(
        articles: List<SyncedChunkedArticle>,
        remoteDeviceId: String,
        localDeviceId: String
    ): SyncedSavedArticleMergeStats
    suspend fun exportSyncedRssSources(deviceId: String): List<SyncedRssSource>
    suspend fun mergeSyncedRssSources(
        sources: List<SyncedRssSource>,
        remoteDeviceId: String,
        localDeviceId: String
    ): SyncedRssSourceMergeStats
    suspend fun retryOfflineMedia(itemId: Long)
    suspend fun toggleLike(itemId: Long): Result<Boolean>
    suspend fun markChannelRead(channelId: Long)
    suspend fun updateItemReadingProgress(itemId: Long, progress: Float)
    suspend fun moveChannelToTop(channelId: Long)
    suspend fun setChannelPinned(channelId: Long, pinned: Boolean)
    suspend fun setChannelOriginalContent(channelId: Long, enabled: Boolean)
    suspend fun setChannelContinuePlaybackInBackground(channelId: Long, enabled: Boolean)
    suspend fun deleteItem(itemId: Long)
    suspend fun clearLocalContentChannel(channelId: Long)
    suspend fun deleteChannel(channelId: Long)
    suspend fun trimCacheToLimit()
}
