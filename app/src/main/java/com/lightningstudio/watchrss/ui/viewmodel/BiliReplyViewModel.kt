package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.bili.comment.ReplyPagingSource
import com.lightningstudio.watchrss.sdk.bili.BiliCommentData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BiliReplyViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: BiliRepository
) : ViewModel() {

    private val oid: Long = savedStateHandle.get<String>("oid")?.toLongOrNull() ?: 0L
    private val root: Long = savedStateHandle.get<String>("root")?.toLongOrNull() ?: 0L

    private val _rootComment = MutableStateFlow<BiliCommentData?>(null)
    val rootComment: StateFlow<BiliCommentData?> = _rootComment.asStateFlow()

    private val replyPagingSource = ReplyPagingSource(repository, oid, root)

    val replyFlow: Flow<PagingData<BiliCommentData>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
        pagingSourceFactory = { replyPagingSource }
    ).flow.cachedIn(viewModelScope)

    fun updateRootComment(comment: BiliCommentData?) {
        _rootComment.value = comment
    }
}
