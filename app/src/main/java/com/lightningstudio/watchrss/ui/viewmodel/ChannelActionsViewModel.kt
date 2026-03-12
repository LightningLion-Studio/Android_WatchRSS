package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelActionsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RssRepository
) : ViewModel() {
    private val channelId: Long = savedStateHandle["channelId"] ?: 0L

    val channel = repository.observeChannel(channelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun moveToTop() {
        if (channelId <= 0L) return
        viewModelScope.launch {
            repository.moveChannelToTop(channelId)
        }
    }

    fun togglePinned() {
        val current = channel.value ?: return
        viewModelScope.launch {
            repository.setChannelPinned(channelId, !current.isPinned)
        }
    }

    fun markRead() {
        if (channelId <= 0L) return
        viewModelScope.launch {
            repository.markChannelRead(channelId)
        }
    }

    fun delete() {
        if (channelId <= 0L) return
        viewModelScope.launch {
            repository.deleteChannel(channelId)
        }
    }

    fun isValid(): Boolean = channelId > 0L
}
