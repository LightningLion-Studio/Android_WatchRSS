package com.lightningstudio.watchrss.data.rss

import androidx.core.net.toUri
import android.net.Uri
import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.cache.ManagedCacheBucket
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.db.OfflineMediaDao
import com.lightningstudio.watchrss.data.db.OfflineMediaEntity
import com.lightningstudio.watchrss.data.db.RssChannelDao
import com.lightningstudio.watchrss.data.db.RssChannelEntity
import com.lightningstudio.watchrss.data.db.RssSourceSyncStateDao
import com.lightningstudio.watchrss.data.db.RssSourceSyncStateEntity
import com.lightningstudio.watchrss.data.db.RssItemDao
import com.lightningstudio.watchrss.data.db.RssItemEntity
import com.lightningstudio.watchrss.data.db.SavedEntryDao
import com.lightningstudio.watchrss.data.db.SavedEntryEntity
import com.lightningstudio.watchrss.data.db.SavedRssItem
import com.lightningstudio.watchrss.data.db.SavedSyncStateDao
import com.lightningstudio.watchrss.data.db.SavedSyncStateEntity
import com.lightningstudio.watchrss.data.db.SyncChangeLogDao
import com.lightningstudio.watchrss.data.db.SyncChangeLogEntity
import com.lightningstudio.watchrss.data.db.SyncPeerStateDao
import com.lightningstudio.watchrss.data.db.SyncPeerStateEntity
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewCache
import com.lightningstudio.watchrss.debug.DebugLogBuffer
import com.lightningstudio.watchrss.debug.PerfTrace
import com.prof18.rssparser.model.RssItem as ParsedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONArray
import java.net.URI
import kotlin.math.roundToLong
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class WatchLibrarySyncWindow(
    val articleManifest: List<SyncedArticleManifest>,
    val fullArticleManifest: List<SyncedArticleManifest>,
    val rssSources: List<SyncedRssSource>,
    val fullSnapshot: Boolean,
    val fromSeqExclusive: Long,
    val toSeqInclusive: Long,
    val peerAckedSeq: Long,
    val fallbackReason: String
)

data class WatchLibrarySyncCursorSnapshot(
    val localMaxSeq: Long,
    val lastRemoteSeqApplied: Long,
    val lastLocalSeqAckedByPeer: Long
)

private data class SyncHydratedRssItem(
    val item: RssItemEntity,
    val bodyAvailable: Boolean
)

private data class SyncBodyContent(
    val contentHtml: String?,
    val contentText: String
)

