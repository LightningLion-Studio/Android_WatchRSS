package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.douyin.DouyinRepository
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DouyinSettingsUiState(
    val isLoggedIn: Boolean = false,
    val message: String? = null,
    val channelId: Long? = null,
    val originalContentEnabled: Boolean = false,
    val deleteEnabled: Boolean = false,
    val showOriginalContent: Boolean = false
)

class DouyinSettingsViewModel(
    private val repository: DouyinRepository,
    private val rssRepository: RssRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DouyinSettingsUiState())
    val uiState: StateFlow<DouyinSettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            rssRepository.ensureBuiltinChannels()
        }
        observeDouyinChannel()
    }

    fun refreshLoginState() {
        viewModelScope.launch {
            val loggedIn = repository.isLoggedIn()
            _uiState.update { it.copy(isLoggedIn = loggedIn) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logoutAndClearMediaCache()
            _uiState.update { it.copy(isLoggedIn = false, message = "已退出登录") }
        }
    }

    fun toggleOriginalContent() {
        val channelId = _uiState.value.channelId ?: return
        val next = !_uiState.value.originalContentEnabled
        viewModelScope.launch {
            rssRepository.setChannelOriginalContent(channelId, next)
            rssRepository.refreshChannelInBackground(channelId, refreshAll = true)
            _uiState.update { it.copy(originalContentEnabled = next) }
        }
    }

    fun deleteChannel() {
        val channelId = _uiState.value.channelId ?: return
        viewModelScope.launch {
            repository.clearCookie()
            rssRepository.deleteChannel(channelId)
            _uiState.update {
                it.copy(
                    channelId = null,
                    originalContentEnabled = false,
                    deleteEnabled = false,
                    showOriginalContent = false,
                    isLoggedIn = false
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun observeDouyinChannel() {
        viewModelScope.launch {
            rssRepository.observeChannels().collect { channels ->
                val channel = channels.firstOrNull { it.url == BuiltinChannelType.DOUYIN.url }
                _uiState.update {
                    it.copy(
                        channelId = channel?.id,
                        originalContentEnabled = channel?.useOriginalContent ?: false,
                        deleteEnabled = channel != null,
                        showOriginalContent = channel != null
                    )
                }
            }
        }
    }
}
