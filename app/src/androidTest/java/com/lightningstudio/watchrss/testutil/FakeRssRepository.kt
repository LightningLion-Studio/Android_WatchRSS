package com.lightningstudio.watchrss.testutil

import com.lightningstudio.watchrss.data.rss.AddRssPreview
import com.lightningstudio.watchrss.data.rss.ExternalSavedItem
import com.lightningstudio.watchrss.data.rss.OfflineMedia
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssChannelPreview
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.data.rss.SavedItem
import com.lightningstudio.watchrss.data.rss.SavedState
import com.lightningstudio.watchrss.data.rss.SyncedArticleBodyRequest
import com.lightningstudio.watchrss.data.rss.SyncedArticleManifest
import com.lightningstudio.watchrss.data.rss.SyncedChunkedArticle
import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import com.lightningstudio.watchrss.data.rss.SyncedSavedArticleMergeStats
import com.lightningstudio.watchrss.data.rss.SyncedRssSource
import com.lightningstudio.watchrss.data.rss.SyncedRssSourceMergeStats
import com.lightningstudio.watchrss.data.rss.WatchLibrarySyncWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeRssRepository(
    initialChannels: List<RssChannel> = emptyList(),
    initialCacheUsageBytes: Long = 0L
) : RssRepository {
    private val channelsFlow = MutableStateFlow(initialChannels)
    private val cacheUsageBytesFlow = MutableStateFlow(initialCacheUsageBytes)

    override fun observeHomeChannels(): Flow<List<RssChannel>> = channelsFlow

    override fun observeChannels(): Flow<List<RssChannel>> = channelsFlow

    override fun observeChannel(channelId: Long): Flow<RssChannel?> {
        return channelsFlow.map { channels -> channels.firstOrNull { it.id == channelId } }
    }

    override fun observeItemsPaged(channelId: Long, limit: Int): Flow<List<RssItem>> = flowOf(emptyList())

    override fun observeItemCount(channelId: Long): Flow<Int> = flowOf(0)

    override fun observeChannelHasPlayableMedia(channelId: Long): Flow<Boolean> = flowOf(false)

    override fun observeItem(itemId: Long): Flow<RssItem?> = flowOf(null)

    override fun searchItems(channelId: Long, keyword: String, limit: Int): Flow<List<RssItem>> = flowOf(emptyList())

    override fun observeCacheUsageBytes(): Flow<Long> = cacheUsageBytesFlow

    override fun observeSavedItems(saveType: SaveType): Flow<List<SavedItem>> = flowOf(emptyList())

    override fun observeSavedState(itemId: Long): Flow<SavedState> = flowOf(SavedState(isFavorite = false, isWatchLater = false))

    override fun observeOfflineMedia(itemId: Long): Flow<List<OfflineMedia>> = flowOf(emptyList())

    override suspend fun getImportedTextReader(itemId: Long) = null

    override suspend fun loadImportedTextChunk(marker: String, chunkIndex: Int) = null

    override suspend fun previewChannel(url: String): Result<AddRssPreview> {
        return Result.failure(UnsupportedOperationException("previewChannel is not configured in FakeRssRepository"))
    }

    override suspend fun confirmAddChannel(preview: RssChannelPreview): Result<RssChannel> {
        return Result.failure(UnsupportedOperationException("confirmAddChannel is not configured in FakeRssRepository"))
    }

    override suspend fun addChannel(url: String): Result<RssChannel> {
        return Result.failure(UnsupportedOperationException("addChannel is not configured in FakeRssRepository"))
    }

    override suspend fun refreshChannel(channelId: Long, refreshAll: Boolean): Result<Unit> = Result.success(Unit)

    override fun refreshChannelInBackground(channelId: Long, refreshAll: Boolean) = Unit

    override fun requestOriginalContent(itemId: Long, force: Boolean) = Unit

    override fun requestOriginalContents(itemIds: List<Long>, force: Boolean) = Unit

    override fun setOriginalContentUpdatesPaused(channelId: Long, paused: Boolean) = Unit

    override suspend fun markItemRead(itemId: Long) = Unit

    override suspend fun toggleFavorite(itemId: Long): Result<SavedState> {
        return Result.success(SavedState(isFavorite = true, isWatchLater = false))
    }

    override suspend fun toggleWatchLater(itemId: Long): Result<SavedState> {
        return Result.success(SavedState(isFavorite = false, isWatchLater = true))
    }

    override suspend fun reorderSavedItems(saveType: SaveType, orderedItemIds: List<Long>) = Unit

    override suspend fun syncExternalSavedItem(
        item: ExternalSavedItem,
        saveType: SaveType,
        saved: Boolean
    ): Result<SavedState> {
        return Result.success(SavedState(isFavorite = saveType == SaveType.FAVORITE && saved, isWatchLater = saveType == SaveType.WATCH_LATER && saved))
    }

    override suspend fun exportSyncedSavedArticles(deviceId: String): List<SyncedSavedArticle> = emptyList()

    override suspend fun exportSyncedArticleManifests(deviceId: String): List<SyncedArticleManifest> = emptyList()

    override suspend fun prepareLibrarySyncWindow(
        peerDeviceId: String,
        localDeviceId: String
    ): WatchLibrarySyncWindow {
        return WatchLibrarySyncWindow(
            articleManifest = emptyList(),
            fullArticleManifest = emptyList(),
            rssSources = emptyList(),
            fullSnapshot = true,
            fromSeqExclusive = 0L,
            toSeqInclusive = 0L,
            peerAckedSeq = 0L,
            fallbackReason = "fake"
        )
    }

    override suspend fun markLibrarySyncSuccess(
        peerDeviceId: String,
        localSeqToInclusive: Long,
        remoteSeqToInclusive: Long,
        remoteProtocolVersion: Int,
        fullSnapshot: Boolean
    ) = Unit

    override suspend fun exportSyncedSavedArticlesForRequests(
        deviceId: String,
        requests: List<SyncedArticleBodyRequest>
    ): List<SyncedSavedArticle> = emptyList()

    override suspend fun mergeSyncedSavedArticles(
        articles: List<SyncedSavedArticle>,
        remoteDeviceId: String,
        localDeviceId: String
    ): SyncedSavedArticleMergeStats {
        return SyncedSavedArticleMergeStats(
            received = articles.size,
            applied = articles.size
        )
    }

    override suspend fun mergeSyncedChunkedArticles(
        articles: List<SyncedChunkedArticle>,
        remoteDeviceId: String,
        localDeviceId: String
    ): SyncedSavedArticleMergeStats {
        return SyncedSavedArticleMergeStats(
            received = articles.size,
            applied = articles.size
        )
    }

    override suspend fun exportSyncedRssSources(deviceId: String): List<SyncedRssSource> = emptyList()

    override suspend fun mergeSyncedRssSources(
        sources: List<SyncedRssSource>,
        remoteDeviceId: String,
        localDeviceId: String
    ): SyncedRssSourceMergeStats {
        return SyncedRssSourceMergeStats(
            received = sources.size,
            applied = sources.size
        )
    }

    override suspend fun retryOfflineMedia(itemId: Long) = Unit

    override suspend fun toggleLike(itemId: Long): Result<Boolean> = Result.success(false)

    override suspend fun markChannelRead(channelId: Long) = Unit

    override suspend fun updateItemReadingProgress(itemId: Long, progress: Float) = Unit

    override suspend fun moveChannelToTop(channelId: Long) {
        val current = channelsFlow.value
        val target = current.firstOrNull { it.id == channelId } ?: return
        channelsFlow.value = listOf(target) + current.filterNot { it.id == channelId }
    }

    override suspend fun setChannelPinned(channelId: Long, pinned: Boolean) {
        channelsFlow.value = channelsFlow.value.map { channel ->
            if (channel.id == channelId) channel.copy(isPinned = pinned) else channel
        }
    }

    override suspend fun setChannelOriginalContent(channelId: Long, enabled: Boolean) = Unit

    override suspend fun setChannelContinuePlaybackInBackground(channelId: Long, enabled: Boolean) {
        channelsFlow.value = channelsFlow.value.map { channel ->
            if (channel.id == channelId) {
                channel.copy(continuePlaybackInBackground = enabled)
            } else {
                channel
            }
        }
    }

    override suspend fun deleteItem(itemId: Long) = Unit

    override suspend fun clearLocalContentChannel(channelId: Long) = Unit

    override suspend fun deleteChannel(channelId: Long) {
        channelsFlow.value = channelsFlow.value.filterNot { it.id == channelId }
    }

    override suspend fun trimCacheToLimit() = Unit
}