class DefaultRssRepository(
    private val channelDao: RssChannelDao,
    private val itemDao: RssItemDao,
    private val savedEntryDao: SavedEntryDao,
    private val savedSyncStateDao: SavedSyncStateDao,
    private val offlineMediaDao: OfflineMediaDao,
    private val syncChangeLogDao: SyncChangeLogDao,
    private val syncPeerStateDao: SyncPeerStateDao,
    private val rssSourceSyncStateDao: RssSourceSyncStateDao,
    private val cacheService: ManagedCacheService,
    private val appScope: CoroutineScope,
    private val fetchService: RssFetchService,
    private val readableService: RssReadableService,
    private val parseService: RssParseService,
    private val offlineStore: RssOfflineStore,
    private val deviceId: String,
    private val articleContentStore: ArticleContentStore? = null
) : RssRepository {
    private val refreshJobs = ConcurrentHashMap<Long, Job>()
    private val originalContentItemJobs = ConcurrentHashMap<Long, Job>()
    private val pausedOriginalChannels: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val pendingOriginalUpdates:
        ConcurrentHashMap<Long, ConcurrentHashMap<String, PendingOriginalUpdate>> =
        ConcurrentHashMap()
    private val previewJobs: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val previewAttemptKeys = ConcurrentHashMap<Long, String>()

    override fun observeCloudSyncRevision(): Flow<Long> =
        syncChangeLogDao.observeMaxSeq()

    override fun observeHomeChannels(): Flow<List<RssChannel>> =
        channelDao.observeChannels().map { channels ->
            channels.map { channel -> channel.toModel(unreadCount = 0) }
        }

    override fun observeChannels(): Flow<List<RssChannel>> =
        combine(
            channelDao.observeChannels(),
            itemDao.observeUnreadCounts()
        ) { channels, unreadCounts ->
            val unreadMap = unreadCounts.associate { it.channelId to it.unreadCount }
            channels.map { channel ->
                channel.toModel(unreadMap[channel.id] ?: 0)
            }
        }

    override fun observeChannel(channelId: Long): Flow<RssChannel?> =
        combine(
            channelDao.observeChannel(channelId),
            itemDao.observeUnreadCount(channelId)
        ) { channel, unreadCount ->
            channel?.toModel(unreadCount)
        }

    override fun observeItemsPaged(channelId: Long, limit: Int): Flow<List<RssItem>> =
        itemDao.observeItemsPaged(channelId, limit)
            .onEach { items ->
                PerfTrace.log(
                    "repo",
                    "observeItemsPaged emit channelId=$channelId limit=$limit size=${items.size} missingSummary=${items.count { it.summary.isNullOrBlank() }} missingPreview=${items.count { it.previewImageUrl.isNullOrBlank() && it.imageUrl.isNullOrBlank() }} withContent=${items.count { !it.content.isNullOrBlank() }}"
                )
            }
            .map { items ->
                val startNanos = PerfTrace.now()
                schedulePreviewUpdates(items)
                PerfTrace.log(
                    "repo",
                    "observeItemsPaged map channelId=$channelId limit=$limit size=${items.size} schedulePreviewMs=${PerfTrace.elapsedMs(startNanos)}"
                )
                items.map { it.toModel() }
            }

    override fun observeItemCount(channelId: Long): Flow<Int> =
        itemDao.observeItemCount(channelId)

    override fun observeChannelHasPlayableMedia(channelId: Long): Flow<Boolean> =
        itemDao.observeChannelHasPlayableMedia(channelId)

    override fun observeItem(itemId: Long): Flow<RssItem?> =
        itemDao.observeItem(itemId).map { item ->
            withContext(Dispatchers.IO) {
                if (item != null) {
                    schedulePreviewUpdate(item)
                }
                val hydrated = item?.let { entity ->
                    if (entity.isFileBackedImportedText()) {
                        entity
                    } else {
                        entity.hydrateExternalContent()
                    }
                }
                hydrated?.toModel()
            }
        }

    override fun searchItems(channelId: Long, keyword: String, limit: Int): Flow<List<RssItem>> {
        val pattern = buildSearchPattern(keyword)
        return itemDao.searchItems(channelId, pattern, limit).map { items ->
            schedulePreviewUpdates(items)
            items.map { it.toModel() }
        }
    }

    override fun observeCacheUsageBytes(): Flow<Long> =
        cacheService.observeUsageBytes()

    override fun observeSavedItems(saveType: SaveType): Flow<List<SavedItem>> =
        savedEntryDao.observeSavedItems(saveType.name).map { items ->
            schedulePreviewUpdates(items.map { it.item })
            items.map { it.toModel() }
        }

    override fun observeSavedState(itemId: Long): Flow<SavedState> =
        savedEntryDao.observeByItemId(itemId).map { entries ->
            val types = entries.map { it.saveType }.toSet()
            SavedState(
                isFavorite = SaveType.FAVORITE.name in types,
                isWatchLater = SaveType.WATCH_LATER.name in types
            )
        }

    override fun observeOfflineMedia(itemId: Long): Flow<List<OfflineMedia>> =
        offlineMediaDao.observeByItemId(itemId).map { list ->
            list.map { it.toModel() }
        }

    override suspend fun getImportedTextReader(itemId: Long): ImportedTextReader? =
        withContext(Dispatchers.IO) {
            val store = articleContentStore ?: return@withContext null
            val item = itemDao.getItem(itemId) ?: return@withContext null
            if (!ImportedContentIds.isImportedTextItemUrl(item.link)) return@withContext null
            val marker = item.originalContent?.takeIf(store::isMarker)
                ?: item.content?.takeIf(store::isMarker)
                ?: return@withContext null
            val handle = store.textChunkHandle(marker) ?: return@withContext null
            ImportedTextReader(
                marker = handle.marker,
                byteLength = handle.byteLength,
                chunkCount = handle.chunkCount
            )
        }

    override suspend fun loadImportedTextChunk(marker: String, chunkIndex: Int): String? =
        withContext(Dispatchers.IO) {
            articleContentStore?.loadTextChunk(marker, chunkIndex)
        }

    override suspend fun previewChannel(url: String): Result<AddRssPreview> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        if (!isValidUrl(normalizedUrl)) {
            return@withContext Result.failure(IllegalArgumentException("URL 不合法"))
        }

        val builtinType = builtinTypeFromInputUrl(normalizedUrl)
        if (builtinType != null) {
            val existing = channelDao.getChannelByUrl(builtinType.url)
            if (existing != null) {
                return@withContext Result.success(AddRssPreview.Existing(existing.toModel(0)))
            }
            val preview = RssChannelPreview(
                url = builtinType.url,
                title = builtinType.title,
                description = builtinType.description,
                imageUrl = null,
                siteUrl = null,
                items = emptyList(),
                isBuiltin = true
            )
            return@withContext Result.success(AddRssPreview.Ready(preview))
        }

        val existing = channelDao.getChannelByUrl(normalizedUrl)
        if (existing != null) {
            return@withContext Result.success(AddRssPreview.Existing(existing.toModel(0)))
        }

        runCatching {
            val parsed = fetchService.fetchChannel(normalizedUrl)
            val preview = RssChannelPreview(
                url = normalizedUrl,
                title = parseService.channelTitle(parsed, normalizedUrl),
                description = parsed.description?.trim()?.ifEmpty { null },
                imageUrl = parsed.image?.url?.trim()?.ifEmpty { null },
                siteUrl = parsed.link?.trim()?.ifEmpty { null },
                items = parsed.items.map { parseService.toPreviewItem(it) },
                isBuiltin = false
            )
            AddRssPreview.Ready(preview)
        }.mapError()
    }

    override suspend fun confirmAddChannel(preview: RssChannelPreview): Result<RssChannel> =
        withContext(Dispatchers.IO) {
            if (preview.isBuiltin) {
                val builtinType = BuiltinChannelType.fromUrl(preview.url)
                    ?: return@withContext Result.failure(IllegalArgumentException("不支持的内置频道"))
                val existing = channelDao.getChannelByUrl(builtinType.url)
                if (existing != null) {
                    return@withContext Result.success(existing.toModel(0))
                }
                val now = System.currentTimeMillis()
                val channel = RssChannelEntity(
                    url = builtinType.url,
                    title = builtinType.title,
                    description = builtinType.description,
                    imageUrl = null,
                    lastFetchedAt = null,
                    createdAt = now,
                    sortOrder = now,
                    isPinned = false,
                    useOriginalContent = builtinType.useOriginalContentByDefault
                )
                val channelId = channelDao.insertChannel(channel)
                val storedChannel = if (channelId > 0) {
                    channel.copy(id = channelId)
                } else {
                    channelDao.getChannelByUrl(builtinType.url) ?: channel
                }
                return@withContext Result.success(storedChannel.toModel(0))
            }

            val existing = channelDao.getChannelByUrl(preview.url)
            if (existing != null) {
                return@withContext Result.success(existing.toModel(0))
            }

            runCatching {
                val fetchedAt = System.currentTimeMillis()
                val channel = RssChannelEntity(
                    url = preview.url,
                    title = preview.title,
                    description = preview.description,
                    imageUrl = preview.imageUrl,
                    lastFetchedAt = fetchedAt,
                    createdAt = fetchedAt,
                    sortOrder = fetchedAt,
                    isPinned = false
                )
                val channelId = channelDao.insertChannel(channel)
                val storedChannel = if (channelId > 0) {
                    channel.copy(id = channelId)
                } else {
                    channelDao.getChannelByUrl(preview.url) ?: channel
                }
                if (storedChannel.isSyncedRssSource()) {
                    rssSourceSyncStateDao.upsert(storedChannel.toSourceSyncState(deviceId, deleted = false))
                    recordRssSourceChange(storedChannel.url, "upsert", fetchedAt)
                }
                val items = preview.items.map { item ->
                    parseService.toEntityFromPreviewItem(
                        item = item,
                        channelId = storedChannel.id,
                        isRead = true,
                        fetchedAt = fetchedAt
                    )
                }
                if (items.isNotEmpty()) {
                    itemDao.insertItems(items)
                }
                trimCacheToLimit()
                storedChannel.toModel(0)
            }.mapError()
        }

    override suspend fun addChannel(url: String): Result<RssChannel> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        if (!isValidUrl(normalizedUrl)) {
            return@withContext Result.failure(IllegalArgumentException("URL 不合法"))
        }

        val builtinType = builtinTypeFromInputUrl(normalizedUrl)
        if (builtinType != null) {
            val existing = channelDao.getChannelByUrl(builtinType.url)
            if (existing != null) {
                return@withContext Result.success(existing.toModel(0))
            }
            val now = System.currentTimeMillis()
            val channel = RssChannelEntity(
                url = builtinType.url,
                title = builtinType.title,
                description = builtinType.description,
                imageUrl = null,
                lastFetchedAt = null,
                createdAt = now,
                sortOrder = now,
                isPinned = false,
                useOriginalContent = builtinType.useOriginalContentByDefault
            )
            val channelId = channelDao.insertChannel(channel)
            val storedChannel = if (channelId > 0) {
                channel.copy(id = channelId)
            } else {
                channelDao.getChannelByUrl(builtinType.url) ?: channel
            }
            return@withContext Result.success(storedChannel.toModel(0))
        }

        val existing = channelDao.getChannelByUrl(normalizedUrl)
        if (existing != null) {
            return@withContext Result.success(existing.toModel(0))
        }

        val preview = previewChannel(normalizedUrl).getOrElse { return@withContext Result.failure(it) }
        if (preview is AddRssPreview.Existing) {
            return@withContext Result.success(preview.channel)
        }
        confirmAddChannel((preview as AddRssPreview.Ready).preview)
    }

    override suspend fun refreshChannel(
        channelId: Long,
        refreshAll: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val startNanos = PerfTrace.now()
        PerfTrace.log("repo", "refreshChannel start channelId=$channelId refreshAll=$refreshAll")
        val channel = channelDao.getChannel(channelId)
            ?: return@withContext Result.failure(IllegalArgumentException("频道不存在"))
        if (BuiltinChannelType.fromUrl(channel.url) != null || channel.isImportedContentChannel()) {
            PerfTrace.log(
                "repo",
                "refreshChannel skip local channelId=$channelId durMs=${PerfTrace.elapsedMs(startNanos)}"
            )
            return@withContext Result.success(Unit)
        }

        val result = runCatching {
            val fetchedAt = System.currentTimeMillis()
            val parsed = fetchService.fetchChannel(channel.url)
            val items = parsed.items.map { item ->
                parseService.toEntityFromParsedItem(
                    item = item,
                    channelId = channelId,
                    isRead = false,
                    fetchedAt = fetchedAt
                )
            }
            if (items.isNotEmpty()) {
                val insertResults = itemDao.insertItems(items)
                if (refreshAll) {
                    var updated = 0
                    insertResults.forEachIndexed { index, rowId ->
                        if (rowId <= 0L) {
                            val entity = items[index]
                            itemDao.updateContentByDedupKey(
                                channelId = channelId,
                                dedupKey = entity.dedupKey,
                                description = entity.description,
                                content = entity.content,
                                imageUrl = entity.imageUrl,
                                audioUrl = entity.audioUrl,
                                videoUrl = entity.videoUrl,
                                summary = entity.summary,
                                previewImageUrl = entity.previewImageUrl,
                                contentSizeBytes = entity.contentSizeBytes
                            )
                            updated += 1
                        }
                    }
                }
            }
            val updatedChannel = channel.copy(
                title = parseService.channelTitle(parsed, channel.url),
                description = parsed.description?.trim()?.ifEmpty { null },
                imageUrl = parsed.image?.url?.trim()?.ifEmpty { null },
                lastFetchedAt = fetchedAt
            )
            channelDao.updateChannel(updatedChannel)
            if (updatedChannel.isSyncedRssSource()) {
                rssSourceSyncStateDao.upsert(updatedChannel.toSourceSyncState(deviceId, deleted = false))
                recordRssSourceChange(updatedChannel.url, "metadata", fetchedAt)
            }
            trimCacheToLimit()
        }.mapError()
        PerfTrace.log(
            "repo",
            "refreshChannel end channelId=$channelId refreshAll=$refreshAll success=${result.isSuccess} durMs=${PerfTrace.elapsedMs(startNanos)}"
        )
        result
    }

    override fun refreshChannelInBackground(channelId: Long, refreshAll: Boolean) {
        refreshJobs[channelId]?.cancel()
        refreshJobs[channelId] = appScope.launch {
            val result = refreshChannel(channelId, refreshAll)
            if (refreshAll && result.isFailure) {
                DebugLogBuffer.log(
                    "orig",
                    "refresh failed channelId=$channelId error=${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    override fun requestOriginalContent(itemId: Long, force: Boolean) {
        if (itemId <= 0L) return
        if (originalContentItemJobs.containsKey(itemId)) return
        val job = appScope.launch(Dispatchers.IO) {
            val startNanos = PerfTrace.now()
            val item = itemDao.getItem(itemId) ?: return@launch
            if (!item.originalContent.isNullOrBlank()) return@launch
            val channel = channelDao.getChannel(item.channelId) ?: return@launch
            if (!force && !channel.useOriginalContent) return@launch
            PerfTrace.log(
                "repo",
                "requestOriginalContent start itemId=$itemId channelId=${item.channelId} force=$force summaryMissing=${item.summary.isNullOrBlank()} pending=${pendingOriginalUpdates[item.channelId]?.size ?: 0}"
            )
            if (item.summary.isNullOrBlank()) {
                itemDao.updatePreview(item.id, "暂无摘要", null)
            }
            val baseLink = channel.url
            val originalContent = readableService.fetchOriginalContent(item.link, baseLink)
            val contentOverride = originalContent ?: buildOriginalFallbackContent(item)
            val contentSizeBytes = estimateContentSize(
                title = item.title,
                description = item.description,
                content = item.content,
                originalContent = contentOverride,
                link = item.link,
                imageUrl = item.imageUrl,
                audioUrl = item.audioUrl,
                videoUrl = item.videoUrl
            )
            if (originalContent == null) {
                DebugLogBuffer.log(
                    "orig",
                    "fallback link=${item.link} size=${contentOverride.length}"
                )
            }
            val update = PendingOriginalUpdate(
                dedupKey = item.dedupKey,
                content = externalizeContentValue("${item.dedupKey}-original", contentOverride) ?: contentOverride,
                contentSizeBytes = contentSizeBytes
            )
            if (pausedOriginalChannels.contains(item.channelId)) {
                PerfTrace.log(
                    "repo",
                    "requestOriginalContent queue itemId=$itemId channelId=${item.channelId} contentSize=${contentOverride.length} durMs=${PerfTrace.elapsedMs(startNanos)}"
                )
                enqueueOriginalUpdate(item.channelId, update)
            } else {
                itemDao.updateOriginalContentByDedupKey(
                    channelId = item.channelId,
                    dedupKey = update.dedupKey,
                    content = update.content,
                    contentSizeBytes = update.contentSizeBytes
                )
                PerfTrace.log(
                    "repo",
                    "requestOriginalContent apply itemId=$itemId channelId=${item.channelId} contentSize=${contentOverride.length} durMs=${PerfTrace.elapsedMs(startNanos)}"
                )
            }
        }
        originalContentItemJobs[itemId] = job
        job.invokeOnCompletion { originalContentItemJobs.remove(itemId) }
    }

    override fun requestOriginalContents(itemIds: List<Long>, force: Boolean) {
        PerfTrace.log(
            "repo",
            "requestOriginalContents batch size=${itemIds.size} force=$force ids=${itemIds.joinToString(",")}"
        )
        itemIds.forEach { requestOriginalContent(it, force) }
    }

    override fun setOriginalContentUpdatesPaused(channelId: Long, paused: Boolean) {
        if (channelId <= 0L) return
        if (paused) {
            pausedOriginalChannels.add(channelId)
        } else {
            pausedOriginalChannels.remove(channelId)
            flushOriginalUpdates(channelId)
        }
        PerfTrace.log(
            "repo",
            "setOriginalContentUpdatesPaused channelId=$channelId paused=$paused pending=${pendingOriginalUpdates[channelId]?.size ?: 0}"
        )
    }

    override suspend fun markItemRead(itemId: Long) {
        withContext(Dispatchers.IO) {
            val item = itemDao.getItem(itemId) ?: return@withContext
            if (item.isRead) return@withContext
            itemDao.markRead(itemId)
            recordArticleChange(stableArticleId(item.link ?: item.dedupKey), "read")
        }
    }

    override suspend fun updateItemReadingProgress(itemId: Long, progress: Float) {
        withContext(Dispatchers.IO) {
            val clamped = progress.coerceIn(0f, 1f)
            val item = itemDao.getItem(itemId) ?: return@withContext
            if (kotlin.math.abs(item.readingProgress - clamped) < 0.001f) return@withContext
            val importedReader = getImportedTextReader(itemId)
            val byteLength = importedReader?.byteLength
                ?: (item.originalContent ?: item.content.orEmpty()).toByteArray(Charsets.UTF_8).size.toLong()
            val positionBytes = (byteLength.toDouble() * clamped.toDouble())
                .roundToLong()
                .coerceIn(0L, byteLength)
            val changedAt = System.currentTimeMillis()
            itemDao.updateReadingProgress(
                id = itemId,
                progress = clamped,
                positionBytes = positionBytes,
                positionContentHash = item.syncBodyHash,
                positionChangedAt = changedAt
            )
            recordArticleChange(
                stableArticleId(item.link ?: item.dedupKey),
                "readingProgress",
                changedAt
            )
        }
    }

    override suspend fun toggleFavorite(itemId: Long): Result<SavedState> =
        toggleSaved(itemId, SaveType.FAVORITE)

    override suspend fun toggleWatchLater(itemId: Long): Result<SavedState> =
        toggleSaved(itemId, SaveType.WATCH_LATER)

    override suspend fun reorderSavedItems(saveType: SaveType, orderedItemIds: List<Long>) {
        withContext(Dispatchers.IO) {
            if (orderedItemIds.isEmpty()) return@withContext
            savedEntryDao.reorderSavedItems(saveType.name, orderedItemIds.distinct())
        }
    }

    override suspend fun syncExternalSavedItem(
        item: ExternalSavedItem,
        saveType: SaveType,
        saved: Boolean
    ): Result<SavedState> = withContext(Dispatchers.IO) {
        val channel = resolveExternalChannel(item.channelUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("频道不存在"))
        val entity = parseService.toEntityFromPreviewItem(
            item = item.item,
            channelId = channel.id,
            isRead = false,
            fetchedAt = item.fetchedAt
        )
        val existingItem = itemDao.getItemByDedupKey(channel.id, entity.dedupKey)
        if (!saved && existingItem == null) {
            return@withContext Result.success(SavedState(isFavorite = false, isWatchLater = false))
        }
        val itemId = if (existingItem != null) {
            itemDao.updateContentByDedupKey(
                channelId = channel.id,
                dedupKey = entity.dedupKey,
                description = entity.description,
                content = entity.content,
                imageUrl = entity.imageUrl,
                audioUrl = entity.audioUrl,
                videoUrl = entity.videoUrl,
                summary = entity.summary,
                previewImageUrl = entity.previewImageUrl,
                contentSizeBytes = entity.contentSizeBytes
            )
            existingItem.id
        } else {
            val insertId = itemDao.insertItems(listOf(entity)).firstOrNull() ?: -1L
            if (insertId > 0) {
                insertId
            } else {
                itemDao.updateContentByDedupKey(
                    channelId = channel.id,
                    dedupKey = entity.dedupKey,
                    description = entity.description,
                    content = entity.content,
                    imageUrl = entity.imageUrl,
                    audioUrl = entity.audioUrl,
                    videoUrl = entity.videoUrl,
                    summary = entity.summary,
                    previewImageUrl = entity.previewImageUrl,
                    contentSizeBytes = entity.contentSizeBytes
                )
                itemDao.getItemByDedupKey(channel.id, entity.dedupKey)?.id
            }
        } ?: return@withContext Result.failure(IllegalStateException("保存失败"))

        val existing = savedEntryDao.getByItemId(itemId)
        val hasType = existing.any { it.saveType == saveType.name }
        if (hasType == saved) {
            return@withContext Result.success(buildSavedState(existing))
        }
        toggleSaved(itemId, saveType)
    }

    override suspend fun exportSyncedSavedArticles(deviceId: String): List<SyncedSavedArticle> =
        withContext(Dispatchers.IO) {
            exportSyncedSavedArticlesInternal(deviceId = deviceId, requestedArticleIds = null)
        }

    override suspend fun exportCloudRssStateArticles(deviceId: String): List<SyncedSavedArticle> =
        withContext(Dispatchers.IO) {
            val stateByArticleId = exportSyncedSavedArticlesInternal(
                deviceId = deviceId,
                requestedArticleIds = null
            ).associateByTo(linkedMapOf()) { it.articleId }
            channelDao.getAllChannels()
                .filter { it.isSyncedRssSource() }
                .forEach { channel ->
                    itemDao.getItemsForChannelSyncManifest(channel.id, Int.MAX_VALUE)
                        .asSequence()
                        .filter { it.isRead || it.readingProgress > 0f }
                        .forEach { item ->
                            val articleId = stableArticleId(item.link ?: item.dedupKey)
                            if (articleId !in stateByArticleId) {
                                val url = item.link.orEmpty().ifBlank { item.dedupKey }
                                stateByArticleId[articleId] = SyncedSavedArticle(
                                    articleId = articleId,
                                    sourceDeviceId = deviceId,
                                    url = url,
                                    title = item.title.ifBlank { url },
                                    siteName = channel.title,
                                    excerpt = item.summary ?: item.description.orEmpty(),
                                    contentHtml = null,
                                    contentText = "",
                                    imageUrl = item.previewImageUrl ?: item.imageUrl,
                                    contentHash = item.syncBodyHash.ifBlank { sha256(url) },
                                    importedAt = item.fetchedAt,
                                    updatedAt = item.fetchedAt,
                                    rssSourceUrl = channel.url,
                                    rssSourceTitle = channel.title,
                                    favoriteSaved = false,
                                    favoriteChangedAt = 0L,
                                    favoriteSortOrder = 0L,
                                    watchLaterSaved = false,
                                    watchLaterChangedAt = 0L,
                                    watchLaterSortOrder = 0L,
                                    deleted = false,
                                    deletedAt = 0L,
                                    readingProgress = item.readingProgress,
                                    readingPositionBytes = item.readingPositionBytes,
                                    readingPositionContentHash = item.readingPositionContentHash,
                                    readingPositionChangedAt = item.readingPositionChangedAt,
                                    isRead = item.isRead
                                )
                            }
                        }
                }
            stateByArticleId.values.toList()
        }

    override suspend fun exportSyncedArticleManifests(deviceId: String): List<SyncedArticleManifest> =
        withContext(Dispatchers.IO) {
            val lightweight = exportLightweightSyncedArticleManifests(deviceId)
            val missingMetadataIds = lightweight
                .filter { it.needsBodyMetadataRefresh() }
                .mapTo(mutableSetOf()) { it.articleId }
            if (missingMetadataIds.isEmpty()) {
                return@withContext lightweight
            }
            val repaired = exportSyncedSavedArticlesInternal(
                deviceId = deviceId,
                requestedArticleIds = missingMetadataIds
            ).associate { article ->
                val cachedArticle = ensureSyncedArticleMetadata(article)
                val metadata = cachedArticle.cachedBodyMetadata
                    ?: ArticleSyncBody.metadataFor(cachedArticle)
                cachedArticle.articleId to cachedArticle.toManifest(metadata)
            }
            lightweight.map { manifest -> repaired[manifest.articleId] ?: manifest }
        }

    override suspend fun getLibrarySyncCursor(peerDeviceId: String): WatchLibrarySyncCursorSnapshot =
        withContext(Dispatchers.IO) {
            val normalizedPeerId = peerDeviceId.ifBlank { DEFAULT_LIBRARY_PEER_ID }
            val peerState = syncPeerStateDao.get(normalizedPeerId)
            WatchLibrarySyncCursorSnapshot(
                localMaxSeq = syncChangeLogDao.maxSeq(),
                lastRemoteSeqApplied = peerState?.lastRemoteSeqApplied ?: 0L,
                lastLocalSeqAckedByPeer = peerState?.lastLocalSeqAckedByPeer ?: 0L
            )
        }

    override suspend fun prepareLibrarySyncWindow(
        peerDeviceId: String,
        localDeviceId: String,
        peerAppliedLocalSeq: Long?
    ): WatchLibrarySyncWindow = withContext(Dispatchers.IO) {
        val normalizedPeerId = peerDeviceId.ifBlank { DEFAULT_LIBRARY_PEER_ID }
        val peerState = syncPeerStateDao.get(normalizedPeerId)
        val now = System.currentTimeMillis()
        val fullArticleManifest = exportSyncedArticleManifests(localDeviceId)
        repairMissingArticleChangeLogEntries(fullArticleManifest)
        val maxSeq = syncChangeLogDao.maxSeq()
        val cachedPeerAckedSeq = peerState?.lastLocalSeqAckedByPeer ?: 0L
        val peerAckedSeq = peerAppliedLocalSeq?.coerceAtLeast(0L) ?: cachedPeerAckedSeq
        val fullSnapshotReason = when {
            peerState == null -> "newPeer"
            peerState.lastProtocolVersion < CHANGE_SEQUENCE_PROTOCOL_VERSION -> "peerProtocol"
            peerAppliedLocalSeq != null && peerAckedSeq < cachedPeerAckedSeq -> "peerCursorBehind"
            peerAppliedLocalSeq != null && peerAckedSeq > maxSeq -> "peerCursorAhead"
            peerState.lastFullSyncAt <= 0L -> "noFullSnapshot"
            now - peerState.lastFullSyncAt >= FULL_SNAPSHOT_INTERVAL_MS -> "periodicFull"
            else -> ""
        }
        if (fullSnapshotReason.isNotBlank()) {
            return@withContext WatchLibrarySyncWindow(
                articleManifest = fullArticleManifest,
                fullArticleManifest = fullArticleManifest,
                rssSources = exportSyncedRssSources(localDeviceId),
                fullSnapshot = true,
                fromSeqExclusive = 0L,
                toSeqInclusive = maxSeq,
                peerAckedSeq = peerAckedSeq,
                fallbackReason = fullSnapshotReason
            )
        }

        val changedArticleIds = syncChangeLogDao.entityIdsChangedAfter(
            kind = SYNC_KIND_ARTICLE,
            afterSeq = peerAckedSeq
        ).toSet()
        val changedSourceUrls = syncChangeLogDao.entityIdsChangedAfter(
            kind = SYNC_KIND_RSS_SOURCE,
            afterSeq = peerAckedSeq
        )
        WatchLibrarySyncWindow(
            articleManifest = fullArticleManifest.filter { it.articleId in changedArticleIds },
            fullArticleManifest = fullArticleManifest,
            rssSources = exportSyncedRssSources(localDeviceId, changedSourceUrls),
            fullSnapshot = false,
            fromSeqExclusive = peerAckedSeq,
            toSeqInclusive = maxSeq,
            peerAckedSeq = peerAckedSeq,
            fallbackReason = ""
        )
    }

    override suspend fun markLibrarySyncSuccess(
        peerDeviceId: String,
        localSeqToInclusive: Long,
        remoteSeqToInclusive: Long,
        remoteProtocolVersion: Int,
        fullSnapshot: Boolean
    ) = withContext(Dispatchers.IO) {
        val normalizedPeerId = peerDeviceId.ifBlank { DEFAULT_LIBRARY_PEER_ID }
        val now = System.currentTimeMillis()
        val current = syncPeerStateDao.get(normalizedPeerId)
        syncPeerStateDao.upsert(
            SyncPeerStateEntity(
                peerDeviceId = normalizedPeerId,
                lastLocalSeqAckedByPeer = maxOf(current?.lastLocalSeqAckedByPeer ?: 0L, localSeqToInclusive),
                lastRemoteSeqApplied = maxOf(current?.lastRemoteSeqApplied ?: 0L, remoteSeqToInclusive),
                lastFullSyncAt = if (fullSnapshot) now else current?.lastFullSyncAt ?: 0L,
                lastProtocolVersion = remoteProtocolVersion,
                updatedAt = now
            )
        )
    }

    private suspend fun repairMissingArticleChangeLogEntries(
        articleManifest: List<SyncedArticleManifest>
    ) {
        val candidates = articleManifest
            .asSequence()
            .filterNot { it.deleted }
            .filter { it.latestOperationAt() > 0L }
            .distinctBy { it.articleId }
            .toList()
        if (candidates.isEmpty()) return

        val loggedChangedAt = syncChangeLogDao.maxChangedAtByEntityIds(
            kind = SYNC_KIND_ARTICLE,
            entityIds = candidates.map { it.articleId }
        ).associate { it.entityId to it.changedAt }
        val now = System.currentTimeMillis()
        candidates.forEach { article ->
            val changedAt = article.latestOperationAt()
            if (changedAt <= (loggedChangedAt[article.articleId] ?: 0L)) return@forEach
            syncChangeLogDao.insert(
                SyncChangeLogEntity(
                    kind = SYNC_KIND_ARTICLE,
                    entityId = article.articleId,
                    changedAt = changedAt,
                    originDeviceId = article.sourceDeviceId.ifBlank { deviceId },
                    reason = "repairState",
                    createdAt = now
                )
            )
        }
    }

    private suspend fun exportSyncedSavedArticlesInternal(
        deviceId: String,
        requestedArticleIds: Set<String>?
    ): List<SyncedSavedArticle> {
        val favorites = savedEntryDao.getSavedItems(SaveType.FAVORITE.name)
        val watchLater = savedEntryDao.getSavedItems(SaveType.WATCH_LATER.name)
        val states = savedSyncStateDao.getAll().associateBy { it.articleId to it.saveType }
        val grouped = (favorites + watchLater).groupBy { saved ->
            stableArticleId(saved.item.link ?: saved.item.dedupKey)
        }
        val savedArticles = grouped.mapNotNull { (articleId, entries) ->
            if (requestedArticleIds != null && articleId !in requestedArticleIds) {
                return@mapNotNull null
            }
            val representative = entries.maxByOrNull { it.savedAt } ?: entries.first()
            val favorite = entries.firstOrNull { it.saveType == SaveType.FAVORITE.name }
            val later = entries.firstOrNull { it.saveType == SaveType.WATCH_LATER.name }
            val favoriteState = states[articleId to SaveType.FAVORITE.name]
            val laterState = states[articleId to SaveType.WATCH_LATER.name]
            representative.toSyncedArticle(
                articleId = articleId,
                deviceId = deviceId,
                favoriteSaved = favorite != null,
                favoriteChangedAt = favoriteState?.changedAt ?: favorite?.savedAt ?: 0L,
                favoriteSortOrder = favoriteState?.sortOrder ?: favorite?.savedAt ?: 0L,
                watchLaterSaved = later != null,
                watchLaterChangedAt = laterState?.changedAt ?: later?.savedAt ?: 0L,
                watchLaterSortOrder = laterState?.sortOrder ?: later?.savedAt ?: 0L
            )
        }
        val savedArticleIds = savedArticles.mapTo(mutableSetOf()) { it.articleId }
        val independentArticles = exportSyncedIndependentArticles(
            deviceId = deviceId,
            excludedArticleIds = savedArticleIds,
            requestedArticleIds = requestedArticleIds
        )
        val importedContentArticles = exportSyncedImportedContentArticles(
            deviceId = deviceId,
            excludedArticleIds = savedArticleIds + independentArticles.map { it.articleId },
            requestedArticleIds = requestedArticleIds
        )
        val activeArticleIds = savedArticleIds +
            independentArticles.map { it.articleId } +
            importedContentArticles.map { it.articleId }
        val tombstones = states.values
            .filter { !it.saved && it.articleId !in activeArticleIds }
            .filter { requestedArticleIds == null || it.articleId in requestedArticleIds }
            .groupBy { it.articleId }
            .mapNotNull { (articleId, articleStates) ->
                val url = articleStates.firstOrNull()?.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val articleDelete = articleStates
                    .filter { it.saveType == ARTICLE_DELETE_SYNC_TYPE }
                    .maxByOrNull { it.changedAt }
                SyncedSavedArticle(
                    articleId = articleId,
                    sourceDeviceId = articleStates.maxByOrNull { it.changedAt }?.sourceDeviceId ?: deviceId,
                    url = url,
                    title = url,
                    siteName = hostLabel(url),
                    excerpt = "",
                    contentHtml = null,
                    contentText = "",
                    imageUrl = null,
                    contentHash = sha256(url),
                    importedAt = articleStates.minOf { it.changedAt },
                    updatedAt = articleStates.maxOf { it.changedAt },
                    independentSaved = false,
                    independentChangedAt = articleDelete?.changedAt ?: 0L,
                    independentSortOrder = 0L,
                    rssSourceUrl = null,
                    rssSourceTitle = null,
                    favoriteSaved = false,
                    favoriteChangedAt = articleStates.firstOrNull { it.saveType == SaveType.FAVORITE.name }?.changedAt ?: 0L,
                    favoriteSortOrder = 0L,
                    watchLaterSaved = false,
                    watchLaterChangedAt = articleStates.firstOrNull { it.saveType == SaveType.WATCH_LATER.name }?.changedAt ?: 0L,
                    watchLaterSortOrder = 0L,
                    deleted = articleDelete != null,
                    deletedAt = articleDelete?.changedAt ?: articleStates.maxOf { it.changedAt }
                )
            }
        return savedArticles + independentArticles + importedContentArticles + tombstones
    }

    private fun SyncedArticleManifest.needsBodyMetadataRefresh(): Boolean {
        if (deletedAt > 0L) return false
        return bodyHash.isBlank() ||
            bodyByteCount <= 0L ||
            chunkSize <= 0 ||
            chunkHashes.isEmpty() ||
            metadataHash.isBlank()
    }

    override suspend fun exportSyncedSavedArticlesForRequests(
        deviceId: String,
        requests: List<SyncedArticleBodyRequest>
    ): List<SyncedSavedArticle> = withContext(Dispatchers.IO) {
        val requestedIds = requests.mapTo(mutableSetOf()) { it.articleId }
        if (requestedIds.isEmpty()) return@withContext emptyList()
        exportSyncedSavedArticlesInternal(
            deviceId = deviceId,
            requestedArticleIds = requestedIds
        )
            .map { article -> ensureSyncedArticleMetadata(article) }
    }

    override suspend fun exportSyncedRssSources(deviceId: String): List<SyncedRssSource> =
        withContext(Dispatchers.IO) {
            channelDao.getAllChannels()
                .filter { it.isSyncedRssSource() }
                .map { channel -> channel.toSyncedRssSource(deviceId) }
                .mergeSourceTombstones(deviceId, rssSourceSyncStateDao.getAll())
        }

    private suspend fun exportSyncedRssSources(
        deviceId: String,
        sourceUrls: Collection<String>
    ): List<SyncedRssSource> {
        val urlSet = sourceUrls.toSet()
        if (urlSet.isEmpty()) return emptyList()
        val activeSources = channelDao.getAllChannels()
            .filter { it.url in urlSet && it.isSyncedRssSource() }
            .map { channel -> channel.toSyncedRssSource(deviceId) }
        val states = rssSourceSyncStateDao.getByUrls(urlSet.toList())
        return activeSources.mergeSourceTombstones(deviceId, states)
            .filter { it.url in urlSet }
    }

    private suspend fun exportLightweightSyncedArticleManifests(deviceId: String): List<SyncedArticleManifest> {
        val favorites = savedEntryDao.getSavedItemsForSyncManifest(SaveType.FAVORITE.name)
        val watchLater = savedEntryDao.getSavedItemsForSyncManifest(SaveType.WATCH_LATER.name)
        val states = savedSyncStateDao.getAll().associateBy { it.articleId to it.saveType }
        val grouped = (favorites + watchLater).groupBy { saved ->
            stableArticleId(saved.item.link ?: saved.item.dedupKey)
        }
        val savedManifests = grouped.map { (articleId, entries) ->
            val representative = entries.maxByOrNull { it.savedAt } ?: entries.first()
            val favorite = entries.firstOrNull { it.saveType == SaveType.FAVORITE.name }
            val later = entries.firstOrNull { it.saveType == SaveType.WATCH_LATER.name }
            val favoriteState = states[articleId to SaveType.FAVORITE.name]
            val laterState = states[articleId to SaveType.WATCH_LATER.name]
            representative.toSyncedArticleManifest(
                articleId = articleId,
                deviceId = deviceId,
                favoriteSaved = favorite != null,
                favoriteChangedAt = favoriteState?.changedAt ?: favorite?.savedAt ?: 0L,
                favoriteSortOrder = favoriteState?.sortOrder ?: favorite?.savedAt ?: 0L,
                watchLaterSaved = later != null,
                watchLaterChangedAt = laterState?.changedAt ?: later?.savedAt ?: 0L,
                watchLaterSortOrder = laterState?.sortOrder ?: later?.savedAt ?: 0L
            )
        }
        val savedArticleIds = savedManifests.mapTo(mutableSetOf()) { it.articleId }
        val independentManifests = exportLightweightIndependentArticleManifests(deviceId, savedArticleIds)
        val importedContentManifests = exportLightweightImportedContentArticleManifests(
            deviceId = deviceId,
            excludedArticleIds = savedArticleIds + independentManifests.map { it.articleId }
        )
        val activeArticleIds = savedArticleIds +
            independentManifests.map { it.articleId } +
            importedContentManifests.map { it.articleId }
        val tombstones = states.values
            .filter { !it.saved && it.articleId !in activeArticleIds }
            .groupBy { it.articleId }
            .mapNotNull { (articleId, articleStates) ->
                val url = articleStates.firstOrNull()?.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val articleDelete = articleStates
                    .filter { it.saveType == ARTICLE_DELETE_SYNC_TYPE }
                    .maxByOrNull { it.changedAt }
                SyncedArticleManifest(
                    articleId = articleId,
                    sourceDeviceId = articleStates.maxByOrNull { it.changedAt }?.sourceDeviceId ?: deviceId,
                    contentHash = sha256(url),
                    updatedAt = articleStates.maxOf { it.changedAt },
                    independentChangedAt = articleDelete?.changedAt ?: 0L,
                    favoriteChangedAt = articleStates.firstOrNull { it.saveType == SaveType.FAVORITE.name }?.changedAt ?: 0L,
                    watchLaterChangedAt = articleStates.firstOrNull { it.saveType == SaveType.WATCH_LATER.name }?.changedAt ?: 0L,
                    deletedAt = articleDelete?.changedAt ?: articleStates.maxOf { it.changedAt },
                    bodyHash = sha256(url),
                    bodyByteCount = 0L,
                    chunkSize = 0,
                    chunkHashes = emptyList(),
                    metadataHash = sha256(url),
                    bodySyncMode = ARTICLE_BODY_SYNC_MODE_SAVED
                )
            }
        return savedManifests + independentManifests + importedContentManifests + tombstones
    }

    override suspend fun mergeSyncedRssSources(
        sources: List<SyncedRssSource>,
        remoteDeviceId: String,
        localDeviceId: String
    ): SyncedRssSourceMergeStats = withContext(Dispatchers.IO) {
        var applied = 0
        sources.forEach { source ->
            val normalizedUrl = normalizeUrl(source.url)
            if (!isValidUrl(normalizedUrl)) return@forEach
            if (ImportedContentIds.isImportedTextSourceUrl(normalizedUrl)) return@forEach
            val existing = channelDao.getChannelByUrl(normalizedUrl)
            if (source.deleted) {
                rssSourceSyncStateDao.upsert(source.toSourceSyncState(normalizedUrl, remoteDeviceId))
                if (existing != null) {
                    deleteChannelInternal(existing.id, recordSyncChange = false)
                    applied += 1
                } else {
                    applied += 1
                }
                return@forEach
            }
            val remoteUpdatedAt = source.updatedAt.takeIf { it > 0L }
                ?: source.sortOrder.takeIf { it > 0L }
                ?: source.createdAt
            val localUpdatedAt = existing?.syncUpdatedAt() ?: 0L
            val remoteNewer = existing == null ||
                remoteUpdatedAt > localUpdatedAt ||
                (remoteUpdatedAt == localUpdatedAt && remoteDeviceId > localDeviceId)
            if (!remoteNewer) return@forEach

            val now = System.currentTimeMillis()
            val entity = RssChannelEntity(
                id = existing?.id ?: 0L,
                url = normalizedUrl,
                title = source.title.ifBlank { hostLabel(normalizedUrl) },
                description = source.description.takeIf { it.isNotBlank() } ?: source.siteUrl,
                imageUrl = source.imageUrl,
                lastFetchedAt = existing?.lastFetchedAt,
                createdAt = existing?.createdAt ?: source.createdAt.takeIf { it > 0L } ?: now,
                sortOrder = source.sortOrder.takeIf { it > 0L } ?: remoteUpdatedAt.takeIf { it > 0L } ?: now,
                isPinned = source.isPinned,
                useOriginalContent = existing?.useOriginalContent ?: false,
                continuePlaybackInBackground = existing?.continuePlaybackInBackground ?: false
            )
            if (existing == null) {
                val inserted = channelDao.insertChannel(entity)
                if (inserted <= 0L) {
                    channelDao.getChannelByUrl(normalizedUrl)?.let { current ->
                        channelDao.updateChannel(entity.copy(id = current.id))
                    }
                }
            } else {
                channelDao.updateChannel(entity)
            }
            rssSourceSyncStateDao.upsert(entity.toSourceSyncState(localDeviceId, deleted = false))
            applied += 1
        }
        SyncedRssSourceMergeStats(
            received = sources.size,
            applied = applied
        )
    }

    override suspend fun mergeSyncedSavedArticles(
        articles: List<SyncedSavedArticle>,
        remoteDeviceId: String,
        localDeviceId: String
    ): SyncedSavedArticleMergeStats = withContext(Dispatchers.IO) {
        var applied = 0
        articles.forEach { article ->
            if (article.deleted) {
                val itemId = findSyncedArticleItemId(article)
                var changed = applySyncedArticleDeletion(article, itemId, remoteDeviceId, localDeviceId)
                if (applySyncedTombstoneState(article, SaveType.FAVORITE, remoteDeviceId, localDeviceId)) {
                    changed = true
                }
                if (applySyncedTombstoneState(article, SaveType.WATCH_LATER, remoteDeviceId, localDeviceId)) {
                    changed = true
                }
                if (changed) {
                    applied += 1
                }
                return@forEach
            }
            val shouldMaterializeArticle = article.favoriteSaved ||
                article.watchLaterSaved ||
                article.independentSaved ||
                !article.deleted
            val itemId = if (shouldMaterializeArticle) {
                val channel = resolveSyncedArticleChannel(article)
                upsertSyncedArticleItem(channel.id, article)
            } else {
                findSyncedArticleItemId(article)
            }
            if (itemId == null) {
                if (applySyncedTombstoneState(article, SaveType.FAVORITE, remoteDeviceId, localDeviceId)) {
                    applied += 1
                }
                if (applySyncedTombstoneState(article, SaveType.WATCH_LATER, remoteDeviceId, localDeviceId)) {
                    applied += 1
                }
            } else {
                if (applySyncedState(article, itemId, SaveType.FAVORITE, remoteDeviceId, localDeviceId)) {
                    applied += 1
                }
                if (applySyncedState(article, itemId, SaveType.WATCH_LATER, remoteDeviceId, localDeviceId)) {
                    applied += 1
                }
            }
        }
        SyncedSavedArticleMergeStats(
            received = articles.size,
            applied = applied
        )
    }

    override suspend fun mergeSyncedChunkedArticles(
        articles: List<SyncedChunkedArticle>,
        remoteDeviceId: String,
        localDeviceId: String
    ): SyncedSavedArticleMergeStats = withContext(Dispatchers.IO) {
        val rebuilt = articles.map { payload ->
            val localItemId = findSyncedArticleItemId(payload.article)
            val localItem = localItemId?.let { itemDao.getItem(it)?.hydrateExternalContent() }
            val localArticle = localItem?.let { item ->
                item.toSyncedChannelArticle(
                    articleId = payload.article.articleId,
                    channel = resolveSyncedArticleChannel(payload.article),
                    deviceId = localDeviceId,
                    independentSaved = payload.article.independentSaved,
                    rssSourceUrl = payload.article.rssSourceUrl,
                    rssSourceTitle = payload.article.rssSourceTitle
                )
            }
            val (contentHtml, contentText) = if (payload.article.deleted) {
                localArticle?.contentHtml to localArticle?.contentText.orEmpty()
            } else if (payload.metadataOnly) {
                localArticle?.contentHtml to localArticle?.contentText.orEmpty()
            } else {
                ArticleSyncBody.rebuildBody(
                    localArticle = localArticle,
                    payload = payload,
                    localBodyHash = localItem?.syncBodyHash.orEmpty()
                )
            }
            val bodyMetadata = payload.bodyMetadata()
            payload.article.copy(
                contentHtml = contentHtml,
                contentText = contentText,
                cachedBodyMetadata = bodyMetadata
            )
        }
        mergeSyncedSavedArticles(
            articles = rebuilt,
            remoteDeviceId = remoteDeviceId,
            localDeviceId = localDeviceId
        )
    }

    override suspend fun retryOfflineMedia(itemId: Long) {
        withContext(Dispatchers.IO) {
            val item = itemDao.getItem(itemId) ?: return@withContext
            runCatching { offlineStore.downloadMediaForItem(item) }
                .onFailure { error ->
                    DebugLogBuffer.log(
                        "offline",
                        "retry error item=$itemId msg=${error.message}"
                    )
                }
        }
    }

    override suspend fun toggleLike(itemId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val item = itemDao.getItem(itemId)
            ?: return@withContext Result.failure(IllegalArgumentException("内容不存在"))
        val newValue = !item.isLiked
        itemDao.updateLiked(itemId, newValue)
        Result.success(newValue)
    }

    override suspend fun markChannelRead(channelId: Long) {
        withContext(Dispatchers.IO) {
            val unreadItems = itemDao.getItemsForChannelSyncManifest(channelId, Int.MAX_VALUE)
                .filterNot { it.isRead }
            itemDao.markReadByChannel(channelId)
            val now = System.currentTimeMillis()
            unreadItems.forEach { item ->
                recordArticleChange(
                    stableArticleId(item.link ?: item.dedupKey),
                    "read",
                    now
                )
            }
        }
    }

    override suspend fun moveChannelToTop(channelId: Long) {
        withContext(Dispatchers.IO) {
            val channel = channelDao.getChannel(channelId) ?: return@withContext
            val now = System.currentTimeMillis()
            val updated = channel.copy(sortOrder = now)
            channelDao.updateChannel(updated)
            if (updated.isSyncedRssSource()) {
                rssSourceSyncStateDao.upsert(updated.toSourceSyncState(deviceId, deleted = false))
                recordRssSourceChange(updated.url, "sourceState", now)
            }
        }
    }

    override suspend fun setChannelPinned(channelId: Long, pinned: Boolean) {
        withContext(Dispatchers.IO) {
            val channel = channelDao.getChannel(channelId) ?: return@withContext
            val newOrder = if (pinned) System.currentTimeMillis() else channel.sortOrder
            val updated = channel.copy(isPinned = pinned, sortOrder = newOrder)
            channelDao.updateChannel(updated)
            if (updated.isSyncedRssSource()) {
                rssSourceSyncStateDao.upsert(updated.toSourceSyncState(deviceId, deleted = false))
                recordRssSourceChange(updated.url, "sourceState", newOrder)
            }
        }
    }

    override suspend fun setChannelOriginalContent(channelId: Long, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val channel = channelDao.getChannel(channelId) ?: return@withContext
            if (channel.useOriginalContent == enabled) return@withContext
            channelDao.updateChannel(channel.copy(useOriginalContent = enabled))
        }
    }

    override suspend fun setChannelContinuePlaybackInBackground(channelId: Long, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val channel = channelDao.getChannel(channelId) ?: return@withContext
            if (channel.continuePlaybackInBackground == enabled) return@withContext
            channelDao.updateChannel(channel.copy(continuePlaybackInBackground = enabled))
        }
    }

    override suspend fun deleteItem(itemId: Long) {
        withContext(Dispatchers.IO) {
            val item = itemDao.getItem(itemId) ?: return@withContext
            val channel = channelDao.getChannel(item.channelId) ?: return@withContext
            val now = System.currentTimeMillis()
            val articleId = stableArticleId(item.link ?: item.dedupKey)
            val url = item.link?.takeIf { it.isNotBlank() } ?: item.dedupKey
            if (ImportedContentIds.isDeletableLocalContentChannel(channel.url)) {
                savedSyncStateDao.upsert(
                    SavedSyncStateEntity(
                        articleId = articleId,
                        saveType = ARTICLE_DELETE_SYNC_TYPE,
                        itemId = null,
                        url = url,
                        saved = false,
                        changedAt = now,
                        sortOrder = 0L,
                        sourceDeviceId = deviceId
                    )
                )
            }
            savedEntryDao.getByItemId(itemId).forEach { entry ->
                savedSyncStateDao.upsert(
                    SavedSyncStateEntity(
                        articleId = articleId,
                        saveType = entry.saveType,
                        itemId = null,
                        url = url,
                        saved = false,
                        changedAt = now,
                        sortOrder = 0L,
                        sourceDeviceId = deviceId
                    )
                )
            }
            offlineStore.deleteMediaForItem(itemId)
            itemDao.deleteItem(itemId)
            recordArticleChange(articleId, "delete", now)
        }
    }

    override suspend fun clearLocalContentChannel(channelId: Long) {
        withContext(Dispatchers.IO) {
            val channel = channelDao.getChannel(channelId) ?: return@withContext
            if (!ImportedContentIds.isDeletableLocalContentChannel(channel.url)) return@withContext
            val items = itemDao.getItemsForChannelSync(channelId, Int.MAX_VALUE)
            if (items.isEmpty()) return@withContext
            val now = System.currentTimeMillis()
            val tombstones = items.flatMapIndexed { index, item ->
                val changedAt = now + index
                val url = item.link?.takeIf { it.isNotBlank() } ?: item.dedupKey
                val articleId = stableArticleId(url.ifBlank { item.dedupKey })
                buildList {
                    add(
                        SavedSyncStateEntity(
                            articleId = articleId,
                            saveType = ARTICLE_DELETE_SYNC_TYPE,
                            itemId = null,
                            url = url,
                            saved = false,
                            changedAt = changedAt,
                            sortOrder = 0L,
                            sourceDeviceId = deviceId
                        )
                    )
                    savedEntryDao.getByItemId(item.id).forEach { entry ->
                        add(
                            SavedSyncStateEntity(
                                articleId = articleId,
                                saveType = entry.saveType,
                                itemId = null,
                                url = url,
                                saved = false,
                                changedAt = changedAt,
                                sortOrder = 0L,
                                sourceDeviceId = deviceId
                            )
                        )
                    }
                }
            }
            savedSyncStateDao.upsertAll(tombstones)
            tombstones
                .filter { it.saveType == ARTICLE_DELETE_SYNC_TYPE }
                .forEach { tombstone ->
                    recordArticleChange(tombstone.articleId, "delete", tombstone.changedAt)
                }
            offlineStore.deleteMediaForChannel(channelId)
            itemDao.deleteByChannel(channelId)
        }
    }

    override suspend fun deleteChannel(channelId: Long) {
        withContext(Dispatchers.IO) {
            deleteChannelInternal(channelId, recordSyncChange = true)
        }
    }

    private suspend fun deleteChannelInternal(channelId: Long, recordSyncChange: Boolean) {
        val channel = channelDao.getChannel(channelId) ?: return
        val now = System.currentTimeMillis()
        if (recordSyncChange && channel.isSyncedRssSource()) {
            rssSourceSyncStateDao.upsert(channel.toSourceSyncState(deviceId, deleted = true, deletedAt = now))
            recordRssSourceChange(channel.url, "delete", now)
        }
        offlineStore.deleteMediaForChannel(channelId)
        when (channel.url) {
            BuiltinChannelType.BILI.url -> cacheService.clearBucket(ManagedCacheBucket.BILI_PREVIEW)
            BuiltinChannelType.DOUYIN.url -> {
                cacheService.clearBucket(ManagedCacheBucket.DOUYIN_PRELOAD)
                DouyinPlaybackPreviewCache.clearAll()
            }
        }
        channelDao.deleteChannel(channelId)
    }

    override suspend fun trimCacheToLimit() {
        withContext(Dispatchers.IO) {
            cacheService.trimToLimit(CacheTrimReason.MANUAL)
        }
    }

    private fun schedulePreviewUpdates(items: List<RssItemEntity>) {
        items.forEach { schedulePreviewUpdate(it) }
    }

    private fun schedulePreviewUpdate(item: RssItemEntity) {
        if (!RssPreviewUpdatePlanner.needsPreviewUpdate(item)) {
            previewAttemptKeys.remove(item.id)
            return
        }
        val attemptKey = RssPreviewUpdatePlanner.attemptKeyFor(item)
        val previousAttemptKey = previewAttemptKeys[item.id]
        if (previousAttemptKey == attemptKey) {
            PerfTrace.log(
                "repo",
                "preview build skip-same-attempt itemId=${item.id} channelId=${item.channelId} attempt=${attemptKey.take(8)}"
            )
            return
        }
        if (!previewJobs.add(item.id)) return
        appScope.launch(Dispatchers.Default) {
            val startNanos = PerfTrace.now()
            try {
                PerfTrace.log(
                    "repo",
                    "preview build start itemId=${item.id} channelId=${item.channelId} hasContent=${!previewSourceContent(item).isNullOrBlank()} hasImage=${!item.imageUrl.isNullOrBlank()} attempt=${attemptKey.take(8)}"
                )
                val preview = RssPreviewFormatter.buildPreview(
                    description = item.description,
                    content = previewSourceContent(item),
                    imageUrl = item.imageUrl,
                    link = item.link
                )
                val payload = RssPreviewUpdatePlanner.buildWritePayload(item, preview)
                if (payload == null) {
                    previewAttemptKeys[item.id] = attemptKey
                    PerfTrace.log(
                        "repo",
                        "preview build skip-noop-write itemId=${item.id} channelId=${item.channelId} attempt=${attemptKey.take(8)} durMs=${PerfTrace.elapsedMs(startNanos)}"
                    )
                    return@launch
                }
                previewAttemptKeys[item.id] = attemptKey
                itemDao.updatePreview(item.id, payload.summary, payload.previewImageUrl)
                PerfTrace.log(
                    "repo",
                    "preview build end itemId=${item.id} summaryLen=${payload.summary?.length ?: 0} previewImage=${!payload.previewImageUrl.isNullOrBlank()} attempt=${attemptKey.take(8)} durMs=${PerfTrace.elapsedMs(startNanos)}"
                )
            } catch (t: Throwable) {
                previewAttemptKeys.remove(item.id, attemptKey)
                throw t
            } finally {
                previewJobs.remove(item.id)
            }
        }
    }

    private fun enqueueOriginalUpdate(channelId: Long, update: PendingOriginalUpdate) {
        val pending = pendingOriginalUpdates.getOrPut(channelId) { ConcurrentHashMap() }
        pending[update.dedupKey] = update
    }

    private fun flushOriginalUpdates(channelId: Long) {
        val pending = pendingOriginalUpdates.remove(channelId) ?: return
        if (pending.isEmpty()) return
        appScope.launch(Dispatchers.IO) {
            val startNanos = PerfTrace.now()
            PerfTrace.log(
                "repo",
                "flushOriginalUpdates start channelId=$channelId count=${pending.size}"
            )
            pending.values.forEach { update ->
                itemDao.updateOriginalContentByDedupKey(
                    channelId = channelId,
                    dedupKey = update.dedupKey,
                    content = update.content,
                    contentSizeBytes = update.contentSizeBytes
                )
            }
            PerfTrace.log(
                "repo",
                "flushOriginalUpdates end channelId=$channelId count=${pending.size} durMs=${PerfTrace.elapsedMs(startNanos)}"
            )
        }
    }

    private fun buildSearchPattern(keyword: String): String {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return "%"
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    private fun isValidUrl(url: String): Boolean {
        if (url.length > 2048) return false
        if (url.any { it.isWhitespace() }) return false
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        return !uri.host.isNullOrBlank()
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        val lower = trimmed.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return trimmed
        if (trimmed.contains("://")) return trimmed
        return if (looksLikeUrlWithoutScheme(trimmed)) {
            "http://$trimmed"
        } else {
            trimmed
        }
    }

    private fun looksLikeUrlWithoutScheme(input: String): Boolean {
        if (input.startsWith("/")) return false
        if (input.any { it.isWhitespace() }) return false
        val hostPort = input.substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostPort.isEmpty()) return false
        val host = hostPort.substringBefore(':')
        if (host.equals("localhost", ignoreCase = true)) return true
        if (host.startsWith('.') || host.endsWith('.')) return false
        if (!host.contains('.')) return false
        return host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
    }

    private fun builtinTypeFromInputUrl(url: String): BuiltinChannelType? {
        val host = runCatching { url.toUri().host }.getOrNull()
        return BuiltinChannelType.fromHost(host)
    }

    private suspend fun resolveExternalChannel(url: String): RssChannelEntity? {
        val existing = channelDao.getChannelByUrl(url)
        if (existing != null) return existing
        val builtin = BuiltinChannelType.fromUrl(url) ?: return null
        val now = System.currentTimeMillis()
        val entity = RssChannelEntity(
            url = builtin.url,
            title = builtin.title,
            description = builtin.description,
            imageUrl = null,
            lastFetchedAt = null,
            createdAt = now,
            sortOrder = now,
            isPinned = false,
            useOriginalContent = builtin.useOriginalContentByDefault
        )
        val insertedId = channelDao.insertChannel(entity)
        return if (insertedId > 0) {
            entity.copy(id = insertedId)
        } else {
            channelDao.getChannelByUrl(url)
        }
    }

    private suspend fun toggleSaved(itemId: Long, saveType: SaveType): Result<SavedState> =
        withContext(Dispatchers.IO) {
            val item = itemDao.getItem(itemId)
                ?: return@withContext Result.failure(IllegalArgumentException("内容不存在"))
            val existing = savedEntryDao.getByItemId(itemId)
            val hasType = existing.any { it.saveType == saveType.name }
            val now = System.currentTimeMillis()
            var changedArticleId = ""
            if (hasType) {
                savedEntryDao.delete(itemId, saveType.name)
                changedArticleId = upsertSavedSyncState(item, saveType, saved = false, changedAt = now, sortOrder = now)
            } else {
                savedEntryDao.insert(
                    SavedEntryEntity(
                        itemId = itemId,
                        saveType = saveType.name,
                        createdAt = now,
                        sortOrder = now
                    )
                )
                changedArticleId = upsertSavedSyncState(item, saveType, saved = true, changedAt = now, sortOrder = now)
                runCatching { offlineStore.downloadMediaForItem(item) }
            }
            recordArticleChange(changedArticleId, "sourceState", now)
            if (savedEntryDao.countByItemId(itemId) == 0) {
                maybeClearBiliPreviewForItem(item)
                offlineStore.deleteMediaForItem(itemId)
            }
            val updated = savedEntryDao.getByItemId(itemId)
            Result.success(buildSavedState(updated))
        }

    private suspend fun maybeClearBiliPreviewForItem(item: RssItemEntity) {
        val channel = channelDao.getChannel(item.channelId) ?: return
        if (channel.url != BuiltinChannelType.BILI.url) return
        val ids = extractBiliVideoIds(item) ?: return
        cacheService.clearBiliPreviewsForVideo(ids.aid, ids.bvid)
    }

    private suspend fun upsertSavedSyncState(
        item: RssItemEntity,
        saveType: SaveType,
        saved: Boolean,
        changedAt: Long,
        sortOrder: Long
    ): String {
        val url = item.link?.trim().orEmpty()
        val articleId = stableArticleId(url.ifBlank { item.dedupKey })
        savedSyncStateDao.upsert(
            SavedSyncStateEntity(
                articleId = articleId,
                saveType = saveType.name,
                itemId = item.id,
                url = url,
                saved = saved,
                changedAt = changedAt,
                sortOrder = sortOrder,
                sourceDeviceId = deviceId
            )
        )
        return articleId
    }

    private fun SavedRssItem.toSyncedArticle(
        articleId: String,
        deviceId: String,
        favoriteSaved: Boolean,
        favoriteChangedAt: Long,
        favoriteSortOrder: Long,
        watchLaterSaved: Boolean,
        watchLaterChangedAt: Long,
        watchLaterSortOrder: Long
    ): SyncedSavedArticle? {
        val hydrated = item.hydrateExternalContentForSync()
        if (!hydrated.bodyAvailable) return null
        val fullItem = hydrated.item
        val bodyContent = fullItem.toSyncBodyContent()
        val url = fullItem.link.orEmpty()
        val updatedAt = maxOf(fullItem.fetchedAt, savedAt, favoriteChangedAt, watchLaterChangedAt)
        val rssSourceUrl = channelUrl.takeIf { it.isSyncedRssSourceUrl() }
        val independentSaved = channelUrl == PHONE_IMPORT_CHANNEL_URL
        val article = SyncedSavedArticle(
            articleId = articleId,
            sourceDeviceId = deviceId,
            url = url,
            title = fullItem.title,
            siteName = channelTitle,
            excerpt = fullItem.summary ?: fullItem.description.orEmpty(),
            contentHtml = bodyContent.contentHtml,
            contentText = bodyContent.contentText,
            imageUrl = fullItem.previewImageUrl ?: fullItem.imageUrl,
            contentHash = fullItem.syncBodyHash.ifBlank {
                sha256(bodyContent.contentHtml ?: bodyContent.contentText.ifBlank { url })
            },
            importedAt = savedAt,
            updatedAt = updatedAt,
            independentSaved = independentSaved,
            independentChangedAt = if (independentSaved) savedAt else 0L,
            independentSortOrder = if (independentSaved) savedAt else 0L,
            rssSourceUrl = rssSourceUrl,
            rssSourceTitle = rssSourceUrl?.let { channelTitle },
            favoriteSaved = favoriteSaved,
            favoriteChangedAt = favoriteChangedAt,
            favoriteSortOrder = favoriteSortOrder,
            watchLaterSaved = watchLaterSaved,
            watchLaterChangedAt = watchLaterChangedAt,
            watchLaterSortOrder = watchLaterSortOrder,
            deleted = false,
            deletedAt = 0L,
            readingProgress = fullItem.readingProgress,
            readingPositionBytes = fullItem.readingPositionBytes,
            readingPositionContentHash = fullItem.readingPositionContentHash,
            readingPositionChangedAt = fullItem.readingPositionChangedAt,
            isRead = fullItem.isRead
        )
        return article.copy(cachedBodyMetadata = fullItem.currentSyncMetadataFor(article))
    }

    private fun SavedRssItem.toSyncedArticleManifest(
        articleId: String,
        deviceId: String,
        favoriteSaved: Boolean,
        favoriteChangedAt: Long,
        favoriteSortOrder: Long,
        watchLaterSaved: Boolean,
        watchLaterChangedAt: Long,
        watchLaterSortOrder: Long
    ): SyncedArticleManifest {
        val url = item.link.orEmpty()
        val updatedAt = maxOf(item.fetchedAt, savedAt, favoriteChangedAt, watchLaterChangedAt)
        val rssSourceUrl = channelUrl.takeIf { it.isSyncedRssSourceUrl() }
        val independentSaved = channelUrl == PHONE_IMPORT_CHANNEL_URL
        val article = SyncedSavedArticle(
            articleId = articleId,
            sourceDeviceId = deviceId,
            url = url,
            title = item.title,
            siteName = channelTitle,
            excerpt = item.summary ?: item.description.orEmpty(),
            contentHtml = null,
            contentText = "",
            imageUrl = item.previewImageUrl ?: item.imageUrl,
            contentHash = item.syncBodyHash.ifBlank { sha256(url) },
            importedAt = savedAt,
            updatedAt = updatedAt,
            independentSaved = independentSaved,
            independentChangedAt = if (independentSaved) savedAt else 0L,
            independentSortOrder = if (independentSaved) savedAt else 0L,
            rssSourceUrl = rssSourceUrl,
            rssSourceTitle = rssSourceUrl?.let { channelTitle },
            favoriteSaved = favoriteSaved,
            favoriteChangedAt = favoriteChangedAt,
            favoriteSortOrder = favoriteSortOrder,
            watchLaterSaved = watchLaterSaved,
            watchLaterChangedAt = watchLaterChangedAt,
            watchLaterSortOrder = watchLaterSortOrder,
            deleted = false,
            deletedAt = 0L,
            readingProgress = item.readingProgress,
            readingPositionBytes = item.readingPositionBytes,
            readingPositionContentHash = item.readingPositionContentHash,
            readingPositionChangedAt = item.readingPositionChangedAt,
            isRead = item.isRead
        )
        return article.toManifestFromItem(
            item = item,
            bodyAvailable = item.isExternalContentAvailableForSync()
        )
    }

    private suspend fun exportSyncedIndependentArticles(
        deviceId: String,
        excludedArticleIds: Set<String>,
        requestedArticleIds: Set<String>? = null
    ): List<SyncedSavedArticle> {
        val channel = channelDao.getChannelByUrl(PHONE_IMPORT_CHANNEL_URL) ?: return emptyList()
        return itemDao.getItemsForChannelSync(channel.id, Int.MAX_VALUE).mapNotNull { item ->
            val articleId = stableArticleId(item.link ?: item.dedupKey)
            if (articleId in excludedArticleIds ||
                (requestedArticleIds != null && articleId !in requestedArticleIds)
            ) {
                null
            } else {
                item.toSyncedChannelArticle(
                    articleId = articleId,
                    channel = channel,
                    deviceId = deviceId,
                    independentSaved = true,
                    rssSourceUrl = null,
                    rssSourceTitle = null
                )
            }
        }
    }

    private suspend fun exportLightweightIndependentArticleManifests(
        deviceId: String,
        excludedArticleIds: Set<String>
    ): List<SyncedArticleManifest> {
        val channel = channelDao.getChannelByUrl(PHONE_IMPORT_CHANNEL_URL) ?: return emptyList()
        return itemDao.getItemsForChannelSyncManifest(channel.id, Int.MAX_VALUE).mapNotNull { item ->
            val articleId = stableArticleId(item.link ?: item.dedupKey)
            if (articleId in excludedArticleIds) {
                null
            } else {
                item.toSyncedChannelArticleManifest(
                    articleId = articleId,
                    channel = channel,
                    deviceId = deviceId,
                    independentSaved = true,
                    rssSourceUrl = null,
                    rssSourceTitle = null
                )
            }
        }
    }

    private suspend fun exportSyncedImportedContentArticles(
        deviceId: String,
        excludedArticleIds: Set<String>,
        requestedArticleIds: Set<String>? = null
    ): List<SyncedSavedArticle> {
        return channelDao.getAllChannels()
            .filter { it.isImportedContentChannel() }
            .flatMap { channel ->
                itemDao.getItemsForChannelSync(channel.id, Int.MAX_VALUE).mapNotNull { item ->
                    val articleId = stableArticleId(item.link ?: item.dedupKey)
                    if (articleId in excludedArticleIds ||
                        (requestedArticleIds != null && articleId !in requestedArticleIds)
                    ) {
                        null
                    } else {
                        item.toSyncedChannelArticle(
                            articleId = articleId,
                            channel = channel,
                            deviceId = deviceId,
                            independentSaved = false,
                            rssSourceUrl = channel.url,
                            rssSourceTitle = channel.title
                        )
                    }
                }
            }
    }

    private suspend fun exportLightweightImportedContentArticleManifests(
        deviceId: String,
        excludedArticleIds: Set<String>
    ): List<SyncedArticleManifest> {
        return channelDao.getAllChannels()
            .filter { it.isImportedContentChannel() }
            .flatMap { channel ->
                itemDao.getItemsForChannelSyncManifest(channel.id, Int.MAX_VALUE).mapNotNull { item ->
                    val articleId = stableArticleId(item.link ?: item.dedupKey)
                    if (articleId in excludedArticleIds) {
                        null
                    } else {
                        item.toSyncedChannelArticleManifest(
                            articleId = articleId,
                            channel = channel,
                            deviceId = deviceId,
                            independentSaved = false,
                            rssSourceUrl = channel.url,
                            rssSourceTitle = channel.title
                        )
                    }
                }
            }
    }

    private fun RssItemEntity.toSyncedChannelArticle(
        articleId: String,
        channel: RssChannelEntity,
        deviceId: String,
        independentSaved: Boolean,
        rssSourceUrl: String?,
        rssSourceTitle: String?
    ): SyncedSavedArticle? {
        val hydrated = hydrateExternalContentForSync()
        if (!hydrated.bodyAvailable) return null
        val fullItem = hydrated.item
        val bodyContent = fullItem.toSyncBodyContent()
        val url = fullItem.link.orEmpty().ifBlank { fullItem.dedupKey }
        val article = SyncedSavedArticle(
            articleId = articleId,
            sourceDeviceId = deviceId,
            url = url,
            title = fullItem.title.ifBlank { url },
            siteName = channel.title,
            excerpt = fullItem.summary ?: fullItem.description.orEmpty(),
            contentHtml = bodyContent.contentHtml,
            contentText = bodyContent.contentText,
            imageUrl = fullItem.previewImageUrl ?: fullItem.imageUrl,
            contentHash = fullItem.syncBodyHash.ifBlank {
                sha256(bodyContent.contentHtml ?: bodyContent.contentText.ifBlank { url })
            },
            importedAt = fullItem.fetchedAt,
            updatedAt = fullItem.fetchedAt,
            independentSaved = independentSaved,
            independentChangedAt = if (independentSaved) fullItem.fetchedAt else 0L,
            independentSortOrder = if (independentSaved) fullItem.fetchedAt else 0L,
            rssSourceUrl = rssSourceUrl,
            rssSourceTitle = rssSourceTitle,
            favoriteSaved = false,
            favoriteChangedAt = 0L,
            favoriteSortOrder = 0L,
            watchLaterSaved = false,
            watchLaterChangedAt = 0L,
            watchLaterSortOrder = 0L,
            deleted = false,
            deletedAt = 0L,
            readingProgress = fullItem.readingProgress,
            readingPositionBytes = fullItem.readingPositionBytes,
            readingPositionContentHash = fullItem.readingPositionContentHash,
            readingPositionChangedAt = fullItem.readingPositionChangedAt,
            isRead = fullItem.isRead
        )
        return article.copy(cachedBodyMetadata = fullItem.currentSyncMetadataFor(article))
    }

    private fun RssItemEntity.toSyncedChannelArticleManifest(
        articleId: String,
        channel: RssChannelEntity,
        deviceId: String,
        independentSaved: Boolean,
        rssSourceUrl: String?,
        rssSourceTitle: String?
    ): SyncedArticleManifest {
        val url = link.orEmpty().ifBlank { dedupKey }
        val article = SyncedSavedArticle(
            articleId = articleId,
            sourceDeviceId = deviceId,
            url = url,
            title = title.ifBlank { url },
            siteName = channel.title,
            excerpt = summary ?: description.orEmpty(),
            contentHtml = null,
            contentText = "",
            imageUrl = previewImageUrl ?: imageUrl,
            contentHash = syncBodyHash.ifBlank { sha256(url) },
            importedAt = fetchedAt,
            updatedAt = fetchedAt,
            independentSaved = independentSaved,
            independentChangedAt = if (independentSaved) fetchedAt else 0L,
            independentSortOrder = if (independentSaved) fetchedAt else 0L,
            rssSourceUrl = rssSourceUrl,
            rssSourceTitle = rssSourceTitle,
            favoriteSaved = false,
            favoriteChangedAt = 0L,
            favoriteSortOrder = 0L,
            watchLaterSaved = false,
            watchLaterChangedAt = 0L,
            watchLaterSortOrder = 0L,
            deleted = false,
            deletedAt = 0L,
            readingProgress = readingProgress,
            readingPositionBytes = readingPositionBytes,
            readingPositionContentHash = readingPositionContentHash,
            readingPositionChangedAt = readingPositionChangedAt,
            isRead = isRead
        )
        return article.toManifestFromItem(
            item = this,
            bodyAvailable = isExternalContentAvailableForSync()
        )
    }

    private suspend fun resolveSyncedArticleChannel(article: SyncedSavedArticle): RssChannelEntity {
        val sourceUrl = article.rssSourceUrl?.takeIf { it.isSyncedRssSourceUrl() } ?: return resolvePhoneImportChannel()
        return resolveSyncedRssSourceChannel(
            url = sourceUrl,
            title = article.rssSourceTitle?.takeIf { it.isNotBlank() }
                ?: article.siteName.takeIf { it.isNotBlank() }
                ?: hostLabel(sourceUrl),
            description = null,
            imageUrl = null,
            updatedAt = maxOf(article.updatedAt, article.importedAt)
        )
    }

    private suspend fun resolvePhoneImportChannel(): RssChannelEntity {
        channelDao.getChannelByUrl(PHONE_IMPORT_CHANNEL_URL)?.let { existing ->
            if (existing.title != PHONE_IMPORT_CHANNEL_TITLE || existing.description != PHONE_IMPORT_CHANNEL_DESCRIPTION) {
                val updated = existing.copy(
                    title = PHONE_IMPORT_CHANNEL_TITLE,
                    description = PHONE_IMPORT_CHANNEL_DESCRIPTION,
                    useOriginalContent = true
                )
                channelDao.updateChannel(updated)
                return updated
            }
            return existing
        }
        val now = System.currentTimeMillis()
        val entity = RssChannelEntity(
            url = PHONE_IMPORT_CHANNEL_URL,
            title = PHONE_IMPORT_CHANNEL_TITLE,
            description = PHONE_IMPORT_CHANNEL_DESCRIPTION,
            imageUrl = null,
            lastFetchedAt = now,
            createdAt = now,
            sortOrder = now,
            isPinned = false,
            useOriginalContent = true
        )
        val id = channelDao.insertChannel(entity)
        return if (id > 0L) {
            entity.copy(id = id)
        } else {
            channelDao.getChannelByUrl(PHONE_IMPORT_CHANNEL_URL) ?: entity
        }
    }

    private suspend fun resolveSyncedRssSourceChannel(
        url: String,
        title: String,
        description: String?,
        imageUrl: String?,
        updatedAt: Long
    ): RssChannelEntity {
        val existing = channelDao.getChannelByUrl(url)
        val now = System.currentTimeMillis()
        val importedContent = ImportedContentIds.isImportedContentUrl(url)
        if (existing != null) {
            val next = existing.copy(
                title = title.ifBlank { existing.title },
                description = description ?: existing.description,
                imageUrl = imageUrl ?: existing.imageUrl,
                lastFetchedAt = if (importedContent) maxOf(existing.lastFetchedAt ?: 0L, updatedAt) else existing.lastFetchedAt,
                sortOrder = maxOf(existing.sortOrder, updatedAt),
                useOriginalContent = if (importedContent) true else existing.useOriginalContent
            )
            if (next != existing) {
                channelDao.updateChannel(next)
            }
            return next
        }
        val entity = RssChannelEntity(
            url = url,
            title = title.ifBlank { hostLabel(url) },
            description = description,
            imageUrl = imageUrl,
            lastFetchedAt = if (importedContent) updatedAt.takeIf { it > 0L } else null,
            createdAt = now,
            sortOrder = updatedAt.takeIf { it > 0L } ?: now,
            isPinned = false,
            useOriginalContent = importedContent,
            continuePlaybackInBackground = false
        )
        val id = channelDao.insertChannel(entity)
        return if (id > 0L) {
            entity.copy(id = id)
        } else {
            channelDao.getChannelByUrl(url) ?: entity
        }
    }

    private suspend fun upsertSyncedArticleItem(
        channelId: Long,
        article: SyncedSavedArticle
    ): Long {
        val hasIncomingBody =
            !article.contentHtml.isNullOrBlank() || article.contentText.isNotBlank()
        val content = article.contentHtml?.takeIf { it.isNotBlank() }
            ?: article.contentText.takeIf { it.isNotBlank() }
            ?: article.excerpt.takeIf { it.isNotBlank() }
        val title = article.title.ifBlank { article.url }
        val fetchedAt = syncedArticleFetchedAt(article, fallbackNow = System.currentTimeMillis())
        val syncMetadata = article.cachedBodyMetadata?.takeIf { it.isCurrentFor(article) }
            ?: ArticleSyncBody.metadataFor(article)
        val incomingReadingProgress = article.readingProgress.coerceIn(0f, 1f)
        val entity = RssItemEntity(
            channelId = channelId,
            title = title,
            description = article.excerpt.ifBlank { null },
            content = content,
            originalContent = content,
            link = article.url,
            guid = article.articleId,
            pubDate = null,
            imageUrl = article.imageUrl,
            audioUrl = null,
            videoUrl = null,
            summary = article.excerpt.ifBlank { null },
            previewImageUrl = article.imageUrl,
            isRead = article.isRead,
            isLiked = false,
            readingProgress = incomingReadingProgress,
            dedupKey = article.articleId,
            fetchedAt = fetchedAt,
            contentSizeBytes = estimateSyncedContentSize(title, article.excerpt, content, article.url, article.imageUrl),
            syncBodyHash = syncMetadata.bodyHash,
            syncBodyByteCount = syncMetadata.bodyByteCount,
            syncChunkSize = syncMetadata.chunkSize,
            syncChunkHashesJson = syncMetadata.chunkHashes.toJsonString(),
            syncMetadataHash = syncMetadata.metadataHash,
            readingPositionBytes = article.readingPositionBytes,
            readingPositionContentHash = article.readingPositionContentHash,
            readingPositionChangedAt = article.readingPositionChangedAt
        ).externalizeLargeContent()
        val existing = itemDao.getItemByDedupKey(channelId, article.articleId)
        if (existing == null) {
            val inserted = itemDao.insertItems(listOf(entity)).firstOrNull() ?: -1L
            if (inserted > 0L) return inserted
            return itemDao.getItemByDedupKey(channelId, article.articleId)?.id
                ?: error("同步文章保存失败")
        }
        val incomingReadingPositionWins =
            (hasIncomingBody && entity.syncBodyHash != existing.syncBodyHash) ||
                article.readingPositionChangedAt > existing.readingPositionChangedAt ||
                (
                    article.readingPositionChangedAt == 0L &&
                        existing.readingPositionChangedAt == 0L &&
                        incomingReadingProgress > existing.readingProgress
                    )
        itemDao.updateSyncedArticle(
            id = existing.id,
            title = entity.title,
            description = entity.description,
            content = if (hasIncomingBody) entity.content else existing.content,
            originalContent = if (hasIncomingBody) {
                entity.originalContent
            } else {
                existing.originalContent
            },
            link = entity.link,
            imageUrl = entity.imageUrl,
            summary = entity.summary,
            previewImageUrl = entity.previewImageUrl,
            fetchedAt = entity.fetchedAt,
            contentSizeBytes = if (hasIncomingBody) {
                entity.contentSizeBytes
            } else {
                existing.contentSizeBytes
            },
            syncBodyHash = if (hasIncomingBody) entity.syncBodyHash else existing.syncBodyHash,
            syncBodyByteCount = if (hasIncomingBody) {
                entity.syncBodyByteCount
            } else {
                existing.syncBodyByteCount
            },
            syncChunkSize = if (hasIncomingBody) entity.syncChunkSize else existing.syncChunkSize,
            syncChunkHashesJson = if (hasIncomingBody) {
                entity.syncChunkHashesJson
            } else {
                existing.syncChunkHashesJson
            },
            syncMetadataHash = if (hasIncomingBody) {
                entity.syncMetadataHash
            } else {
                existing.syncMetadataHash
            },
            readingProgress = if (incomingReadingPositionWins) {
                incomingReadingProgress
            } else {
                existing.readingProgress
            },
            isRead = existing.isRead || article.isRead,
            readingPositionBytes = if (incomingReadingPositionWins) {
                article.readingPositionBytes
            } else {
                existing.readingPositionBytes
            },
            readingPositionContentHash = if (incomingReadingPositionWins) {
                article.readingPositionContentHash
            } else {
                existing.readingPositionContentHash
            },
            readingPositionChangedAt = maxOf(
                existing.readingPositionChangedAt,
                article.readingPositionChangedAt
            )
        )
        return existing.id
    }

    private suspend fun ensureSyncedArticleMetadata(article: SyncedSavedArticle): SyncedSavedArticle {
        val articleMetadata = article.cachedBodyMetadata?.takeIf { it.isCurrentFor(article) }
        val itemId = findSyncedArticleItemId(article) ?: return articleMetadata
            ?.let { article.copy(cachedBodyMetadata = it) }
            ?: article
        val item = itemDao.getItem(itemId)
        val itemMetadata = item?.currentSyncMetadataFor(article)
        if (itemMetadata != null) {
            return article.copy(cachedBodyMetadata = itemMetadata)
        }
        if (articleMetadata != null) {
            itemDao.updateSyncMetadata(
                id = itemId,
                syncBodyHash = articleMetadata.bodyHash,
                syncBodyByteCount = articleMetadata.bodyByteCount,
                syncChunkSize = articleMetadata.chunkSize,
                syncChunkHashesJson = articleMetadata.chunkHashes.toJsonString(),
                syncMetadataHash = articleMetadata.metadataHash
            )
            return article.copy(cachedBodyMetadata = articleMetadata)
        }
        val metadata = ArticleSyncBody.metadataFor(article)
        itemDao.updateSyncMetadata(
            id = itemId,
            syncBodyHash = metadata.bodyHash,
            syncBodyByteCount = metadata.bodyByteCount,
            syncChunkSize = metadata.chunkSize,
            syncChunkHashesJson = metadata.chunkHashes.toJsonString(),
            syncMetadataHash = metadata.metadataHash
        )
        return article.copy(cachedBodyMetadata = metadata)
    }

    private fun SyncedSavedArticle.toManifest(metadata: ArticleBodyMetadata): SyncedArticleManifest {
        return SyncedArticleManifest(
            articleId = articleId,
            sourceDeviceId = sourceDeviceId,
            contentHash = contentHash,
            updatedAt = updatedAt,
            independentChangedAt = independentChangedAt,
            favoriteChangedAt = favoriteChangedAt,
            watchLaterChangedAt = watchLaterChangedAt,
            deletedAt = deletedAt,
            bodyHash = metadata.bodyHash,
            bodyByteCount = metadata.bodyByteCount,
            chunkSize = metadata.chunkSize,
            chunkHashes = metadata.chunkHashes,
            metadataHash = metadata.metadataHash,
            bodyAvailable = true,
            bodySyncMode = bodySyncModeForSync(),
            readingProgress = readingProgress,
            readingPositionBytes = readingPositionBytes,
            readingPositionContentHash = readingPositionContentHash,
            readingPositionChangedAt = readingPositionChangedAt,
            isRead = isRead
        )
    }

    private fun SyncedChunkedArticle.bodyMetadata(): ArticleBodyMetadata {
        return ArticleBodyMetadata(
            bodyHash = bodyHash,
            bodyByteCount = bodyByteCount,
            chunkSize = chunkSize,
            chunkHashes = chunkHashes,
            metadataHash = ArticleSyncBody.metadataHashFor(article)
        )
    }

    private fun SyncedSavedArticle.toManifestFromItem(
        item: RssItemEntity,
        bodyAvailable: Boolean
    ): SyncedArticleManifest {
        val metadata = item.currentSyncMetadataFor(this)
        return SyncedArticleManifest(
            articleId = articleId,
            sourceDeviceId = sourceDeviceId,
            contentHash = contentHash,
            updatedAt = updatedAt,
            independentChangedAt = independentChangedAt,
            favoriteChangedAt = favoriteChangedAt,
            watchLaterChangedAt = watchLaterChangedAt,
            deletedAt = deletedAt,
            bodyHash = metadata?.bodyHash.orEmpty(),
            bodyByteCount = metadata?.bodyByteCount ?: 0L,
            chunkSize = metadata?.chunkSize ?: 0,
            chunkHashes = metadata?.chunkHashes.orEmpty(),
            metadataHash = metadata?.metadataHash.orEmpty(),
            bodyAvailable = bodyAvailable,
            bodySyncMode = bodySyncModeForSync(),
            readingProgress = readingProgress,
            readingPositionBytes = readingPositionBytes,
            readingPositionContentHash = readingPositionContentHash,
            readingPositionChangedAt = readingPositionChangedAt,
            isRead = isRead
        )
    }

    private suspend fun findSyncedArticleItemId(article: SyncedSavedArticle): Long? {
        for (saveType in listOf(SaveType.FAVORITE, SaveType.WATCH_LATER)) {
            val itemId = savedSyncStateDao.get(article.articleId, saveType.name)?.itemId ?: continue
            if (itemDao.getItem(itemId) != null) {
                return itemId
            }
        }
        val sourceUrl = article.rssSourceUrl?.takeIf { it.isSyncedRssSourceUrl() }
        val channel = if (sourceUrl != null) {
            channelDao.getChannelByUrl(sourceUrl)
        } else {
            channelDao.getChannelByUrl(PHONE_IMPORT_CHANNEL_URL)
        } ?: return null
        return itemDao.getItemByDedupKey(channel.id, article.articleId)?.id
    }

    private suspend fun applySyncedState(
        article: SyncedSavedArticle,
        itemId: Long,
        saveType: SaveType,
        remoteDeviceId: String,
        localDeviceId: String
    ): Boolean {
        val remoteSaved = when (saveType) {
            SaveType.FAVORITE -> article.favoriteSaved
            SaveType.WATCH_LATER -> article.watchLaterSaved
        }
        val remoteChangedAt = when (saveType) {
            SaveType.FAVORITE -> article.favoriteChangedAt
            SaveType.WATCH_LATER -> article.watchLaterChangedAt
        }
        val remoteSortOrder = when (saveType) {
            SaveType.FAVORITE -> article.favoriteSortOrder
            SaveType.WATCH_LATER -> article.watchLaterSortOrder
        }
        if (!remoteSaved && remoteChangedAt <= 0L) return false
        val current = savedSyncStateDao.get(article.articleId, saveType.name)
        val currentSource = current?.sourceDeviceId ?: localDeviceId
        val remoteNewer = current == null ||
            remoteChangedAt > current.changedAt ||
            (remoteChangedAt == current.changedAt && remoteDeviceId > currentSource)
        if (!remoteNewer) return false

        val existing = savedEntryDao.getByItemId(itemId)
        val hasType = existing.any { it.saveType == saveType.name }
        if (remoteSaved) {
            if (hasType) {
                savedEntryDao.updateSortOrder(itemId, saveType.name, remoteSortOrder)
            } else {
                savedEntryDao.insert(
                    SavedEntryEntity(
                        itemId = itemId,
                        saveType = saveType.name,
                        createdAt = remoteChangedAt,
                        sortOrder = remoteSortOrder
                    )
                )
            }
            itemDao.getItem(itemId)?.let { item ->
                runCatching { offlineStore.downloadMediaForItem(item) }
            }
        } else if (hasType) {
            savedEntryDao.delete(itemId, saveType.name)
            if (savedEntryDao.countByItemId(itemId) == 0) {
                offlineStore.deleteMediaForItem(itemId)
            }
        }
        savedSyncStateDao.upsert(
            SavedSyncStateEntity(
                articleId = article.articleId,
                saveType = saveType.name,
                itemId = itemId,
                url = article.url,
                saved = remoteSaved,
                changedAt = remoteChangedAt,
                sortOrder = remoteSortOrder,
                sourceDeviceId = remoteDeviceId
            )
        )
        return true
    }

    private suspend fun applySyncedTombstoneState(
        article: SyncedSavedArticle,
        saveType: SaveType,
        remoteDeviceId: String,
        localDeviceId: String
    ): Boolean {
        val remoteSaved = when (saveType) {
            SaveType.FAVORITE -> article.favoriteSaved
            SaveType.WATCH_LATER -> article.watchLaterSaved
        }
        if (remoteSaved) return false
        val remoteChangedAt = when (saveType) {
            SaveType.FAVORITE -> article.favoriteChangedAt
            SaveType.WATCH_LATER -> article.watchLaterChangedAt
        }
        if (remoteChangedAt <= 0L) return false
        val current = savedSyncStateDao.get(article.articleId, saveType.name)
        val currentSource = current?.sourceDeviceId ?: localDeviceId
        val remoteNewer = current == null ||
            remoteChangedAt > current.changedAt ||
            (remoteChangedAt == current.changedAt && remoteDeviceId > currentSource)
        if (!remoteNewer) return false
        savedSyncStateDao.upsert(
            SavedSyncStateEntity(
                articleId = article.articleId,
                saveType = saveType.name,
                itemId = null,
                url = article.url,
                saved = false,
                changedAt = remoteChangedAt,
                sortOrder = 0L,
                sourceDeviceId = remoteDeviceId
            )
        )
        return true
    }

    private suspend fun applySyncedArticleDeletion(
        article: SyncedSavedArticle,
        itemId: Long?,
        remoteDeviceId: String,
        localDeviceId: String
    ): Boolean {
        if (!article.deleted || article.deletedAt <= 0L) return false
        val current = savedSyncStateDao.get(article.articleId, ARTICLE_DELETE_SYNC_TYPE)
        val currentSource = current?.sourceDeviceId ?: localDeviceId
        val remoteNewer = current == null ||
            article.deletedAt > current.changedAt ||
            (article.deletedAt == current.changedAt && remoteDeviceId > currentSource)
        if (!remoteNewer) return false

        if (itemId != null) {
            savedEntryDao.getByItemId(itemId).forEach { entry ->
                savedSyncStateDao.upsert(
                    SavedSyncStateEntity(
                        articleId = article.articleId,
                        saveType = entry.saveType,
                        itemId = null,
                        url = article.url,
                        saved = false,
                        changedAt = article.deletedAt,
                        sortOrder = 0L,
                        sourceDeviceId = remoteDeviceId
                    )
                )
            }
            offlineStore.deleteMediaForItem(itemId)
            itemDao.deleteItem(itemId)
        }
        savedSyncStateDao.upsert(
            SavedSyncStateEntity(
                articleId = article.articleId,
                saveType = ARTICLE_DELETE_SYNC_TYPE,
                itemId = null,
                url = article.url,
                saved = false,
                changedAt = article.deletedAt,
                sortOrder = 0L,
                sourceDeviceId = remoteDeviceId
            )
        )
        return true
    }

    private suspend fun recordArticleChange(
        articleId: String,
        reason: String,
        changedAt: Long = System.currentTimeMillis()
    ) {
        if (articleId.isBlank()) return
        syncChangeLogDao.insert(
            SyncChangeLogEntity(
                kind = SYNC_KIND_ARTICLE,
                entityId = articleId,
                changedAt = changedAt,
                originDeviceId = deviceId,
                reason = reason,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun recordRssSourceChange(
        sourceUrl: String,
        reason: String,
        changedAt: Long = System.currentTimeMillis()
    ) {
        if (sourceUrl.isBlank() || ImportedContentIds.isImportedTextSourceUrl(sourceUrl)) return
        syncChangeLogDao.insert(
            SyncChangeLogEntity(
                kind = SYNC_KIND_RSS_SOURCE,
                entityId = sourceUrl,
                changedAt = changedAt,
                originDeviceId = deviceId,
                reason = reason,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun extractBiliVideoIds(item: RssItemEntity): BiliVideoIds? {
        parseBiliGuid(item.guid)?.let { return it }
        return parseBiliLink(item.link)
    }

    private fun parseBiliGuid(guid: String?): BiliVideoIds? {
        val raw = guid?.trim()?.removePrefix("bili:")?.takeIf { it.isNotBlank() } ?: return null
        return when {
            raw.startsWith("BV", ignoreCase = true) -> BiliVideoIds(bvid = raw)
            raw.startsWith("av", ignoreCase = true) -> BiliVideoIds(aid = raw.substring(2).toLongOrNull())
            else -> null
        }
    }

    private fun parseBiliLink(link: String?): BiliVideoIds? {
        val uri = runCatching { link?.toUri() }.getOrNull() ?: return null
        val segments = uri.pathSegments.orEmpty()
        val videoIndex = segments.indexOf("video")
        if (videoIndex >= 0 && videoIndex + 1 < segments.size) {
            val videoId = segments[videoIndex + 1]
            if (videoId.startsWith("BV", ignoreCase = true)) {
                return BiliVideoIds(bvid = videoId)
            }
            if (videoId.startsWith("av", ignoreCase = true)) {
                return BiliVideoIds(aid = videoId.substring(2).toLongOrNull())
            }
        }
        val bvid = uri.getQueryParameter("bvid")?.takeIf { it.isNotBlank() }
        if (bvid != null) {
            return BiliVideoIds(bvid = bvid)
        }
        val aid = uri.getQueryParameter("aid")?.toLongOrNull()
        return if (aid != null) BiliVideoIds(aid = aid) else null
    }

    private fun buildOriginalFallbackContent(item: ParsedItem): String {
        val notice = "原文抓取失败，已显示 RSS 内容。"
        val body = item.content?.trim()?.ifEmpty { null }
            ?: item.description?.trim()?.ifEmpty { null }
        return if (body == null) {
            "<p>$notice</p>"
        } else {
            "<p>$notice</p>\n$body"
        }
    }

    private fun buildOriginalFallbackContent(item: RssItemEntity): String {
        val notice = "原文抓取失败，已显示 RSS 内容。"
        val body = item.content?.trim()?.ifEmpty { null }
            ?: item.description?.trim()?.ifEmpty { null }
        return if (body == null) {
            "<p>$notice</p>"
        } else {
            "<p>$notice</p>\n$body"
        }
    }

    private fun estimateContentSize(
        title: String?,
        description: String?,
        content: String?,
        originalContent: String?,
        link: String?,
        imageUrl: String?,
        audioUrl: String?,
        videoUrl: String?
    ): Long {
        var total = 0L
        val parts = listOf(title, description, content, originalContent, link, imageUrl, audioUrl, videoUrl)
        for (part in parts) {
            if (!part.isNullOrEmpty()) {
                total += part.toByteArray(Charsets.UTF_8).size
            }
        }
        return total
    }

    private fun RssItemEntity.externalizeLargeContent(): RssItemEntity {
        val store = articleContentStore ?: return this
        if (!shouldExternalizeContent(store)) return this
        val storedContent = externalizeContentValue("${dedupKey}-content", content)
        val storedOriginalContent = if (originalContent == content && storedContent != content) {
            storedContent
        } else {
            externalizeContentValue("${dedupKey}-original", originalContent)
        }
        return copy(
            content = storedContent,
            originalContent = storedOriginalContent
        )
    }

    private fun RssItemEntity.hydrateExternalContent(): RssItemEntity {
        val store = articleContentStore ?: return this
        if (content != null && content == originalContent && store.isMarker(content)) {
            val hydrated = store.loadText(content)
            return copy(
                content = hydrated,
                originalContent = hydrated
            )
        }
        val hydratedContent = content?.let { value ->
            if (store.isMarker(value)) store.loadText(value) else value
        }
        val hydratedOriginalContent = originalContent?.let { value ->
            if (store.isMarker(value)) store.loadText(value) else value
        }
        return copy(
            content = hydratedContent,
            originalContent = hydratedOriginalContent
        )
    }

    private fun RssItemEntity.isFileBackedImportedText(): Boolean {
        val store = articleContentStore ?: return false
        if (!ImportedContentIds.isImportedTextItemUrl(link)) return false
        return content?.let(store::isMarker) == true || originalContent?.let(store::isMarker) == true
    }

    private fun RssItemEntity.hydrateExternalContentForSync(): SyncHydratedRssItem {
        val store = articleContentStore ?: return SyncHydratedRssItem(this, bodyAvailable = true)
        var bodyAvailable = true
        if (content != null && content == originalContent && store.isMarker(content)) {
            val hydrated = store.loadText(content) ?: run {
                bodyAvailable = false
                content
            }
            return SyncHydratedRssItem(
                item = copy(
                    content = hydrated,
                    originalContent = hydrated
                ),
                bodyAvailable = bodyAvailable
            )
        }
        val hydratedContent = content?.let { value ->
            if (store.isMarker(value)) {
                store.loadText(value) ?: run {
                    bodyAvailable = false
                    value
                }
            } else {
                value
            }
        }
        val hydratedOriginalContent = originalContent?.let { value ->
            if (store.isMarker(value)) {
                store.loadText(value) ?: run {
                    bodyAvailable = false
                    value
                }
            } else {
                value
            }
        }
        return SyncHydratedRssItem(
            item = copy(
                content = hydratedContent,
                originalContent = hydratedOriginalContent
            ),
            bodyAvailable = bodyAvailable
        )
    }

    private fun RssItemEntity.isExternalContentAvailableForSync(): Boolean {
        val store = articleContentStore ?: return true
        fun isAvailable(value: String?): Boolean {
            if (value.isNullOrBlank() || !store.isMarker(value)) return true
            return store.hasText(value)
        }
        return isAvailable(content) && isAvailable(originalContent)
    }

    private fun RssItemEntity.toSyncBodyContent(): SyncBodyContent {
        val body = originalContent?.takeIf { it.isNotBlank() }
            ?: content?.takeIf { it.isNotBlank() }
            ?: description?.takeIf { it.isNotBlank() }
            ?: return SyncBodyContent(contentHtml = null, contentText = "")
        return if (body.looksLikeHtmlContent()) {
            SyncBodyContent(
                contentHtml = body,
                contentText = Jsoup.parse(body).text().trim()
            )
        } else {
            SyncBodyContent(
                contentHtml = null,
                contentText = body
            )
        }
    }

    private fun String.looksLikeHtmlContent(): Boolean {
        val sample = take(HTML_DETECTION_SAMPLE_CHARS)
        return HTML_TAG_PATTERN.containsMatchIn(sample)
    }

    private fun RssItemEntity.shouldExternalizeContent(store: ArticleContentStore): Boolean {
        val totalChars = inlineContentLength(content, store) + inlineContentLength(originalContent, store)
        return totalChars > MAX_INLINE_CONTENT_CHARS ||
            shouldExternalizeField(content, store) ||
            shouldExternalizeField(originalContent, store)
    }

    private fun externalizeContentValue(key: String, value: String?): String? {
        val store = articleContentStore ?: return value
        if (!shouldExternalizeField(value, store)) return value
        return store.storeText(key, value.orEmpty())
    }

    private fun inlineContentLength(value: String?, store: ArticleContentStore): Int {
        if (value.isNullOrBlank() || store.isMarker(value)) return 0
        return value.length
    }

    private fun shouldExternalizeField(value: String?, store: ArticleContentStore): Boolean {
        if (value.isNullOrBlank() || store.isMarker(value)) return false
        return value.length > MAX_INLINE_CONTENT_CHARS / 2
    }

    private fun previewSourceContent(item: RssItemEntity): String? {
        return item.originalContent.previewSnippet()
            ?: item.content.previewSnippet()
    }

    private fun String?.previewSnippet(): String? {
        val value = this ?: return null
        if (value.isBlank()) return null
        val store = articleContentStore
        if (store != null && store.isMarker(value)) return null
        val start = value.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return null
        val rawEndExclusive = value.indexOfLast { !it.isWhitespace() } + 1
        val endExclusive = (start + PREVIEW_SOURCE_MAX_CHARS).coerceAtMost(rawEndExclusive)
        return value.substring(start, endExclusive).ifEmpty { null }
    }

    private fun buildSavedState(entries: List<SavedEntryEntity>): SavedState {
        val types = entries.map { it.saveType }.toSet()
        return SavedState(
            isFavorite = SaveType.FAVORITE.name in types,
            isWatchLater = SaveType.WATCH_LATER.name in types
        )
    }

    private fun SavedRssItem.toModel(): SavedItem = SavedItem(
        item = item.hydrateExternalContent().toModel(),
        channelTitle = channelTitle,
        savedAt = savedAt,
        saveType = SaveType.valueOf(saveType)
    )

    private fun OfflineMediaEntity.toModel(): OfflineMedia = OfflineMedia(
        itemId = itemId,
        type = OfflineMediaType.valueOf(mediaType),
        originUrl = originUrl,
        localPath = localPath
    )

    private fun <T> Result<T>.mapError(): Result<T> {
        val error = exceptionOrNull() ?: return this
        val message = when (error) {
            is java.net.UnknownHostException -> "网络不可用"
            is java.net.SocketTimeoutException -> "请求超时"
            is java.net.ConnectException -> "网络不可用"
            is javax.net.ssl.SSLException -> "网络不可用"
            is java.io.EOFException -> "解析失败"
            is org.xml.sax.SAXParseException -> "不是有效的RSS/Atom"
            is IllegalArgumentException -> error.message ?: "参数错误"
            else -> error.message ?: "解析失败"
        }
        return Result.failure(IllegalStateException(message))
    }

    private fun RssChannelEntity.toModel(unreadCount: Int): RssChannel = RssChannel(
        id = id,
        url = url,
        title = title,
        description = description,
        imageUrl = imageUrl,
        lastFetchedAt = lastFetchedAt,
        sortOrder = sortOrder,
        isPinned = isPinned,
        useOriginalContent = useOriginalContent,
        unreadCount = unreadCount,
        continuePlaybackInBackground = continuePlaybackInBackground
    )

    private fun RssItemEntity.toModel(): RssItem = RssItem(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        content = content,
        originalContent = originalContent,
        link = link,
        pubDate = pubDate,
        imageUrl = imageUrl,
        audioUrl = audioUrl,
        videoUrl = videoUrl,
        summary = summary,
        previewImageUrl = previewImageUrl,
        isRead = isRead,
        isLiked = isLiked,
        readingProgress = readingProgress,
        fetchedAt = fetchedAt
    )

    /**
     * 仅供 instrumented tests 直接操作真实 Room DAO，用于截图测试准备数据。
     * 不应用于生产代码。
     */
    val testChannelDao: RssChannelDao
        get() = channelDao

    /**
     * 仅供 instrumented tests 直接操作真实 Room DAO，用于截图测试准备数据。
     * 不应用于生产代码。
     */
    val testItemDao: RssItemDao
        get() = itemDao
}

private data class PendingOriginalUpdate(
    val dedupKey: String,
    val content: String,
    val contentSizeBytes: Long
)

private const val PHONE_IMPORT_CHANNEL_URL = ImportedContentIds.PHONE_IMPORT_CHANNEL_URL
private const val PHONE_IMPORT_CHANNEL_TITLE = ImportedContentIds.PHONE_IMPORT_CHANNEL_TITLE
private const val PHONE_IMPORT_CHANNEL_DESCRIPTION = "从手机同步来的独立网页文章"
private const val ARTICLE_DELETE_SYNC_TYPE = "ARTICLE_DELETE"
private const val CHANGE_SEQUENCE_PROTOCOL_VERSION = 13
private const val DEFAULT_LIBRARY_PEER_ID = "phone"
private const val FULL_SNAPSHOT_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L
private const val SYNC_KIND_ARTICLE = "article"
private const val SYNC_KIND_RSS_SOURCE = "rssSource"
private const val MAX_INLINE_CONTENT_CHARS = 100_000
private const val PREVIEW_SOURCE_MAX_CHARS = 16_384
private const val HASH_CHUNK_CHARS = 8 * 1024
private const val HTML_DETECTION_SAMPLE_CHARS = 4096
private val HTML_TAG_PATTERN = Regex(
    """<\s*/?\s*(html|head|body|article|section|main|div|p|br|h[1-6]|ul|ol|li|span|table|blockquote|pre|code)\b""",
    RegexOption.IGNORE_CASE
)

private fun RssChannelEntity.isSyncedRssSource(): Boolean {
    return url.isSyncedRssSourceUrl() && !ImportedContentIds.isImportedTextSourceUrl(url)
}

private fun RssChannelEntity.isImportedContentChannel(): Boolean {
    return ImportedContentIds.isImportedContentUrl(url)
}

private fun String.isSyncedRssSourceUrl(): Boolean {
    val lower = trim().lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://")
}

private fun SyncedSavedArticle.bodySyncModeForSync(): String {
    return if (
        independentSaved ||
        ImportedContentIds.isImportedContentUrl(rssSourceUrl) ||
        ImportedContentIds.isImportedContentUrl(url)
    ) {
        ARTICLE_BODY_SYNC_MODE_FULL
    } else {
        ARTICLE_BODY_SYNC_MODE_SAVED
    }
}

private fun RssChannelEntity.syncUpdatedAt(): Long {
    return maxOf(lastFetchedAt ?: 0L, sortOrder, createdAt)
}

private fun RssChannelEntity.toSyncedRssSource(deviceId: String): SyncedRssSource {
    return SyncedRssSource(
        url = url,
        sourceDeviceId = deviceId,
        title = title,
        description = description.orEmpty(),
        siteUrl = null,
        imageUrl = imageUrl,
        createdAt = createdAt,
        updatedAt = syncUpdatedAt(),
        sortOrder = sortOrder,
        isPinned = isPinned,
        deleted = false,
        deletedAt = 0L
    )
}

private fun RssChannelEntity.toSourceSyncState(
    deviceId: String,
    deleted: Boolean,
    deletedAt: Long = 0L
): RssSourceSyncStateEntity {
    val updatedAt = if (deleted) {
        deletedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    } else {
        syncUpdatedAt()
    }
    return RssSourceSyncStateEntity(
        url = url,
        sourceDeviceId = deviceId,
        title = title,
        description = description.orEmpty(),
        siteUrl = null,
        imageUrl = imageUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sortOrder = sortOrder,
        isPinned = if (deleted) false else isPinned,
        deleted = deleted,
        deletedAt = if (deleted) updatedAt else 0L
    )
}

private fun SyncedRssSource.toSourceSyncState(url: String, deviceId: String): RssSourceSyncStateEntity {
    val updatedAt = updatedAt.takeIf { it > 0L }
        ?: deletedAt.takeIf { it > 0L }
        ?: sortOrder.takeIf { it > 0L }
        ?: createdAt
    val effectiveDeletedAt = if (deleted) {
        deletedAt.takeIf { it > 0L } ?: updatedAt
    } else {
        0L
    }
    return RssSourceSyncStateEntity(
        url = url,
        sourceDeviceId = sourceDeviceId.ifBlank { deviceId },
        title = title.ifBlank { hostLabel(url) },
        description = description,
        siteUrl = siteUrl,
        imageUrl = imageUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sortOrder = sortOrder,
        isPinned = isPinned,
        deleted = deleted,
        deletedAt = effectiveDeletedAt
    )
}

private fun RssSourceSyncStateEntity.toSyncedRssSource(deviceId: String): SyncedRssSource {
    return SyncedRssSource(
        url = url,
        sourceDeviceId = sourceDeviceId.ifBlank { deviceId },
        title = title,
        description = description,
        siteUrl = siteUrl,
        imageUrl = imageUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sortOrder = sortOrder,
        isPinned = isPinned,
        deleted = deleted,
        deletedAt = deletedAt
    )
}

private fun List<SyncedRssSource>.mergeSourceTombstones(
    deviceId: String,
    states: List<RssSourceSyncStateEntity>
): List<SyncedRssSource> {
    if (states.isEmpty()) return this
    val activeUrls = mapTo(mutableSetOf()) { it.url }
    val tombstones = states
        .filter { it.deleted && it.url !in activeUrls }
        .map { it.toSyncedRssSource(deviceId) }
    return this + tombstones
}

private fun stableArticleId(value: String): String {
    return sha256(
        runCatching {
            val uri = URI(normalizeUrl(value))
            val scheme = uri.scheme.lowercase()
            val host = uri.host.orEmpty().lowercase().removePrefix("www.")
            val path = uri.rawPath.orEmpty().ifBlank { "/" }
            val query = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
            "$scheme://$host$path$query"
        }.getOrElse { value.trim() }
    )
}

internal fun syncedArticleFetchedAt(article: SyncedSavedArticle, fallbackNow: Long): Long {
    return maxOf(article.updatedAt, article.importedAt)
        .takeIf { it > 0L }
        ?: fallbackNow
}

private fun SyncedArticleManifest.latestOperationAt(): Long {
    return maxOf(updatedAt, independentChangedAt, favoriteChangedAt, watchLaterChangedAt, deletedAt)
}

private fun normalizeUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return "https://$trimmed"
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    var start = 0
    while (start < value.length) {
        var end = (start + HASH_CHUNK_CHARS).coerceAtMost(value.length)
        if (end < value.length && Character.isHighSurrogate(value[end - 1])) {
            end -= 1
        }
        digest.update(value.substring(start, end).toByteArray(Charsets.UTF_8))
        start = end
    }
    val bytes = digest.digest()
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun hostLabel(url: String): String {
    return runCatching { URI(normalizeUrl(url)).host.orEmpty().removePrefix("www.") }
        .getOrDefault("")
        .trim()
}

private fun estimateSyncedContentSize(vararg parts: String?): Long {
    var total = 0L
    for (part in parts) {
        if (!part.isNullOrBlank()) {
            total += part.toByteArray(Charsets.UTF_8).size
        }
    }
    return total
}

private fun List<String>.toJsonString(): String {
    return JSONArray().also { array ->
        forEach(array::put)
    }.toString()
}

private fun RssItemEntity.currentSyncMetadataFor(article: SyncedSavedArticle): ArticleBodyMetadata? {
    val expectedMetadataHash = ArticleSyncBody.metadataHashFor(article)
    if (syncMetadataHash != expectedMetadataHash) return null
    val chunkHashes = syncChunkHashesJson.toStringList()
    val metadata = ArticleBodyMetadata(
        bodyHash = syncBodyHash,
        bodyByteCount = syncBodyByteCount,
        chunkSize = syncChunkSize,
        chunkHashes = chunkHashes,
        metadataHash = syncMetadataHash
    )
    return metadata.takeIf { it.isCurrentFor(article) }
}

private fun ArticleBodyMetadata.isCurrentFor(article: SyncedSavedArticle): Boolean {
    return metadataHash == ArticleSyncBody.metadataHashFor(article) &&
        bodyHash.isNotBlank() &&
        bodyByteCount > 0L &&
        chunkSize > 0 &&
        chunkHashes.isNotEmpty()
}

private fun String.toStringList(): List<String> {
    if (isBlank()) return emptyList()
    val array = runCatching { JSONArray(this) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private data class BiliVideoIds(
    val aid: Long? = null,
    val bvid: String? = null
)
