package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.settings.DEFAULT_CACHE_LIMIT_MB
import com.lightningstudio.watchrss.data.settings.DEFAULT_READING_FONT_SIZE_SP
import com.lightningstudio.watchrss.data.settings.MB_BYTES
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
        .map { it / MB_BYTES }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val readingThemeDark: StateFlow<Boolean> = settingsRepository.readingThemeDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val readingFontSizeSp: StateFlow<Int> = settingsRepository.readingFontSizeSp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_READING_FONT_SIZE_SP)

    val shareUseSystem: StateFlow<Boolean> = settingsRepository.shareUseSystem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val phoneConnectionEnabled: StateFlow<Boolean> = settingsRepository.phoneConnectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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
}
