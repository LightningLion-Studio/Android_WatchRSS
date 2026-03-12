package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.AddRssPreview
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssChannelPreview
import com.lightningstudio.watchrss.data.rss.RssRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AddRssStep {
    INPUT,
    PREVIEW,
    EXISTING,
    QR_CODE
}

data class AddRssUiState(
    val url: String = "",
    val step: AddRssStep = AddRssStep.INPUT,
    val isSubmitting: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val errorMessage: String? = null,
    val preview: RssChannelPreview? = null,
    val existingChannel: RssChannel? = null,
    val createdChannelId: Long? = null,
    val serverAddress: String? = null
)

class AddRssViewModel(private val repository: RssRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AddRssUiState())
    val uiState: StateFlow<AddRssUiState> = _uiState.asStateFlow()

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url, errorMessage = null)
    }

    fun submit() {
        val url = _uiState.value.url
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请输入 RSS 地址")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPreview = true, errorMessage = null)
            val result = repository.previewChannel(url)
            _uiState.value = if (result.isSuccess) {
                when (val preview = result.getOrNull()) {
                    is AddRssPreview.Existing -> {
                        _uiState.value.copy(
                            step = AddRssStep.EXISTING,
                            existingChannel = preview.channel,
                            preview = null,
                            isLoadingPreview = false
                        )
                    }
                    is AddRssPreview.Ready -> {
                        _uiState.value.copy(
                            step = AddRssStep.PREVIEW,
                            preview = preview.preview,
                            existingChannel = null,
                            isLoadingPreview = false
                        )
                    }
                    null -> _uiState.value.copy(isLoadingPreview = false)
                }
            } else {
                _uiState.value.copy(
                    isLoadingPreview = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "解析失败"
                )
            }
        }
    }

    fun confirmAdd() {
        val preview = _uiState.value.preview ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
            val result = repository.confirmAddChannel(preview)
            _uiState.value = if (result.isSuccess) {
                _uiState.value.copy(
                    isSubmitting = false,
                    createdChannelId = result.getOrNull()?.id
                )
            } else {
                _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "添加失败"
                )
            }
        }
    }

    fun backToInput() {
        _uiState.value = _uiState.value.copy(
            step = AddRssStep.INPUT,
            preview = null,
            existingChannel = null,
            errorMessage = null,
            isSubmitting = false,
            isLoadingPreview = false
        )
    }

    fun consumeCreatedChannel() {
        _uiState.value = _uiState.value.copy(createdChannelId = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun showQrCode(serverAddress: String) {
        _uiState.value = _uiState.value.copy(
            step = AddRssStep.QR_CODE,
            serverAddress = serverAddress
        )
    }
}
