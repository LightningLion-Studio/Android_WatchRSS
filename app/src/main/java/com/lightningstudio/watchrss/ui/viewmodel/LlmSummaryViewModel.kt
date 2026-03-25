package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.settings.LlmApiKeyStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

private const val TAG = "LlmSummaryVM"
private const val MAX_CONTENT_CHARS = 3000

data class LlmSummaryUiState(
    val itemId: Long = -1L,
    val status: SummaryStatus = SummaryStatus.Idle,
    val text: String = "",
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
) {
    val firstLine: String
        get() = text.lines().firstOrNull { it.isNotBlank() }?.take(60) ?: ""
}

sealed interface SummaryStatus {
    data object Idle : SummaryStatus
    data object Generating : SummaryStatus
    data object Done : SummaryStatus
    data class Error(val message: String) : SummaryStatus
}

object LlmPromptPresets {
    data class Preset(val index: Int, val label: String, val prompt: String)

    val all = listOf(
        Preset(1, "简洁摘要", "请用简洁的中文对以下文章进行摘要，不超过80字。"),
        Preset(2, "关键要点", "请提取以下文章的关键信息，以要点形式列出，不超过5条。"),
        Preset(3, "深度解读", "请对以下文章进行深度分析，包括主要观点和潜在影响，用中文回答。"),
        Preset(4, "一句话总结", "请用一句话（不超过30字）概括以下文章的核心内容。")
    )

    fun getPrompt(index: Int): String {
        return all.getOrNull(index - 1)?.prompt ?: all.first().prompt
    }
}

