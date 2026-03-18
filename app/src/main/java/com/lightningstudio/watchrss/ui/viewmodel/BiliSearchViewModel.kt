package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.search.SearchPagingSource
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResultItem
import com.lightningstudio.watchrss.sdk.bili.BiliTrendingWord
import com.lightningstudio.watchrss.ui.utils.BiliFormatUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface BiliSearchSubmitAction {
    data object None : BiliSearchSubmitAction
    data object OpenResults : BiliSearchSubmitAction
    data class OpenVideo(val aid: Long?, val bvid: String?) : BiliSearchSubmitAction
}

class BiliSearchViewModel(
    private val repository: BiliRepositoryContract
) : ViewModel() {

    private val _hotSearchWords = MutableStateFlow<List<BiliTrendingWord>>(emptyList())
    val hotSearchWords: StateFlow<List<BiliTrendingWord>> = _hotSearchWords.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _draftQuery = MutableStateFlow("")
    val draftQuery: StateFlow<String> = _draftQuery.asStateFlow()

    private val _activeQuery = MutableStateFlow("")
    val activeQuery: StateFlow<String> = _activeQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResultFlow: Flow<PagingData<BiliSearchResultItem.Video>> = activeQuery
        .map(::normalizeQuery)
        .distinctUntilChanged()
        .flatMapLatest { keyword ->
            if (keyword.isBlank()) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(
                        pageSize = 20,
                        enablePlaceholders = false,
                        initialLoadSize = 20
                    ),
                    pagingSourceFactory = { SearchPagingSource(repository, keyword) }
                ).flow.map { pagingData ->
                    pagingData
                        .filter { it is BiliSearchResultItem.Video }
                        .map { it as BiliSearchResultItem.Video }
                }
            }
        }
        .cachedIn(viewModelScope)

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

    fun updateDraftQuery(query: String) {
        _draftQuery.value = query
    }

    fun submitSearch(query: String = _draftQuery.value): BiliSearchSubmitAction {
        val normalized = normalizeQuery(query)
        _draftQuery.value = normalized
        if (normalized.isBlank()) {
            return BiliSearchSubmitAction.None
        }
        if (BiliFormatUtils.isVideoId(normalized)) {
            val (aid, bvid) = BiliFormatUtils.parseVideoId(normalized)
            _draftQuery.value = canonicalVideoQuery(aid = aid, bvid = bvid) ?: normalized
            return if (aid != null || !bvid.isNullOrBlank()) {
                BiliSearchSubmitAction.OpenVideo(aid = aid, bvid = bvid)
            } else {
                BiliSearchSubmitAction.None
            }
        }
        _activeQuery.value = normalized
        addSearchHistory(normalized)
        return BiliSearchSubmitAction.OpenResults
    }

    fun addSearchHistory(keyword: String) {
        val normalized = normalizeQuery(keyword)
        if (normalized.isBlank()) {
            return
        }
        viewModelScope.launch {
            repository.addSearchHistory(normalized)
            _searchHistory.value = repository.getSearchHistory()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            _searchHistory.value = emptyList()
        }
    }

    fun resetSearchSession() {
        _draftQuery.value = ""
        _activeQuery.value = ""
    }

    private fun normalizeQuery(query: String): String {
        val normalized = StringBuilder()
        var pendingSpace = false
        query.forEach { char ->
            val isWhitespace = char.isWhitespace() || Character.isSpaceChar(char)
            if (isWhitespace) {
                pendingSpace = normalized.isNotEmpty()
            } else {
                if (pendingSpace) {
                    normalized.append(' ')
                    pendingSpace = false
                }
                normalized.append(char)
            }
        }
        return normalized.toString().trim()
    }

    private fun canonicalVideoQuery(aid: Long?, bvid: String?): String? {
        return when {
            !bvid.isNullOrBlank() -> bvid
            aid != null -> "av$aid"
            else -> null
        }
    }
}
