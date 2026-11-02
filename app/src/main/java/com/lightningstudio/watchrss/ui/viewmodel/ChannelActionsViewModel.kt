package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val markingNovelRead = MutableStateFlow(false)
    val isMarkingNovelRead = markingNovelRead.asStateFlow()
    private val novelReadError = MutableStateFlow<String?>(null)
    val readError = novelReadError.asStateFlow()

    /** Only novel callers opt into waiting; ordinary RSS actions retain their current policy. */
    fun markNovelRead(onConfirmed: () -> Unit) {
        if (markingNovelRead.value || !ImportedContentIds.isNovelSourceUrl(channel.value?.url)) return
        markingNovelRead.value = true
        novelReadError.value = null
        viewModelScope.launch {
            try {
                repository.markChannelRead(channelId)
                withTimeout(5_000) {
                    repository.observeChannel(channelId).first { it?.unreadCount == 0 }
                }
                onConfirmed()
            } catch (_: TimeoutCancellationException) {
                novelReadError.value = "已读状态尚未确认，请重试"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                novelReadError.value = "标记已读失败，请重试"
            } finally {
                markingNovelRead.value = false
            }
        }
    }

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

    fun clearLocalContent() {
        if (channelId <= 0L) return
        viewModelScope.launch {
            repository.clearLocalContentChannel(channelId)
        }
    }

    fun isValid(): Boolean = channelId > 0L
}
