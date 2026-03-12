package com.lightningstudio.watchrss.data.bili.comment

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.sdk.bili.BiliCommentData

class CommentPagingSource(
    private val repository: BiliRepository,
    private val oid: Long
) : PagingSource<Long, BiliCommentData>() {

    private var topComments: List<BiliCommentData>? = null

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, BiliCommentData> {
        return try {
            val next = params.key ?: 0L
            val result = repository.getComments(oid, next)

            if (!result.isSuccess) {
                return LoadResult.Error(Exception(result.message ?: "Load comments failed"))
            }

            val data = result.data ?: return LoadResult.Page(
                data = emptyList(),
                prevKey = null,
                nextKey = null
            )

            val items = mutableListOf<BiliCommentData>()

            // On first page, prepend top comments
            if (next == 0L) {
                topComments = data.topReplies
                topComments?.let { items.addAll(it) }
            }

            // Add regular comments
            data.replies?.let { items.addAll(it) }

            val cursor = data.cursor
            val isEnd = cursor?.isEnd ?: true
            val nextCursor = if (isEnd) null else cursor?.next

            LoadResult.Page(
                data = items,
                prevKey = null,
                nextKey = nextCursor
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, BiliCommentData>): Long? {
        return null
    }
}
