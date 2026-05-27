package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.buildBiliExternalSavedItem
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.sdk.bili.BiliFeedPage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedSource
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BiliFeedUiState(
    val isLoggedIn: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<BiliItem> = emptyList(),
    val feedSource: BiliFeedSource? = null,
    val lastRefreshAt: Long? = null,
    val canLoadMore: Boolean = true,
    val message: String? = null
)

class BiliFeedViewModel(
    private val repository: BiliRepositoryContract,
    private val rssRepository: RssRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(BiliFeedUiState())
    val uiState: StateFlow<BiliFeedUiState> = _uiState

    init {
        refreshLoginState()
    }

    fun refreshLoginState() {
        viewModelScope.launch {
            val loggedIn = repository.isLoggedIn()
            _uiState.update { it.copy(isLoggedIn = loggedIn) }
            if (loggedIn && _uiState.value.items.isEmpty()) {
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val loggedIn = repository.isLoggedIn()
            if (!loggedIn) {
                _uiState.update {
                    it.copy(
                        isLoggedIn = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        items = emptyList(),
                        feedSource = null,
                        canLoadMore = true,
                        message = "请先登录获取推荐内容"
                    )
                }
                return@launch
            }
            val currentItems = _uiState.value.items
            val useCachePrefix = currentItems.isEmpty()
            val cachedItems = if (useCachePrefix) repository.readFeedCache() else emptyList()
            val cachedPrefix = if (useCachePrefix) cachedItems.take(5) else emptyList()
            _uiState.update {
                it.copy(
                    isLoggedIn = true,
                    isRefreshing = true,
                    isLoadingMore = false,
                    items = if (cachedPrefix.isNotEmpty()) cachedPrefix else it.items,
                    feedSource = if (cachedPrefix.isNotEmpty()) null else it.feedSource,
                    canLoadMore = true,
                    message = null
                )
            }
            val result = repository.fetchFeed()
            if (result.isSuccess) {
                val page = result.data
                val freshItems = page?.items.orEmpty()
                val merged = when {
                    freshItems.isEmpty() && cachedItems.isNotEmpty() -> cachedItems
                    freshItems.isEmpty() && currentItems.isNotEmpty() -> currentItems
                    cachedPrefix.isNotEmpty() -> mergeCachedAndFresh(cachedPrefix, freshItems)
                    else -> freshItems
                }
                if (freshItems.isNotEmpty()) {
                    repository.writeFeedCache(freshItems)
                }
                updateFeed(page, merged)
            } else {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoadingMore = false,
                        items = if (cachedItems.isNotEmpty()) cachedItems else it.items,
                        message = formatBiliError(result.code, result.message)
                    )
                }
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isLoggedIn || state.isRefreshing || state.isLoadingMore || !state.canLoadMore) {
                return@launch
            }
            _uiState.update { it.copy(isLoadingMore = true, message = null) }
            val result = repository.fetchFeed()
            if (result.isSuccess) {
                val page = result.data
                val freshItems = page?.items.orEmpty()
                val merged = appendUnique(state.items, freshItems)
                val hasNewItems = merged.size > state.items.size
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        items = merged,
                        feedSource = page?.source ?: it.feedSource,
                        canLoadMore = hasNewItems && freshItems.isNotEmpty()
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        message = formatBiliError(result.code, result.message)
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun favorite(item: BiliItem) {
        viewModelScope.launch {
            val targetItem = resolveFavoriteTarget(item) ?: return@launch
            val aid = targetItem.aid ?: return@launch
            val result = repository.favorite(aid, add = true, bvid = targetItem.bvid)
            if (result.isSuccess) {
                _uiState.update { it.copy(message = "已收藏") }
                syncLocalSaved(targetItem, SaveType.FAVORITE, true)
            } else {
                _uiState.update { it.copy(message = formatBiliError(result.code, result.message)) }
            }
        }
    }

    fun watchLater(item: BiliItem) {
        viewModelScope.launch {
            val result = repository.addToView(aid = item.aid, bvid = item.bvid)
            if (result.isSuccess) {
                _uiState.update { it.copy(message = "已加入稍后再看") }
                syncLocalSaved(item, SaveType.WATCH_LATER, true)
            } else {
                _uiState.update { it.copy(message = formatBiliError(result.code, result.message)) }
            }
        }
    }

    private fun updateFeed(page: BiliFeedPage?, items: List<BiliItem>) {
        val refreshedAt = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isRefreshing = false,
                isLoadingMore = false,
                items = items,
                feedSource = page?.source,
                lastRefreshAt = refreshedAt,
                canLoadMore = items.isNotEmpty(),
                message = if (items.isEmpty()) "暂无推荐内容" else null
            )
        }
    }

    private fun mergeCachedAndFresh(
        cachedPrefix: List<BiliItem>,
        freshItems: List<BiliItem>
    ): List<BiliItem> {
        if (cachedPrefix.isEmpty()) return freshItems
        val seen = cachedPrefix.map { itemKey(it) }.toMutableSet()
        val filteredFresh = freshItems.filter { seen.add(itemKey(it)) }
        return cachedPrefix + filteredFresh
    }

    private fun itemKey(item: BiliItem): String {
        return when {
            !item.bvid.isNullOrBlank() -> "bvid:${item.bvid}"
            item.aid != null -> "aid:${item.aid}"
            else -> "title:${item.title.orEmpty()}"
        }
    }

    private fun appendUnique(
        existing: List<BiliItem>,
        freshItems: List<BiliItem>
    ): List<BiliItem> {
        if (freshItems.isEmpty()) return existing
        val seen = existing.map { itemKey(it) }.toMutableSet()
        val filtered = freshItems.filter { seen.add(itemKey(it)) }
        return if (filtered.isEmpty()) existing else existing + filtered
    }

    private suspend fun syncLocalSaved(item: BiliItem, saveType: SaveType, saved: Boolean) {
        val rss = rssRepository ?: return
        val external = buildBiliExternalSavedItem(item)
        rss.syncExternalSavedItem(external, saveType, saved)
        if (saved) {
            repository.cachePreviewClip(aid = item.aid, bvid = item.bvid, cid = item.cid)
        } else {
            repository.clearCachedPreview(aid = item.aid, bvid = item.bvid, cid = item.cid)
        }
    }

    private suspend fun resolveFavoriteTarget(item: BiliItem): BiliItem? {
        if (item.aid != null) return item
        val bvid = item.bvid?.trim()?.takeIf { it.isNotEmpty() }
        if (bvid == null) {
            _uiState.update { it.copy(message = "当前内容暂不支持收藏") }
            return null
        }
        val result = repository.fetchVideoDetail(aid = null, bvid = bvid)
        if (!result.isSuccess) {
            _uiState.update { it.copy(message = formatBiliError(result.code, result.message)) }
            return null
        }
        val resolved = result.data?.item
        if (resolved?.aid == null) {
            _uiState.update { it.copy(message = "当前内容暂不支持收藏") }
            return null
        }
        return item.copy(
            aid = resolved.aid,
            bvid = item.bvid ?: resolved.bvid,
            cid = item.cid ?: resolved.cid,
            title = item.title ?: resolved.title,
            cover = item.cover ?: resolved.cover,
            duration = item.duration ?: resolved.duration,
            pubdate = item.pubdate ?: resolved.pubdate,
            owner = item.owner ?: resolved.owner,
            stat = item.stat ?: resolved.stat
        )
    }
}
