package com.lightningstudio.watchrss.data.bili.comment

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.sdk.bili.BiliCommentData

class ReplyPagingSource(
    private val repository: BiliRepository,
    private val oid: Long,
    private val root: Long
) : PagingSource<Int, BiliCommentData>() {

    var rootComment: BiliCommentData? = null
        private set

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BiliCommentData> {
        return try {
            val page = params.key ?: 1
            val result = repository.getReplies(oid, root, page)

            if (!result.isSuccess) {
                return LoadResult.Error(Exception(result.message ?: "Load replies failed"))
            }

            val data = result.data ?: return LoadResult.Page(
                data = emptyList(),
                prevKey = null,
                nextKey = null
            )

            // Store root comment on first page
            if (page == 1) {
                rootComment = data.root
            }

            val items = data.replies ?: emptyList()

            // Simple page-based pagination
            val hasMore = items.isNotEmpty()

            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (hasMore) page + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, BiliCommentData>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
