package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.account.WatchAccountStore
import com.lightningstudio.watchrss.data.llm.LlmProviderCatalog
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
    val isLoggedIn: Boolean = false,
    val isDefaultModel: Boolean = false,
    val configMessage: String = "",
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
    private val llmApiKeyStore: LlmApiKeyStore,
    private val watchAccountStore: AccountStore
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
                val account = watchAccountStore.read()
                val isLoggedIn = account != null
                val isDefaultModel = LlmProviderCatalog.isDefaultModel(provider)
                val hasKey = if (isDefaultModel) isLoggedIn else llmApiKeyStore.hasApiKey()
                _state.update {
                    it.copy(
                        provider = provider,
                        model = model,
                        baseUrl = baseUrl,
                        enabled = enabled,
                        hasApiKey = hasKey,
                        isLoggedIn = isLoggedIn,
                        isDefaultModel = isDefaultModel
                    )
                }
            }
        }
    }

    fun runTest() {
        val current = _state.value
        if (current.testStatus is LlmTestStatus.Testing) return

        _state.update { it.copy(configMessage = "", testStatus = LlmTestStatus.Testing) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                doConnectivityTest(current)
            }
            _state.update { it.copy(testStatus = result) }
        }
    }

    fun useDefaultModel() {
        viewModelScope.launch {
            runCatching {
                val account = watchAccountStore.read()
                    ?: throw IllegalStateException("使用默认模型前请先登录")
                val backendBaseUrl = account.backendBaseUrl
                if (backendBaseUrl.isBlank()) {
                    throw IllegalStateException("账号后端地址未配置")
                }
                settingsRepository.setLlmConfig(
                    provider = LlmProviderCatalog.PROVIDER_DEFAULT_MODEL,
                    model = "",
                    baseUrl = backendBaseUrl,
                    enabled = true
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        provider = LlmProviderCatalog.PROVIDER_DEFAULT_MODEL,
                        model = "",
                        baseUrl = it.baseUrl,
                        enabled = true,
                        hasApiKey = it.isLoggedIn,
                        isDefaultModel = true,
                        configMessage = "已使用默认模型",
                        testStatus = LlmTestStatus.Idle
                    )
                }
            }.onFailure { error ->
                AppLogger.e(TAG, "Apply default model failed", error)
                _state.update {
                    it.copy(
                        configMessage = error.message ?: "应用默认模型失败",
                        testStatus = LlmTestStatus.Idle
                    )
                }
            }
        }
    }

    private fun doConnectivityTest(state: LlmConnectivityState): LlmTestStatus {
        if (state.provider.isEmpty()) {
            return LlmTestStatus.Failure("未配置服务商")
        }
        val account = watchAccountStore.read()
        val isDefaultModel = LlmProviderCatalog.isDefaultModel(state.provider)
        if (isDefaultModel) {
            if (account == null) {
                return LlmTestStatus.Failure("使用默认模型前请先登录")
            }
            if (account.backendBaseUrl.isBlank()) {
                return LlmTestStatus.Failure("账号后端地址未配置")
            }
            return doDefaultModelTest(account.backendBaseUrl, account.watchDeviceToken)
        }
        return doByokTest(state)
    }

    private fun doDefaultModelTest(backendBaseUrl: String, token: String): LlmTestStatus {
        val url = "${backendBaseUrl.trimEnd('/')}/api/v1/llm/default-model/llm-summary"
        val body = JSONObject().apply {
            put("model", "deepseek-v4-flash")
            put("input", "Hi")
            put("stream", false)
        }.toString()

        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val startMs = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startMs
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: ""
                }.getOrDefault("").ifEmpty { "HTTP ${response.code}" }
                return LlmTestStatus.Failure(errMsg)
            }

            val snippet = runCatching {
                val json = JSONObject(responseBody)
                extractResponsesOutputText(json)?.trim()?.take(40) ?: "（无回复内容）"
            }.getOrDefault("（解析失败）")

            LlmTestStatus.Success(latencyMs = latencyMs, replySnippet = snippet)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Default model connectivity test failed", e)
            LlmTestStatus.Failure(e.message ?: "网络异常")
        }
    }

    private fun extractResponsesOutputText(response: JSONObject): String? {
        val output = response.optJSONArray("output") ?: return null
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            if (item.optString("role") != "assistant") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") {
                    return part.optString("text")
                }
            }
        }
        return null
    }

    private fun doByokTest(state: LlmConnectivityState): LlmTestStatus {
        val apiKey = llmApiKeyStore.getApiKey()
        if (apiKey.isEmpty()) {
            return LlmTestStatus.Failure("未配置 API Key")
        }
        val baseUrl = LlmProviderCatalog.resolveBaseUrl(state.provider, state.baseUrl)
        if (baseUrl.isEmpty()) {
            return LlmTestStatus.Failure("无法解析 Base URL")
        }
        val model = state.model.ifEmpty { LlmProviderCatalog.defaultModel(state.provider) }
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
                val errMsg = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: ""
                }.getOrDefault("").ifEmpty { "HTTP ${response.code}" }
                return LlmTestStatus.Failure(errMsg)
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
}
