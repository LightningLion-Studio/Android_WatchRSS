package com.lightningstudio.watchrss.ui.viewmodel

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.bili.comment.CommentPagingSource
import com.lightningstudio.watchrss.sdk.bili.BiliCommentData
import kotlinx.coroutines.flow.Flow

class BiliCommentViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: BiliRepository
) : ViewModel() {

    private val oid: Long = savedStateHandle.get<String>("oid")?.toLongOrNull() ?: 0L
    val uploaderMid: Long = savedStateHandle.get<String>("uploaderMid")?.toLongOrNull() ?: 0L

    private val scrollStates = mutableMapOf<String, LazyListState>()

    val commentFlow: Flow<PagingData<BiliCommentData>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
        pagingSourceFactory = { CommentPagingSource(repository, oid) }
    ).flow.cachedIn(viewModelScope)

    fun getScrollState(key: String): LazyListState {
        return scrollStates.getOrPut(key) { LazyListState() }
    }
}
