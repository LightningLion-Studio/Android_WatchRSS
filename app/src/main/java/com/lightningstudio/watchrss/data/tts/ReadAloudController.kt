package com.lightningstudio.watchrss.data.tts

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.settings.ReadAloudApiKeyStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import java.io.File
import java.security.MessageDigest

private const val TAG = "ReadAloudController"
private const val QUEUE_LIMIT = 48
private const val MAX_SEGMENT_CHARS = 900
private const val MAX_ARTICLE_CHARS = 18_000
private const val PROGRESS_UPDATE_MS = 500L

enum class ReadAloudPhase {
    IDLE,
    RESOLVING_CONTENT,
    SYNTHESIZING,
    READY,
    ERROR
}

data class ReadAloudUiState(
    val visible: Boolean = false,
    val phase: ReadAloudPhase = ReadAloudPhase.IDLE,
    val currentItemId: Long? = null,
    val currentTitle: String = "",
    val currentChannelTitle: String = "",
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val providerLabel: String = "",
    val errorMessage: String? = null
)

class ReadAloudController(
    context: Context,
    private val appScope: CoroutineScope,
    private val rssRepository: RssRepository,
    private val settingsRepository: SettingsRepository,
    private val apiKeyStore: ReadAloudApiKeyStore,
    private val synthesisService: ReadAloudSynthesisService
) {
    private val appContext = context.applicationContext
    private val player = ExoPlayer.Builder(appContext).build()
    private val _uiState = MutableStateFlow(ReadAloudUiState())
    val uiState: StateFlow<ReadAloudUiState> = _uiState

    private var queue: List<QueueEntry> = emptyList()
    private var currentQueueIndex: Int = -1
    private var playbackJob: Job? = null
    private var prefetchJob: Job? = null

    init {
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED &&
                    player.currentMediaItemIndex >= player.mediaItemCount - 1
                ) {
                    playNext()
                }
            }
        })
        appScope.launch {
            while (true) {
                val state = _uiState.value
                if (state.visible) {
                    _uiState.update {
                        it.copy(
                            progressMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.takeIf { value -> value > 0L } ?: 0L,
                            isPlaying = player.isPlaying
                        )
                    }
                }
                delay(PROGRESS_UPDATE_MS)
            }
        }
    }

    suspend fun hasConfig(): Boolean = currentConfig().isComplete()

    fun startFromItem(itemId: Long) {
        playbackJob?.cancel()
        playbackJob = appScope.launch {
            val item = rssRepository.observeItem(itemId).filterNotNull().first()
            val channel = rssRepository.observeChannel(item.channelId).first()
                ?: error("频道不存在")
            queue = buildQueue(item, channel)
            currentQueueIndex = queue.indexOfFirst { it.item.id == itemId }.coerceAtLeast(0)
            playQueueIndex(currentQueueIndex)
        }
    }

    fun togglePlayPause() {
        when (_uiState.value.phase) {
            ReadAloudPhase.IDLE -> Unit
            ReadAloudPhase.ERROR -> {
                if (currentQueueIndex >= 0) {
                    playQueueIndex(currentQueueIndex)
                }
            }
            ReadAloudPhase.RESOLVING_CONTENT,
            ReadAloudPhase.SYNTHESIZING -> Unit
            ReadAloudPhase.READY -> {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
        }
    }

    fun playNext() {
        val nextIndex = currentQueueIndex + 1
        if (nextIndex !in queue.indices) {
            stop()
            return
        }
        playQueueIndex(nextIndex)
    }

    fun playPrevious() {
        val previousIndex = currentQueueIndex - 1
        if (previousIndex !in queue.indices) {
            return
        }
        playQueueIndex(previousIndex)
    }

    fun stop() {
        playbackJob?.cancel()
        prefetchJob?.cancel()
        player.stop()
        player.clearMediaItems()
        currentQueueIndex = -1
        queue = emptyList()
        _uiState.value = ReadAloudUiState()
    }

    fun release() {
        stop()
        player.release()
    }

    private fun playQueueIndex(index: Int) {
        if (index !in queue.indices) return
        playbackJob?.cancel()
        playbackJob = appScope.launch {
            runCatching {
                currentQueueIndex = index
                val entry = queue[index]
                val config = currentConfig()
                require(config.isComplete()) { "请先配置朗读 API" }

                updateState(
                    entry = entry,
                    phase = ReadAloudPhase.RESOLVING_CONTENT,
                    errorMessage = null,
                    config = config
                )

                val files = resolveArticleAudio(entry, config)
                require(files.isNotEmpty()) { "没有生成可播放音频" }

                updateState(
                    entry = entry,
                    phase = ReadAloudPhase.READY,
                    errorMessage = null,
                    config = config
                )

                player.stop()
                player.clearMediaItems()
                player.setMediaItems(files.map { file ->
                    MediaItem.fromUri(Uri.fromFile(file))
                })
                player.prepare()
                player.play()

                prefetchJob?.cancel()
                prefetchJob = appScope.launch {
                    prefetchAround(index + 1, config)
                }
            }.onFailure { error ->
                AppLogger.e(TAG, "Play queue entry failed", error)
                val entry = queue.getOrNull(index)
                _uiState.update {
                    it.copy(
                        visible = entry != null,
                        phase = ReadAloudPhase.ERROR,
                        currentItemId = entry?.item?.id,
                        currentTitle = entry?.item?.title.orEmpty(),
                        currentChannelTitle = entry?.channelTitle.orEmpty(),
                        queueIndex = (index + 1).coerceAtLeast(0),
                        queueSize = queue.size,
                        providerLabel = runCatching { currentConfig().provider.displayName }.getOrDefault(""),
                        errorMessage = error.message ?: "朗读失败",
                        isPlaying = false
                    )
                }
                player.stop()
                player.clearMediaItems()
            }
        }
    }

    private suspend fun prefetchAround(startIndex: Int, config: ReadAloudConfig) {
        for (index in startIndex until minOf(queue.size, startIndex + 2)) {
            runCatching {
                resolveArticleAudio(queue[index], config)
            }
        }
    }

    private suspend fun resolveArticleAudio(
        entry: QueueEntry,
        config: ReadAloudConfig
    ): List<File> {
        updateState(entry, ReadAloudPhase.SYNTHESIZING, null, config)
        val text = awaitReadableText(entry)
        val segments = segmentArticleText(text)
        val apiKey = apiKeyStore.getApiKey()
        val digest = sha256(
            buildString {
                append(entry.item.id)
                append('|')
                append(config.provider.persistedValue)
                append('|')
                append(config.model)
                append('|')
                append(config.voice)
                append('|')
                append(config.baseUrl)
                append('|')
                append(config.region)
                append('|')
                append(config.appId)
                append('|')
                append(config.resourceId)
                append('|')
                append(text)
            }
        )
        val articleDir = File(appContext.cacheDir, "read_aloud/$digest")
        val files = mutableListOf<File>()
        segments.forEachIndexed { segmentIndex, segment ->
            val target = File(articleDir, "segment_$segmentIndex.mp3")
            if (!target.exists() || target.length() == 0L) {
                synthesisService.synthesizeToFile(
                    config = config,
                    apiKey = apiKey,
                    text = segment,
                    targetFile = target
                ).getOrThrow()
            }
            files += target
        }
        return files
    }

    private suspend fun awaitReadableText(entry: QueueEntry): String {
        val item = entry.item
        val current = if (!item.content.isNullOrBlank()) {
            item
        } else {
            if (entry.useOriginalContent) {
                rssRepository.requestOriginalContent(item.id)
            }
            withTimeoutOrNull(25_000L) {
                rssRepository.observeItem(item.id)
                    .filterNotNull()
                    .first { latest ->
                        !entry.useOriginalContent || !latest.content.isNullOrBlank()
                    }
            } ?: rssRepository.observeItem(item.id).filterNotNull().first()
        }

        val baseText = buildArticleText(current)
        require(baseText.isNotBlank()) { "文章内容为空" }
        return baseText
    }

    private suspend fun buildQueue(item: RssItem, channel: RssChannel): List<QueueEntry> {
        val items = rssRepository.observeItemsPaged(channel.id, QUEUE_LIMIT).first()
        val initial = if (items.any { it.id == item.id }) items else listOf(item) + items
        return initial
            .distinctBy { it.id }
            .map { queueItem ->
                QueueEntry(
                    item = queueItem,
                    channelTitle = channel.title,
                    useOriginalContent = channel.useOriginalContent
                )
            }
    }

    private suspend fun currentConfig(): ReadAloudConfig {
        val provider = ReadAloudProvider.fromPersistedValue(settingsRepository.readAloudProvider.first())
        return ReadAloudConfig(
            provider = provider,
            model = settingsRepository.readAloudModel.first().ifBlank { provider.defaultModel },
            voice = settingsRepository.readAloudVoice.first().ifBlank { provider.defaultVoice },
            baseUrl = settingsRepository.readAloudBaseUrl.first().ifBlank { provider.defaultBaseUrl },
            region = settingsRepository.readAloudRegion.first(),
            appId = settingsRepository.readAloudAppId.first(),
            resourceId = settingsRepository.readAloudResourceId.first().ifBlank {
                provider.defaultResourceId
            },
            enabled = settingsRepository.readAloudEnabled.first(),
            hasApiKey = apiKeyStore.hasApiKey()
        )
    }

    private fun buildArticleText(item: RssItem): String {
        val raw = item.content
            ?: item.description
            ?: item.summary
            ?: item.title
        val text = Jsoup.parse(raw).text()
            .replace(Regex("\\s+"), " ")
            .trim()
        val limited = if (text.length > MAX_ARTICLE_CHARS) {
            text.take(MAX_ARTICLE_CHARS)
        } else {
            text
        }
        return buildString {
            append(item.title.trim())
            append("。")
            if (limited.isNotBlank() && !limited.equals(item.title.trim(), ignoreCase = false)) {
                append(limited)
            }
        }
    }

    private fun segmentArticleText(text: String): List<String> {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        val parts = normalized.split(Regex("(?<=[。！？!?；;：:\\.])"))
        for (part in parts) {
            val segment = part.trim()
            if (segment.isBlank()) continue
            if (segment.length > MAX_SEGMENT_CHARS) {
                flushCurrent(current, chunks)
                var start = 0
                while (start < segment.length) {
                    val end = (start + MAX_SEGMENT_CHARS).coerceAtMost(segment.length)
                    chunks += segment.substring(start, end)
                    start = end
                }
                continue
            }
            val appendedLength = current.length + segment.length
            if (appendedLength > MAX_SEGMENT_CHARS && current.isNotEmpty()) {
                flushCurrent(current, chunks)
            }
            current.append(segment)
        }
        flushCurrent(current, chunks)
        return chunks
    }

    private fun flushCurrent(current: StringBuilder, chunks: MutableList<String>) {
        if (current.isNotEmpty()) {
            chunks += current.toString()
            current.clear()
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun updateState(
        entry: QueueEntry,
        phase: ReadAloudPhase,
        errorMessage: String?,
        config: ReadAloudConfig
    ) {
        _uiState.update {
            it.copy(
                visible = true,
                phase = phase,
                currentItemId = entry.item.id,
                currentTitle = entry.item.title,
                currentChannelTitle = entry.channelTitle,
                queueIndex = currentQueueIndex + 1,
                queueSize = queue.size,
                isPlaying = player.isPlaying,
                progressMs = if (phase == ReadAloudPhase.READY) player.currentPosition else 0L,
                durationMs = if (phase == ReadAloudPhase.READY) {
                    player.duration.takeIf { value -> value > 0L } ?: 0L
                } else {
                    0L
                },
                providerLabel = config.provider.displayName,
                errorMessage = errorMessage
            )
        }
    }

    private data class QueueEntry(
        val item: RssItem,
        val channelTitle: String,
        val useOriginalContent: Boolean
    )
}
