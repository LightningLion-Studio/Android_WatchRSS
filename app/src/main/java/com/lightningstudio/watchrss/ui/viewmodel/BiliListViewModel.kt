package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryCursor
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryPage
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

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

data class BiliFavoriteFolderChoice(val id: Long, val title: String)

data class BiliListUiState(
    val type: BiliListType,
    val isRefreshing: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<BiliListItem> = emptyList(),
    val message: String? = null,
    val messageEventId: Long = 0L,
    val canLoadMore: Boolean = false,
    val favoriteFolders: List<BiliFavoriteFolderChoice> = emptyList(),
    val selectedFavoriteFolderId: Long? = null,
    val favoriteError: String? = null
)

private data class FavoriteFolderSnapshot(
    val items: List<BiliListItem>, val nextPage: Int, val hasMore: Boolean
)

class BiliListViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: BiliRepositoryContract
) : ViewModel() {
    private val type: BiliListType = BiliListType.from(savedStateHandle.get<String>("type"))
    private val _uiState = MutableStateFlow(BiliListUiState(
        type = type,
        selectedFavoriteFolderId = if (type == BiliListType.FAVORITE) {
            savedStateHandle.get<Long>(SAVED_FAVORITE_FOLDER_ID)
        } else null
    ))
    val uiState: StateFlow<BiliListUiState> = _uiState

    private var historyCursor: BiliHistoryCursor? = null
    private val favoriteSnapshots = mutableMapOf<Long, FavoriteFolderSnapshot>()
    private var favoriteJob: Job? = null
    private var favoriteRequestVersion = 0L

    init {
        refresh()
    }

    fun refresh() {
        if (type == BiliListType.FAVORITE) {
            refreshFavoriteFolders()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, isLoadingMore = false, message = null) }
            historyCursor = null
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
                            it.copy(
                                isRefreshing = false,
                                message = formatBiliError(result.code, result.message),
                                messageEventId = it.messageEventId + 1
                            )
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
                BiliListType.FAVORITE -> Unit
            }
        }
    }

    fun loadMore() {
        if (type != BiliListType.FAVORITE || _uiState.value.isRefreshing ||
            _uiState.value.isLoadingMore || !_uiState.value.canLoadMore) return
        val id = _uiState.value.selectedFavoriteFolderId ?: return
        val snapshot = favoriteSnapshots[id] ?: return
        _uiState.update { it.copy(isLoadingMore = true) }
        startFavoriteRequest { version -> loadFavoritePage(id, snapshot.nextPage, version) }
    }

    fun selectFavoriteFolder(id: Long) {
        if (type != BiliListType.FAVORITE || id == _uiState.value.selectedFavoriteFolderId ||
            _uiState.value.favoriteFolders.none { it.id == id }) return
        favoriteJob?.cancel()
        favoriteRequestVersion++
        savedStateHandle[SAVED_FAVORITE_FOLDER_ID] = id
        val snapshot = favoriteSnapshots[id]
        _uiState.update { it.copy(
            selectedFavoriteFolderId = id, items = snapshot?.items.orEmpty(),
            isRefreshing = snapshot == null, isLoadingMore = false,
            canLoadMore = snapshot?.hasMore ?: false, message = null, favoriteError = null
        ) }
        if (snapshot == null) startFavoriteRequest { version -> loadFavoritePage(id, 1, version) }
    }

    fun clearMessage(expectedEventId: Long? = null) {
        if (expectedEventId != null && _uiState.value.messageEventId != expectedEventId) return
        _uiState.update { it.copy(message = null) }
    }

    private fun startFavoriteRequest(block: suspend (Long) -> Unit) {
        favoriteJob?.cancel()
        val version = ++favoriteRequestVersion
        favoriteJob = viewModelScope.launch {
            try {
                block(version)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == favoriteRequestVersion) favoriteFailure(BiliErrorCodes.REQUEST_FAILED)
            }
        }
    }

    private fun refreshFavoriteFolders() {
        startFavoriteRequest { version ->
            _uiState.update { it.copy(isRefreshing = true, isLoadingMore = false, message = null, favoriteError = null) }
            val result = repository.fetchFavoriteFolders()
            if (version != favoriteRequestVersion) return@startFavoriteRequest
            if (!result.isSuccess) {
                favoriteFailure(result.code)
                return@startFavoriteRequest
            }
            val folders = result.data.orEmpty().mapNotNull { folder ->
                (folder.id ?: folder.fid)?.takeIf { it > 0 }?.let {
                    BiliFavoriteFolderChoice(it, folder.title?.takeIf(String::isNotBlank) ?: "收藏夹 $it")
                }
            }.distinctBy { it.id }
            favoriteSnapshots.keys.retainAll(folders.map { it.id }.toSet())
            val selected = _uiState.value.selectedFavoriteFolderId?.takeIf { id -> folders.any { it.id == id } }
                ?: folders.firstOrNull()?.id
            savedStateHandle[SAVED_FAVORITE_FOLDER_ID] = selected
            val previous = selected?.let(favoriteSnapshots::get)
            _uiState.update { it.copy(
                favoriteFolders = folders, selectedFavoriteFolderId = selected,
                items = previous?.items.orEmpty(), canLoadMore = previous?.hasMore ?: false
            ) }
            if (selected == null) {
                _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false) }
            } else loadFavoritePage(selected, 1, version)
        }
    }

    private suspend fun loadFavoritePage(id: Long, pageNumber: Int, version: Long) {
        _uiState.update { it.copy(isRefreshing = pageNumber == 1, isLoadingMore = pageNumber > 1, message = null, favoriteError = null) }
        val result = repository.fetchFavoriteItems(id, pn = pageNumber)
        if (version != favoriteRequestVersion || _uiState.value.selectedFavoriteFolderId != id) return
        val page = result.data
        if (!result.isSuccess || page == null) {
            favoriteFailure(if (result.isSuccess) BiliErrorCodes.REQUEST_FAILED else result.code)
            return
        }
        val incoming = page.medias.map { media ->
            BiliListItem(null, media.bvid, null, media.title.orEmpty(), media.cover,
                media.owner?.name, media.duration)
        }
        val items = if (pageNumber == 1) incoming else favoriteSnapshots[id]?.items.orEmpty() + incoming
        favoriteSnapshots[id] = FavoriteFolderSnapshot(items, pageNumber + 1, page.hasMore)
        _uiState.update { it.copy(items = items, canLoadMore = page.hasMore,
            isRefreshing = false, isLoadingMore = false, message = null, favoriteError = null) }
    }

    private fun favoriteFailure(code: Int) {
        _uiState.update {
            it.copy(
                isRefreshing = false,
                isLoadingMore = false,
                message = formatBiliError(code),
                messageEventId = it.messageEventId + 1,
                favoriteError = formatBiliError(code)
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
                    message = formatBiliError(errorCode),
                    messageEventId = it.messageEventId + 1
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

    private companion object {
        const val SAVED_FAVORITE_FOLDER_ID = "bili_list_selected_favorite_folder_id"
    }
}
