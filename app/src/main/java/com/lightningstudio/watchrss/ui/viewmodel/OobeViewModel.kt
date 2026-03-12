package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OobeUiState(
    val introPage: Int = 0
)

sealed interface OobeEvent {
    data object Finish : OobeEvent
}

class OobeViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OobeUiState())
    val uiState: StateFlow<OobeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OobeEvent>()
    val events: SharedFlow<OobeEvent> = _events.asSharedFlow()

    fun setIntroPage(page: Int) {
        _uiState.update { state ->
            state.copy(introPage = page.coerceIn(0, INTRO_PAGE_COUNT - 1))
        }
    }

    fun completeOobe() {
        viewModelScope.launch {
            settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            _events.emit(OobeEvent.Finish)
        }
    }

    companion object {
        const val INTRO_PAGE_COUNT = 2
    }
}
