package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SavedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RssRepository
) : ViewModel() {
    private val channelId: Long = savedStateHandle["channelId"] ?: 0L

    val channel = repository.observeChannel(channelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _visibleCount = MutableStateFlow(DEFAULT_PAGE_SIZE)

    val items = _visibleCount
        .flatMapLatest { limit ->
            repository.observeItemsPaged(channelId, limit)
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

    fun refresh() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            val result = repository.refreshChannel(channelId)
            if (result.isFailure) {
                _message.value = result.exceptionOrNull()?.message ?: "刷新失败"
            }
            _isRefreshing.value = false
        }
    }

    fun loadMore() {
        _visibleCount.value = (_visibleCount.value + PAGE_SIZE).coerceAtMost(MAX_VISIBLE_ITEMS)
    }

    fun requestOriginalContents(itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        val newIds = itemIds.filter { requestedOriginalIds.add(it) }
        if (newIds.isEmpty()) return
        repository.requestOriginalContents(newIds)
    }

    fun setOriginalContentUpdatesPaused(paused: Boolean) {
        if (channelId <= 0L) return
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
