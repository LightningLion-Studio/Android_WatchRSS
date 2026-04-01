package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: RssRepository
) : ViewModel() {
    val channels: StateFlow<List<RssChannel>?> = repository.observeHomeChannels()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refresh(channel: RssChannel) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.refreshChannel(channel.id)
            if (result.isFailure) {
                _message.value = result.exceptionOrNull()?.message ?: "刷新失败"
            }
            _isRefreshing.value = false
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            val snapshot = channels.value.orEmpty().filter { channel ->
                BuiltinChannelType.fromUrl(channel.url) == null
            }
            var errorMessage: String? = null
            for (channel in snapshot) {
                val result = repository.refreshChannel(channel.id)
                if (result.isFailure && errorMessage == null) {
                    errorMessage = result.exceptionOrNull()?.message ?: "刷新失败"
                }
            }
            if (errorMessage != null) {
                _message.value = errorMessage
            }
            _isRefreshing.value = false
        }
    }

    fun moveToTop(channel: RssChannel) {
        viewModelScope.launch {
            repository.moveChannelToTop(channel.id)
        }
    }

    fun togglePinned(channel: RssChannel) {
        viewModelScope.launch {
            repository.setChannelPinned(channel.id, !channel.isPinned)
        }
    }

    fun markChannelRead(channel: RssChannel) {
        viewModelScope.launch {
            repository.markChannelRead(channel.id)
        }
    }

    fun deleteChannel(channel: RssChannel) {
        viewModelScope.launch {
            repository.deleteChannel(channel.id)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
