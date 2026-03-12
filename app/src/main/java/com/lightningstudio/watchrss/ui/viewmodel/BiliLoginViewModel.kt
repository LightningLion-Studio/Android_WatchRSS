package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.bili.formatBiliError
import com.lightningstudio.watchrss.sdk.bili.QrPollStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BiliLoginUiState(
    val qrUrl: String? = null,
    val qrKey: String? = null,
    val status: QrPollStatus = QrPollStatus.PENDING,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val message: String? = null
)

class BiliLoginViewModel(
    private val repository: BiliRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BiliLoginUiState())
    val uiState: StateFlow<BiliLoginUiState> = _uiState
    private var pollingJob: Job? = null

    fun startLogin() {
        viewModelScope.launch {
            pollingJob?.cancel()
            _uiState.update { it.copy(isLoading = true, message = null, isSuccess = false) }
            val qr = repository.requestWebQrCode()
            if (qr == null) {
                _uiState.update {
                    it.copy(isLoading = false, message = formatBiliError(BiliErrorCodes.QR_REQUEST_FAILED))
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    qrUrl = qr.url,
                    qrKey = qr.qrKey,
                    isLoading = false,
                    status = QrPollStatus.PENDING,
                    message = null
                )
            }
            startPolling(qr.qrKey)
        }
    }

    fun applyCookies(rawCookie: String) {
        viewModelScope.launch {
            val result = repository.applyCookieHeader(rawCookie)
            if (result.isSuccess) {
                _uiState.update { it.copy(isSuccess = true, message = "登录成功") }
            } else {
                _uiState.update { it.copy(message = formatBiliError(BiliErrorCodes.COOKIE_INVALID)) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun startPolling(qrKey: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val result = repository.pollWebQrCode(qrKey)
                when (result.status) {
                    QrPollStatus.SUCCESS -> {
                        _uiState.update {
                            it.copy(status = result.status, isSuccess = true, message = "登录成功")
                        }
                        return@launch
                    }
                    QrPollStatus.EXPIRED -> {
                        _uiState.update {
                            it.copy(status = result.status, message = formatBiliError(result.rawCode))
                        }
                        return@launch
                    }
                    QrPollStatus.ERROR -> {
                        _uiState.update {
                            it.copy(status = result.status, message = formatBiliError(result.rawCode))
                        }
                        return@launch
                    }
                    else -> {
                        _uiState.update { it.copy(status = result.status) }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }

    private companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
