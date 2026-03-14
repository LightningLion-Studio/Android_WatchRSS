package com.lightningstudio.watchrss.testutil

import com.lightningstudio.watchrss.data.rss.AddRssPreview
import com.lightningstudio.watchrss.data.rss.ExternalSavedItem
import com.lightningstudio.watchrss.data.rss.OfflineMedia
import com.lightningstudio.watchrss.data.rss.OfflineMediaType
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssChannelPreview
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.RssPreviewItem
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.data.rss.SavedItem
import com.lightningstudio.watchrss.data.rss.SavedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class TestRssRepository(
    initialChannels: List<RssChannel> = emptyList()
) : RssRepository {
    private val channelsFlow = MutableStateFlow(initialChannels)
    private val itemsByChannelFlow = MutableStateFlow<Map<Long, List<RssItem>>>(emptyMap())
    private val itemsByIdFlow = MutableStateFlow<Map<Long, RssItem>>(emptyMap())
    private val savedItemsFlow = MutableStateFlow<Map<SaveType, List<SavedItem>>>(emptyMap())
    private val savedStateFlow = MutableStateFlow<Map<Long, SavedState>>(emptyMap())
    private val offlineMediaFlow = MutableStateFlow<Map<Long, List<OfflineMedia>>>(emptyMap())
    private val cacheUsageBytesFlow = MutableStateFlow(0L)

    var ensureBuiltinChannelsCount = 0
    var previewChannelResult: Result<AddRssPreview> =
        Result.failure(UnsupportedOperationException("previewChannel not configured"))
    var confirmAddChannelResult: Result<RssChannel> =
        Result.failure(UnsupportedOperationException("confirmAddChannel not configured"))
    var addChannelResult: Result<RssChannel> =
        Result.failure(UnsupportedOperationException("addChannel not configured"))
    val refreshRequests = mutableListOf<Pair<Long, Boolean>>()
    val refreshResults = mutableMapOf<Long, Result<Unit>>()
    val refreshBackgroundRequests = mutableListOf<Pair<Long, Boolean>>()
    val requestedOriginalContentIds = mutableListOf<Long>()
    val requestedOriginalContentBatchIds = mutableListOf<List<Long>>()
    val pausedOriginalContentUpdates = mutableListOf<Pair<Long, Boolean>>()
    val markedReadItemIds = mutableListOf<Long>()
    val toggledFavoriteIds = mutableListOf<Long>()
    val toggledWatchLaterIds = mutableListOf<Long>()
    val syncedExternalSavedItems = mutableListOf<Triple<ExternalSavedItem, SaveType, Boolean>>()
    val retriedOfflineMediaIds = mutableListOf<Long>()
    val toggledLikeIds = mutableListOf<Long>()
    val markedReadChannelIds = mutableListOf<Long>()
    val readingProgressUpdates = mutableListOf<Pair<Long, Float>>()
    val movedToTopChannelIds = mutableListOf<Long>()
    val setPinnedRequests = mutableListOf<Pair<Long, Boolean>>()
    val setOriginalContentRequests = mutableListOf<Pair<Long, Boolean>>()
    val deletedChannelIds = mutableListOf<Long>()
    var trimCacheCalls = 0

    fun setChannels(channels: List<RssChannel>) {
        channelsFlow.value = channels
    }

    fun setChannelItems(channelId: Long, items: List<RssItem>) {
        val updated = itemsByChannelFlow.value.toMutableMap()
        updated[channelId] = items
        itemsByChannelFlow.value = updated
        rebuildItemsIndex()
    }

    fun setSavedItems(saveType: SaveType, items: List<SavedItem>) {
        val updated = savedItemsFlow.value.toMutableMap()
        updated[saveType] = items
        savedItemsFlow.value = updated
    }

    fun setSavedState(itemId: Long, state: SavedState) {
        val updated = savedStateFlow.value.toMutableMap()
        updated[itemId] = state
        savedStateFlow.value = updated
    }

    fun setOfflineMedia(itemId: Long, media: List<OfflineMedia>) {
        val updated = offlineMediaFlow.value.toMutableMap()
        updated[itemId] = media
        offlineMediaFlow.value = updated
    }

    fun setCacheUsageBytes(bytes: Long) {
        cacheUsageBytesFlow.value = bytes
    }

    override fun observeChannels(): Flow<List<RssChannel>> = channelsFlow

    override fun observeChannel(channelId: Long): Flow<RssChannel?> {
        return channelsFlow.map { channels -> channels.firstOrNull { it.id == channelId } }
    }

    override fun observeItemsPaged(channelId: Long, limit: Int): Flow<List<RssItem>> {
        return itemsByChannelFlow.map { items ->
            items[channelId].orEmpty().take(limit.coerceAtLeast(0))
        }
    }

    override fun observeItemCount(channelId: Long): Flow<Int> {
        return itemsByChannelFlow.map { items -> items[channelId].orEmpty().size }
    }

    override fun observeItem(itemId: Long): Flow<RssItem?> {
        return itemsByIdFlow.map { items -> items[itemId] }
    }

    override fun searchItems(channelId: Long, keyword: String, limit: Int): Flow<List<RssItem>> {
        return itemsByChannelFlow.map { items ->
            val query = keyword.trim()
            if (query.isBlank()) {
                emptyList()
            } else {
                items[channelId]
                    .orEmpty()
                    .filter { item ->
                        item.title.contains(query, ignoreCase = true) ||
                            item.description.orEmpty().contains(query, ignoreCase = true) ||
                            item.content.orEmpty().contains(query, ignoreCase = true)
                    }
                    .take(limit.coerceAtLeast(0))
            }
        }
    }

    override fun observeCacheUsageBytes(): Flow<Long> = cacheUsageBytesFlow

    override fun observeSavedItems(saveType: SaveType): Flow<List<SavedItem>> {
        return savedItemsFlow.map { items -> items[saveType].orEmpty() }
    }

    override fun observeSavedState(itemId: Long): Flow<SavedState> {
        return savedStateFlow.map { states ->
            states[itemId] ?: SavedState(isFavorite = false, isWatchLater = false)
        }
    }

    override fun observeOfflineMedia(itemId: Long): Flow<List<OfflineMedia>> {
        return offlineMediaFlow.map { media -> media[itemId].orEmpty() }
    }

    override suspend fun ensureBuiltinChannels() {
        ensureBuiltinChannelsCount += 1
    }

    override suspend fun previewChannel(url: String): Result<AddRssPreview> = previewChannelResult

    override suspend fun confirmAddChannel(preview: RssChannelPreview): Result<RssChannel> {
        return confirmAddChannelResult
    }

    override suspend fun addChannel(url: String): Result<RssChannel> = addChannelResult

    override suspend fun refreshChannel(channelId: Long, refreshAll: Boolean): Result<Unit> {
        refreshRequests += channelId to refreshAll
        return refreshResults[channelId] ?: Result.success(Unit)
    }

    override fun refreshChannelInBackground(channelId: Long, refreshAll: Boolean) {
        refreshBackgroundRequests += channelId to refreshAll
    }

    override fun requestOriginalContent(itemId: Long) {
        requestedOriginalContentIds += itemId
    }

    override fun requestOriginalContents(itemIds: List<Long>) {
        requestedOriginalContentBatchIds += itemIds
    }

    override fun setOriginalContentUpdatesPaused(channelId: Long, paused: Boolean) {
        pausedOriginalContentUpdates += channelId to paused
    }

    override suspend fun markItemRead(itemId: Long) {
        markedReadItemIds += itemId
        updateItem(itemId) { it.copy(isRead = true) }
    }

    override suspend fun toggleFavorite(itemId: Long): Result<SavedState> {
        toggledFavoriteIds += itemId
        val current = savedStateFlow.value[itemId] ?: SavedState(isFavorite = false, isWatchLater = false)
        val updated = current.copy(isFavorite = !current.isFavorite)
        setSavedState(itemId, updated)
        return Result.success(updated)
    }

    override suspend fun toggleWatchLater(itemId: Long): Result<SavedState> {
        toggledWatchLaterIds += itemId
        val current = savedStateFlow.value[itemId] ?: SavedState(isFavorite = false, isWatchLater = false)
        val updated = current.copy(isWatchLater = !current.isWatchLater)
        setSavedState(itemId, updated)
        return Result.success(updated)
    }

    override suspend fun syncExternalSavedItem(
        item: ExternalSavedItem,
        saveType: SaveType,
        saved: Boolean
    ): Result<SavedState> {
        syncedExternalSavedItems += Triple(item, saveType, saved)
        return Result.success(
            SavedState(
                isFavorite = saveType == SaveType.FAVORITE && saved,
                isWatchLater = saveType == SaveType.WATCH_LATER && saved
            )
        )
    }

    override suspend fun retryOfflineMedia(itemId: Long) {
        retriedOfflineMediaIds += itemId
    }

    override suspend fun toggleLike(itemId: Long): Result<Boolean> {
        toggledLikeIds += itemId
        return Result.success(true)
    }

    override suspend fun markChannelRead(channelId: Long) {
        markedReadChannelIds += channelId
        channelsFlow.value = channelsFlow.value.map { channel ->
            if (channel.id == channelId) channel.copy(unreadCount = 0) else channel
        }
    }

    override suspend fun updateItemReadingProgress(itemId: Long, progress: Float) {
        readingProgressUpdates += itemId to progress
        updateItem(itemId) { it.copy(readingProgress = progress) }
    }

    override suspend fun moveChannelToTop(channelId: Long) {
        movedToTopChannelIds += channelId
        val current = channelsFlow.value
        val target = current.firstOrNull { it.id == channelId } ?: return
        channelsFlow.value = listOf(target) + current.filterNot { it.id == channelId }
    }

    override suspend fun setChannelPinned(channelId: Long, pinned: Boolean) {
        setPinnedRequests += channelId to pinned
        channelsFlow.value = channelsFlow.value.map { channel ->
            if (channel.id == channelId) channel.copy(isPinned = pinned) else channel
        }
    }

    override suspend fun setChannelOriginalContent(channelId: Long, enabled: Boolean) {
        setOriginalContentRequests += channelId to enabled
        channelsFlow.value = channelsFlow.value.map { channel ->
            if (channel.id == channelId) channel.copy(useOriginalContent = enabled) else channel
        }
    }

    override suspend fun deleteChannel(channelId: Long) {
        deletedChannelIds += channelId
        channelsFlow.value = channelsFlow.value.filterNot { it.id == channelId }
        val updated = itemsByChannelFlow.value.toMutableMap()
        updated.remove(channelId)
        itemsByChannelFlow.value = updated
        rebuildItemsIndex()
    }

    override suspend fun trimCacheToLimit() {
        trimCacheCalls += 1
    }

    private fun rebuildItemsIndex() {
        itemsByIdFlow.value = itemsByChannelFlow.value
            .values
            .flatten()
            .associateBy { item -> item.id }
    }

    private fun updateItem(itemId: Long, transform: (RssItem) -> RssItem) {
        val current = itemsByIdFlow.value[itemId] ?: return
        val updatedItem = transform(current)
        val channelItems = itemsByChannelFlow.value.toMutableMap()
        channelItems[current.channelId] = channelItems[current.channelId]
            .orEmpty()
            .map { item -> if (item.id == itemId) updatedItem else item }
        itemsByChannelFlow.value = channelItems
        rebuildItemsIndex()
    }
}

