package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SavedState
import com.lightningstudio.watchrss.data.rss.effectiveContent
import com.lightningstudio.watchrss.data.rss.isOriginalContentMissing
import com.lightningstudio.watchrss.data.settings.DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.data.settings.DEFAULT_READING_FONT_SIZE_SP
import com.lightningstudio.watchrss.ui.util.RssContentCache
import com.lightningstudio.watchrss.ui.util.buildContentBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val temporaryOriginalContentOverride = MutableStateFlow<Boolean?>(
        savedStateHandle[TEMPORARY_ORIGINAL_CONTENT_OVERRIDE_KEY]
    )
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    val item = repository.observeItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val channel = item
        .flatMapLatest { current ->
            if (current == null) flowOf(null) else repository.observeChannel(current.channelId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val effectiveUseOriginalContent = combine(channel, temporaryOriginalContentOverride) { currentChannel, override ->
        override ?: (currentChannel?.useOriginalContent ?: false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val contentBlocks = combine(item, effectiveUseOriginalContent) { current, useOriginalContent ->
        current to useOriginalContent
    }.mapLatest { (current, useOriginalContent) ->
            if (current == null) return@mapLatest emptyList()
            val raw = current.effectiveContent(useOriginalContent)
            if (raw.isNullOrBlank()) return@mapLatest emptyList()
            val contentHash = 31 * raw.hashCode() + if (useOriginalContent) 1 else 0
            withContext(Dispatchers.Default) {
                RssContentCache.getOrPut(current.id, contentHash) {
                    buildContentBlocks(current, useOriginalContent)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedState = repository.observeSavedState(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedState(false, false))

    val offlineMedia = repository.observeOfflineMedia(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRetryingOfflineMedia = MutableStateFlow(false)
    val isRetryingOfflineMedia = _isRetryingOfflineMedia.asStateFlow()

    val readingThemeDark = settingsRepository.readingThemeDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val readingFontSizeSp = settingsRepository.readingFontSizeSp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_READING_FONT_SIZE_SP)

    val shareUseSystem = settingsRepository.shareUseSystem

    val llmFeatureEnabled = settingsRepository.llmFeatureEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val llmAutoSummarize = settingsRepository.llmAutoSummarize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val llmShowTokenUsage = settingsRepository.llmShowTokenUsage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val rssInlineImagePrefetchMode = settingsRepository.rssInlineImagePrefetchMode
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE
        )

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
                    currentItem.isOriginalContentMissing() &&
                    requestedOriginalIds.add(id)
                ) {
                    repository.requestOriginalContent(id)
                }
            }
        }
    }

    fun toggleOriginalContent() {
        val currentItem = item.value ?: return
        val channelOriginalEnabled = channel.value?.useOriginalContent == true
        val next = !effectiveUseOriginalContent.value
        setTemporaryOriginalContentOverride(
            when {
                channelOriginalEnabled == next -> null
                else -> next
            }
        )
        if (!next) return

        viewModelScope.launch {
            if (currentItem.isOriginalContentMissing()) {
                repository.requestOriginalContent(currentItem.id, force = true)
            }
            if (!channelOriginalEnabled &&
                settingsRepository.recordTemporaryOriginalContentEnableAndShouldShowHint(currentItem.channelId)
            ) {
                _messages.tryEmit(ORIGINAL_CONTENT_MODE_HINT_MESSAGE)
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
        if (itemId <= 0L || _isRetryingOfflineMedia.value) return
        viewModelScope.launch {
            _isRetryingOfflineMedia.value = true
            try {
                repository.retryOfflineMedia(itemId)
            } finally {
                _isRetryingOfflineMedia.value = false
            }
        }
    }

    fun updateReadingProgress(progress: Float) {
        if (itemId <= 0L) return
        viewModelScope.launch {
            repository.updateItemReadingProgress(itemId, progress)
        }
    }

    private fun setTemporaryOriginalContentOverride(value: Boolean?) {
        temporaryOriginalContentOverride.value = value
        savedStateHandle[TEMPORARY_ORIGINAL_CONTENT_OVERRIDE_KEY] = value
    }

    companion object {
        private const val TEMPORARY_ORIGINAL_CONTENT_OVERRIDE_KEY = "temporaryOriginalContentOverride"
        const val ORIGINAL_CONTENT_MODE_HINT_MESSAGE = "点击频道标题，可在频道设置中开启原文阅读模式"
    }
}
