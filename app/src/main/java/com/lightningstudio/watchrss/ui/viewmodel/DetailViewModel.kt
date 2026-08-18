package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SavedState
import com.lightningstudio.watchrss.data.rss.effectiveContent
import com.lightningstudio.watchrss.data.rss.isArticleContentMarker
import com.lightningstudio.watchrss.data.rss.isOriginalContentMissing
import com.lightningstudio.watchrss.data.settings.DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.data.settings.DEFAULT_READING_FONT_SIZE_SP
import com.lightningstudio.watchrss.data.settings.DEFAULT_READER_AUTO_SCROLL_ENABLED
import com.lightningstudio.watchrss.data.settings.DEFAULT_READER_AUTO_SCROLL_LINES_PER_SECOND
import com.lightningstudio.watchrss.ui.util.RssContentCache
import com.lightningstudio.watchrss.ui.util.buildContentBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: RssRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val itemId: Long = savedStateHandle["itemId"] ?: 0L
    private val temporaryOriginalContentOverride = MutableStateFlow<Boolean?>(
        savedStateHandle[TEMPORARY_ORIGINAL_CONTENT_OVERRIDE_KEY]
    )
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

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

    val importedTextReader = item.mapLatest { current ->
        if (current == null || !ImportedContentIds.isImportedTextItemUrl(current.link)) {
            null
        } else {
            repository.getImportedTextReader(current.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val contentBlocks = combine(item, effectiveUseOriginalContent, importedTextReader) { current, useOriginalContent, reader ->
        Triple(current, useOriginalContent, reader)
    }.mapLatest { (current, useOriginalContent, reader) ->
            if (current == null) return@mapLatest emptyList()
            if (ImportedContentIds.isImportedTextItemUrl(current.link) &&
                (reader != null ||
                    isArticleContentMarker(current.content) ||
                    isArticleContentMarker(current.originalContent))
            ) {
                return@mapLatest emptyList()
            }
            val raw = current.effectiveContent(useOriginalContent)
            if (raw.isNullOrBlank()) return@mapLatest emptyList()
            val contentHash = 31 * raw.hashCode() + if (useOriginalContent) 1 else 0
            withContext(Dispatchers.Default) {
                if (raw.length > LARGE_CONTENT_CACHE_LIMIT_CHARS) {
                    buildContentBlocks(current, useOriginalContent)
                } else {
                    RssContentCache.getOrPut(current.id, contentHash) {
                        buildContentBlocks(current, useOriginalContent)
                    }
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

    val readingFontSizeSp = settingsRepository.readingFontSizeSp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_READING_FONT_SIZE_SP)

    val readerAutoScrollEnabled = settingsRepository.readerAutoScrollEnabled
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DEFAULT_READER_AUTO_SCROLL_ENABLED
        )

    val readerAutoScrollLinesPerSecond = settingsRepository.readerAutoScrollLinesPerSecond
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DEFAULT_READER_AUTO_SCROLL_LINES_PER_SECOND
        )

    private val readerAutoScrollPlayingOverride = MutableStateFlow<Boolean?>(null)

    val readerAutoScrollPlaying = combine(
        readerAutoScrollEnabled,
        readerAutoScrollPlayingOverride
    ) { autoStartEnabled, playingOverride ->
        playingOverride ?: autoStartEnabled
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val shareUseSystem = settingsRepository.shareUseSystem

    val llmEnabled = settingsRepository.llmEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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

    fun setReaderAutoScrollEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setReaderAutoScrollEnabled(value) }
    }

    fun setReaderAutoScrollLinesPerSecond(value: Float) {
        viewModelScope.launch { settingsRepository.setReaderAutoScrollLinesPerSecond(value) }
    }

    fun setReaderAutoScrollPlaying(value: Boolean) {
        readerAutoScrollPlayingOverride.value = value
    }

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
                if (!ImportedContentIds.isImportedTextItemUrl(currentItem.link) &&
                    currentChannel?.useOriginalContent == true &&
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
        if (ImportedContentIds.isImportedTextItemUrl(currentItem.link)) return
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
                _message.value = ORIGINAL_CONTENT_MODE_HINT_MESSAGE
            }
        }
    }

    fun setOriginalContentEnabled(enabled: Boolean) {
        if (itemId <= 0L) return

        viewModelScope.launch {
            val currentItem = item.value ?: item.filterNotNull().first()
            if (ImportedContentIds.isImportedTextItemUrl(currentItem.link)) return@launch

            setTemporaryOriginalContentOverride(enabled)
            if (!enabled) return@launch

            if (currentItem.isOriginalContentMissing()) {
                repository.requestOriginalContent(currentItem.id, force = true)
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
        viewModelScope.launch(NonCancellable) {
            saveReadingProgress(progress)
        }
    }

    suspend fun saveReadingProgress(progress: Float) {
        if (itemId <= 0L) return
        withContext(NonCancellable + Dispatchers.IO) {
            repository.updateItemReadingProgress(itemId, progress)
        }
    }

    suspend fun loadImportedTextChunk(marker: String, chunkIndex: Int): String? {
        return repository.loadImportedTextChunk(marker, chunkIndex)
    }

    fun clearMessage() {
        _message.value = null
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

private const val LARGE_CONTENT_CACHE_LIMIT_CHARS = 500_000
