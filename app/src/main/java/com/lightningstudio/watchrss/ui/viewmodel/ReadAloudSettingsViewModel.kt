package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.settings.ReadAloudApiKeyStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.data.tts.ReadAloudConfig
import com.lightningstudio.watchrss.data.tts.ReadAloudProvider
import com.lightningstudio.watchrss.data.tts.ReadAloudSynthesisService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReadAloudSettingsState(
    val provider: ReadAloudProvider = ReadAloudProvider.OPENAI,
    val model: String = ReadAloudProvider.OPENAI.defaultModel,
    val voice: String = ReadAloudProvider.OPENAI.defaultVoice,
    val baseUrl: String = ReadAloudProvider.OPENAI.defaultBaseUrl,
    val region: String = "",
    val appId: String = "",
    val resourceId: String = "",
    val enabled: Boolean = true,
    val hasSavedApiKey: Boolean = false,
    val apiKeyInput: String = "",
    val saveMessage: String? = null,
    val testStatus: ReadAloudTestStatus = ReadAloudTestStatus.Idle
) {
    fun effectiveConfig(): ReadAloudConfig {
        return ReadAloudConfig(
            provider = provider,
            model = model.ifBlank { provider.defaultModel },
            voice = voice.ifBlank { provider.defaultVoice },
            baseUrl = baseUrl.ifBlank { provider.defaultBaseUrl },
            region = region.trim(),
            appId = appId.trim(),
            resourceId = resourceId.ifBlank { provider.defaultResourceId }.trim(),
            enabled = enabled,
            hasApiKey = hasSavedApiKey || apiKeyInput.isNotBlank()
        )
    }
}

sealed interface ReadAloudTestStatus {
    data object Idle : ReadAloudTestStatus
    data object Testing : ReadAloudTestStatus
    data class Success(val fileSizeBytes: Long) : ReadAloudTestStatus
    data class Failure(val message: String) : ReadAloudTestStatus
}

class ReadAloudSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val apiKeyStore: ReadAloudApiKeyStore,
    private val synthesisService: ReadAloudSynthesisService
) : ViewModel() {
    private val _state = MutableStateFlow(ReadAloudSettingsState())
    val state: StateFlow<ReadAloudSettingsState> = _state

    init {
        viewModelScope.launch {
            combine(
                combine(
                    settingsRepository.readAloudProvider,
                    settingsRepository.readAloudModel,
                    settingsRepository.readAloudVoice
                ) { providerValue, model, voice ->
                    Triple(providerValue, model, voice)
                },
                combine(
                    combine(
                        settingsRepository.readAloudBaseUrl,
                        settingsRepository.readAloudRegion,
                        settingsRepository.readAloudAppId
                    ) { baseUrl, region, appId ->
                        Triple(baseUrl, region, appId)
                    },
                    combine(
                        settingsRepository.readAloudResourceId,
                        settingsRepository.readAloudEnabled
                    ) { resourceId, enabled ->
                        resourceId to enabled
                    }
                ) { first, second ->
                    CombinedReadAloudStoredValues(
                        baseUrl = first.first,
                        region = first.second,
                        appId = first.third,
                        resourceId = second.first,
                        enabled = second.second
                    )
                }
            ) { first, second ->
                StoredValues(
                    provider = ReadAloudProvider.fromPersistedValue(first.first),
                    model = first.second,
                    voice = first.third,
                    baseUrl = second.baseUrl,
                    region = second.region,
                    appId = second.appId,
                    resourceId = second.resourceId,
                    enabled = second.enabled
                )
            }.collect { stored ->
                _state.update { current ->
                    val provider = stored.provider
                    current.copy(
                        provider = provider,
                        model = stored.model.ifBlank { provider.defaultModel },
                        voice = stored.voice.ifBlank { provider.defaultVoice },
                        baseUrl = stored.baseUrl.ifBlank { provider.defaultBaseUrl },
                        region = stored.region,
                        appId = stored.appId,
                        resourceId = stored.resourceId.ifBlank { provider.defaultResourceId },
                        enabled = stored.enabled,
                        hasSavedApiKey = apiKeyStore.hasApiKey(),
                        saveMessage = null
                    )
                }
            }
        }
    }

    fun cycleProvider() {
        _state.update { current ->
            val options = ReadAloudProvider.orderedValues
            val nextIndex = (options.indexOf(current.provider) + 1).mod(options.size)
            val next = options[nextIndex]
            current.copy(
                provider = next,
                model = next.defaultModel,
                voice = next.defaultVoice,
                baseUrl = next.defaultBaseUrl,
                region = if (next.requiresRegion) current.region else "",
                appId = if (next.requiresAppId) current.appId else "",
                resourceId = if (next.requiresResourceId) {
                    current.resourceId.ifBlank { next.defaultResourceId }
                } else {
                    ""
                },
                saveMessage = null,
                testStatus = ReadAloudTestStatus.Idle
            )
        }
    }

    fun updateModel(value: String) {
        _state.update { it.copy(model = value, saveMessage = null) }
    }

    fun updateVoice(value: String) {
        _state.update { it.copy(voice = value, saveMessage = null) }
    }

    fun updateBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value, saveMessage = null) }
    }

    fun updateRegion(value: String) {
        _state.update { it.copy(region = value, saveMessage = null) }
    }

    fun updateAppId(value: String) {
        _state.update { it.copy(appId = value, saveMessage = null) }
    }

    fun updateResourceId(value: String) {
        _state.update { it.copy(resourceId = value, saveMessage = null) }
    }

    fun updateApiKey(value: String) {
        _state.update { it.copy(apiKeyInput = value, saveMessage = null) }
    }

    suspend fun saveConfig(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val current = _state.value
            val config = current.effectiveConfig()
            require(config.voice.isNotBlank()) { "请填写语音/Voice" }
            if (config.provider.requiresRegion) {
                require(config.region.isNotBlank()) { "微软朗读需要区域" }
            }
            if (config.provider.requiresAppId) {
                require(config.appId.isNotBlank()) { "请填写 App ID" }
            }
            if (config.provider.requiresResourceId) {
                require(config.resourceId.isNotBlank()) { "请填写 Resource ID" }
            }
            if (config.provider.allowsCustomBaseUrl) {
                require(config.baseUrl.isNotBlank()) { "请填写 Base URL" }
            }
            val inputKey = current.apiKeyInput.trim()
            if (inputKey.isNotEmpty()) {
                apiKeyStore.setApiKey(inputKey)
            }
            require(apiKeyStore.hasApiKey()) { "请填写 API Key" }
            settingsRepository.setReadAloudConfig(
                provider = config.provider.persistedValue,
                model = config.model,
                voice = config.voice,
                baseUrl = config.baseUrl,
                region = config.region,
                appId = config.appId,
                resourceId = config.resourceId,
                enabled = true
            )
            _state.update {
                it.copy(
                    apiKeyInput = "",
                    hasSavedApiKey = true,
                    saveMessage = "朗读配置已保存"
                )
            }
        }.onFailure { error ->
            _state.update { it.copy(saveMessage = error.message ?: "保存失败") }
        }
    }

    fun runTest() {
        val current = _state.value
        if (current.testStatus is ReadAloudTestStatus.Testing) return
        _state.update { it.copy(testStatus = ReadAloudTestStatus.Testing, saveMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    saveConfig().getOrThrow()
                    val state = _state.value
                    val config = state.effectiveConfig()
                    val apiKey = state.apiKeyInput.trim().ifBlank { apiKeyStore.getApiKey() }
                    val tempFile = File.createTempFile("read_aloud_test_", ".mp3")
                    val synthResult = synthesisService.synthesizeToFile(
                        config = config,
                        apiKey = apiKey,
                        text = "这是腕上 RSS 的朗读测试。",
                        targetFile = tempFile
                    ).getOrThrow()
                    val size = synthResult.length()
                    synthResult.delete()
                    size
                }
            }
            _state.update {
                result.fold(
                    onSuccess = { size ->
                        it.copy(testStatus = ReadAloudTestStatus.Success(size))
                    },
                    onFailure = { error ->
                        it.copy(
                            testStatus = ReadAloudTestStatus.Failure(
                                error.message ?: "测试失败"
                            )
                        )
                    }
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(saveMessage = null) }
    }

    private data class StoredValues(
        val provider: ReadAloudProvider,
        val model: String,
        val voice: String,
        val baseUrl: String,
        val region: String,
        val appId: String,
        val resourceId: String,
        val enabled: Boolean
    )

    private data class CombinedReadAloudStoredValues(
        val baseUrl: String,
        val region: String,
        val appId: String,
        val resourceId: String,
        val enabled: Boolean
    )
}
