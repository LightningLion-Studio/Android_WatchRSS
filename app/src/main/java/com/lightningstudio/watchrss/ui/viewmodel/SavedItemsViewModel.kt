package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.data.rss.SavedItem
import com.lightningstudio.watchrss.data.rss.RssRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedItemsViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: RssRepository
) : ViewModel() {
    private val typeName: String = savedStateHandle["saveType"] ?: SaveType.FAVORITE.name
    val saveType: SaveType = runCatching { SaveType.valueOf(typeName) }.getOrDefault(SaveType.FAVORITE)

    val items: StateFlow<List<SavedItem>> = repository.observeSavedItems(saveType)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleSaved(itemId: Long) {
        viewModelScope.launch {
            when (saveType) {
                SaveType.FAVORITE -> repository.toggleFavorite(itemId)
                SaveType.WATCH_LATER -> repository.toggleWatchLater(itemId)
            }
        }
    }
}
