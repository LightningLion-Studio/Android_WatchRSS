package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStore
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManager
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStore
import com.lightningstudio.watchrss.data.douyin.formatDouyinError
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

class DouyinFeedViewModel(
    private val repository: DouyinRepositoryContract,
    private val preloadManager: DouyinPreloadManager,
    private val watchHistoryStore: DouyinWatchHistoryStore,
    private val feedCacheStore: DouyinFeedCacheStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(DouyinFeedUiState())
    val uiState: StateFlow<DouyinFeedUiState> = _uiState

    private var nextCursor: String? = null
    private var isRequestingMore: Boolean = false
    private val awemeIdSet = linkedSetOf<String>()
    private var preloadJob: Job? = null

    init {
        viewModelScope.launch {
            val loggedIn = repository.isLoggedIn()
            _uiState.update { it.copy(isLoggedIn = loggedIn) }
            if (loggedIn) {
                val headers = repository.buildPlayHeaders()
                _uiState.update { it.copy(playHeaders = headers) }
                loadCachedBootstrap(headers)
                loadInitial()
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
                val preserveIds = preservePrefix.map { it.awemeId }.toHashSet()
                val mergedItems = if (preservePrefix.isEmpty()) {
                    mapped
                } else {
                    preservePrefix + mapped.filterNot { preserveIds.contains(it.awemeId) }
                }
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
                feedCacheStore.save(mapped)
                schedulePreload(mergedItems, headers)
            } else {
                if (result.code == DouyinErrorCodes.NOT_LOGGED_IN) {
                    repository.clearCookie()
                    _uiState.update {
                        it.copy(isLoading = false, isLoggedIn = false, items = emptyList())
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = formatDouyinError(result.code, result.message)
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadCachedBootstrap(headers: Map<String, String>) {
        val cachedItems = feedCacheStore.read(limit = BOOTSTRAP_ITEMS)
        if (cachedItems.isEmpty()) return
        val localPaths = preloadManager.resolveLocalPaths(cachedItems.map { it.awemeId })
        val localFirstItems = cachedItems
            .sortedByDescending { item -> localPaths[item.awemeId] != null }
            .take(BOOTSTRAP_ITEMS)
        if (localFirstItems.isEmpty()) return
        val playablePaths = localPaths.filterKeys { awemeId -> localFirstItems.any { it.awemeId == awemeId } }
        _uiState.update {
            it.copy(
                items = localFirstItems,
                hasMore = true,
                playHeaders = headers,
                localPlayPaths = playablePaths
            )
        }
    }

    fun onPageSettled(page: Int) {
        val safePage = page.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                currentPage = safePage,
                showTitlePage = if (safePage > 0) false else it.showTitlePage
            )
        }
        if (safePage <= 0) return

        val itemIndex = safePage - 1
        val currentItems = _uiState.value.items
        val current = currentItems.getOrNull(itemIndex) ?: return
        watchHistoryStore.markWatched(current.awemeId)

        schedulePreload(currentItems, _uiState.value.playHeaders)

        if (itemIndex >= currentItems.size - LOAD_MORE_THRESHOLD) {
            loadMore()
        }
    }

    fun enterVideoFlow() {
        _uiState.update { it.copy(showTitlePage = false, currentPage = 1) }
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
                if (result.code == DouyinErrorCodes.NOT_LOGGED_IN) {
                    repository.clearCookie()
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            isLoggedIn = false,
                            items = emptyList(),
                            localPlayPaths = emptyMap()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            message = formatDouyinError(result.code, result.message)
                        )
                    }
                }
            }
            isRequestingMore = false
        }
    }

    private fun mapToStreamItems(items: List<com.lightningstudio.watchrss.sdk.douyin.DouyinVideo>): List<DouyinStreamItem> {
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
                    likeCount = video.likeCount
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

    companion object {
        private const val PAGE_SIZE = 16
        private const val LOAD_MORE_THRESHOLD = 3
        private const val TARGET_PRELOAD_UNWATCHED = 2
        private const val BOOTSTRAP_ITEMS = 2
    }
}
