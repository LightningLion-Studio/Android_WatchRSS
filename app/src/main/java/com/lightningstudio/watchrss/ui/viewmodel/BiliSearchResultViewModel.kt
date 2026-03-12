package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.bili.search.SearchPagingSource
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BiliSearchResultViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: BiliRepository
) : ViewModel() {

    private val keyword: String = savedStateHandle.get<String>("keyword") ?: ""

    val searchResultFlow: Flow<PagingData<BiliSearchResultItem.Video>> = Pager(
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
    }.cachedIn(viewModelScope)
}
