package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.settings.LlmApiKeyStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "LlmConnectivityVM"

data class LlmConnectivityState(
    val provider: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val enabled: Boolean = false,
    val hasApiKey: Boolean = false,
    val testStatus: LlmTestStatus = LlmTestStatus.Idle
)

sealed interface LlmTestStatus {
    data object Idle : LlmTestStatus
    data object Testing : LlmTestStatus
    data class Success(val latencyMs: Long, val replySnippet: String) : LlmTestStatus
    data class Failure(val message: String) : LlmTestStatus
}

class LlmConnectivityViewModel(
    private val settingsRepository: SettingsRepository,
    private val llmApiKeyStore: LlmApiKeyStore
) : ViewModel() {

    private val _state = MutableStateFlow(LlmConnectivityState())
    val state: StateFlow<LlmConnectivityState> = _state

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.llmProvider,
                settingsRepository.llmModel,
                settingsRepository.llmBaseUrl,
                settingsRepository.llmEnabled
            ) { provider, model, baseUrl, enabled ->
                Triple(provider, model, Pair(baseUrl, enabled))
            }.collect { (provider, model, rest) ->
                val (baseUrl, enabled) = rest
                val hasKey = llmApiKeyStore.hasApiKey()
                _state.update {
                    it.copy(
                        provider = provider,
                        model = model,
                        baseUrl = baseUrl,
                        enabled = enabled,
                        hasApiKey = hasKey
                    )
                }
            }
        }
    }

    fun runTest() {
        val current = _state.value
        if (current.testStatus is LlmTestStatus.Testing) return

        _state.update { it.copy(testStatus = LlmTestStatus.Testing) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                doConnectivityTest(current)
            }
            _state.update { it.copy(testStatus = result) }
        }
    }

    private fun doConnectivityTest(state: LlmConnectivityState): LlmTestStatus {
        val apiKey = llmApiKeyStore.getApiKey()
        if (apiKey.isEmpty()) {
            return LlmTestStatus.Failure("未配置 API Key")
        }
        if (state.provider.isEmpty()) {
            return LlmTestStatus.Failure("未配置服务商")
        }

        val baseUrl = resolveBaseUrl(state.provider, state.baseUrl)
        if (baseUrl.isEmpty()) {
            return LlmTestStatus.Failure("无法解析 Base URL")
        }
        val model = state.model.ifEmpty { defaultModel(state.provider) }
        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 16)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Hi")
                })
            })
        }.toString()

        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val startMs = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startMs
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: ""
                }.getOrDefault("").ifEmpty { "HTTP ${response.code}" }
                return LlmTestStatus.Failure(errorMsg)
            }

            val snippet = runCatching {
                val json = JSONObject(responseBody)
                json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    ?.take(40)
                    ?: "（无回复内容）"
            }.getOrDefault("（解析失败）")

            LlmTestStatus.Success(latencyMs = latencyMs, replySnippet = snippet)
        } catch (e: Exception) {
            AppLogger.e(TAG, "LLM connectivity test failed", e)
            LlmTestStatus.Failure(e.message ?: "网络异常")
        }
    }

    private fun resolveBaseUrl(provider: String, customBaseUrl: String): String = when (provider) {
        "openai" -> "https://api.openai.com/v1"
        "deepseek" -> "https://api.deepseek.com/v1"
        "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        "zhipu" -> "https://open.bigmodel.cn/api/paas/v4"
        "custom" -> customBaseUrl
        else -> ""
    }

    private fun defaultModel(provider: String): String = when (provider) {
        "openai" -> "gpt-4o-mini"
        "deepseek" -> "deepseek-chat"
        "qwen" -> "qwen-turbo"
        "zhipu" -> "glm-4-flash"
        else -> ""
    }
}
