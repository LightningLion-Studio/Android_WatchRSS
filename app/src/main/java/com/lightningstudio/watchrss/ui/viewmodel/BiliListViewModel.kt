package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.sdk.bili.BiliFavoritePage
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryCursor
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryPage
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BiliListType(val id: String, val title: String) {
    WATCH_LATER("watch_later", "稍后再看"),
    HISTORY("history", "历史记录"),
    FAVORITE("favorite", "收藏夹");

    companion object {
        fun from(id: String?): BiliListType {
            return values().firstOrNull { it.id == id } ?: WATCH_LATER
        }
    }
}

data class BiliListItem(
    val aid: Long?,
    val bvid: String?,
    val cid: Long?,
    val title: String,
    val cover: String?,
    val subtitle: String?,
    val durationSeconds: Int?
)

data class BiliListUiState(
    val type: BiliListType,
    val isRefreshing: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<BiliListItem> = emptyList(),
    val message: String? = null,
    val canLoadMore: Boolean = false
)

private data class FavoritePageResult(
    val page: BiliFavoritePage?,
    val code: Int
)

class BiliListViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BiliRepository
) : ViewModel() {
    private val type: BiliListType = BiliListType.from(savedStateHandle.get<String>("type"))
    private val _uiState = MutableStateFlow(BiliListUiState(type = type))
    val uiState: StateFlow<BiliListUiState> = _uiState

    private var historyCursor: BiliHistoryCursor? = null
    private var favoritePage = 1
    private var favoriteMediaId: Long? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoadingMore = false, message = null) }
            historyCursor = null
            favoritePage = 1
            when (type) {
                BiliListType.WATCH_LATER -> {
                    val result = repository.fetchToView()
                    if (result.isSuccess) {
                        val items = result.data?.items?.map { it.toListItem() }.orEmpty()
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                items = items,
                                canLoadMore = false
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isRefreshing = false, message = formatBiliError(result.code))
                        }
                    }
                }
                BiliListType.HISTORY -> {
                    val result = repository.fetchHistory()
                    val errorCode = if (result.isSuccess && result.data == null) {
                        BiliErrorCodes.REQUEST_FAILED
                    } else {
                        result.code
                    }
                    handleHistoryResult(result.data, errorCode)
                }
                BiliListType.FAVORITE -> {
                    val page = fetchFavoritePage(reset = true)
                    handleFavoritePage(page)
                }
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            when (type) {
                BiliListType.HISTORY -> return@launch
                BiliListType.FAVORITE -> {
                    if (_uiState.value.isLoadingMore || !_uiState.value.canLoadMore) return@launch
                    _uiState.update { it.copy(isLoadingMore = true, message = null) }
                    val page = fetchFavoritePage(reset = false)
                    handleFavoritePage(page, append = true)
                }
                else -> Unit
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun fetchFavoritePage(reset: Boolean): FavoritePageResult {
        if (reset) {
            favoritePage = 1
            favoriteMediaId = null
        }
        val mediaId = favoriteMediaId ?: run {
            val folders = repository.fetchFavoriteFolders()
            if (!folders.isSuccess) return FavoritePageResult(null, folders.code)
            val first = folders.data?.firstOrNull()
                ?: return FavoritePageResult(null, BiliErrorCodes.MISSING_FAVORITE_FOLDER)
            val id = first.id ?: first.fid
                ?: return FavoritePageResult(null, BiliErrorCodes.MISSING_FAVORITE_FOLDER)
            favoriteMediaId = id
            id
        }
        val result = repository.fetchFavoriteItems(mediaId, pn = favoritePage)
        return if (result.isSuccess && result.data != null) {
            FavoritePageResult(result.data, result.code)
        } else if (result.isSuccess) {
            FavoritePageResult(null, BiliErrorCodes.REQUEST_FAILED)
        } else {
            FavoritePageResult(null, result.code)
        }
    }

    private fun handleFavoritePage(
        result: FavoritePageResult,
        append: Boolean = false
    ) {
        val page = result.page
        if (page == null) {
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    isLoadingMore = false,
                    message = formatBiliError(result.code)
                )
            }
            return
        }
        val items = page.medias.map { media ->
            BiliListItem(
                aid = null,
                bvid = media.bvid,
                cid = null,
                title = media.title.orEmpty(),
                cover = media.cover,
                subtitle = media.owner?.name,
                durationSeconds = media.duration
            )
        }
        favoritePage += 1
        _uiState.update {
            val merged = if (append) it.items + items else items
            it.copy(
                isRefreshing = false,
                isLoadingMore = false,
                items = merged,
                canLoadMore = page.hasMore
            )
        }
    }

    private fun handleHistoryResult(
        page: BiliHistoryPage?,
        errorCode: Int,
        append: Boolean = false
    ) {
        if (page == null) {
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    isLoadingMore = false,
                    message = formatBiliError(errorCode)
                )
            }
            return
        }
        historyCursor = page.cursor
        val items = page.items.map { item ->
            BiliListItem(
                aid = item.history?.oid,
                bvid = item.history?.bvid,
                cid = item.history?.cid,
                title = item.title.orEmpty(),
                cover = item.cover,
                subtitle = item.authorName,
                durationSeconds = item.duration?.toInt()
            )
        }
        _uiState.update {
            val merged = if (append) it.items + items else items
            it.copy(
                isRefreshing = false,
                isLoadingMore = false,
                items = merged,
                canLoadMore = false
            )
        }
    }

    private fun BiliItem.toListItem(): BiliListItem {
        return BiliListItem(
            aid = aid,
            bvid = bvid,
            cid = cid,
            title = title.orEmpty(),
            cover = cover,
            subtitle = owner?.name,
            durationSeconds = duration
        )
    }
}
