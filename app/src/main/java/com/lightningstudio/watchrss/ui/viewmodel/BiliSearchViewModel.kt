package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.sdk.bili.BiliTrendingWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BiliSearchViewModel(
    private val repository: BiliRepository
) : ViewModel() {

    private val _hotSearchWords = MutableStateFlow<List<BiliTrendingWord>>(emptyList())
    val hotSearchWords: StateFlow<List<BiliTrendingWord>> = _hotSearchWords.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadHotSearch()
        loadSearchHistory()
    }

    fun loadHotSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.getHotSearch()
                if (result.isSuccess) {
                    _hotSearchWords.value = result.data?.list ?: emptyList()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSearchHistory() {
        viewModelScope.launch {
            _searchHistory.value = repository.getSearchHistory()
        }
    }

    fun addSearchHistory(keyword: String) {
        viewModelScope.launch {
            repository.addSearchHistory(keyword)
            _searchHistory.value = repository.getSearchHistory()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            _searchHistory.value = emptyList()
        }
    }
}
