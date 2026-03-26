package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.formatBiliError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BiliPlaybackSourceKind {
    PREVIEW,
    REMOTE
}

data class BiliPlaybackSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val kind: BiliPlaybackSourceKind
)

data class BiliPlayerUiState(
    val isLoading: Boolean = true,
    val initialSource: BiliPlaybackSource? = null,
    val upgradeSource: BiliPlaybackSource? = null,
    val isUpgradeLoading: Boolean = false,
    val upgradeErrorMessage: String? = null,
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
    private var loadJob: Job? = null
    private var remoteLoadJob: Job? = null
    private var loadGeneration: Long = 0L

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
        loadJob?.cancel()
        remoteLoadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    initialSource = null,
                    upgradeSource = null,
                    isUpgradeLoading = false,
                    upgradeErrorMessage = null,
                    message = null
                )
            }
            val safeCid = resolvedCid ?: resolveCid()
            if (safeCid == null) {
                if (generation != loadGeneration) return@launch
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = formatBiliError(BiliErrorCodes.PLAY_PARAM_MISSING)
                    )
                }
                return@launch
            }
            if (generation != loadGeneration) return@launch

            val previewSource = repository.cachedPreviewUri(aid, bvid, safeCid)
                ?.takeIf { it.isNotBlank() }
                ?.let(::previewSource)
            if (generation != loadGeneration) return@launch
            if (previewSource != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        initialSource = previewSource,
                        upgradeSource = null,
                        isUpgradeLoading = true,
                        upgradeErrorMessage = null,
                        message = null
                    )
                }
            }
            startRemoteLoad(safeCid, generation)
        }
    }

    fun onPreviewPlaybackFailed() {
        val safeCid = resolvedCid ?: cid ?: return
        viewModelScope.launch {
            repository.clearCachedPreview(aid, bvid, safeCid)
            val upgradeSource = _uiState.value.upgradeSource
            if (upgradeSource != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        initialSource = upgradeSource,
                        upgradeSource = null,
                        isUpgradeLoading = false,
                        upgradeErrorMessage = null,
                        message = null
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    initialSource = null,
                    upgradeSource = null,
                    isUpgradeLoading = false,
                    upgradeErrorMessage = null,
                    message = null
                )
            }
            if (remoteLoadJob?.isActive != true) {
                startRemoteLoad(safeCid, loadGeneration)
            }
        }
    }

    fun promoteUpgradeSource() {
        _uiState.update { current ->
            val upgradeSource = current.upgradeSource ?: return@update current
            current.copy(
                initialSource = upgradeSource,
                upgradeSource = null,
                isLoading = false,
                isUpgradeLoading = false,
                upgradeErrorMessage = null,
                message = null
            )
        }
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

    private fun startRemoteLoad(cid: Long, generation: Long) {
        remoteLoadJob?.cancel()
        remoteLoadJob = viewModelScope.launch {
            val result = repository.fetchPlayUrlMp4(cid = cid, aid = aid, bvid = bvid)
            if (generation != loadGeneration) return@launch
            if (result.isSuccess) {
                val url = result.data?.durl?.firstOrNull()?.url
                if (url.isNullOrBlank()) {
                    applyRemoteFailure(formatBiliError(BiliErrorCodes.PLAY_URL_EMPTY), generation)
                    return@launch
                }
                val remoteSource = BiliPlaybackSource(
                    url = url,
                    headers = repository.buildPlayHeaders(),
                    kind = BiliPlaybackSourceKind.REMOTE
                )
                applyRemoteSuccess(remoteSource, generation)
                return@launch
            }
            applyRemoteFailure(formatBiliError(result.code, result.message), generation)
        }
    }

    private fun applyRemoteSuccess(source: BiliPlaybackSource, generation: Long) {
        if (generation != loadGeneration) return
        _uiState.update { current ->
            if (generation != loadGeneration) {
                current
            } else if (current.initialSource?.kind == BiliPlaybackSourceKind.PREVIEW) {
                current.copy(
                    isLoading = false,
                    upgradeSource = source,
                    isUpgradeLoading = false,
                    upgradeErrorMessage = null,
                    message = null
                )
            } else {
                current.copy(
                    isLoading = false,
                    initialSource = source,
                    upgradeSource = null,
                    isUpgradeLoading = false,
                    upgradeErrorMessage = null,
                    message = null
                )
            }
        }
    }

    private fun applyRemoteFailure(message: String, generation: Long) {
        if (generation != loadGeneration) return
        _uiState.update { current ->
            if (generation != loadGeneration) {
                current
            } else if (current.initialSource?.kind == BiliPlaybackSourceKind.PREVIEW) {
                current.copy(
                    isLoading = false,
                    upgradeSource = null,
                    isUpgradeLoading = false,
                    upgradeErrorMessage = message,
                    message = null
                )
            } else {
                current.copy(
                    isLoading = false,
                    upgradeSource = null,
                    isUpgradeLoading = false,
                    upgradeErrorMessage = null,
                    message = message
                )
            }
        }
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

    private fun previewSource(url: String): BiliPlaybackSource {
        return BiliPlaybackSource(
            url = url,
            headers = emptyMap(),
            kind = BiliPlaybackSourceKind.PREVIEW
        )
    }
}
