package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.biliRecommendationId
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
    val message: String? = null,
    val messageEventId: Long = 0L,
    val accountGeneration: Long = 0
)

class BiliFeedViewModel(
    private val repository: BiliRepositoryContract,
    private val rssRepository: RssRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(BiliFeedUiState())
    val uiState: StateFlow<BiliFeedUiState> = _uiState

    private var accountInitialized = false
    private var accountScope: String? = null
    private var requestVersion = 0L

    init {
        refreshLoginState()
    }

    fun refreshLoginState() {
        viewModelScope.launch {
            val loggedIn = repository.isLoggedIn()
            val scope = if (loggedIn) repository.feedAccountScope() else null
            adoptAccount(loggedIn, scope)
            if (loggedIn && _uiState.value.items.isEmpty() && !_uiState.value.isRefreshing) {
                refresh()
            }
        }
    }

    private fun adoptAccount(loggedIn: Boolean, scope: String?) {
        if (!accountInitialized || accountScope != scope || _uiState.value.isLoggedIn != loggedIn) {
            accountInitialized = true
            accountScope = scope
            requestVersion++
            _uiState.value = BiliFeedUiState(
                isLoggedIn = loggedIn,
                accountGeneration = _uiState.value.accountGeneration + 1,
                messageEventId = _uiState.value.messageEventId
            )
        }
    }

    private suspend fun acceptsResult(version: Long, scope: String?): Boolean {
        if (version != requestVersion) return false
        if (!repository.isLoggedIn() || repository.feedAccountScope() != scope) {
            refreshLoginState()
            return false
        }
        return version == requestVersion
    }

    fun rememberShownItems(generation: Long, items: List<BiliItem>) {
        val state = _uiState.value
        if (!state.isLoggedIn || state.accountGeneration != generation) return
        val visible = items.filter { it in state.items }
        if (visible.isEmpty()) return
        val scope = accountScope
        viewModelScope.launch {
            if (_uiState.value.accountGeneration != generation ||
                !repository.isLoggedIn() || repository.feedAccountScope() != scope
            ) return@launch
            repository.rememberShownRecommendations(scope, visible)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val loggedIn = repository.isLoggedIn()
            val scope = if (loggedIn) repository.feedAccountScope() else null
            adoptAccount(loggedIn, scope)
            if (!loggedIn) {
                publishMessage("请先登录获取推荐内容")
                return@launch
            }
            if (_uiState.value.isRefreshing) return@launch
            val version = ++requestVersion
            val currentItems = _uiState.value.items
            _uiState.update { it.copy(isRefreshing = true, isLoadingMore = false, message = null) }
            val shown = repository.readShownRecommendations(scope).toSet()
            val cachedItems = if (currentItems.isEmpty()) {
                unseenUnique(repository.readFeedCacheForAccount(scope), shown)
            } else emptyList()
            if (!acceptsResult(version, scope)) return@launch
            val cachedPrefix = cachedItems.take(5)
            if (cachedPrefix.isNotEmpty()) {
                _uiState.update { it.copy(items = cachedPrefix, feedSource = null) }
            }
            val result = repository.fetchFeed()
            if (!acceptsResult(version, scope)) return@launch
            if (result.isSuccess) {
                val page = result.data
                val fetchedItems = page?.items.orEmpty()
                val freshItems = unseenUnique(fetchedItems, repository.readShownRecommendations(scope).toSet())
                if (!acceptsResult(version, scope)) return@launch
                val merged = when {
                    fetchedItems.isEmpty() && cachedItems.isNotEmpty() -> cachedItems
                    fetchedItems.isEmpty() && currentItems.isNotEmpty() -> currentItems
                    cachedPrefix.isNotEmpty() -> appendUnique(cachedPrefix, freshItems)
                    else -> freshItems
                }
                if (freshItems.isNotEmpty()) {
                    repository.writeFeedCacheForAccount(scope, freshItems)
                }
                if (acceptsResult(version, scope)) updateFeed(page, merged)
            } else {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoadingMore = false,
                        items = if (cachedItems.isNotEmpty()) cachedItems else it.items,
                        message = formatBiliError(result.code, result.message),
                        messageEventId = it.messageEventId + 1
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
            val version = requestVersion
            val scope = accountScope
            if (!acceptsResult(version, scope)) return@launch
            if (_uiState.value.isLoadingMore || _uiState.value.isRefreshing) return@launch
            _uiState.update { it.copy(isLoadingMore = true, message = null) }
            val result = repository.fetchFeed()
            if (!acceptsResult(version, scope)) return@launch
            if (result.isSuccess) {
                val page = result.data
                val freshItems = unseenUnique(page?.items.orEmpty(), repository.readShownRecommendations(scope).toSet())
                if (!acceptsResult(version, scope)) return@launch
                val merged = appendUnique(state.items, freshItems)
                val hasNewItems = merged.size > state.items.size
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        items = merged,
                        feedSource = page?.source ?: it.feedSource,
                        canLoadMore = hasNewItems
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        message = formatBiliError(result.code, result.message),
                        messageEventId = it.messageEventId + 1
                    )
                }
            }
        }
    }

    private fun unseenUnique(items: List<BiliItem>, shown: Set<String>): List<BiliItem> {
        val seen = shown.toMutableSet()
        return items.filter { seen.add(itemKey(it)) }
    }

    fun clearMessage(expectedEventId: Long? = null) {
        if (expectedEventId != null && _uiState.value.messageEventId != expectedEventId) return
        _uiState.update { it.copy(message = null) }
    }

    fun favorite(item: BiliItem) {
        viewModelScope.launch {
            val targetItem = resolveFavoriteTarget(item) ?: return@launch
            val aid = targetItem.aid ?: return@launch
            val result = repository.favorite(aid, add = true, bvid = targetItem.bvid)
            if (result.isSuccess) {
                publishMessage("已收藏")
                syncLocalSaved(targetItem, SaveType.FAVORITE, true)
            } else {
                publishMessage(formatBiliError(result.code, result.message))
            }
        }
    }

    fun watchLater(item: BiliItem) {
        viewModelScope.launch {
            val result = repository.addToView(aid = item.aid, bvid = item.bvid)
            if (result.isSuccess) {
                publishMessage("已加入稍后再看")
                syncLocalSaved(item, SaveType.WATCH_LATER, true)
            } else {
                publishMessage(formatBiliError(result.code, result.message))
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
                message = if (items.isEmpty()) "暂无推荐内容" else null,
                messageEventId = if (items.isEmpty()) it.messageEventId + 1 else it.messageEventId
            )
        }
    }

    private fun itemKey(item: BiliItem): String =
        biliRecommendationId(item) ?: "title:${item.title.orEmpty()}"

    private fun publishMessage(message: String) {
        _uiState.update {
            it.copy(message = message, messageEventId = it.messageEventId + 1)
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
            publishMessage("当前内容暂不支持收藏")
            return null
        }
        val result = repository.fetchVideoDetail(aid = null, bvid = bvid)
        if (!result.isSuccess) {
            publishMessage(formatBiliError(result.code, result.message))
            return null
        }
        val resolved = result.data?.item
        if (resolved?.aid == null) {
            publishMessage("当前内容暂不支持收藏")
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
