package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.llm.LlmTokenUsageRepository
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.effectiveContent
import com.lightningstudio.watchrss.data.rss.isOriginalContentMissing
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
import com.lightningstudio.watchrss.data.network.withWatchRssAppVersionHeader
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    private val tokenUsageRepository: LlmTokenUsageRepository,
    private val watchAccountStore: AccountStore
) : ViewModel() {

    private val _state = MutableStateFlow(LlmSummaryUiState())
    val state: StateFlow<LlmSummaryUiState> = _state

    private var pendingTitle = ""
    private var pendingContent = ""
    private var pendingContentHash = ""
    private var contentReady = false
    private var manualStartRequested = false
    private var generationJob: Job? = null
    private var preparationJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    fun prepare(itemId: Long) {
        if (_state.value.itemId == itemId) return
        startPreparation(itemId, forceStartWhenReady = false)
    }

    fun startIfIdle() {
        if (_state.value.status != SummaryStatus.Idle &&
            _state.value.status != SummaryStatus.WaitingForContent
        ) return
        manualStartRequested = true
        if (contentReady) {
            startGeneration()
        } else {
            _state.update { it.copy(status = SummaryStatus.WaitingForContent, text = "") }
        }
    }

    /** 加载内容并立即开始生成（不依赖 autoSummarize 设置，适用于用户主动触发的场景）。 */
    fun prepareAndStart(itemId: Long) {
        if (_state.value.itemId == itemId) {
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
        pendingTitle = ""
        pendingContent = ""
        pendingContentHash = ""
        contentReady = false
        manualStartRequested = forceStartWhenReady
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
                pendingContentHash = item.contentHash
                val rawHtml = item.effectiveContent(
                    useOriginalContent = channel?.useOriginalContent == true || !item.isOriginalContentMissing()
                ).orEmpty()

                val isImportedText = ImportedContentIds.isImportedTextItemUrl(item.link)
                val waitingForOriginalContent = !isImportedText &&
                    item.isOriginalContentMissing() &&
                    channel?.useOriginalContent == true &&
                    rawHtml.isBlank()
                if (waitingForOriginalContent) {
                    pendingContent = ""
                    contentReady = false
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
                contentReady = true

                if ((autoStart || manualStartRequested) &&
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
        manualStartRequested = false
        _state.update { it.copy(status = SummaryStatus.Generating, text = "") }
        generationJob?.cancel()
        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                generateServerSummary()
            } catch (e: Exception) {
                if (isActive) {
                    AppLogger.e(TAG, "Summary generation failed", e)
                    _state.update { it.copy(status = SummaryStatus.Error(e.message ?: "网络异常")) }
                }
            }
        }
    }

    private suspend fun generateServerSummary() {
        val account = watchAccountStore.read()
        if (account == null) {
            _state.update { it.copy(status = SummaryStatus.Error("请先从已授权手机同步账号")) }
            return
        }
        val backendBaseUrl = account.backendBaseUrl
        if (backendBaseUrl.isBlank()) {
            _state.update { it.copy(status = SummaryStatus.Error("账号后端地址未配置")) }
            return
        }
        val token = account.watchDeviceToken
        if (token.isBlank()) {
            _state.update { it.copy(status = SummaryStatus.Error("手机授权令牌无效")) }
            return
        }

        val presetIndex = settingsRepository.llmPromptPreset.first()
            .takeIf { it in 1..4 } ?: 1
        val url = "${backendBaseUrl.trimEnd('/')}/api/v1/llm/default-model/article-summary"

        val body = JSONObject().apply {
            put("title", pendingTitle)
            put("content", pendingContent)
            pendingContentHash.takeIf { it.matches(CONTENT_HASH) }?.let {
                put("contentHash", it)
            }
            put("promptPreset", presetIndex)
            put("stream", false)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .withWatchRssAppVersionHeader()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            val errorJson = runCatching { JSONObject(errorBody) }.getOrElse { JSONObject() }
            val errMsg = when (response.code) {
                402 -> "AI 总结仅限已购买 6 元授权的用户"
                401, 403 -> "账号授权已失效，请从手机重新同步"
                else -> errorJson.optString("detail").ifBlank {
                    errorJson.optString("error").ifBlank { "HTTP ${response.code}" }
                }
            }
            _state.update { it.copy(status = SummaryStatus.Error(errMsg)) }
            return
        }
        val json = JSONObject(response.body?.string().orEmpty())
        val usage = json.optJSONObject("usage")
        val promptTokens = usage?.optInt("inputTokens")?.takeIf { it > 0 }
        val completionTokens = usage?.optInt("outputTokens")?.takeIf { it > 0 }
        val totalTokens = usage?.optInt("totalTokens")?.takeIf { it > 0 }
        val model = json.optString("model").ifBlank { "server-managed" }
        tokenUsageRepository.record(
            provider = "WatchRSS",
            model = model,
            requestId = "${System.currentTimeMillis()}_server_summary",
            rawUsage = usage
        )
        _state.update {
            it.copy(
                status = SummaryStatus.Done,
                text = json.optString("text").ifBlank { "（无内容）" },
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens
            )
        }
    }

    companion object {
        private val CONTENT_HASH = Regex("^[0-9a-f]{64}$")
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
