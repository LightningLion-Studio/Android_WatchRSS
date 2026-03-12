package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.ExternalSavedItem
import com.lightningstudio.watchrss.data.rss.RssPreviewItem
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
    private val repository: BiliRepository,
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
            val useCachePrefix = _uiState.value.items.isEmpty()
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
                val merged = if (cachedPrefix.isNotEmpty()) {
                    mergeCachedAndFresh(cachedPrefix, freshItems)
                } else {
                    freshItems
                }
                repository.writeFeedCache(freshItems)
                updateFeed(page, merged)
            } else {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoadingMore = false,
                        items = if (cachedItems.isNotEmpty()) cachedItems else it.items,
                        message = formatBiliError(result.code)
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
                        message = formatBiliError(result.code)
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun favorite(item: BiliItem) {
        val aid = item.aid ?: return
        viewModelScope.launch {
            val result = repository.favorite(aid, add = true)
            if (result.isSuccess) {
                _uiState.update { it.copy(message = "已收藏") }
                syncLocalSaved(item, SaveType.FAVORITE, true)
            } else {
                _uiState.update { it.copy(message = formatBiliError(result.code)) }
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
                _uiState.update { it.copy(message = formatBiliError(result.code)) }
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
        val external = buildExternalSavedItem(item)
        rss.syncExternalSavedItem(external, saveType, saved)
        if (saved) {
            repository.cachePreviewClip(aid = item.aid, bvid = item.bvid, cid = item.cid)
        } else {
            repository.clearCachedPreview(aid = item.aid, bvid = item.bvid, cid = item.cid)
        }
    }

    private fun buildExternalSavedItem(item: BiliItem): ExternalSavedItem {
        val title = item.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: item.bvid?.let { "BV号 $it" }
            ?: item.aid?.let { "av$it" }
            ?: "哔哩哔哩视频"
        val link = repository.savedLink(item.bvid, item.aid, item.cid)
        val guid = when {
            !item.bvid.isNullOrBlank() -> "bili:${item.bvid}"
            item.aid != null -> "bili:av${item.aid}"
            !link.isNullOrBlank() -> "bili:$link"
            else -> null
        }
        val owner = item.owner?.name?.trim().takeUnless { it.isNullOrBlank() }
        val description = owner?.let { "UP主：$it" }
        val preview = RssPreviewItem(
            title = title,
            description = description,
            content = description,
            link = link,
            guid = guid,
            pubDate = null,
            imageUrl = item.cover,
            audioUrl = null,
            videoUrl = null
        )
        return ExternalSavedItem(
            channelUrl = BuiltinChannelType.BILI.url,
            item = preview
        )
    }
}
