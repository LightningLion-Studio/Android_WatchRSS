package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.settings.DEFAULT_CACHE_LIMIT_MB
import com.lightningstudio.watchrss.data.settings.DEFAULT_READING_FONT_SIZE_SP
import com.lightningstudio.watchrss.data.settings.DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE
import com.lightningstudio.watchrss.data.settings.MB_BYTES
import com.lightningstudio.watchrss.data.settings.RssInlineImagePrefetchMode
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val rssRepository: RssRepository
) : ViewModel() {
    val cacheLimitMb: StateFlow<Long> = settingsRepository.cacheLimitBytes
        .map { it / MB_BYTES }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_CACHE_LIMIT_MB)

    val cacheUsageMb: StateFlow<Long> = rssRepository.observeCacheUsageBytes()
        .map { bytes ->
            if (bytes <= 0L) 0L else (bytes + MB_BYTES - 1L) / MB_BYTES
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val readingThemeDark: StateFlow<Boolean> = settingsRepository.readingThemeDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val readingFontSizeSp: StateFlow<Int> = settingsRepository.readingFontSizeSp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_READING_FONT_SIZE_SP)

    val shareUseSystem: StateFlow<Boolean> = settingsRepository.shareUseSystem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val phoneConnectionEnabled: StateFlow<Boolean> = settingsRepository.phoneConnectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val rssInlineImagePrefetchMode: StateFlow<RssInlineImagePrefetchMode> =
        settingsRepository.rssInlineImagePrefetchMode
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE
            )

    val llmFeatureEnabled: StateFlow<Boolean> = settingsRepository.llmFeatureEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val llmAutoSummarize: StateFlow<Boolean> = settingsRepository.llmAutoSummarize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val llmShowTokenUsage: StateFlow<Boolean> = settingsRepository.llmShowTokenUsage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val llmPromptPreset: StateFlow<Int> = settingsRepository.llmPromptPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun updateCacheLimitMb(value: Long) {
        viewModelScope.launch {
            settingsRepository.setCacheLimitBytes(value * MB_BYTES)
            rssRepository.trimCacheToLimit()
        }
    }

    fun toggleReadingTheme() {
        viewModelScope.launch {
            val current = readingThemeDark.value
            settingsRepository.setReadingThemeDark(!current)
        }
    }

    fun updateReadingFontSizeSp(value: Int) {
        viewModelScope.launch {
            settingsRepository.setReadingFontSizeSp(value)
        }
    }

    fun toggleShareUseSystem() {
        viewModelScope.launch {
            val current = shareUseSystem.value
            settingsRepository.setShareUseSystem(!current)
        }
    }

    fun togglePhoneConnection() {
        viewModelScope.launch {
            val current = phoneConnectionEnabled.value
            settingsRepository.setPhoneConnectionEnabled(!current)
        }
    }

    fun updateRssInlineImagePrefetchMode(value: RssInlineImagePrefetchMode) {
        viewModelScope.launch {
            settingsRepository.setRssInlineImagePrefetchMode(value)
        }
    }

    fun toggleLlmFeatureEnabled() {
        viewModelScope.launch {
            settingsRepository.setLlmFeatureEnabled(!llmFeatureEnabled.value)
        }
    }

    fun toggleLlmAutoSummarize() {
        viewModelScope.launch {
            settingsRepository.setLlmAutoSummarize(!llmAutoSummarize.value)
        }
    }

    fun toggleLlmShowTokenUsage() {
        viewModelScope.launch {
            settingsRepository.setLlmShowTokenUsage(!llmShowTokenUsage.value)
        }
    }

    fun updateLlmPromptPreset(value: Int) {
        viewModelScope.launch {
            settingsRepository.setLlmPromptPreset(value)
        }
    }
}
