package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.formatBiliError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BiliPlayerUiState(
    val isLoading: Boolean = true,
    val playUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val message: String? = null,
    val title: String? = null,
    val owner: String? = null,
    val pageTitle: String? = null
)

class BiliPlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BiliRepositoryContract
) : ViewModel() {
    private val _uiState = MutableStateFlow(BiliPlayerUiState())
    val uiState: StateFlow<BiliPlayerUiState> = _uiState

    private val aid: Long? = savedStateHandle.get<String>("aid")?.toLongOrNull()
    private val bvid: String? = savedStateHandle.get<String>("bvid")?.takeIf { it.isNotBlank() }
    private val cid: Long? = savedStateHandle.get<String>("cid")?.toLongOrNull()
    private var resolvedCid: Long? = cid
    private val titleArg: String? = savedStateHandle.get<String>("title")?.trim()?.takeIf { it.isNotBlank() }
    private val ownerArg: String? = savedStateHandle.get<String>("owner")?.trim()?.takeIf { it.isNotBlank() }
    private val pageTitleArg: String? = savedStateHandle.get<String>("pageTitle")?.trim()?.takeIf { it.isNotBlank() }

    init {
        _uiState.update {
            it.copy(
                title = titleArg,
                owner = ownerArg,
                pageTitle = pageTitleArg
            )
        }
        loadPlayUrl()
    }

    fun loadPlayUrl() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val safeCid = resolvedCid ?: resolveCid()
            if (safeCid == null) {
                if (!applyCachedPreviewFallback()) {
                    _uiState.update {
                        it.copy(isLoading = false, message = formatBiliError(BiliErrorCodes.PLAY_PARAM_MISSING))
                    }
                }
                return@launch
            }
            val result = repository.fetchPlayUrlMp4(cid = safeCid, aid = aid, bvid = bvid)
            if (result.isSuccess) {
                val url = result.data?.durl?.firstOrNull()?.url
                val headers = repository.buildPlayHeaders()
                if (url.isNullOrBlank()) {
                    if (!applyCachedPreviewFallback(safeCid)) {
                        _uiState.update {
                            it.copy(isLoading = false, message = formatBiliError(BiliErrorCodes.PLAY_URL_EMPTY))
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, playUrl = url, headers = headers) }
                }
            } else {
                if (!applyCachedPreviewFallback(safeCid)) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = formatBiliError(result.code, result.message)
                        )
                    }
                }
            }
        }
    }

    private suspend fun applyCachedPreviewFallback(cid: Long? = null): Boolean {
        val fallback = if (cid == null) {
            repository.cachedPreviewUriAny(aid, bvid)
        } else {
            repository.cachedPreviewUri(aid, bvid, cid)
        }
        if (fallback.isNullOrBlank()) {
            return false
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                playUrl = fallback,
                headers = emptyMap(),
                message = null
            )
        }
        return true
    }

    private suspend fun resolveCid(): Long? {
        val result = repository.fetchVideoDetail(aid = aid, bvid = bvid)
        val detail = result.data ?: return null
        val pageCid = detail.pages.firstOrNull()?.cid
        val fallbackCid = pageCid ?: detail.item.cid
        resolvedCid = fallbackCid
        applyDetailMeta(detail, fallbackCid)
        return fallbackCid
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun applyDetailMeta(detail: com.lightningstudio.watchrss.sdk.bili.BiliVideoDetail, cid: Long?) {
        val title = detail.item.title?.trim()?.takeIf { it.isNotBlank() }
        val owner = detail.item.owner?.name?.trim()?.takeIf { it.isNotBlank() }
        val pageTitle = cid?.let { targetCid ->
            detail.pages.firstOrNull { it.cid == targetCid }?.part?.trim()?.takeIf { it.isNotBlank() }
        }
        _uiState.update { current ->
            current.copy(
                title = current.title ?: title,
                owner = current.owner ?: owner,
                pageTitle = current.pageTitle ?: pageTitle
            )
        }
    }
}
