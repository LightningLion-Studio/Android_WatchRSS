package com.lightningstudio.watchrss.data.tts.engine

import android.content.Context
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.data.settings.TtsApiKeyProvider
import com.lightningstudio.watchrss.data.tts.TtsProviderCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TtsEngineFactory(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ttsApiKeyProvider: TtsApiKeyProvider,
    private val watchAccountStore: AccountStore
) {
    fun create(): TtsEngine {
        val engineType = runBlocking {
            settingsRepository.ttsEngine.first()
        }.ifEmpty { TtsProviderCatalog.ENGINE_LOCAL }

        return when (engineType) {
            TtsProviderCatalog.ENGINE_BACKEND_DEFAULT -> BackendTtsEngine(
                context = context,
                watchAccountStore = watchAccountStore
            )
            TtsProviderCatalog.ENGINE_MINIMAX -> {
                val model = runBlocking { settingsRepository.ttsModel.first() }
                    .ifEmpty { TtsProviderCatalog.defaultModel(TtsProviderCatalog.ENGINE_MINIMAX) }
                val voiceId = runBlocking { settingsRepository.ttsVoiceId.first() }
                    .ifEmpty { TtsProviderCatalog.defaultVoiceId(TtsProviderCatalog.ENGINE_MINIMAX) }
                val baseUrl = runBlocking { settingsRepository.ttsBaseUrl.first() }
                    .ifEmpty { "https://api.minimaxi.com" }
                MiniMaxTtsEngine(
                    context = context,
                    apiKeyProvider = ttsApiKeyProvider,
                    model = model,
                    voiceId = voiceId,
                    baseUrl = baseUrl
                )
            }
            TtsProviderCatalog.ENGINE_AZURE -> {
                val voiceId = runBlocking { settingsRepository.ttsVoiceId.first() }
                    .ifEmpty { TtsProviderCatalog.defaultVoiceId(TtsProviderCatalog.ENGINE_AZURE) }
                AzureTtsEngine(
                    context = context,
                    apiKeyProvider = ttsApiKeyProvider,
                    voiceId = voiceId
                )
            }
            TtsProviderCatalog.ENGINE_DOUBAO -> {
                val voiceId = runBlocking { settingsRepository.ttsVoiceId.first() }
                    .ifEmpty { TtsProviderCatalog.defaultVoiceId(TtsProviderCatalog.ENGINE_DOUBAO) }
                val baseUrl = runBlocking { settingsRepository.ttsBaseUrl.first() }
                    .ifEmpty { "https://openspeech.bytedance.com" }
                DoubaoTtsEngine(
                    context = context,
                    apiKeyProvider = ttsApiKeyProvider,
                    voiceId = voiceId,
                    baseUrl = baseUrl
                )
            }
            else -> LocalTtsEngine(context)
        }
    }
}

private fun String.ifEmpty(fallback: () -> String): String = if (this.isEmpty()) fallback() else this
