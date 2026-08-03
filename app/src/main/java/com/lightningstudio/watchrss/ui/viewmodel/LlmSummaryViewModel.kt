package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.llm.LlmProviderCatalog
import com.lightningstudio.watchrss.data.llm.LlmTokenUsageRepository
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.effectiveContent
import com.lightningstudio.watchrss.data.rss.isOriginalContentMissing
import com.lightningstudio.watchrss.data.settings.LlmApiKeyProvider
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
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
    data object WaitingForContent : SummaryStatus
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
    private val llmApiKeyProvider: LlmApiKeyProvider,
    private val tokenUsageRepository: LlmTokenUsageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LlmSummaryUiState())
    val state: StateFlow<LlmSummaryUiState> = _state

    private var pendingTitle = ""
    private var pendingContent = ""
    private var generationJob: Job? = null
    private var preparationJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun prepare(itemId: Long) {
        if (_state.value.itemId == itemId) return
        startPreparation(itemId, forceStartWhenReady = false)
    }

    fun startIfIdle() {
        if (_state.value.status != SummaryStatus.Idle) return
        startGeneration()
    }

    /** 加载内容并立即开始生成（不依赖 autoSummarize 设置，适用于用户主动触发的场景）。 */
    fun prepareAndStart(itemId: Long) {
        if (_state.value.itemId == itemId) {
            if (_state.value.status == SummaryStatus.WaitingForContent) return
            startIfIdle()
            return
        }
        startPreparation(itemId, forceStartWhenReady = true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startPreparation(itemId: Long, forceStartWhenReady: Boolean) {
        generationJob?.cancel()
        generationJob = null
        preparationJob?.cancel()
        preparationJob = null
        _state.value = LlmSummaryUiState(itemId = itemId)

        preparationJob = viewModelScope.launch {
            val llmEnabled = settingsRepository.llmEnabled.first()
            val autoStart = llmEnabled && (forceStartWhenReady || settingsRepository.llmAutoSummarize.first())

            combine(
                rssRepository.observeItem(itemId),
                rssRepository.observeItem(itemId).flatMapLatest { item ->
                    if (item == null) {
                        flowOf(null)
                    } else {
                        rssRepository.observeChannel(item.channelId)
                    }
                }
            ) { item, channel ->
                item to channel
            }.collect { (item, channel) ->
                if (item == null) return@collect

                pendingTitle = item.title.orEmpty()
                val rawHtml = item.effectiveContent(
                    useOriginalContent = channel?.useOriginalContent == true || !item.isOriginalContentMissing()
                ).orEmpty()

                val isImportedText = ImportedContentIds.isImportedTextItemUrl(item.link)
                if (ImportedContentIds.isNovelContentItemUrl(item.link)) {
                    pendingContent = ""
                    _state.update {
                        it.copy(
                            status = SummaryStatus.Error("小说内容暂不支持 AI 总结"),
                            text = ""
                        )
                    }
                    return@collect
                }
                val waitingForOriginalContent = !isImportedText &&
                    item.isOriginalContentMissing() &&
                    channel?.useOriginalContent == true &&
                    rawHtml.isBlank()
                if (waitingForOriginalContent) {
                    pendingContent = ""
                    if (_state.value.status == SummaryStatus.Idle ||
                        _state.value.status == SummaryStatus.WaitingForContent
                    ) {
                        _state.update { it.copy(status = SummaryStatus.WaitingForContent, text = "") }
                    }
                    return@collect
                }

                pendingContent = if (isImportedText) {
                    val reader = rssRepository.getImportedTextReader(item.id)
                    reader?.let { rssRepository.loadImportedTextChunk(it.marker, 0) }
                        ?.toSummarySourceText(isPlainText = true)
                        ?: item.description.orEmpty().take(MAX_CONTENT_CHARS)
                } else if (rawHtml.isNotBlank()) {
                    withContext(Dispatchers.Default) {
                        rawHtml.toSummarySourceText(isPlainText = false)
                    }
                } else {
                    ""
                }

                if (autoStart &&
                    (_state.value.status == SummaryStatus.Idle ||
                        _state.value.status == SummaryStatus.WaitingForContent)
                ) {
                    startGeneration()
                } else if (_state.value.status == SummaryStatus.WaitingForContent) {
                    _state.update { it.copy(status = SummaryStatus.Idle, text = "") }
                }
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
                if (!settingsRepository.llmEnabled.first()) {
                    _state.update { it.copy(status = SummaryStatus.Error("AI 总结功能已关闭")) }
                    return@launch
                }
                val apiKey = llmApiKeyProvider.getApiKey()
                if (apiKey.isEmpty()) {
                    _state.update { it.copy(status = SummaryStatus.Error("未配置 API Key")) }
                    return@launch
                }
                val provider = settingsRepository.llmProvider.first()
                if (provider.isEmpty()) {
                    _state.update { it.copy(status = SummaryStatus.Error("未配置服务商")) }
                    return@launch
                }

                val model = settingsRepository.llmModel.first().ifEmpty {
                    LlmProviderCatalog.defaultModel(provider)
                }
                val baseUrl = LlmProviderCatalog.resolveBaseUrl(
                    provider,
                    settingsRepository.llmBaseUrl.first()
                )
                if (baseUrl.isEmpty()) {
                    _state.update { it.copy(status = SummaryStatus.Error("无法解析 Base URL")) }
                    return@launch
                }
                val presetIndex = settingsRepository.llmPromptPreset.first()
                val systemPrompt = LlmPromptPresets.getPrompt(presetIndex)
                val userMessage = buildUserMessage(pendingTitle, pendingContent)
                val requestIdPrefix = System.currentTimeMillis().toString()
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
                    val errMsg = LlmProviderCatalog.publicWelfareOverloadedMessage(
                        provider = provider,
                        httpCode = response.code,
                        responseBody = errBody
                    ) ?: runCatching {
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

                val usageAccum = JSONObject()

                reader.use { br ->
                    var line = br.readLine()
                    while (line != null && isActive) {
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data != "[DONE]") {
                                runCatching {
                                    val json = JSONObject(data)
                                    json.optJSONObject("usage")?.let { usage ->
                                        usage.keys().forEach { key ->
                                            val existing = usageAccum.opt(key)
                                            val incoming = usage.get(key)
                                            if (existing == null) {
                                                usageAccum.put(key, incoming)
                                            }
                                        }
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
                    val finalText = accum.toString().ifBlank { "（无内容）" }
                    val requestId = "${requestIdPrefix}_${provider}_$model"
                    tokenUsageRepository.record(
                        provider = provider,
                        model = model,
                        requestId = requestId,
                        rawUsage = usageAccum.takeIf { it.length() > 0 }
                    )
                    _state.update {
                        it.copy(
                            status = SummaryStatus.Done,
                            text = finalText,
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

}

private fun String.toSummarySourceText(isPlainText: Boolean): String {
    return if (isPlainText || !mayContainSummaryHtml()) {
        toPlainSummaryPreview(MAX_PLAIN_SUMMARY_SCAN_CHARS)
    } else {
        toHtmlSummaryPreview()
    }
}

private fun String.mayContainSummaryHtml(): Boolean {
    val sampleEnd = length.coerceAtMost(HTML_DETECTION_SAMPLE_CHARS)
    var index = 0
    while (index < sampleEnd) {
        if (this[index] == '<') return true
        index += 1
    }
    return false
}

private fun String.toPlainSummaryPreview(scanLimit: Int): String {
    val builder = StringBuilder(MAX_CONTENT_CHARS)
    var pendingSpace = false
    val end = length.coerceAtMost(scanLimit)
    var index = 0
    while (index < end && builder.length < MAX_CONTENT_CHARS) {
        val char = this[index]
        if (char.isWhitespace()) {
            if (builder.isNotEmpty()) pendingSpace = true
        } else {
            if (pendingSpace && builder.isNotEmpty() && builder.length < MAX_CONTENT_CHARS) {
                builder.append(' ')
            }
            builder.append(char)
            pendingSpace = false
        }
        index += 1
    }
    return builder.toString()
}

private fun String.toHtmlSummaryPreview(): String {
    val builder = StringBuilder(MAX_CONTENT_CHARS)
    var pendingSpace = false
    var inTag = false
    val end = length.coerceAtMost(MAX_HTML_SUMMARY_SCAN_CHARS)
    var index = 0

    fun appendWhitespace() {
        if (builder.isNotEmpty()) pendingSpace = true
    }

    fun appendTextChar(char: Char) {
        if (char.isWhitespace()) {
            appendWhitespace()
            return
        }
        if (pendingSpace && builder.isNotEmpty() && builder.length < MAX_CONTENT_CHARS) {
            builder.append(' ')
        }
        builder.append(char)
        pendingSpace = false
    }

    while (index < end && builder.length < MAX_CONTENT_CHARS) {
        val char = this[index]
        when {
            inTag -> {
                if (char == '>') {
                    inTag = false
                    appendWhitespace()
                }
            }
            char == '<' -> {
                inTag = true
                appendWhitespace()
            }
            char == '&' -> {
                val entityEnd = findSummaryEntityEnd(index + 1, end)
                if (entityEnd > index) {
                    decodeSummaryEntity(substring(index + 1, entityEnd))?.let(::appendTextChar)
                        ?: appendWhitespace()
                    index = entityEnd
                } else {
                    appendTextChar(char)
                }
            }
            else -> appendTextChar(char)
        }
        index += 1
    }
    return builder.toString()
}

private fun String.findSummaryEntityEnd(start: Int, end: Int): Int {
    val limit = (start + MAX_ENTITY_CHARS).coerceAtMost(end)
    var index = start
    while (index < limit) {
        if (this[index] == ';') return index
        if (this[index].isWhitespace() || this[index] == '<' || this[index] == '&') return -1
        index += 1
    }
    return -1
}

private fun decodeSummaryEntity(entity: String): Char? {
    return when (entity.lowercase()) {
        "nbsp" -> ' '
        "amp" -> '&'
        "lt" -> '<'
        "gt" -> '>'
        "quot" -> '"'
        "apos" -> '\''
        else -> decodeNumericSummaryEntity(entity)
    }
}

private fun decodeNumericSummaryEntity(entity: String): Char? {
    if (!entity.startsWith("#")) return null
    val codePoint = if (entity.startsWith("#x", ignoreCase = true)) {
        entity.drop(2).toIntOrNull(16)
    } else {
        entity.drop(1).toIntOrNull()
    } ?: return null
    return runCatching { codePoint.toChar() }.getOrNull()
}

private const val HTML_DETECTION_SAMPLE_CHARS = 4096
private const val MAX_PLAIN_SUMMARY_SCAN_CHARS = 64_000
private const val MAX_HTML_SUMMARY_SCAN_CHARS = 64_000
private const val MAX_ENTITY_CHARS = 12
