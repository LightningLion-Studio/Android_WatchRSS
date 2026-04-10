package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewCache
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceKind
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManagerContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowCacheCoordinatorContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStoreContract
import com.lightningstudio.watchrss.data.douyin.DOUYIN_PRELOAD_LOAD_MORE_THRESHOLD
import com.lightningstudio.watchrss.data.douyin.DOUYIN_RECENT_WINDOW_SIZE
import com.lightningstudio.watchrss.data.douyin.NoOpDouyinRecentWindowCacheCoordinator
import com.lightningstudio.watchrss.data.douyin.NoOpDouyinRecentWindowStore
import com.lightningstudio.watchrss.data.douyin.buildDouyinRecentWindow
import com.lightningstudio.watchrss.data.douyin.formatDouyinError
import com.lightningstudio.watchrss.data.douyin.mergeDouyinBootstrapItems
import com.lightningstudio.watchrss.data.douyin.resolveDouyinPlaybackAnchorAwemeId
import com.lightningstudio.watchrss.data.settings.DEFAULT_DOUYIN_VIDEO_CODEC_PREFERENCE
import com.lightningstudio.watchrss.data.settings.DouyinVideoCodecPreference
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DouyinFeedUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<DouyinStreamItem> = emptyList(),
    val hasMore: Boolean = true,
    val currentPage: Int = 0,
    val playHeaders: Map<String, String> = emptyMap(),
    val localPlayPaths: Map<String, String> = emptyMap(),
    val codecPreference: DouyinVideoCodecPreference = DEFAULT_DOUYIN_VIDEO_CODEC_PREFERENCE,
    val message: String? = null,
    val showTitlePage: Boolean = true
)

private data class DouyinBootstrapState(
    val items: List<DouyinStreamItem>,
    val localPaths: Map<String, String>,
    val savedAtMs: Long,
    val resumeAnchorAwemeId: String?,
    val nextCursor: String?,
    val hasMore: Boolean
)

