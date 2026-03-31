package com.lightningstudio.watchrss.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.buildDouyinPlaybackWebUrl
import com.lightningstudio.watchrss.data.douyin.formatDouyinError
import com.lightningstudio.watchrss.data.douyin.isDouyinWebUrl
import com.lightningstudio.watchrss.data.douyin.parseDouyinAwemeId
import com.lightningstudio.watchrss.data.douyin.shouldRefreshDouyinPlayback
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class RssPlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val douyinRepository: DouyinRepositoryContract
) : ViewModel() {
    private val rawPlayUrl: String = savedStateHandle.get<String>(KEY_PLAY_URL)?.trim().orEmpty()
    private val rawWebUrl: String = savedStateHandle.get<String>(KEY_WEB_URL)?.trim().orEmpty()
    private val rawAwemeId: String = savedStateHandle.get<String>(KEY_AWEME_ID)?.trim().orEmpty()
    private val douyinAwemeId: String? = rawAwemeId.takeIf { it.isNotEmpty() }
        ?: parseDouyinAwemeId(rawWebUrl)
        ?: parseDouyinAwemeId(rawPlayUrl)
    private val isDouyinTarget: Boolean = douyinAwemeId != null ||
        isDouyinWebUrl(rawWebUrl) ||
        isDouyinWebUrl(rawPlayUrl)
    private val normalizedFallbackPlayUrl: String? = normalizePlayUrl(rawPlayUrl)
        ?.takeUnless { shouldRefreshDouyinPlayback(it) }

    private val _uiState = MutableStateFlow(BiliPlayerUiState())
    val uiState: StateFlow<BiliPlayerUiState> = _uiState

    private var resolvedPlayUrl: String = rawPlayUrl
    private var resolvedWebUrl: String = if (isDouyinTarget) {
        buildDouyinPlaybackWebUrl(
            awemeId = douyinAwemeId,
            fallbackUrl = rawWebUrl.ifBlank { rawPlayUrl }
        ).orEmpty()
    } else {
        rawWebUrl
    }
    private var playHeaders: Map<String, String> = emptyMap()
    private var sourceVersion: Long = 0L
    private var refreshJob: Job? = null

    init {
        loadPlayUrl()
    }

    fun loadPlayUrl() {
        if (!isDouyinTarget) {
            emitCurrentState(
                isLoading = false,
                message = if (normalizePlayUrl(resolvedPlayUrl) != null) {
                    null
                } else {
                    "播放地址为空"
                }
            )
            return
        }

        if (refreshJob?.isActive == true) {
            return
        }

        refreshJob = viewModelScope.launch {
            ensureDouyinHeaders()
            val targetAwemeId = douyinAwemeId
            if (targetAwemeId.isNullOrBlank()) {
                emitCurrentState(
                    isLoading = false,
                    message = if (normalizePlayUrl(resolvedPlayUrl) != null) {
                        null
                    } else {
                        "播放地址为空"
                    }
                )
                refreshJob = null
                return@launch
            }

            emitCurrentState(isLoading = true, message = null, sourceUrl = null)
            refreshDouyinPlayback(
                awemeId = targetAwemeId,
                allowFallback = true
            )
            refreshJob = null
        }
    }

    fun recoverFromPlaybackError(): Boolean {
        if (!isDouyinTarget) {
            return false
        }
        val targetAwemeId = douyinAwemeId ?: return false
        if (refreshJob?.isActive == true) {
            return true
        }

        refreshJob = viewModelScope.launch {
            ensureDouyinHeaders()
            emitCurrentState(isLoading = true, message = null, sourceUrl = null)
            refreshDouyinPlayback(
                awemeId = targetAwemeId,
                allowFallback = false
            )
            refreshJob = null
        }
        return true
    }

    fun webUrl(): String? {
        return if (isDouyinTarget) {
            resolvedWebUrl.ifBlank { null }
        } else {
            rawWebUrl.ifBlank { rawPlayUrl }.ifBlank { null }
        }
    }

    fun currentPlayUrl(): String? {
        return normalizePlayUrl(resolvedPlayUrl)
    }

    fun awemeId(): String? = douyinAwemeId

    private suspend fun ensureDouyinHeaders() {
        if (playHeaders.isNotEmpty()) {
            return
        }
        playHeaders = douyinRepository.buildPlayHeaders()
    }

    private suspend fun refreshDouyinPlayback(
        awemeId: String,
        allowFallback: Boolean
    ) {
        val result = douyinRepository.fetchVideo(awemeId)
        when {
            result.isSuccess -> {
                when (val content = result.data) {
                    is DouyinContent.Video -> {
                        resolvedPlayUrl = content.playUrl.trim()
                        resolvedWebUrl = buildDouyinPlaybackWebUrl(
                            awemeId = content.awemeId,
                            fallbackUrl = rawWebUrl.ifBlank { rawPlayUrl }
                        ).orEmpty()
                        sourceVersion += 1L
                        emitCurrentState(isLoading = false, message = null)
                    }

                    is DouyinContent.Note -> {
                        if (!emitDouyinFallbackIfAvailable(allowFallback)) {
                            emitCurrentState(
                                isLoading = false,
                                message = "当前内容暂无可播放视频",
                                sourceUrl = null
                            )
                        }
                    }

                    null -> {
                        if (!emitDouyinFallbackIfAvailable(allowFallback)) {
                            emitCurrentState(
                                isLoading = false,
                                message = "加载失败",
                                sourceUrl = null
                            )
                        }
                    }
                }
            }

            result.code == DouyinErrorCodes.NOT_LOGGED_IN -> {
                douyinRepository.clearCookie()
                emitCurrentState(
                    isLoading = false,
                    message = "需要登录",
                    sourceUrl = null
                )
            }

            else -> {
                if (!emitDouyinFallbackIfAvailable(allowFallback)) {
                    emitCurrentState(
                        isLoading = false,
                        message = formatDouyinError(result.code, result.message),
                        sourceUrl = null
                    )
                }
            }
        }
    }

    private fun emitDouyinFallbackIfAvailable(allowFallback: Boolean): Boolean {
        val fallback = normalizedFallbackPlayUrl ?: return false
        if (!allowFallback) {
            return false
        }
        resolvedPlayUrl = fallback
        emitCurrentState(isLoading = false, message = null, sourceUrl = fallback)
        return true
    }

    private fun emitCurrentState(
        isLoading: Boolean,
        message: String?,
        sourceUrl: String? = normalizePlayUrl(resolvedPlayUrl)
            ?.takeUnless { isDouyinTarget && shouldRefreshDouyinPlayback(it) }
    ) {
        val source = sourceUrl?.let { url ->
            BiliPlaybackSource(
                url = url,
                headers = if (isDouyinTarget) playHeaders else emptyMap(),
                kind = BiliPlaybackSourceKind.REMOTE,
                cacheKey = if (isDouyinTarget) {
                    "${douyinAwemeId ?: url}:$sourceVersion"
                } else {
                    null
                }
            )
        }

        _uiState.update {
            it.copy(
                isLoading = isLoading,
                initialSource = source,
                upgradeSource = null,
                isUpgradeLoading = false,
                upgradeErrorMessage = null,
                message = message
            )
        }
    }

    private fun normalizePlayUrl(url: String): String? {
        if (url.isBlank()) return null
        if (url.startsWith("/")) {
            return Uri.fromFile(File(url)).toString()
        }
        return url
    }

    companion object {
        const val KEY_PLAY_URL = "playUrl"
        const val KEY_WEB_URL = "webUrl"
        const val KEY_AWEME_ID = "awemeId"
    }
}
