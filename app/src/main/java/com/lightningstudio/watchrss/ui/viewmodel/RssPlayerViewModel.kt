package com.lightningstudio.watchrss.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class RssPlayerViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val rawPlayUrl: String = savedStateHandle.get<String>(KEY_PLAY_URL)?.trim().orEmpty()
    private val rawWebUrl: String = savedStateHandle.get<String>(KEY_WEB_URL)?.trim().orEmpty()

    private val _uiState = MutableStateFlow(BiliPlayerUiState())
    val uiState: StateFlow<BiliPlayerUiState> = _uiState

    init {
        loadPlayUrl()
    }

    fun loadPlayUrl() {
        val normalized = normalizePlayUrl(rawPlayUrl)
        if (normalized.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, playUrl = null, message = "播放地址为空") }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                playUrl = normalized,
                headers = emptyMap(),
                message = null
            )
        }
    }

    fun webUrl(): String? {
        return rawWebUrl.ifBlank { rawPlayUrl }.ifBlank { null }
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
    }
}