class DouyinFeedViewModel(
    private val repository: DouyinRepositoryContract,
    private val preloadManager: DouyinPreloadManagerContract,
    private val watchHistoryStore: DouyinWatchHistoryStoreContract,
    private val feedCacheStore: DouyinFeedCacheStoreContract,
    private val recentWindowStore: DouyinRecentWindowStoreContract = NoOpDouyinRecentWindowStore,
    private val recentWindowCacheCoordinator: DouyinRecentWindowCacheCoordinatorContract =
        NoOpDouyinRecentWindowCacheCoordinator,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
) : ViewModel() {
    private val _uiState = MutableStateFlow(DouyinFeedUiState())
    val uiState: StateFlow<DouyinFeedUiState> = _uiState

    private var nextCursor: String? = null
    private var isRequestingMore: Boolean = false
    private val awemeIdSet = linkedSetOf<String>()
    private val playbackRefreshJobs = linkedMapOf<String, Job>()

    init {
        viewModelScope.launch {
            repository.observeVideoCodecPreference().collectLatest { preference ->
                _uiState.update { it.copy(codecPreference = preference) }
            }
        }
        viewModelScope.launch {
            val loggedIn = repository.isLoggedIn()
            if (loggedIn) {
                val headers = repository.buildPlayHeaders()
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        playHeaders = headers,
                        message = null
                    )
                }
                val hasBootstrapItems = restoreEntryPlayback(headers)
                if (!hasBootstrapItems || shouldRefreshBootstrapWindow() || shouldPrimeBootstrapCursor()) {
                    loadInitial()
                }
            } else {
                _uiState.update { it.copy(isLoggedIn = false) }
            }
        }
    }

    fun applyCookie(rawCookie: String) {
        viewModelScope.launch {
            val result = repository.applyCookieHeader(rawCookie)
            if (result.isSuccess) {
                val headers = repository.buildPlayHeaders()
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        playHeaders = headers,
                        message = null
                    )
                }
                loadCachedBootstrap(headers)
                loadInitial()
            } else {
                _uiState.update {
                    it.copy(message = result.exceptionOrNull()?.message ?: "登录失败")
                }
            }
        }
    }

    fun loadCachedFeedForAppLaunch() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val headers = repository.buildPlayHeaders()
            _uiState.update {
                it.copy(
                    isLoggedIn = true,
                    playHeaders = headers,
                    message = null
                )
            }
            val hasBootstrapItems = restoreEntryPlayback(headers)
            if (!hasBootstrapItems || shouldRefreshBootstrapWindow() || shouldPrimeBootstrapCursor()) {
                loadInitial()
            }
        }
    }

    fun loadInitial() {
        loadInitial(replaceExistingFeed = false)
    }

    fun refreshTitlePageFeed() {
        loadInitial(replaceExistingFeed = true)
    }

    private fun loadInitial(replaceExistingFeed: Boolean) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val headers = repository.buildPlayHeaders()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    playHeaders = headers,
                    message = null
                )
            }
            awemeIdSet.clear()
            nextCursor = null
            val result = repository.fetchFeedPage(cursor = null, count = PAGE_SIZE)
            if (result.isSuccess) {
                val page = result.data
                val mapped = mapToStreamItems(page?.items.orEmpty())
                val latestState = _uiState.value
                val currentVideoIndex = latestState.currentPage - 1
                val preservePrefix = if (
                    !replaceExistingFeed &&
                    currentVideoIndex >= 0 &&
                    latestState.items.isNotEmpty()
                ) {
                    latestState.items.take((currentVideoIndex + 1).coerceAtMost(latestState.items.size))
                } else {
                    emptyList()
                }
                val mergedItems = mergeFreshItemsWithPreservedPrefix(
                    preservedPrefix = preservePrefix,
                    freshItems = mapped
                )
                mergedItems.forEach { awemeIdSet.add(it.awemeId) }
                nextCursor = page?.nextCursor
                val hasMore = page?.hasMore ?: false
                if (replaceExistingFeed) {
                    withContext(storageDispatcher) {
                        recentWindowStore.clear()
                        DouyinPlaybackPreviewCache.clearAll()
                    }
                }
                val localPaths = withContext(storageDispatcher) {
                    preloadManager.resolveLocalPaths(mergedItems.map { it.awemeId })
                }
                val adjustedCurrentPage = when {
                    replaceExistingFeed -> 0
                    latestState.showTitlePage -> latestState.currentPage.coerceAtMost(mergedItems.size)
                    mergedItems.isEmpty() -> 0
                    latestState.currentPage <= 0 -> 1
                    else -> latestState.currentPage.coerceAtMost(mergedItems.size)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = mergedItems,
                        hasMore = hasMore,
                        playHeaders = headers,
                        localPlayPaths = localPaths,
                        showTitlePage = latestState.showTitlePage,
                        currentPage = adjustedCurrentPage,
                        message = null
                    )
                }
                maybePersistRecentWindow(
                    items = mergedItems,
                    targetPage = adjustedCurrentPage,
                    headers = headers,
                    reason = "load_initial"
                )
                cacheBootstrapPage(
                    items = mapped,
                    nextCursorSnapshot = nextCursor,
                    hasMore = hasMore
                )
                schedulePreload(mergedItems, headers)
            } else {
                handleFeedFailure(
                    code = result.code,
                    message = result.message,
                    isLoadingMore = false
                )
            }
        }
    }

    fun refreshPlaybackSource(
        awemeId: String,
        failedSource: DouyinPlaybackSourceKind
    ) {
        val normalizedAwemeId = awemeId.trim()
        if (normalizedAwemeId.isEmpty()) return
        if (playbackRefreshJobs[normalizedAwemeId]?.isActive == true) return

        playbackRefreshJobs[normalizedAwemeId] = viewModelScope.launch {
            val localFallbackAvailable = _uiState.value.items.any {
                it.awemeId == normalizedAwemeId && it.playUrl.isNotBlank()
            }
            if (failedSource == DouyinPlaybackSourceKind.LOCAL) {
                preloadManager.invalidate(normalizedAwemeId)
                _uiState.update { state ->
                    state.copy(localPlayPaths = state.localPlayPaths - normalizedAwemeId)
                }
            }

            val result = repository.fetchVideo(normalizedAwemeId)
            if (result.isSuccess) {
                when (val content = result.data) {
                    is DouyinContent.Video -> {
                        val updated = updatePlaybackSource(normalizedAwemeId, content)
                        if (updated) {
                            val currentState = _uiState.value
                            cacheBootstrapPage(
                                items = currentState.items,
                                nextCursorSnapshot = nextCursor,
                                hasMore = currentState.hasMore
                            )
                            schedulePreload(currentState.items, currentState.playHeaders)
                        }
                    }
                    is DouyinContent.Note -> {
                        _uiState.update {
                            it.copy(message = "当前内容暂无可播放视频")
                        }
                    }
                    null -> {
                        _uiState.update {
                            it.copy(message = "加载失败")
                        }
                    }
                }
            } else if (result.code == DouyinErrorCodes.NOT_LOGGED_IN) {
                repository.clearCookie()
                _uiState.update {
                    it.copy(
                        isLoggedIn = false,
                        items = emptyList(),
                        localPlayPaths = emptyMap(),
                        message = "需要登录"
                    )
                }
            } else {
                val shouldSuppressMessage =
                    failedSource == DouyinPlaybackSourceKind.LOCAL && localFallbackAvailable
                if (!shouldSuppressMessage) {
                    _uiState.update {
                        it.copy(message = formatDouyinError(result.code, result.message))
                    }
                }
            }

            playbackRefreshJobs.remove(normalizedAwemeId)
        }
    }

    private suspend fun loadCachedBootstrap(headers: Map<String, String>): Long {
        val bootstrapState = readBootstrapState()
        seedFeedWindow(bootstrapState.items)
        nextCursor = bootstrapState.nextCursor
        _uiState.update {
            it.copy(
                items = bootstrapState.items,
                hasMore = bootstrapState.hasMore,
                playHeaders = headers,
                localPlayPaths = bootstrapState.localPaths
            )
        }
        return bootstrapState.savedAtMs
    }

    private suspend fun restoreEntryPlayback(headers: Map<String, String>): Boolean {
        val bootstrapState = readBootstrapState()
        val hasBootstrapItems = bootstrapState.items.isNotEmpty()
        val latestAwemeId = withContext(storageDispatcher) {
            watchHistoryStore.readHistory()
                .firstOrNull()
                ?.awemeId
                ?.takeIf { it.isNotBlank() }
        }
        val resumeAnchorPage = resolvePageForAwemeId(
            awemeId = bootstrapState.resumeAnchorAwemeId,
            items = bootstrapState.items
        )
        val targetPage = when {
            !latestAwemeId.isNullOrBlank() -> {
                resolveNextPageAfter(
                    awemeId = latestAwemeId,
                    items = bootstrapState.items
                ) ?: resumeAnchorPage ?: if (bootstrapState.items.isNotEmpty()) 1 else 0
            }
            else -> resumeAnchorPage ?: 0
        }
        AppLogger.d(
            TAG,
            "restore bootstrap items=${bootstrapState.items.size} latest=$latestAwemeId " +
                "resume=${bootstrapState.resumeAnchorAwemeId} targetPage=$targetPage " +
                "nextCursor=${bootstrapState.nextCursor} hasMore=${bootstrapState.hasMore}"
        )
        seedFeedWindow(bootstrapState.items)
        nextCursor = bootstrapState.nextCursor
        _uiState.update {
            it.copy(
                items = bootstrapState.items,
                hasMore = bootstrapState.hasMore,
                playHeaders = headers,
                localPlayPaths = bootstrapState.localPaths,
                currentPage = targetPage,
                showTitlePage = true,
                message = null
            )
        }
        maybePersistRecentWindow(
            items = bootstrapState.items,
            targetPage = targetPage,
            headers = headers,
            reason = "restore_entry"
        )
        schedulePreload(bootstrapState.items, headers)
        return hasBootstrapItems
    }

    private fun shouldRefreshBootstrapWindow(): Boolean {
        val state = _uiState.value
        if (state.items.size < MIN_BOOTSTRAP_ITEMS_BEFORE_NETWORK_REFRESH) {
            return true
        }
        if (state.currentPage <= 0) {
            return false
        }
        if (state.items.size < BOOTSTRAP_ITEMS) {
            return false
        }
        val remainingForwardItems = (state.items.size - state.currentPage).coerceAtLeast(0)
        return remainingForwardItems < MIN_FORWARD_ITEMS_BEFORE_NETWORK_REFRESH
    }

    private fun shouldPrimeBootstrapCursor(): Boolean {
        val state = _uiState.value
        return state.items.isNotEmpty() && state.hasMore && nextCursor.isNullOrBlank()
    }

    private suspend fun readBootstrapState(): DouyinBootstrapState = withContext(storageDispatcher) {
        val feedSnapshot = feedCacheStore.readSnapshot(limit = BOOTSTRAP_ITEMS)
        val pinnedSnapshotItems = DouyinPlaybackPreviewCache.restorePinnedItems()
            .map { it.copy(sourceOrigin = DouyinSourceOrigin.BOOTSTRAP_CACHE) }
        val cachedItems = feedSnapshot.items
            .map { it.copy(sourceOrigin = DouyinSourceOrigin.BOOTSTRAP_CACHE) }
        val recentWindowSnapshot = recentWindowStore.readSnapshot(limit = DOUYIN_RECENT_WINDOW_SIZE)
        val recentItems = recentWindowSnapshot.items
            .map { it.copy(sourceOrigin = DouyinSourceOrigin.BOOTSTRAP_CACHE) }
        val mergedBootstrapItems = mergeDouyinBootstrapItems(
            feedItems = cachedItems,
            recentItems = recentItems,
            limit = BOOTSTRAP_ITEMS + DOUYIN_RECENT_WINDOW_SIZE
        )
        val bootstrapItems = buildList {
            val seenAwemeIds = linkedSetOf<String>()
            pinnedSnapshotItems.forEach { item ->
                if (seenAwemeIds.add(item.awemeId)) {
                    add(item)
                }
            }
            mergedBootstrapItems.forEach { item ->
                if (seenAwemeIds.add(item.awemeId)) {
                    add(item)
                }
            }
        }.take(BOOTSTRAP_ITEMS + DOUYIN_PLAYBACK_SNAPSHOT_STARTUP_COUNT)
        AppLogger.d(
            TAG,
            "read bootstrap pinned=${pinnedSnapshotItems.size} feed=${cachedItems.size} recent=${recentItems.size} " +
                "merged=${bootstrapItems.size} anchor=${recentWindowSnapshot.anchorAwemeId}"
        )
        if (bootstrapItems.isEmpty()) {
            return@withContext DouyinBootstrapState(
                items = emptyList(),
                localPaths = emptyMap(),
                savedAtMs = maxOf(feedSnapshot.savedAtMs, recentWindowSnapshot.savedAtMs),
                resumeAnchorAwemeId = recentWindowSnapshot.anchorAwemeId,
                nextCursor = feedSnapshot.nextCursor,
                hasMore = feedSnapshot.hasMore
            )
        }

        val localPaths = preloadManager.resolveLocalPaths(bootstrapItems.map { it.awemeId })
        val playablePaths = localPaths.filterKeys { awemeId -> bootstrapItems.any { it.awemeId == awemeId } }
        DouyinBootstrapState(
            items = bootstrapItems,
            localPaths = playablePaths,
            savedAtMs = maxOf(feedSnapshot.savedAtMs, recentWindowSnapshot.savedAtMs),
            resumeAnchorAwemeId = recentWindowSnapshot.anchorAwemeId,
            nextCursor = feedSnapshot.nextCursor,
            hasMore = feedSnapshot.hasMore
        )
    }

    fun onPageSettled(page: Int) {
        val safePage = page.coerceAtLeast(0)
        val previousState = _uiState.value
        val deferredTargetPage = if (
            previousState.showTitlePage &&
            safePage > 0 &&
            previousState.currentPage > safePage
        ) {
            previousState.currentPage
        } else {
            null
        }
        val resolvedPage = when {
            safePage > 0 && deferredTargetPage != null -> deferredTargetPage
            safePage > 0 -> safePage
            else -> previousState.currentPage
        }
        _uiState.update {
            it.copy(
                currentPage = resolvedPage,
                showTitlePage = safePage <= 0
            )
        }
        if (safePage <= 0 || deferredTargetPage != null) return

        val itemIndex = resolvedPage - 1
        val currentItems = _uiState.value.items
        val current = currentItems.getOrNull(itemIndex) ?: return
        val playHeaders = _uiState.value.playHeaders
        viewModelScope.launch(storageDispatcher) {
            watchHistoryStore.markWatched(current)
            persistRecentWindow(
                items = currentItems,
                anchorIndex = itemIndex,
                headers = playHeaders,
                reason = "page_settled"
            )
        }

        schedulePreload(currentItems, playHeaders)

        if (itemIndex >= currentItems.size - DOUYIN_PRELOAD_LOAD_MORE_THRESHOLD) {
            loadMore()
        }
    }

    fun enterVideoFlow(targetAwemeId: String? = null) {
        _uiState.update {
            val targetPageForAwemeId = resolvePageForAwemeId(
                awemeId = targetAwemeId,
                items = it.items
            )
            val targetPage = when {
                targetPageForAwemeId != null -> targetPageForAwemeId
                it.currentPage > 0 -> it.currentPage.coerceAtMost(it.items.size)
                it.items.isNotEmpty() -> 1
                else -> 0
            }
            it.copy(
                showTitlePage = false,
                currentPage = targetPage
            )
        }
    }

    fun loadMoreForList() {
        loadMore()
    }

    private fun loadMore() {
        val state = _uiState.value
        if (isRequestingMore || !state.hasMore || state.isLoadingMore || state.isLoading) return
        isRequestingMore = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = repository.fetchFeedPage(cursor = nextCursor, count = PAGE_SIZE)
            if (result.isSuccess) {
                val page = result.data
                val incoming = mapToStreamItems(page?.items.orEmpty())
                    .filter { awemeIdSet.add(it.awemeId) }
                nextCursor = page?.nextCursor ?: nextCursor
                val merged = _uiState.value.items + incoming
                val localPaths = withContext(storageDispatcher) {
                    preloadManager.resolveLocalPaths(merged.map { it.awemeId })
                }
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMore = page?.hasMore ?: false,
                        items = merged,
                        localPlayPaths = localPaths
                    )
                }
                schedulePreload(merged, _uiState.value.playHeaders)
            } else {
                handleFeedFailure(
                    code = result.code,
                    message = result.message,
                    isLoadingMore = true
                )
            }
            isRequestingMore = false
        }
    }

    private fun handleFeedFailure(
        code: Int,
        message: String?,
        isLoadingMore: Boolean
    ) {
        if (code == DouyinErrorCodes.NOT_LOGGED_IN) {
            viewModelScope.launch {
                repository.clearCookie()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isLoggedIn = false,
                        items = emptyList(),
                        localPlayPaths = emptyMap()
                    )
                }
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                message = formatDouyinError(code, message)
            )
        }
    }

    private fun mapToStreamItems(
        items: List<com.lightningstudio.watchrss.sdk.douyin.DouyinVideo>,
        resolvedAtMs: Long = System.currentTimeMillis(),
        sourceOrigin: DouyinSourceOrigin = DouyinSourceOrigin.NETWORK_FEED
    ): List<DouyinStreamItem> {
        return items.mapNotNull { video ->
            val awemeId = video.awemeId?.trim().orEmpty()
            val playUrl = video.playUrl?.trim().orEmpty()
            if (awemeId.isEmpty() || playUrl.isEmpty()) {
                null
            } else {
                DouyinStreamItem(
                    awemeId = awemeId,
                    playUrl = playUrl,
                    coverUrl = video.coverUrl?.takeIf { it.isNotBlank() },
                    title = video.desc?.takeIf { it.isNotBlank() },
                    author = video.authorName?.takeIf { it.isNotBlank() },
                    likeCount = video.likeCount,
                    playUrlResolvedAtMs = resolvedAtMs,
                    sourceOrigin = sourceOrigin,
                    durationMs = video.duration.toLong().coerceAtLeast(0L),
                    variants = video.variants
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun schedulePreload(items: List<DouyinStreamItem>, headers: Map<String, String>) {
        if (items.isEmpty()) return
        AppLogger.d(
            TAG,
            "skip full-file preload items=${items.size} anchor=${
                resolveDouyinPlaybackAnchorAwemeId(items = items, currentPage = _uiState.value.currentPage)
            }"
        )
    }

    private fun updatePlaybackSource(
        awemeId: String,
        content: DouyinContent.Video
    ): Boolean {
        val resolvedAtMs = System.currentTimeMillis()
        var updated = false
        _uiState.update { state ->
            val refreshedItems = state.items.map { item ->
                if (item.awemeId != awemeId) {
                    item
                } else {
                    updated = true
                    item.copy(
                        playUrl = content.playUrl.trim(),
                        coverUrl = content.coverUrl.takeIf { it.isNotBlank() } ?: item.coverUrl,
                        title = content.desc.takeIf { it.isNotBlank() } ?: item.title,
                        author = content.authorName.takeIf { it.isNotBlank() } ?: item.author,
                        likeCount = content.diggCount,
                        playUrlResolvedAtMs = resolvedAtMs,
                        sourceOrigin = DouyinSourceOrigin.VIDEO_REFRESH,
                        variants = content.variants
                    )
                }
            }
            if (!updated) state else state.copy(items = refreshedItems)
        }
        return updated
    }

    private fun cacheBootstrapPage(
        items: List<DouyinStreamItem>,
        nextCursorSnapshot: String?,
        hasMore: Boolean
    ) {
        if (items.isEmpty()) return
        val bootstrapItems = items.take(PAGE_SIZE)
        viewModelScope.launch(storageDispatcher) {
            feedCacheStore.save(
                items = bootstrapItems,
                nextCursor = nextCursorSnapshot,
                hasMore = hasMore
            )
        }
    }

    private fun mergeFreshItemsWithPreservedPrefix(
        preservedPrefix: List<DouyinStreamItem>,
        freshItems: List<DouyinStreamItem>
    ): List<DouyinStreamItem> {
        if (preservedPrefix.isEmpty()) return freshItems

        val freshById = freshItems.associateBy { it.awemeId }
        val mergedPrefix = preservedPrefix.map { preserved ->
            freshById[preserved.awemeId] ?: preserved
        }
        val preserveIds = mergedPrefix.map { it.awemeId }.toHashSet()
        return mergedPrefix + freshItems.filterNot { preserveIds.contains(it.awemeId) }
    }

    private fun persistRecentWindow(
        items: List<DouyinStreamItem>,
        anchorIndex: Int,
        headers: Map<String, String>,
        reason: String
    ) {
        val recentWindow = buildDouyinRecentWindow(items, anchorIndex)
        if (recentWindow.isEmpty()) return
        val anchorAwemeId = items.getOrNull(anchorIndex)?.awemeId ?: return
        val mergedBootstrapItems = mergeDouyinBootstrapItems(
            feedItems = feedCacheStore.readSnapshot(limit = BOOTSTRAP_ITEMS).items,
            recentItems = recentWindow,
            limit = BOOTSTRAP_ITEMS
        )
        AppLogger.d(
            TAG,
            "persist recent window reason=$reason anchor=$anchorAwemeId items=${recentWindow.size}"
        )
        recentWindowStore.saveWindow(
            items = recentWindow,
            anchorAwemeId = anchorAwemeId
        )
        if (mergedBootstrapItems.isNotEmpty()) {
            val feedSnapshot = feedCacheStore.readSnapshot(limit = BOOTSTRAP_ITEMS)
            feedCacheStore.save(
                items = mergedBootstrapItems,
                nextCursor = feedSnapshot.nextCursor,
                hasMore = feedSnapshot.hasMore
            )
        }
    }

    private fun maybePersistRecentWindow(
        items: List<DouyinStreamItem>,
        targetPage: Int,
        headers: Map<String, String>,
        reason: String
    ) {
        if (targetPage <= 0) return
        val anchorIndex = targetPage - 1
        viewModelScope.launch(storageDispatcher) {
            persistRecentWindow(
                items = items,
                anchorIndex = anchorIndex,
                headers = headers,
                reason = reason
            )
        }
    }

    private fun resolveNextPageAfter(
        awemeId: String,
        items: List<DouyinStreamItem>
    ): Int? {
        val currentIndex = items.indexOfFirst { it.awemeId == awemeId }
        if (currentIndex < 0) return null
        val nextIndex = currentIndex + 1
        if (nextIndex >= items.size) return null
        return nextIndex + 1
    }

    private fun resolvePageForAwemeId(
        awemeId: String?,
        items: List<DouyinStreamItem>
    ): Int? {
        val normalizedAwemeId = awemeId?.trim().orEmpty()
        if (normalizedAwemeId.isEmpty()) return null
        val itemIndex = items.indexOfFirst { it.awemeId == normalizedAwemeId }
        if (itemIndex < 0) return null
        return itemIndex + 1
    }

    private fun seedFeedWindow(items: List<DouyinStreamItem>) {
        awemeIdSet.clear()
        items.forEach { awemeIdSet.add(it.awemeId) }
        nextCursor = null
    }

    companion object {
        private const val TAG = "DouyinFeedVM"
        private const val PAGE_SIZE = 16
        private const val BOOTSTRAP_ITEMS = PAGE_SIZE
        private const val DOUYIN_PLAYBACK_SNAPSHOT_STARTUP_COUNT = 2
        private const val MIN_BOOTSTRAP_ITEMS_BEFORE_NETWORK_REFRESH = 3
        private const val MIN_FORWARD_ITEMS_BEFORE_NETWORK_REFRESH = 3
    }
}
