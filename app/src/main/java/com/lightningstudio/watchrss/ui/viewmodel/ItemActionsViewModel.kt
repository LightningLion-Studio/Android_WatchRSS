package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.SavedState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemActionsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RssRepository
) : ViewModel() {
    private val itemId: Long = savedStateHandle["itemId"] ?: 0L

    val item = repository.observeItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val savedState = repository.observeSavedState(itemId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SavedState(isFavorite = false, isWatchLater = false)
        )

    fun toggleFavorite() {
        if (itemId <= 0L) return
        viewModelScope.launch {
            repository.toggleFavorite(itemId)
        }
    }

    fun toggleWatchLater() {
        if (itemId <= 0L) return
        viewModelScope.launch {
            repository.toggleWatchLater(itemId)
        }
    }

    fun isValid(): Boolean = itemId > 0L
}
