package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RssRepository
) : ViewModel() {
    private val channelId: Long = savedStateHandle["channelId"] ?: 0L

    val channel = repository.observeChannel(channelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasPlayableMedia = repository.observeChannelHasPlayableMedia(channelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun refresh() {
        viewModelScope.launch {
            repository.refreshChannel(channelId)
        }
    }

    fun markRead() {
        viewModelScope.launch {
            repository.markChannelRead(channelId)
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.deleteChannel(channelId)
        }
    }

    fun clearLocalContent() {
        viewModelScope.launch {
            repository.clearLocalContentChannel(channelId)
        }
    }

    fun setOriginalContentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setChannelOriginalContent(channelId, enabled)
            repository.refreshChannelInBackground(channelId, refreshAll = true)
        }
    }

    fun setContinuePlaybackInBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setChannelContinuePlaybackInBackground(channelId, enabled)
        }
    }

    fun isValid(): Boolean = channelId > 0L
}
