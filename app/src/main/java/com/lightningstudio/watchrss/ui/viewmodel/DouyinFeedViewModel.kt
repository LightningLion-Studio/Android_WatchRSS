package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceKind
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManagerContract
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStoreContract
import com.lightningstudio.watchrss.data.douyin.formatDouyinError
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DouyinFeedUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<DouyinStreamItem> = emptyList(),
    val hasMore: Boolean = true,
    val currentPage: Int = 0,
    val playHeaders: Map<String, String> = emptyMap(),
    val localPlayPaths: Map<String, String> = emptyMap(),
    val message: String? = null,
    val showTitlePage: Boolean = true
)

private data class DouyinBootstrapState(
    val items: List<DouyinStreamItem>,
    val localPaths: Map<String, String>,
    val savedAtMs: Long
)

class DouyinFeedViewModel(
    private val repository: DouyinRepositoryContract,
    private val preloadManager: DouyinPreloadManagerContract,
    private val watchHistoryStore: DouyinWatchHistoryStoreContract,
    private val feedCacheStore: DouyinFeedCacheStoreContract
) : ViewModel() {
    private val _uiState = MutableStateFlow(DouyinFeedUiState())
    val uiState: StateFlow<DouyinFeedUiState> = _uiState

    private var nextCursor: String? = null
    private var isRequestingMore: Boolean = false
    private val awemeIdSet = linkedSetOf<String>()
    private var preloadJob: Job? = null
    private val playbackRefreshJobs = linkedMapOf<String, Job>()

    init {
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
                restoreEntryPlayback(headers)
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
            restoreEntryPlayback(headers)
        }
    }

    fun loadInitial() {
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
                val preservePrefix = if (currentVideoIndex >= 0 && latestState.items.isNotEmpty()) {
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
                val localPaths = preloadManager.resolveLocalPaths(mergedItems.map { it.awemeId })
                val adjustedCurrentPage = when {
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
                cacheBootstrapPage(mapped)
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
                            cacheBootstrapPage(_uiState.value.items.take(PAGE_SIZE))
                            schedulePreload(_uiState.value.items, _uiState.value.playHeaders)
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
        _uiState.update {
            it.copy(
                items = bootstrapState.items,
                hasMore = true,
                playHeaders = headers,
                localPlayPaths = bootstrapState.localPaths
            )
        }
        return bootstrapState.savedAtMs
    }

    private suspend fun restoreEntryPlayback(headers: Map<String, String>) {
        val bootstrapState = readBootstrapState()
        val latestAwemeId = watchHistoryStore.readHistory()
            .firstOrNull()
            ?.awemeId
            ?.takeIf { it.isNotBlank() }

        if (latestAwemeId == null) {
            seedFeedWindow(bootstrapState.items)
            _uiState.update {
                it.copy(
                    items = bootstrapState.items,
                    hasMore = true,
                    playHeaders = headers,
                    localPlayPaths = bootstrapState.localPaths,
                    currentPage = 0,
                    showTitlePage = true,
                    message = null
                )
            }
            schedulePreload(bootstrapState.items, headers)
            return
        }

        val cachedTargetPage = resolveNextPageAfter(
            awemeId = latestAwemeId,
            items = bootstrapState.items
        )
        if (cachedTargetPage != null) {
            seedFeedWindow(bootstrapState.items)
            _uiState.update {
                it.copy(
                    items = bootstrapState.items,
                    hasMore = true,
                    playHeaders = headers,
                    localPlayPaths = bootstrapState.localPaths,
                    currentPage = cachedTargetPage,
                    showTitlePage = true,
                    message = null
                )
            }
            schedulePreload(bootstrapState.items, headers)
            return
        }

        val fallbackPage = if (bootstrapState.items.isNotEmpty()) 1 else 0
        seedFeedWindow(bootstrapState.items)
        _uiState.update {
            it.copy(
                items = bootstrapState.items,
                hasMore = true,
                playHeaders = headers,
                localPlayPaths = bootstrapState.localPaths,
                currentPage = fallbackPage,
                showTitlePage = true,
                message = null
            )
        }
        schedulePreload(bootstrapState.items, headers)
    }

    private suspend fun readBootstrapState(): DouyinBootstrapState {
        val snapshot = feedCacheStore.readSnapshot(limit = BOOTSTRAP_ITEMS)
        val cachedItems = snapshot.items
            .map { it.copy(sourceOrigin = DouyinSourceOrigin.BOOTSTRAP_CACHE) }
        if (cachedItems.isEmpty()) {
            return DouyinBootstrapState(
                items = emptyList(),
                localPaths = emptyMap(),
                savedAtMs = snapshot.savedAtMs
            )
        }

        val localPaths = preloadManager.resolveLocalPaths(cachedItems.map { it.awemeId })
        if (cachedItems.isEmpty()) {
            return DouyinBootstrapState(
                items = emptyList(),
                localPaths = emptyMap(),
                savedAtMs = snapshot.savedAtMs
            )
        }

        val playablePaths = localPaths.filterKeys { awemeId ->
            cachedItems.any { it.awemeId == awemeId }
        }
        return DouyinBootstrapState(
            items = cachedItems,
            localPaths = playablePaths,
            savedAtMs = snapshot.savedAtMs
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
                showTitlePage = if (safePage > 0) false else it.showTitlePage
            )
        }
        if (safePage <= 0 || deferredTargetPage != null) return

        val itemIndex = resolvedPage - 1
        val currentItems = _uiState.value.items
        val current = currentItems.getOrNull(itemIndex) ?: return
        watchHistoryStore.markWatched(current)

        schedulePreload(currentItems, _uiState.value.playHeaders)

        if (itemIndex >= currentItems.size - LOAD_MORE_THRESHOLD) {
            loadMore()
        }
    }

    fun enterVideoFlow() {
        _uiState.update {
            val targetPage = when {
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
                val localPaths = preloadManager.resolveLocalPaths(merged.map { it.awemeId })
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
                    sourceOrigin = sourceOrigin
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun schedulePreload(items: List<DouyinStreamItem>, headers: Map<String, String>) {
        if (items.isEmpty()) return
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            preloadManager.ensureUnwatchedCache(
                items = items,
                watchedIds = watchHistoryStore.readWatchedIds(),
                headers = headers,
                targetUnwatchedCount = TARGET_PRELOAD_UNWATCHED
            )
            val updatedLocalPaths = preloadManager.resolveLocalPaths(items.map { it.awemeId })
            _uiState.update { state ->
                if (state.items.map { it.awemeId } == items.map { it.awemeId }) {
                    state.copy(localPlayPaths = updatedLocalPaths)
                } else {
                    state
                }
            }
        }
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
                        sourceOrigin = DouyinSourceOrigin.VIDEO_REFRESH
                    )
                }
            }
            if (!updated) state else state.copy(items = refreshedItems)
        }
        return updated
    }

    private fun cacheBootstrapPage(items: List<DouyinStreamItem>) {
        if (items.isEmpty()) return
        feedCacheStore.save(items.take(PAGE_SIZE))
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

    private fun seedFeedWindow(items: List<DouyinStreamItem>) {
        awemeIdSet.clear()
        items.forEach { awemeIdSet.add(it.awemeId) }
        nextCursor = null
    }

    companion object {
        private const val PAGE_SIZE = 16
        private const val LOAD_MORE_THRESHOLD = 3
        private const val TARGET_PRELOAD_UNWATCHED = 2
        private const val BOOTSTRAP_ITEMS = PAGE_SIZE
    }
}
