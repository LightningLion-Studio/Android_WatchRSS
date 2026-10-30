package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.AddRssPreview
import com.lightningstudio.watchrss.data.rss.RssBrowserChallengeRequiredException
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
    QR_CODE,
    BROWSER_VERIFICATION
}

data class AddRssUiState(
    val url: String = "",
    val step: AddRssStep = AddRssStep.INPUT,
    val isSubmitting: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val errorMessage: String? = null,
    val requiresBrowserVerification: Boolean = false,
    val browserChallengeHtml: String = "",
    val browserVerificationUrl: String? = null,
    val preview: RssChannelPreview? = null,
    val existingChannel: RssChannel? = null,
    val createdChannelId: Long? = null,
    val serverAddress: String? = null
)

class AddRssViewModel(private val repository: RssRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AddRssUiState())
    val uiState: StateFlow<AddRssUiState> = _uiState.asStateFlow()

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            url = url,
            errorMessage = null,
            requiresBrowserVerification = false,
            browserChallengeHtml = "",
            browserVerificationUrl = null
        )
    }

    fun submit() {
        val url = _uiState.value.url
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请输入 RSS 地址")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingPreview = true,
                errorMessage = null,
                requiresBrowserVerification = false
            )
            val result = repository.previewChannel(url)
            showPreviewResult(result)
        }
    }

    fun openBrowserVerification() {
        _uiState.value = _uiState.value.copy(
            step = AddRssStep.BROWSER_VERIFICATION,
            errorMessage = null
        )
    }

    fun finishBrowserVerification(content: String) {
        val current = _uiState.value
        if (content.isBlank() || current.url.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingPreview = true,
                errorMessage = null
            )
            showPreviewResult(repository.previewChannelFromContent(current.url, content))
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
                    errorMessage = result.exceptionOrNull().toUserFacingMessage(
                        fallback = "添加 RSS 失败，请稍后重试"
                    )
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
            requiresBrowserVerification = false,
            browserChallengeHtml = "",
            browserVerificationUrl = null,
            isSubmitting = false,
            isLoadingPreview = false
        )
    }

    fun consumeCreatedChannel() {
        _uiState.value = _uiState.value.copy(createdChannelId = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            requiresBrowserVerification = false,
            browserChallengeHtml = "",
            browserVerificationUrl = null
        )
    }

    fun showQrCode(serverAddress: String) {
        _uiState.value = _uiState.value.copy(
            step = AddRssStep.QR_CODE,
            serverAddress = serverAddress
        )
    }

    private fun showPreviewResult(result: Result<AddRssPreview>) {
        val current = _uiState.value
        _uiState.value = if (result.isSuccess) {
            when (val preview = result.getOrNull()) {
                is AddRssPreview.Existing -> current.copy(
                    step = AddRssStep.EXISTING,
                    existingChannel = preview.channel,
                    preview = null,
                    isLoadingPreview = false,
                    requiresBrowserVerification = false,
                    browserChallengeHtml = "",
                    browserVerificationUrl = null
                )
                is AddRssPreview.Ready -> current.copy(
                    step = AddRssStep.PREVIEW,
                    preview = preview.preview,
                    existingChannel = null,
                    isLoadingPreview = false,
                    requiresBrowserVerification = false,
                    browserChallengeHtml = "",
                    browserVerificationUrl = null
                )
                null -> current.copy(isLoadingPreview = false)
            }
        } else {
            val error = result.exceptionOrNull()
            val challenge = error.browserChallengeCause()
            val requiresVerification = challenge != null
            current.copy(
                step = AddRssStep.INPUT,
                isLoadingPreview = false,
                requiresBrowserVerification = requiresVerification,
                browserChallengeHtml = challenge?.challengeHtml.orEmpty(),
                browserVerificationUrl = challenge?.challengeUrl,
                errorMessage = if (requiresVerification) {
                    "该订阅需要先完成网页安全验证"
                } else {
                    error.toUserFacingMessage("无法解析此 RSS 地址，请检查地址是否正确")
                }
            )
        }
    }
}

private fun Throwable?.browserChallengeCause(): RssBrowserChallengeRequiredException? =
    generateSequence(this) { it.cause }.filterIsInstance<RssBrowserChallengeRequiredException>().firstOrNull()

private fun Throwable?.toUserFacingMessage(fallback: String): String {
    val message = this?.message?.trim().orEmpty()
    if (message.isBlank()) return fallback

    val normalized = message.lowercase()
    return when {
        listOf("unable to resolve host", "unknownhost", "network is unreachable", "failed to connect")
            .any(normalized::contains) -> "网络连接失败，请检查网络后重试"
        listOf("timeout", "timed out").any(normalized::contains) -> "请求超时，请稍后重试"
        listOf("ssl", "handshake", "certificate").any(normalized::contains) -> "安全连接失败，请检查订阅地址"
        listOf("parsing", "parse", "xml", "feed", "atom", "rdf")
            .any(normalized::contains) -> "无法解析此 RSS 地址，请检查地址是否正确"
        message.any { it in '\u4e00'..'\u9fff' } -> message
        else -> fallback
    }
}
