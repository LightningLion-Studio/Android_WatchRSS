package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import com.lightningstudio.watchrss.data.rss.SavedState
import com.lightningstudio.watchrss.debug.PerfTrace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import com.lightningstudio.watchrss.data.novel.orderNovelChapters
import com.lightningstudio.watchrss.data.novel.NovelCatalogSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class NovelCatalogState(val loaded: Boolean = false, val items: List<RssItem> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RssRepository,
    private val novelCatalogSource: NovelCatalogSource = NovelCatalogSource { repository.observeItemsPaged(it, Int.MAX_VALUE) }
) : ViewModel() {
    private val channelId: Long = savedStateHandle["channelId"] ?: 0L
    private val _hasLoadedItems = MutableStateFlow(false)
    val hasLoadedItems: StateFlow<Boolean> = _hasLoadedItems.asStateFlow()

    val channel = repository.observeChannel(channelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Only chaptered novels opt out of the RSS 200-item browsing cap. The existing DAO
    // projection excludes bodies, so opening a long TOC does not hydrate every chapter.
    val novelCatalog = channel.map { it?.url }.distinctUntilChanged().flatMapLatest { url ->
        if (ImportedContentIds.isNovelChapterSourceUrl(url)) {
            novelCatalogSource.observe(channelId).map { chapters ->
                NovelCatalogState(true, withContext(Dispatchers.Default) { orderNovelChapters(chapters) })
            }
        } else flowOf(NovelCatalogState())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NovelCatalogState())

    private val _visibleCount = MutableStateFlow(DEFAULT_PAGE_SIZE)

    val items = _visibleCount
        .flatMapLatest { limit ->
            PerfTrace.log("feed", "viewModel observeItemsPaged channelId=$channelId limit=$limit")
            repository.observeItemsPaged(channelId, limit)
        }
        .onEach { currentItems ->
            _hasLoadedItems.value = true
            val firstId = currentItems.firstOrNull()?.id ?: -1L
            val lastId = currentItems.lastOrNull()?.id ?: -1L
            PerfTrace.log(
                "feed",
                "viewModel items emit channelId=$channelId size=${currentItems.size} firstId=$firstId lastId=$lastId"
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasMore = combine(
        repository.observeItemCount(channelId),
        _visibleCount
    ) { totalCount, limit ->
        totalCount > limit
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val requestedOriginalIds = mutableSetOf<Long>()

    private val openingNovelItem = MutableStateFlow<Long?>(null)
    val openingNovelItemId = openingNovelItem.asStateFlow()

    fun openItem(item: RssItem, onOpen: () -> Unit) {
        if (openingNovelItem.value != null) return
        if (!ImportedContentIds.isNovelContentItemUrl(item.link) || item.isRead) {
            onOpen()
            return
        }
        openingNovelItem.value = item.id
        viewModelScope.launch {
            try {
                repository.markItemRead(item.id)
                withTimeout(5_000) {
                    repository.observeItem(item.id).first { it?.isRead == true }
                }
                onOpen()
            } catch (_: TimeoutCancellationException) {
                _message.value = "阅读状态尚未确认，请重试"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _message.value = "阅读状态保存失败，请重试"
            } finally {
                openingNovelItem.value = null
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            val startNanos = PerfTrace.now()
            PerfTrace.log("feed", "viewModel refresh start channelId=$channelId")
            _isRefreshing.value = true
            val result = repository.refreshChannel(channelId)
            if (result.isFailure) {
                _message.value = result.exceptionOrNull()?.message ?: "刷新失败"
            }
            _isRefreshing.value = false
            PerfTrace.log(
                "feed",
                "viewModel refresh end channelId=$channelId success=${result.isSuccess} durMs=${PerfTrace.elapsedMs(startNanos)}"
            )
        }
    }

    fun loadMore() {
        _visibleCount.value = (_visibleCount.value + PAGE_SIZE).coerceAtMost(MAX_VISIBLE_ITEMS)
        PerfTrace.log("feed", "viewModel loadMore channelId=$channelId newLimit=${_visibleCount.value}")
    }

    fun requestOriginalContents(itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        val newIds = itemIds.filter { requestedOriginalIds.add(it) }
        if (newIds.isEmpty()) return
        PerfTrace.log(
            "feed",
            "viewModel requestOriginalContents channelId=$channelId requested=${itemIds.size} new=${newIds.size} ids=${newIds.joinToString(",")}"
        )
        repository.requestOriginalContents(newIds)
    }

    fun setOriginalContentUpdatesPaused(paused: Boolean) {
        if (channelId <= 0L) return
        PerfTrace.log("feed", "viewModel originalContentPaused channelId=$channelId paused=$paused")
        repository.setOriginalContentUpdatesPaused(channelId, paused)
    }

    fun toggleFavorite(itemId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(itemId)
        }
    }

    fun toggleWatchLater(itemId: Long) {
        viewModelScope.launch {
            repository.toggleWatchLater(itemId)
        }
    }

    suspend fun getSavedState(itemId: Long): SavedState {
        return repository.observeSavedState(itemId).first()
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 12
        private const val PAGE_SIZE = 8
        private const val MAX_VISIBLE_ITEMS = 200
    }
}
