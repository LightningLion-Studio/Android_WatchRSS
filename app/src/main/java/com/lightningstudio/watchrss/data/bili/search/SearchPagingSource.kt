package com.lightningstudio.watchrss.data.bili.search

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResultItem
import com.lightningstudio.watchrss.util.AppLogger

class SearchPagingSource(
    private val repository: BiliRepositoryContract,
    private val keyword: String
) : PagingSource<Int, BiliSearchResultItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BiliSearchResultItem> {
        return try {
            val page = params.key ?: 1
            AppLogger.d("SearchPagingSource", "Loading page $page for keyword: $keyword")

            val result = repository.searchAll(keyword, page)

            AppLogger.d("SearchPagingSource", "Search result - isSuccess: ${result.isSuccess}, code: ${result.code}, message: ${result.message}")

            if (!result.isSuccess) {
                AppLogger.e("SearchPagingSource", "Search failed: ${result.message}")
                return LoadResult.Error(Exception(result.message ?: "Search failed"))
            }

            val data = result.data
            if (data == null) {
                AppLogger.e("SearchPagingSource", "Search data is null")
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            }

            val items = data.result ?: emptyList()
            val numPages = data.numPages ?: 1

            AppLogger.d("SearchPagingSource", "Loaded ${items.size} items, numPages: $numPages, current page: $page")

            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (page >= numPages) null else page + 1
            )
        } catch (e: Exception) {
            AppLogger.e("SearchPagingSource", "Exception during search", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, BiliSearchResultItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
