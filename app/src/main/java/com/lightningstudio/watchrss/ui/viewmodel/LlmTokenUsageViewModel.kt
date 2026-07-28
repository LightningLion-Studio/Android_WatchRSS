package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.db.LlmTokenUsageByProviderPojo
import com.lightningstudio.watchrss.data.db.LlmTokenUsageDailyPojo
import com.lightningstudio.watchrss.data.db.LlmTokenUsageEntity
import com.lightningstudio.watchrss.data.db.LlmTokenUsageStatisticsPojo
import com.lightningstudio.watchrss.data.llm.LlmTokenUsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LlmTokenUsageViewModel(
    private val repository: LlmTokenUsageRepository
) : ViewModel() {

    val recentRecords: StateFlow<List<LlmTokenUsageEntity>> = repository
        .observeRecent(limit = 200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statistics: StateFlow<LlmTokenUsageStatisticsPojo?> = repository
        .observeStatistics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val byProvider: StateFlow<List<LlmTokenUsageByProviderPojo>> = repository
        .observeByProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val daily: StateFlow<List<LlmTokenUsageDailyPojo>> = repository
        .observeDaily(sinceDays = 7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
