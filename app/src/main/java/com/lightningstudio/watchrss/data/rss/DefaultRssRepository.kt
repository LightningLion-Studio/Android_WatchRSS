package com.lightningstudio.watchrss.data.rss

import android.net.Uri
import com.lightningstudio.watchrss.data.db.OfflineMediaDao
import com.lightningstudio.watchrss.data.db.OfflineMediaEntity
import com.lightningstudio.watchrss.data.db.RssChannelDao
import com.lightningstudio.watchrss.data.db.RssChannelEntity
import com.lightningstudio.watchrss.data.db.RssItemDao
import com.lightningstudio.watchrss.data.db.RssItemEntity
import com.lightningstudio.watchrss.data.db.SavedEntryDao
import com.lightningstudio.watchrss.data.db.SavedEntryEntity
import com.lightningstudio.watchrss.data.db.SavedRssItem
import com.lightningstudio.watchrss.debug.DebugLogBuffer
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.prof18.rssparser.model.RssItem as ParsedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class DefaultRssRepository(
    private val channelDao: RssChannelDao,
    private val itemDao: RssItemDao,
    private val savedEntryDao: SavedEntryDao,
    private val offlineMediaDao: OfflineMediaDao,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope,
    private val fetchService: RssFetchService,
    private val readableService: RssReadableService,
    private val parseService: RssParseService,
    private val offlineStore: RssOfflineStore
) : RssRepository {
    private val refreshJobs = ConcurrentHashMap<Long, Job>()
    private val originalContentItemJobs = ConcurrentHashMap<Long, Job>()
    private val pausedOriginalChannels: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val pendingOriginalUpdates:
        ConcurrentHashMap<Long, ConcurrentHashMap<String, PendingOriginalUpdate>> =
        ConcurrentHashMap()
    private val previewJobs: MutableSet<Long> = ConcurrentHashMap.newKeySet()

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
        itemDao.observeItemsPaged(channelId, limit).map { items ->
            schedulePreviewUpdates(items)
            items.map { it.toModel() }
        }

    override fun observeItemCount(channelId: Long): Flow<Int> =
        itemDao.observeItemCount(channelId)

    override fun observeItem(itemId: Long): Flow<RssItem?> =
        itemDao.observeItem(itemId).map { item ->
            if (item != null) {
                schedulePreviewUpdate(item)
            }
            item?.toModel()
        }

    override fun searchItems(channelId: Long, keyword: String, limit: Int): Flow<List<RssItem>> {
        val pattern = buildSearchPattern(keyword)
        return itemDao.searchItems(channelId, pattern, limit).map { items ->
            schedulePreviewUpdates(items)
            items.map { it.toModel() }
        }
    }

    override fun observeCacheUsageBytes(): Flow<Long> =
        itemDao.observeTotalCacheBytes().map { it ?: 0L }

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

    override suspend fun ensureBuiltinChannels() = withContext(Dispatchers.IO) {
        if (settingsRepository.builtinChannelsInitialized.first()) return@withContext
        val now = System.currentTimeMillis()
        BuiltinChannelType.values().forEachIndexed { index, type ->
            val timestamp = now - index
            val entity = RssChannelEntity(
                url = type.url,
                title = type.title,
                description = type.description,
                imageUrl = null,
                lastFetchedAt = null,
                createdAt = timestamp,
                sortOrder = timestamp,
                isPinned = false,
                useOriginalContent = type.useOriginalContentByDefault
            )
            channelDao.insertChannel(entity)
        }
        settingsRepository.setBuiltinChannelsInitialized(true)
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
        val channel = channelDao.getChannel(channelId)
            ?: return@withContext Result.failure(IllegalArgumentException("频道不存在"))
        if (BuiltinChannelType.fromUrl(channel.url) != null) {
            return@withContext Result.success(Unit)
        }

        runCatching {
            val fetchedAt = System.currentTimeMillis()
            val parsed = fetchService.fetchChannel(channel.url)
            val baseLink = parsed.link?.trim()?.ifEmpty { null } ?: channel.url
            val useOriginalContent = channel.useOriginalContent
            if (useOriginalContent) {
                DebugLogBuffer.log(
                    "orig",
                    "refresh channelId=$channelId items=${parsed.items.size} base=$baseLink"
                )
            }
            val items = parsed.items.map { item ->
                val entity = parseService.toEntityFromParsedItem(
                    item = item,
                    channelId = channelId,
                    isRead = false,
                    fetchedAt = fetchedAt
                )
                if (useOriginalContent) {
                    val fallbackDescription = if (entity.description.isNullOrBlank()) {
                        entity.content
                    } else {
                        entity.description
                    }
                    val summary = entity.summary?.takeIf { it.isNotBlank() } ?: "暂无摘要"
                    entity.copy(
                        description = fallbackDescription,
                        content = null,
                        summary = summary
                    )
                } else {
                    entity
                }
            }
            if (useOriginalContent) {
                DebugLogBuffer.log(
                    "orig",
                    "store total=${items.size} withContent=${items.count { it.content != null }} refreshAll=$refreshAll"
                )
            }
            if (items.isNotEmpty()) {
                val insertResults = itemDao.insertItems(items)
                if (refreshAll && !useOriginalContent) {
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
            channelDao.updateChannel(channel.copy(
                title = parseService.channelTitle(parsed, channel.url),
                description = parsed.description?.trim()?.ifEmpty { null },
                imageUrl = parsed.image?.url?.trim()?.ifEmpty { null },
                lastFetchedAt = fetchedAt
            ))
            trimCacheToLimit()
        }.mapError()
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

    override fun requestOriginalContent(itemId: Long) {
        if (itemId <= 0L) return
        if (originalContentItemJobs.containsKey(itemId)) return
        val job = appScope.launch(Dispatchers.IO) {
            val item = itemDao.getItem(itemId) ?: return@launch
            if (!item.content.isNullOrBlank()) return@launch
            val channel = channelDao.getChannel(item.channelId) ?: return@launch
            if (!channel.useOriginalContent) return@launch
            if (item.summary.isNullOrBlank()) {
                itemDao.updatePreview(item.id, "暂无摘要", null)
            }
            val baseLink = channel.url
            val originalContent = readableService.fetchOriginalContent(item.link, baseLink)
            val contentOverride = originalContent ?: buildOriginalFallbackContent(item)
            val contentSizeBytes = estimateContentSize(
                title = item.title,
                description = item.description,
                content = contentOverride,
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
            val update = PendingOriginalUpdate(item.dedupKey, contentOverride, contentSizeBytes)
            if (pausedOriginalChannels.contains(item.channelId)) {
                enqueueOriginalUpdate(item.channelId, update)
            } else {
                itemDao.updateOriginalContentByDedupKey(
                    channelId = item.channelId,
                    dedupKey = update.dedupKey,
                    content = update.content,
                    contentSizeBytes = update.contentSizeBytes
                )
            }
        }
        originalContentItemJobs[itemId] = job
        job.invokeOnCompletion { originalContentItemJobs.remove(itemId) }
    }

    override fun requestOriginalContents(itemIds: List<Long>) {
        itemIds.forEach { requestOriginalContent(it) }
    }

    override fun setOriginalContentUpdatesPaused(channelId: Long, paused: Boolean) {
        if (channelId <= 0L) return
        if (paused) {
            pausedOriginalChannels.add(channelId)
        } else {
            pausedOriginalChannels.remove(channelId)
            flushOriginalUpdates(channelId)
        }
    }

    override suspend fun markItemRead(itemId: Long) {
        withContext(Dispatchers.IO) {
            itemDao.markRead(itemId)
        }
    }

    override suspend fun updateItemReadingProgress(itemId: Long, progress: Float) {
        withContext(Dispatchers.IO) {
            val clamped = progress.coerceIn(0f, 1f)
            itemDao.updateReadingProgress(itemId, clamped)
        }
    }

    override suspend fun toggleFavorite(itemId: Long): Result<SavedState> =
        toggleSaved(itemId, SaveType.FAVORITE)

    override suspend fun toggleWatchLater(itemId: Long): Result<SavedState> =
        toggleSaved(itemId, SaveType.WATCH_LATER)

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
        val insertId = itemDao.insertItems(listOf(entity)).firstOrNull() ?: -1L
        val itemId = if (insertId > 0) {
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
        } ?: return@withContext Result.failure(IllegalStateException("保存失败"))

        val existing = savedEntryDao.getByItemId(itemId)
        val hasType = existing.any { it.saveType == saveType.name }
        if (hasType == saved) {
            return@withContext Result.success(buildSavedState(existing))
        }
        toggleSaved(itemId, saveType)
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
            itemDao.markReadByChannel(channelId)
        }
    }

    override suspend fun moveChannelToTop(channelId: Long) {
        withContext(Dispatchers.IO) {
            val channel = channelDao.getChannel(channelId) ?: return@withContext
            channelDao.updateChannel(channel.copy(sortOrder = System.currentTimeMillis()))
        }
    }

    override suspend fun setChannelPinned(channelId: Long, pinned: Boolean) {
        withContext(Dispatchers.IO) {
            val channel = channelDao.getChannel(channelId) ?: return@withContext
            val newOrder = if (pinned) System.currentTimeMillis() else channel.sortOrder
            channelDao.updateChannel(channel.copy(isPinned = pinned, sortOrder = newOrder))
        }
    }

    override suspend fun setChannelOriginalContent(channelId: Long, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val channel = channelDao.getChannel(channelId) ?: return@withContext
            if (channel.useOriginalContent == enabled) return@withContext
            channelDao.updateChannel(channel.copy(useOriginalContent = enabled))
        }
    }

    override suspend fun deleteChannel(channelId: Long) {
        withContext(Dispatchers.IO) {
            offlineStore.deleteMediaForChannel(channelId)
            channelDao.deleteChannel(channelId)
        }
    }

    override suspend fun trimCacheToLimit() {
        withContext(Dispatchers.IO) {
            val limit = settingsRepository.cacheLimitBytes.first()
            enforceCacheLimit(limit)
        }
    }

    private fun schedulePreviewUpdates(items: List<RssItemEntity>) {
        items.forEach { schedulePreviewUpdate(it) }
    }

    private fun schedulePreviewUpdate(item: RssItemEntity) {
        if (!needsPreviewUpdate(item)) return
        if (!previewJobs.add(item.id)) return
        appScope.launch(Dispatchers.Default) {
            try {
                val preview = RssPreviewFormatter.buildPreview(
                    description = item.description,
                    content = item.content,
                    imageUrl = item.imageUrl,
                    link = item.link
                )
                itemDao.updatePreview(item.id, preview.summary, preview.previewImageUrl)
            } finally {
                previewJobs.remove(item.id)
            }
        }
    }

    private fun needsPreviewUpdate(item: RssItemEntity): Boolean {
        val missingSummary = item.summary.isNullOrBlank()
        val missingPreview = item.previewImageUrl.isNullOrBlank() && item.imageUrl.isNullOrBlank()
        return missingSummary || missingPreview
    }

    private fun enqueueOriginalUpdate(channelId: Long, update: PendingOriginalUpdate) {
        val pending = pendingOriginalUpdates.getOrPut(channelId) { ConcurrentHashMap() }
        pending[update.dedupKey] = update
    }

    private fun flushOriginalUpdates(channelId: Long) {
        val pending = pendingOriginalUpdates.remove(channelId) ?: return
        if (pending.isEmpty()) return
        appScope.launch(Dispatchers.IO) {
            pending.values.forEach { update ->
                itemDao.updateOriginalContentByDedupKey(
                    channelId = channelId,
                    dedupKey = update.dedupKey,
                    content = update.content,
                    contentSizeBytes = update.contentSizeBytes
                )
            }
        }
    }

    private suspend fun enforceCacheLimit(limitBytes: Long) {
        if (limitBytes <= 0) return
        var total = itemDao.getTotalCacheBytes() ?: 0L
        if (total <= limitBytes) return

        val oldest = itemDao.loadOldestItemsExcludingSaved()
        val toDelete = mutableListOf<Long>()
        for (item in oldest) {
            if (total <= limitBytes) break
            total -= item.contentSizeBytes
            toDelete.add(item.id)
        }
        if (toDelete.isNotEmpty()) {
            itemDao.deleteByIds(toDelete)
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
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
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
        val host = runCatching { Uri.parse(url).host }.getOrNull()
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
            if (hasType) {
                savedEntryDao.delete(itemId, saveType.name)
            } else {
                savedEntryDao.insert(
                    SavedEntryEntity(
                        itemId = itemId,
                        saveType = saveType.name,
                        createdAt = System.currentTimeMillis()
                    )
                )
                runCatching { offlineStore.downloadMediaForItem(item) }
            }
            if (savedEntryDao.countByItemId(itemId) == 0) {
                offlineStore.deleteMediaForItem(itemId)
            }
            val updated = savedEntryDao.getByItemId(itemId)
            Result.success(buildSavedState(updated))
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
        link: String?,
        imageUrl: String?,
        audioUrl: String?,
        videoUrl: String?
    ): Long {
        var total = 0L
        val parts = listOf(title, description, content, link, imageUrl, audioUrl, videoUrl)
        for (part in parts) {
            if (!part.isNullOrEmpty()) {
                total += part.toByteArray(Charsets.UTF_8).size
            }
        }
        return total
    }

    private fun buildSavedState(entries: List<SavedEntryEntity>): SavedState {
        val types = entries.map { it.saveType }.toSet()
        return SavedState(
            isFavorite = SaveType.FAVORITE.name in types,
            isWatchLater = SaveType.WATCH_LATER.name in types
        )
    }

    private fun SavedRssItem.toModel(): SavedItem = SavedItem(
        item = item.toModel(),
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
        unreadCount = unreadCount
    )

    private fun RssItemEntity.toModel(): RssItem = RssItem(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        content = content,
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
}

private data class PendingOriginalUpdate(
    val dedupKey: String,
    val content: String,
    val contentSizeBytes: Long
)