class LlmSummaryViewModel(
    private val rssRepository: RssRepository,
    private val settingsRepository: SettingsRepository,
    private val llmApiKeyStore: LlmApiKeyStore
) : ViewModel() {

    private val _state = MutableStateFlow(LlmSummaryUiState())
    val state: StateFlow<LlmSummaryUiState> = _state

    private var pendingTitle = ""
    private var pendingContent = ""
    private var generationJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun prepare(itemId: Long) {
        if (_state.value.itemId == itemId) return
        generationJob?.cancel()
        generationJob = null
        _state.value = LlmSummaryUiState(itemId = itemId)

        viewModelScope.launch {
            val item = rssRepository.observeItem(itemId).first() ?: return@launch
            pendingTitle = item.title.orEmpty()
            val rawHtml = item.content ?: item.description ?: ""
            pendingContent = if (rawHtml.isNotBlank()) {
                withContext(Dispatchers.Default) {
                    Jsoup.parse(rawHtml).text().take(MAX_CONTENT_CHARS)
                }
            } else {
                ""
            }

            val autoStart = settingsRepository.llmAutoSummarize.first()
            val featureEnabled = settingsRepository.llmFeatureEnabled.first()
            if (autoStart && featureEnabled && _state.value.status == SummaryStatus.Idle) {
                startGeneration()
            }
        }
    }

    fun startIfIdle() {
        if (_state.value.status != SummaryStatus.Idle) return
        startGeneration()
    }

    /** 加载内容并立即开始生成（不依赖 autoSummarize 设置，适用于用户主动触发的场景）。 */
    fun prepareAndStart(itemId: Long) {
        if (_state.value.itemId == itemId) {
            startIfIdle()
            return
        }
        generationJob?.cancel()
        generationJob = null
        _state.value = LlmSummaryUiState(itemId = itemId)

        viewModelScope.launch {
            val item = rssRepository.observeItem(itemId).first() ?: return@launch
            pendingTitle = item.title.orEmpty()
            val rawHtml = item.content ?: item.description ?: ""
            pendingContent = if (rawHtml.isNotBlank()) {
                withContext(Dispatchers.Default) {
                    Jsoup.parse(rawHtml).text().take(MAX_CONTENT_CHARS)
                }
            } else {
                ""
            }
            if (_state.value.status == SummaryStatus.Idle) {
                startGeneration()
            }
        }
    }

    fun retry() {
        if (_state.value.status is SummaryStatus.Error) {
            _state.update { it.copy(status = SummaryStatus.Idle, text = "") }
        }
        startGeneration()
    }

    private fun startGeneration() {
        _state.update { it.copy(status = SummaryStatus.Generating, text = "") }
        generationJob?.cancel()
        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = llmApiKeyStore.getApiKey()
                if (apiKey.isEmpty()) {
                    _state.update { it.copy(status = SummaryStatus.Error("未配置 API Key")) }
                    return@launch
                }
                val provider = settingsRepository.llmProvider.first()
                if (provider.isEmpty()) {
                    _state.update { it.copy(status = SummaryStatus.Error("未配置服务商")) }
                    return@launch
                }

                val model = settingsRepository.llmModel.first().ifEmpty { defaultModel(provider) }
                val baseUrl = resolveBaseUrl(provider, settingsRepository.llmBaseUrl.first())
                if (baseUrl.isEmpty()) {
                    _state.update { it.copy(status = SummaryStatus.Error("无法解析 Base URL")) }
                    return@launch
                }
                val presetIndex = settingsRepository.llmPromptPreset.first()
                val systemPrompt = LlmPromptPresets.getPrompt(presetIndex)
                val userMessage = buildUserMessage(pendingTitle, pendingContent)
                val url = "${baseUrl.trimEnd('/')}/chat/completions"

                val messages = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messages.put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })

                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("stream", true)
                    put("stream_options", JSONObject().apply { put("include_usage", true) })
                    put("messages", messages)
                }.toString()

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    val errMsg = runCatching {
                        JSONObject(errBody).optJSONObject("error")?.optString("message") ?: ""
                    }.getOrDefault("").ifEmpty { "HTTP ${response.code}" }
                    _state.update { it.copy(status = SummaryStatus.Error(errMsg)) }
                    return@launch
                }

                val reader = response.body?.byteStream()?.bufferedReader() ?: run {
                    _state.update { it.copy(status = SummaryStatus.Error("空响应")) }
                    return@launch
                }

                val accum = StringBuilder()
                var promptTokens: Int? = null
                var completionTokens: Int? = null
                var totalTokens: Int? = null

                reader.use { br ->
                    var line = br.readLine()
                    while (line != null && isActive) {
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data != "[DONE]") {
                                runCatching {
                                    val json = JSONObject(data)
                                    json.optJSONObject("usage")?.let { usage ->
                                        val pt = usage.optInt("prompt_tokens")
                                        val ct = usage.optInt("completion_tokens")
                                        val tt = usage.optInt("total_tokens")
                                        if (pt > 0) promptTokens = pt
                                        if (ct > 0) completionTokens = ct
                                        if (tt > 0) totalTokens = tt
                                    }
                                    val content = json.optJSONArray("choices")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("delta")
                                        ?.optString("content")
                                        ?: ""
                                    if (content.isNotEmpty()) {
                                        accum.append(content)
                                        _state.update {
                                            it.copy(
                                                text = accum.toString(),
                                                promptTokens = promptTokens,
                                                completionTokens = completionTokens,
                                                totalTokens = totalTokens
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        line = br.readLine()
                    }
                }

                if (isActive) {
                    _state.update {
                        it.copy(
                            status = SummaryStatus.Done,
                            text = accum.toString().ifBlank { "（无内容）" },
                            promptTokens = promptTokens,
                            completionTokens = completionTokens,
                            totalTokens = totalTokens
                        )
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    AppLogger.e(TAG, "Summary generation failed", e)
                    _state.update { it.copy(status = SummaryStatus.Error(e.message ?: "网络异常")) }
                }
            }
        }
    }

    private fun buildUserMessage(title: String, content: String): String {
        val sb = StringBuilder()
        if (title.isNotBlank()) {
            sb.append("标题：").append(title).append("\n\n")
        }
        if (content.isNotBlank()) {
            sb.append("正文：\n").append(content)
        }
        return sb.toString().ifBlank { title.ifBlank { "（空文章）" } }
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
