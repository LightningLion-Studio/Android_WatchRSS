package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewCache
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceKind
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackRefreshOutcome
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackRefreshTrigger
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceCoordinatorContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceRefreshEvent
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackTransportContract
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
import com.lightningstudio.watchrss.data.douyin.NoOpDouyinPlaybackTransport
import com.lightningstudio.watchrss.data.douyin.NoOpDouyinPlaybackSourceCoordinator
import com.lightningstudio.watchrss.data.douyin.buildDouyinRecentWindow
import com.lightningstudio.watchrss.data.douyin.dropDouyinItemsBeforeAwemeId
import com.lightningstudio.watchrss.data.douyin.formatDouyinError
import com.lightningstudio.watchrss.data.douyin.isDouyinPlayUrlExpired
import com.lightningstudio.watchrss.data.douyin.mergeDouyinBootstrapItems
import com.lightningstudio.watchrss.data.douyin.mergeDouyinPrioritizedItems
import com.lightningstudio.watchrss.data.douyin.resolveDouyinPlaybackAnchorAwemeId
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
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
    val proxyPlayUris: Map<String, String> = emptyMap(),
    val playbackRefreshEvents: Map<String, DouyinPlaybackSourceRefreshEvent> = emptyMap(),
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
    private val playbackTransport: DouyinPlaybackTransportContract = NoOpDouyinPlaybackTransport,
    private val playbackSourceCoordinator: DouyinPlaybackSourceCoordinatorContract =
        NoOpDouyinPlaybackSourceCoordinator,
    private val watchHistoryStore: DouyinWatchHistoryStoreContract,
    private val feedCacheStore: DouyinFeedCacheStoreContract,
    private val recentWindowStore: DouyinRecentWindowStoreContract = NoOpDouyinRecentWindowStore,
    private val recentWindowCacheCoordinator: DouyinRecentWindowCacheCoordinatorContract =
        NoOpDouyinRecentWindowCacheCoordinator,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val resumeToVideoFlowOnEntry: Boolean = false,
    private val resumeAwemeIdOnEntry: String? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(DouyinFeedUiState())
    val uiState: StateFlow<DouyinFeedUiState> = _uiState

    private var nextCursor: String? = null
    private var isRequestingMore: Boolean = false
    private var sourceItems: List<DouyinStreamItem> = emptyList()
    private var sourceLocalPlayPaths: Map<String, String> = emptyMap()
    private val awemeIdSet = linkedSetOf<String>()
    private val sessionQuarantinedAwemeIds = linkedSetOf<String>()
    private val playbackRefreshJobs = linkedMapOf<String, Job>()
    private val handledPlaybackRefreshEventIds = linkedSetOf<Long>()

    init {
        viewModelScope.launch {
            playbackSourceCoordinator.updates.collect(::applyPlaybackSourceRefreshEvent)
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
                val hasBootstrapItems = restoreEntryPlayback(
                    headers = headers,
                    resumeToVideoFlow = resumeToVideoFlowOnEntry,
                    resumeAwemeId = resumeAwemeIdOnEntry
                )
                if (shouldLoadInitialAfterRestore(hasBootstrapItems, resumeToVideoFlowOnEntry)) {
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
            if (shouldLoadInitialAfterRestore(hasBootstrapItems, resumeToVideoFlow = false)) {
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
                replaceSourceState(items = mergedItems)
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
                replaceSourceState(
                    items = mergedItems,
                    localPlayPaths = localPaths
                )
                val visibleMergedItems = projectVisibleItems()
                val visibleLocalPaths = projectVisibleLocalPlayPaths(visibleMergedItems)
                val visibleProxyUris = projectVisibleProxyPlayUris(
                    visibleItems = visibleMergedItems,
                    headers = headers,
                    reason = "load_initial"
                )
                val adjustedCurrentPage = when {
                    replaceExistingFeed -> 0
                    latestState.showTitlePage -> latestState.currentPage.coerceAtMost(visibleMergedItems.size)
                    visibleMergedItems.isEmpty() -> 0
                    latestState.currentPage <= 0 -> 1
                    else -> latestState.currentPage.coerceAtMost(visibleMergedItems.size)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = visibleMergedItems,
                        hasMore = hasMore,
                        playHeaders = headers,
                        localPlayPaths = visibleLocalPaths,
                        proxyPlayUris = visibleProxyUris,
                        showTitlePage = if (replaceExistingFeed) true else latestState.showTitlePage,
                        currentPage = adjustedCurrentPage,
                        message = null
                    )
                }
                if (!latestState.showTitlePage) {
                    maybePersistRecentWindow(
                        visibleItems = visibleMergedItems,
                        sourceItems = sourceItems,
                        targetPage = adjustedCurrentPage,
                        headers = headers,
                        reason = "load_initial"
                    )
                }
                cacheBootstrapPage(
                    items = sourceItems,
                    nextCursorSnapshot = nextCursor,
                    hasMore = hasMore
                )
                schedulePreload(visibleMergedItems, headers)
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
        failedSource: DouyinPlaybackSourceKind,
        trigger: DouyinPlaybackRefreshTrigger = DouyinPlaybackRefreshTrigger.PLAYBACK_ERROR
    ) {
        val normalizedAwemeId = awemeId.trim()
        if (normalizedAwemeId.isEmpty()) return
        if (playbackRefreshJobs[normalizedAwemeId]?.isActive == true) return

        playbackRefreshJobs[normalizedAwemeId] = viewModelScope.launch {
            val localFallbackAvailable = sourceItems.any {
                it.awemeId == normalizedAwemeId && it.playUrl.isNotBlank()
            }
            if (failedSource == DouyinPlaybackSourceKind.LOCAL) {
                preloadManager.invalidate(normalizedAwemeId)
                sourceLocalPlayPaths = sourceLocalPlayPaths - normalizedAwemeId
                val visibleItems = projectVisibleItems()
                _uiState.update { state ->
                    state.copy(
                        localPlayPaths = projectVisibleLocalPlayPaths(visibleItems),
                        proxyPlayUris = projectVisibleProxyPlayUris(
                            visibleItems = visibleItems,
                            headers = state.playHeaders,
                            reason = "local_invalidate"
                        )
                    )
                }
            }

            val currentItem = sourceItems.firstOrNull { it.awemeId == normalizedAwemeId }
            val coordinatedEvent = currentItem?.let {
                playbackSourceCoordinator.refresh(item = it, trigger = trigger)
            }
            if (coordinatedEvent != null && coordinatedEvent.outcome != DouyinPlaybackRefreshOutcome.SKIPPED) {
                applyPlaybackSourceRefreshEvent(coordinatedEvent)
                if (coordinatedEvent.outcome == DouyinPlaybackRefreshOutcome.FAILURE) {
                    handlePlaybackRefreshFailure(
                        event = coordinatedEvent,
                        suppressMessage = failedSource == DouyinPlaybackSourceKind.LOCAL &&
                            localFallbackAvailable
                    )
                }
            } else {
                refreshPlaybackSourceDirect(
                    awemeId = normalizedAwemeId,
                    failedSource = failedSource,
                    trigger = trigger,
                    localFallbackAvailable = localFallbackAvailable
                )
            }

            playbackRefreshJobs.remove(normalizedAwemeId)
        }
    }

    private suspend fun refreshPlaybackSourceDirect(
        awemeId: String,
        failedSource: DouyinPlaybackSourceKind,
        trigger: DouyinPlaybackRefreshTrigger,
        localFallbackAvailable: Boolean
    ) {
        val result = repository.fetchVideo(awemeId)
        val content = result.data
        val event = when {
            result.isSuccess && content is DouyinContent.Video -> {
                val updated = updatePlaybackSource(awemeId, content)
                val refreshedItem = sourceItems.firstOrNull { it.awemeId == awemeId }
                if (updated) {
                    val currentState = _uiState.value
                    cacheBootstrapPage(
                        items = sourceItems,
                        nextCursorSnapshot = nextCursor,
                        hasMore = currentState.hasMore
                    )
                    schedulePreload(currentState.items, currentState.playHeaders)
                }
                DouyinPlaybackSourceRefreshEvent(
                    eventId = System.nanoTime(),
                    awemeId = awemeId,
                    trigger = trigger,
                    outcome = if (updated) {
                        DouyinPlaybackRefreshOutcome.SUCCESS
                    } else {
                        DouyinPlaybackRefreshOutcome.FAILURE
                    },
                    item = refreshedItem
                )
            }

            result.isSuccess && content is DouyinContent.Note -> {
                DouyinPlaybackSourceRefreshEvent(
                    eventId = System.nanoTime(),
                    awemeId = awemeId,
                    trigger = trigger,
                    outcome = DouyinPlaybackRefreshOutcome.FAILURE,
                    message = "当前内容暂无可播放视频"
                )
            }

            else -> {
                DouyinPlaybackSourceRefreshEvent(
                    eventId = System.nanoTime(),
                    awemeId = awemeId,
                    trigger = trigger,
                    outcome = DouyinPlaybackRefreshOutcome.FAILURE,
                    errorCode = result.code,
                    message = result.message
                )
            }
        }
        applyPlaybackSourceRefreshEvent(event)
        if (event.outcome == DouyinPlaybackRefreshOutcome.FAILURE) {
            handlePlaybackRefreshFailure(
                event = event,
                suppressMessage = failedSource == DouyinPlaybackSourceKind.LOCAL &&
                    localFallbackAvailable
            )
        }
    }

    private suspend fun handlePlaybackRefreshFailure(
        event: DouyinPlaybackSourceRefreshEvent,
        suppressMessage: Boolean
    ) {
        if (event.errorCode == DouyinErrorCodes.NOT_LOGGED_IN) {
            repository.clearCookie()
            replaceSourceState(items = emptyList(), localPlayPaths = emptyMap())
            _uiState.update {
                it.copy(
                    isLoggedIn = false,
                    items = emptyList(),
                    localPlayPaths = emptyMap(),
                    proxyPlayUris = emptyMap(),
                    message = "需要登录"
                )
            }
            return
        }
        if (!suppressMessage) {
            _uiState.update {
                it.copy(
                    message = event.message?.takeIf(String::isNotBlank)
                        ?: formatDouyinError(event.errorCode ?: -1, event.message)
                )
            }
        }
    }

    private fun applyPlaybackSourceRefreshEvent(event: DouyinPlaybackSourceRefreshEvent) {
        if (event.eventId > 0L && !handledPlaybackRefreshEventIds.add(event.eventId)) return
        if (event.outcome == DouyinPlaybackRefreshOutcome.SUCCESS) {
            event.item?.let(::updatePlaybackItem)
        }
        _uiState.update { state ->
            state.copy(
                playbackRefreshEvents = state.playbackRefreshEvents + (event.awemeId to event)
            )
        }
    }

    private fun scheduleStartupPlaybackRefresh(
        items: List<DouyinStreamItem>,
        targetPage: Int
    ) {
        if (items.isEmpty()) return
        val preparedIndex = if (targetPage > 0) {
            (targetPage - 1).coerceIn(0, items.lastIndex)
        } else {
            0
        }
        val candidates = items
            .drop(preparedIndex)
            .take(STARTUP_PLAYBACK_REFRESH_COUNT)
            .filter(::isDouyinPlayUrlExpired)
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            candidates.forEach { item ->
                playbackSourceCoordinator.refresh(
                    item = item,
                    trigger = DouyinPlaybackRefreshTrigger.STARTUP_TTL
                ).takeIf { it.outcome != DouyinPlaybackRefreshOutcome.SKIPPED }
                    ?.let(::applyPlaybackSourceRefreshEvent)
            }
        }
    }

    private suspend fun loadCachedBootstrap(headers: Map<String, String>): Long {
        val bootstrapState = readBootstrapState()
        replaceSourceState(
            items = bootstrapState.items,
            localPlayPaths = bootstrapState.localPaths
        )
        seedFeedWindow(sourceItems)
        nextCursor = bootstrapState.nextCursor
        val visibleItems = projectVisibleItems()
        val visibleLocalPaths = projectVisibleLocalPlayPaths(visibleItems)
        val visibleProxyUris = projectVisibleProxyPlayUris(
            visibleItems = visibleItems,
            headers = headers,
            reason = "load_cached_bootstrap"
        )
        _uiState.update {
            it.copy(
                items = visibleItems,
                hasMore = bootstrapState.hasMore,
                playHeaders = headers,
                localPlayPaths = visibleLocalPaths,
                proxyPlayUris = visibleProxyUris
            )
        }
        return bootstrapState.savedAtMs
    }

    private suspend fun restoreEntryPlayback(
        headers: Map<String, String>,
        resumeToVideoFlow: Boolean = false,
        resumeAwemeId: String? = null
    ): Boolean {
        val bootstrapState = readBootstrapState()
        val latestAwemeId = withContext(storageDispatcher) {
            watchHistoryStore.readHistory()
                .firstOrNull()
                ?.awemeId
                ?.takeIf { it.isNotBlank() }
        }
        val persistedResumeAnchorAwemeId = resumeAwemeId
            ?: bootstrapState.resumeAnchorAwemeId
        val resumeAnchorAwemeId = persistedResumeAnchorAwemeId ?: latestAwemeId
        val restoredItems = dropDouyinItemsBeforeAwemeId(
            items = bootstrapState.items,
            anchorAwemeId = persistedResumeAnchorAwemeId
        )
        replaceSourceState(
            items = restoredItems,
            localPlayPaths = bootstrapState.localPaths
        )
        val visibleBootstrapItems = projectVisibleItems()
        val visibleLocalPaths = projectVisibleLocalPlayPaths(visibleBootstrapItems)
        val visibleProxyUris = projectVisibleProxyPlayUris(
            visibleItems = visibleBootstrapItems,
            headers = headers,
            reason = "restore_entry"
        )
        val hasBootstrapItems = visibleBootstrapItems.isNotEmpty()
        val resumeAnchorPage = resolvePageAfterAwemeId(
            awemeId = resumeAnchorAwemeId,
            items = visibleBootstrapItems
        ) ?: resolvePageForAwemeId(
            awemeId = resumeAnchorAwemeId,
            items = visibleBootstrapItems
        )
        val targetPage = when {
            resumeAnchorPage != null -> resumeAnchorPage
            !latestAwemeId.isNullOrBlank() -> {
                resolvePageAfterAwemeId(
                    awemeId = latestAwemeId,
                    items = visibleBootstrapItems
                ) ?: resolvePageForAwemeId(
                    awemeId = latestAwemeId,
                    items = visibleBootstrapItems
                ) ?: 0
            }
            else -> 0
        }
        AppLogger.d(
            TAG,
            "restore bootstrap items=${bootstrapState.items.size} restored=${restoredItems.size} latest=$latestAwemeId " +
                "resume=${bootstrapState.resumeAnchorAwemeId} explicit=$resumeAwemeId " +
                "resumeFlow=$resumeToVideoFlow targetPage=$targetPage " +
                "nextCursor=${bootstrapState.nextCursor} hasMore=${bootstrapState.hasMore}"
        )
        seedFeedWindow(sourceItems)
        nextCursor = bootstrapState.nextCursor
        _uiState.update {
            it.copy(
                items = visibleBootstrapItems,
                hasMore = bootstrapState.hasMore,
                playHeaders = headers,
                localPlayPaths = visibleLocalPaths,
                proxyPlayUris = visibleProxyUris,
                currentPage = targetPage,
                showTitlePage = true,
                message = null
            )
        }
        schedulePreload(visibleBootstrapItems, headers)
        scheduleStartupPlaybackRefresh(
            items = visibleBootstrapItems,
            targetPage = targetPage
        )
        return hasBootstrapItems
    }

    private fun shouldLoadInitialAfterRestore(
        hasBootstrapItems: Boolean,
        resumeToVideoFlow: Boolean
    ): Boolean {
        if (!hasBootstrapItems) return true
        if (resumeToVideoFlow) return false
        return shouldRefreshBootstrapWindow() || shouldPrimeBootstrapCursor()
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
        val anchorAwemeId = recentWindowSnapshot.anchorAwemeId
        val mergedWithPinnedItems = if (anchorAwemeId.isNullOrBlank()) {
            mergeDouyinPrioritizedItems(
                prioritizedItems = pinnedSnapshotItems,
                remainingItems = mergedBootstrapItems,
                limit = BOOTSTRAP_ITEMS + DOUYIN_PLAYBACK_SNAPSHOT_STARTUP_COUNT
            )
        } else {
            dropDouyinItemsBeforeAwemeId(
                items = mergedBootstrapItems,
                anchorAwemeId = anchorAwemeId
            )
        }
        val bootstrapItems = mergedWithPinnedItems
            .take(BOOTSTRAP_ITEMS + DOUYIN_PLAYBACK_SNAPSHOT_STARTUP_COUNT)
        AppLogger.d(
            TAG,
            "read bootstrap pinned=${pinnedSnapshotItems.size} feed=${cachedItems.size} recent=${recentItems.size} " +
                "merged=${mergedWithPinnedItems.size} restored=${bootstrapItems.size} anchor=${recentWindowSnapshot.anchorAwemeId}"
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
        val rawAnchorIndex = sourceItems.indexOfFirst { it.awemeId == current.awemeId }
        if (rawAnchorIndex < 0) return
        val playHeaders = _uiState.value.playHeaders
        viewModelScope.launch(storageDispatcher) {
            watchHistoryStore.markWatched(current)
            persistRecentWindow(
                items = sourceItems,
                anchorIndex = rawAnchorIndex,
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

    fun discardPlaybackItem(awemeId: String) {
        val normalizedAwemeId = awemeId.trim()
        if (normalizedAwemeId.isEmpty()) return
        if (!sessionQuarantinedAwemeIds.add(normalizedAwemeId)) return

        playbackRefreshJobs.remove(normalizedAwemeId)?.cancel()
        val previousVisibleItems = _uiState.value.items
        val removedIndex = previousVisibleItems.indexOfFirst { it.awemeId == normalizedAwemeId }
        val nextVisibleItems = projectVisibleItems()
        val nextVisibleLocalPaths = projectVisibleLocalPlayPaths(nextVisibleItems)
        _uiState.update { state ->
            state.copy(
                items = nextVisibleItems,
                localPlayPaths = nextVisibleLocalPaths,
                proxyPlayUris = projectVisibleProxyPlayUris(
                    visibleItems = nextVisibleItems,
                    headers = state.playHeaders,
                    reason = "discard_item"
                ),
                currentPage = resolveCurrentPageAfterDiscard(
                    currentPage = state.currentPage,
                    removedIndex = removedIndex,
                    remainingCount = nextVisibleItems.size
                ),
                showTitlePage = if (nextVisibleItems.isEmpty()) true else state.showTitlePage
            )
        }
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
                val merged = sourceItems + incoming
                val localPaths = withContext(storageDispatcher) {
                    preloadManager.resolveLocalPaths(merged.map { it.awemeId })
                }
                replaceSourceState(
                    items = merged,
                    localPlayPaths = localPaths
                )
                val visibleMergedItems = projectVisibleItems()
                val visibleLocalPaths = projectVisibleLocalPlayPaths(visibleMergedItems)
                val visibleProxyUris = projectVisibleProxyPlayUris(
                    visibleItems = visibleMergedItems,
                    headers = _uiState.value.playHeaders,
                    reason = "load_more"
                )
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMore = page?.hasMore ?: false,
                        items = visibleMergedItems,
                        localPlayPaths = visibleLocalPaths,
                        proxyPlayUris = visibleProxyUris
                    )
                }
                schedulePreload(visibleMergedItems, _uiState.value.playHeaders)
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
                replaceSourceState(items = emptyList(), localPlayPaths = emptyMap())
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isLoggedIn = false,
                        items = emptyList(),
                        localPlayPaths = emptyMap(),
                        proxyPlayUris = emptyMap()
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
            "proxy playback window active; skip full-file preload items=${items.size} anchor=${
                resolveDouyinPlaybackAnchorAwemeId(items = items, currentPage = _uiState.value.currentPage)
            }"
        )
    }

    private fun updatePlaybackSource(
        awemeId: String,
        content: DouyinContent.Video
    ): Boolean {
        val resolvedAtMs = System.currentTimeMillis()
        val current = sourceItems.firstOrNull { it.awemeId == awemeId } ?: return false
        return updatePlaybackItem(
            current.copy(
                playUrl = content.playUrl.trim(),
                coverUrl = content.coverUrl.takeIf { it.isNotBlank() } ?: current.coverUrl,
                title = content.desc.takeIf { it.isNotBlank() } ?: current.title,
                author = content.authorName.takeIf { it.isNotBlank() } ?: current.author,
                likeCount = content.diggCount,
                playUrlResolvedAtMs = resolvedAtMs,
                sourceOrigin = DouyinSourceOrigin.VIDEO_REFRESH,
                variants = content.variants
            )
        )
    }

    private fun updatePlaybackItem(refreshedItem: DouyinStreamItem): Boolean {
        var updated = false
        sourceItems = sourceItems.map { item ->
            if (
                item.awemeId == refreshedItem.awemeId &&
                refreshedItem.playUrlResolvedAtMs >= item.playUrlResolvedAtMs
            ) {
                updated = true
                refreshedItem
            } else {
                item
            }
        }
        if (!updated) return false
        val visibleItems = projectVisibleItems()
        val visibleLocalPaths = projectVisibleLocalPlayPaths(visibleItems)
        val visibleProxyUris = projectVisibleProxyPlayUris(
            visibleItems = visibleItems,
            headers = _uiState.value.playHeaders,
            reason = "playback_source_update"
        )
        _uiState.update { state ->
            state.copy(
                items = visibleItems,
                localPlayPaths = visibleLocalPaths,
                proxyPlayUris = visibleProxyUris
            )
        }
        return true
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
        val mergedBootstrapItems = dropDouyinItemsBeforeAwemeId(
            items = mergeDouyinBootstrapItems(
                feedItems = feedCacheStore.readSnapshot(limit = BOOTSTRAP_ITEMS).items,
                recentItems = recentWindow,
                limit = BOOTSTRAP_ITEMS
            ),
            anchorAwemeId = anchorAwemeId
        ).take(BOOTSTRAP_ITEMS)
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
        visibleItems: List<DouyinStreamItem>,
        sourceItems: List<DouyinStreamItem>,
        targetPage: Int,
        headers: Map<String, String>,
        reason: String
    ) {
        if (targetPage <= 0) return
        val anchorAwemeId = visibleItems.getOrNull(targetPage - 1)?.awemeId ?: return
        val anchorIndex = sourceItems.indexOfFirst { it.awemeId == anchorAwemeId }
        if (anchorIndex < 0) return
        viewModelScope.launch(storageDispatcher) {
            persistRecentWindow(
                items = sourceItems,
                anchorIndex = anchorIndex,
                headers = headers,
                reason = reason
            )
        }
    }

    private fun resolvePageAfterAwemeId(
        awemeId: String?,
        items: List<DouyinStreamItem>
    ): Int? {
        val normalizedAwemeId = awemeId?.trim().orEmpty()
        if (normalizedAwemeId.isEmpty()) return null
        val itemIndex = items.indexOfFirst { it.awemeId == normalizedAwemeId }
        if (itemIndex < 0) return null
        val nextIndex = itemIndex + 1
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

    private fun replaceSourceState(
        items: List<DouyinStreamItem> = sourceItems,
        localPlayPaths: Map<String, String> = sourceLocalPlayPaths
    ) {
        sourceItems = items
        sourceLocalPlayPaths = localPlayPaths
    }

    private fun projectVisibleItems(
        items: List<DouyinStreamItem> = sourceItems
    ): List<DouyinStreamItem> {
        if (sessionQuarantinedAwemeIds.isEmpty()) return items
        return items.filterNot { sessionQuarantinedAwemeIds.contains(it.awemeId) }
    }

    private fun projectVisibleLocalPlayPaths(
        visibleItems: List<DouyinStreamItem>,
        localPlayPaths: Map<String, String> = sourceLocalPlayPaths
    ): Map<String, String> {
        if (visibleItems.isEmpty() || localPlayPaths.isEmpty()) return emptyMap()
        val visibleAwemeIds = visibleItems.mapTo(linkedSetOf()) { it.awemeId }
        return localPlayPaths.filterKeys { visibleAwemeIds.contains(it) }
    }

    private fun projectVisibleProxyPlayUris(
        visibleItems: List<DouyinStreamItem>,
        headers: Map<String, String>,
        reason: String
    ): Map<String, String> {
        if (visibleItems.isEmpty()) return emptyMap()
        return playbackTransport.proxyUrisFor(
            items = visibleItems,
            headers = headers,
            reason = reason
        )
    }

    private fun resolveCurrentPageAfterDiscard(
        currentPage: Int,
        removedIndex: Int,
        remainingCount: Int
    ): Int {
        if (remainingCount <= 0) return 0
        if (currentPage <= 0 || removedIndex < 0) {
            return currentPage.coerceIn(0, remainingCount)
        }
        val removedPage = removedIndex + 1
        return when {
            removedPage < currentPage -> (currentPage - 1).coerceAtLeast(1)
            else -> currentPage.coerceAtMost(remainingCount)
        }
    }

    companion object {
        private const val TAG = "DouyinFeedVM"
        private const val PAGE_SIZE = 16
        private const val BOOTSTRAP_ITEMS = PAGE_SIZE
        private const val DOUYIN_PLAYBACK_SNAPSHOT_STARTUP_COUNT = 6
        private const val STARTUP_PLAYBACK_REFRESH_COUNT = 2
        private const val MIN_BOOTSTRAP_ITEMS_BEFORE_NETWORK_REFRESH = 3
        private const val MIN_FORWARD_ITEMS_BEFORE_NETWORK_REFRESH = 3
    }
}
