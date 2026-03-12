package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SavedState
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.data.settings.DEFAULT_READING_FONT_SIZE_SP
import com.lightningstudio.watchrss.ui.util.RssContentCache
import com.lightningstudio.watchrss.ui.util.buildContentBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RssRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val itemId: Long = savedStateHandle["itemId"] ?: 0L

    val item = repository.observeItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val channel = item
        .flatMapLatest { current ->
            if (current == null) flowOf(null) else repository.observeChannel(current.channelId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val contentBlocks = item
        .mapLatest { current ->
            if (current == null) return@mapLatest emptyList()
            val raw = current.content ?: current.description
            if (raw.isNullOrBlank()) return@mapLatest emptyList()
            val contentHash = raw.hashCode()
            withContext(Dispatchers.Default) {
                RssContentCache.getOrPut(current.id, contentHash) {
                    buildContentBlocks(current)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedState = repository.observeSavedState(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedState(false, false))

    val offlineMedia = repository.observeOfflineMedia(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val readingThemeDark = settingsRepository.readingThemeDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val readingFontSizeSp = settingsRepository.readingFontSizeSp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_READING_FONT_SIZE_SP)

    val shareUseSystem = settingsRepository.shareUseSystem

    private val requestedOriginalIds = mutableSetOf<Long>()

    init {
        if (itemId > 0L) {
            viewModelScope.launch {
                repository.markItemRead(itemId)
            }
        }
        viewModelScope.launch {
            combine(item, channel) { currentItem, currentChannel ->
                currentItem to currentChannel
            }.collect { (currentItem, currentChannel) ->
                val id = currentItem?.id ?: return@collect
                if (currentChannel?.useOriginalContent == true &&
                    currentItem.content.isNullOrBlank() &&
                    requestedOriginalIds.add(id)
                ) {
                    repository.requestOriginalContent(id)
                }
            }
        }
    }

    fun toggleFavorite() {
        if (itemId <= 0L) return
        viewModelScope.launch {
            repository.toggleFavorite(itemId)
        }
    }

    fun toggleLike() {
        if (itemId <= 0L) return
        viewModelScope.launch {
            repository.toggleLike(itemId)
        }
    }

    fun retryOfflineMedia() {
        if (itemId <= 0L) return
        viewModelScope.launch {
            repository.retryOfflineMedia(itemId)
        }
    }

    fun updateReadingProgress(progress: Float) {
        if (itemId <= 0L) return
        viewModelScope.launch {
            repository.updateItemReadingProgress(itemId, progress)
        }
    }
}