fun sampleRssChannel(
    id: Long = 1L,
    title: String = "测试频道",
    url: String = "https://example.com/feed.xml",
    unreadCount: Int = 3,
    isPinned: Boolean = false,
    useOriginalContent: Boolean = false
): RssChannel {
    return RssChannel(
        id = id,
        url = url,
        title = title,
        description = "$title 描述",
        imageUrl = null,
        lastFetchedAt = null,
        sortOrder = id,
        isPinned = isPinned,
        useOriginalContent = useOriginalContent,
        unreadCount = unreadCount
    )
}

fun sampleRssItem(
    id: Long,
    channelId: Long = 1L,
    title: String = "测试条目 $id",
    description: String? = "摘要 $id",
    content: String? = "正文 $id",
    link: String? = "https://example.com/items/$id",
    readingProgress: Float = 0f
): RssItem {
    return RssItem(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        content = content,
        link = link,
        pubDate = "2024-01-01",
        imageUrl = null,
        audioUrl = null,
        videoUrl = null,
        summary = description,
        previewImageUrl = null,
        isRead = false,
        isLiked = false,
        readingProgress = readingProgress,
        fetchedAt = 1_700_000_000L + id
    )
}

fun sampleRssPreviewItem(title: String = "预览条目"): RssPreviewItem {
    return RssPreviewItem(
        title = title,
        description = "预览摘要",
        content = "预览正文",
        link = "https://example.com/preview",
        guid = "preview-guid",
        pubDate = "2024-01-01",
        imageUrl = null,
        audioUrl = null,
        videoUrl = null
    )
}

fun sampleChannelPreview(
    title: String = "预览频道",
    url: String = "https://example.com/preview.xml"
): RssChannelPreview {
    return RssChannelPreview(
        url = url,
        title = title,
        description = "$title 描述",
        imageUrl = null,
        siteUrl = "https://example.com",
        items = listOf(sampleRssPreviewItem()),
        isBuiltin = false
    )
}

fun sampleSavedItem(
    itemId: Long,
    channelId: Long = 1L,
    channelTitle: String = "测试频道",
    saveType: SaveType = SaveType.FAVORITE
): SavedItem {
    return SavedItem(
        item = sampleRssItem(id = itemId, channelId = channelId),
        channelTitle = channelTitle,
        savedAt = 1_700_000_000L + itemId,
        saveType = saveType
    )
}

fun sampleOfflineMedia(
    itemId: Long,
    originUrl: String = "https://example.com/image.jpg",
    localPath: String? = "/tmp/image.jpg"
): OfflineMedia {
    return OfflineMedia(
        itemId = itemId,
        type = OfflineMediaType.IMAGE,
        originUrl = originUrl,
        localPath = localPath
    )
}
