package com.lightningstudio.watchrss.data.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.data.media.MediaPlaybackStartVolumeLimiter
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.ImportedTextReader
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.rss.effectiveContent
import com.lightningstudio.watchrss.ui.util.ContentBlock
import com.lightningstudio.watchrss.ui.util.buildContentBlocks
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.roundToLong

private const val TAG = "ReadAloudController"
private const val QUEUE_LIMIT = 48
private const val TTS_INIT_TIMEOUT_MS = 10_000L

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
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0,
    val providerLabel: String = "本地 TTS",
    val autoAdvanceEnabled: Boolean = true,
    val speechRate: Float = 1f,
    val audioSpectrum: List<Float> = emptyList(),
    val highlightRange: ReadAloudHighlightRange? = null,
    val errorMessage: String? = null
)

data class ReadAloudHighlightRange(
    val itemId: Long,
    val segmentIndex: Int,
    val segmentText: String,
    val rangeStart: Int,
    val rangeEnd: Int,
    val isFallback: Boolean,
    val useOriginalContent: Boolean,
    val importedChunkIndex: Int? = null,
    val importedCharOffset: Int = 0,
    val importedCharEndOffset: Int = importedCharOffset,
    val contentBlockIndex: Int? = null,
    val contentCharOffset: Int = 0,
    val contentCharEndOffset: Int = contentCharOffset,
    val isTitle: Boolean = false
)

data class ReadAloudStartAnchor(
    val textSnippet: String? = null,
    val progress: Float? = null,
    val importedChunkIndex: Int? = null,
    val importedCharOffset: Int = 0,
    val contentBlockIndex: Int? = null,
    val contentCharOffset: Int = 0
)

class ReadAloudController(
    context: Context,
    private val appScope: CoroutineScope,
    private val rssRepository: RssRepository,
    private val playbackStartVolumeLimiter: MediaPlaybackStartVolumeLimiter? = null,
    private val playbackStartVolumeLimitPercentProvider: suspend () -> Int? = { null }
) {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(ReadAloudUiState())
    val uiState: StateFlow<ReadAloudUiState> = _uiState
    private val _audioSpectrumFrames = MutableSharedFlow<FloatArray>(
        replay = 1,
        extraBufferCapacity = READ_ALOUD_AUDIO_SPECTRUM_FRAME_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioSpectrumFrames: SharedFlow<FloatArray> = _audioSpectrumFrames.asSharedFlow()

    private var queue: List<QueueEntry> = emptyList()
    private var currentQueueIndex: Int = -1
    private var playbackJob: Job? = null
    private var fallbackHighlightJob: Job? = null
    private var playbackSessionId: Long = 0L

    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var localVoiceLabel: String = "本地 TTS"
    private var localEnginePackage: String? = null

    private var currentSegmentSource: ReadAloudTextSegmentSource? = null
    private var currentSegment: ReadAloudSegment? = null
    private var currentSegmentIndex: Int = 0
    private var currentSegmentCount: Int = 0
    private var currentUtteranceId: String? = null
    private var currentHighlightRange: ReadAloudHighlightRange? = null
    private var paused: Boolean = false
    private var autoAdvanceEnabled: Boolean = true
    private var speechRate: Float = 1f
    private var fallbackUnitsPerSecond: Double = READ_ALOUD_FALLBACK_INITIAL_UNITS_PER_SECOND
    private var currentSegmentStartedAtNs: Long = 0L
    private var currentSegmentUnits: Double = 0.0
    private var currentSegmentSpeechRate: Float = 1f
    private val audioSpectrumAnalyzer = ReadAloudAudioSpectrumAnalyzer()
    private var synthesisSampleRateHz: Int = 0
    private var synthesisAudioFormat: Int = AudioFormat.ENCODING_INVALID
    private var synthesisChannelCount: Int = 1
    private var synthesisBeginLogged: Boolean = false
    private var audioSpectrumSampleLogged: Boolean = false

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            appScope.launch {
                if (!isCurrentUtterance(utteranceId)) return@launch
                val currentUtterance = utteranceId ?: return@launch
                val segment = currentSegment
                currentSegmentStartedAtNs = System.nanoTime()
                currentSegmentUnits = segment?.let(::fallbackSpeechUnits) ?: 0.0
                currentSegmentSpeechRate = speechRate
                if (segment != null && currentHighlightRange?.isFallback == true) {
                    startFallbackHighlightJob(
                        sessionId = playbackSessionId,
                        queueIndex = currentQueueIndex,
                        utteranceId = currentUtterance,
                        segment = segment
                    )
                }
                _uiState.update {
                    it.copy(
                        phase = ReadAloudPhase.READY,
                        isPlaying = true,
                        highlightRange = currentHighlightRange,
                        errorMessage = null
                    )
                }
            }
        }

        override fun onBeginSynthesis(
            utteranceId: String?,
            sampleRateInHz: Int,
            audioFormat: Int,
            channelCount: Int
        ) {
            if (!isCurrentUtterance(utteranceId)) return
            synthesisSampleRateHz = sampleRateInHz
            synthesisAudioFormat = audioFormat
            synthesisChannelCount = channelCount.coerceAtLeast(1)
            clearAudioSpectrum()
            if (BuildConfig.DEBUG && !synthesisBeginLogged) {
                synthesisBeginLogged = true
                AppLogger.d(
                    TAG,
                    "TTS PCM synthesis started sampleRate=$sampleRateInHz " +
                        "audioFormat=$audioFormat channelCount=$channelCount"
                )
            }
        }

        override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
            if (!isCurrentUtterance(utteranceId) || audio == null || audio.isEmpty()) return
            updateAudioSpectrumFromPcm(
                audio = audio,
                sampleRateHz = synthesisSampleRateHz,
                audioFormat = synthesisAudioFormat,
                channelCount = synthesisChannelCount
            )
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            appScope.launch {
                if (!isCurrentUtterance(utteranceId)) return@launch
                cancelFallbackHighlightJob()
                setCurrentHighlightRange(
                    queueIndex = currentQueueIndex,
                    segment = currentSegment,
                    rangeStart = start,
                    rangeEnd = end,
                    isFallback = false
                )
            }
        }

        override fun onDone(utteranceId: String?) {
            appScope.launch {
                if (!isCurrentUtterance(utteranceId) || paused) return@launch
                recordFallbackTimingSample()
                cancelFallbackHighlightJob()
                runCatching {
                    speakNextSegmentOrAdvance()
                }.onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    showPlaybackError("本地 TTS 朗读失败", error)
                }
            }
        }

        override fun onError(utteranceId: String?) {
            handleUtteranceError(utteranceId, "本地 TTS 朗读失败")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            handleUtteranceError(utteranceId, "本地 TTS 朗读失败：$errorCode")
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            appScope.launch {
                if (!isCurrentUtterance(utteranceId)) return@launch
                cancelFallbackHighlightJob()
                clearAudioSpectrum()
                _uiState.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun startFromItem(
        itemId: Long,
        startAnchor: ReadAloudStartAnchor? = null,
        preferOriginalContent: Boolean? = null
    ) {
        playbackJob?.cancel()
        playbackJob = appScope.launch {
            runCatching {
                val item = rssRepository.observeItem(itemId).filterNotNull().first()
                val channel = rssRepository.observeChannel(item.channelId).first()
                    ?: error("频道不存在")
                queue = buildQueue(item, channel, startAnchor, preferOriginalContent)
                currentQueueIndex = queue.indexOfFirst { it.item.id == itemId }.coerceAtLeast(0)
                enforcePlaybackStartVolumeLimitForNewSession()
                playQueueIndex(currentQueueIndex)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                showPlaybackError(error.message ?: "朗读启动失败", error)
            }
        }
    }

    private suspend fun enforcePlaybackStartVolumeLimitForNewSession() {
        val limiter = playbackStartVolumeLimiter ?: return
        val limitPercent = try {
            playbackStartVolumeLimitPercentProvider()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.e(TAG, "读取朗读静音开播设置失败", error)
            null
        }
        try {
            limiter.enforcePlaybackStartVolumeLimit(limitPercent)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.e(TAG, "朗读静音开播音量限制失败", error)
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
                if (_uiState.value.isPlaying) {
                    pauseCurrentSegment()
                } else {
                    resumeCurrentSegment()
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

    fun toggleAutoAdvance() {
        autoAdvanceEnabled = !autoAdvanceEnabled
        _uiState.update {
            it.copy(
                autoAdvanceEnabled = autoAdvanceEnabled,
                speechRate = speechRate
            )
        }
    }

    fun decreaseSpeechRate() {
        updateSpeechRate(step = -1)
    }

    fun increaseSpeechRate() {
        updateSpeechRate(step = 1)
    }

    fun stop() {
        playbackSessionId += 1
        playbackJob?.cancel()
        cancelFallbackHighlightJob()
        clearAudioSpectrum()
        paused = false
        currentUtteranceId = null
        currentSegmentSource = null
        currentSegment = null
        currentSegmentIndex = 0
        currentSegmentCount = 0
        currentHighlightRange = null
        appScope.launch {
            stopTts()
        }
        currentQueueIndex = -1
        queue = emptyList()
        _uiState.value = ReadAloudUiState(
            autoAdvanceEnabled = autoAdvanceEnabled,
            speechRate = speechRate
        )
    }

    fun release() {
        stop()
        appScope.launch {
            withContext(Dispatchers.Main) {
                tts?.shutdown()
                tts = null
                ttsReady = false
                clearAudioSpectrum()
            }
        }
    }

    private fun playQueueIndex(index: Int) {
        if (index !in queue.indices) return
        playbackJob?.cancel()
        cancelFallbackHighlightJob()
        val sessionId = ++playbackSessionId
        paused = false
        currentUtteranceId = null
        currentSegmentSource = null
        currentSegment = null
        currentSegmentIndex = 0
        currentSegmentCount = 0
        currentHighlightRange = null
        playbackJob = appScope.launch {
            runCatching {
                stopTts()
                currentQueueIndex = index
                val entry = queue[index]
                updateState(
                    entry = entry,
                    phase = ReadAloudPhase.RESOLVING_CONTENT,
                    isPlaying = false,
                    errorMessage = null
                )

                ensureTtsReady()
                if (!isActivePlayback(sessionId, index)) return@launch

                updateState(
                    entry = entry,
                    phase = ReadAloudPhase.SYNTHESIZING,
                    isPlaying = false,
                    errorMessage = null
                )
                val segmentSource = buildSegmentTextSource(entry)
                val firstSegment = segmentSource.next() ?: error("文章内容为空")
                if (!isActivePlayback(sessionId, index)) return@launch

                currentSegmentSource = segmentSource
                currentSegment = firstSegment
                currentSegmentIndex = 1
                currentSegmentCount = segmentSource.segmentCount
                updateState(
                    entry = entry,
                    phase = ReadAloudPhase.READY,
                    isPlaying = false,
                    errorMessage = null
                )
                speakSegment(sessionId, index, firstSegment)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                if (!isActivePlayback(sessionId, index)) return@onFailure
                showPlaybackError(error.message ?: "朗读失败", error)
            }
        }
    }

    private fun pauseCurrentSegment() {
        paused = true
        cancelFallbackHighlightJob()
        appScope.launch {
            stopTts()
            clearAudioSpectrum()
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    private fun resumeCurrentSegment() {
        val index = currentQueueIndex
        val segment = currentSegment
        if (index !in queue.indices || segment == null || segment.text.isBlank()) return
        val sessionId = playbackSessionId
        paused = false
        playbackJob?.cancel()
        playbackJob = appScope.launch {
            runCatching {
                speakSegment(sessionId, index, segment)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                if (isActivePlayback(sessionId, index)) {
                    showPlaybackError(error.message ?: "朗读失败", error)
                }
            }
        }
    }

    private suspend fun speakNextSegmentOrAdvance() {
        val sessionId = playbackSessionId
        val index = currentQueueIndex
        if (!isActivePlayback(sessionId, index)) return
        val source = currentSegmentSource ?: run {
            playNext()
            return
        }
        val nextSegment = source.next()
        if (!isActivePlayback(sessionId, index)) return
        if (nextSegment == null) {
            currentSegmentSource = null
            currentSegment = null
            currentHighlightRange = null
            cancelFallbackHighlightJob()
            if (autoAdvanceEnabled) {
                currentSegmentIndex = 0
                playNext()
            } else {
                clearAudioSpectrum()
                queue.getOrNull(index)?.let { entry ->
                    updateState(
                        entry = entry,
                        phase = ReadAloudPhase.READY,
                        isPlaying = false,
                        errorMessage = null
                    )
                }
            }
            return
        }
        currentSegment = nextSegment
        currentSegmentIndex += 1
        currentSegmentCount = source.segmentCount
        queue.getOrNull(index)?.let { entry ->
            updateState(
                entry = entry,
                phase = ReadAloudPhase.READY,
                isPlaying = false,
                errorMessage = null
            )
        }
        speakSegment(sessionId, index, nextSegment)
    }

    private suspend fun speakSegment(sessionId: Long, index: Int, segment: ReadAloudSegment) {
        val engine = ensureTtsReady()
        if (!isActivePlayback(sessionId, index)) return
        cancelFallbackHighlightJob()
        val utteranceId = "$sessionId:$index:$currentSegmentIndex:${System.nanoTime()}"
        currentUtteranceId = utteranceId
        val initialFallbackRange = fallbackHighlightOffsets(segment.text, progress = 0f)
        setCurrentHighlightRange(
            queueIndex = index,
            segment = segment,
            rangeStart = initialFallbackRange.start,
            rangeEnd = initialFallbackRange.end,
            isFallback = true
        )
        val result = withContext(Dispatchers.Main) {
            if (engine.setSpeechRate(speechRate) == TextToSpeech.ERROR) {
                TextToSpeech.ERROR
            } else {
                engine.speak(segment.text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            }
        }
        require(result != TextToSpeech.ERROR) { "本地 TTS 无法朗读当前段落" }
        queue.getOrNull(index)?.let { entry ->
            updateState(
                entry = entry,
                phase = ReadAloudPhase.READY,
                isPlaying = true,
                errorMessage = null
            )
        }
    }

    private fun updateSpeechRate(step: Int) {
        val currentIndex = SPEECH_RATE_OPTIONS.indexOfFirst { it == speechRate }
            .takeIf { it >= 0 }
            ?: SPEECH_RATE_OPTIONS.indexOf(1f)
        val nextRate = SPEECH_RATE_OPTIONS
            .getOrNull((currentIndex + step).coerceIn(0, SPEECH_RATE_OPTIONS.lastIndex))
            ?: return
        if (nextRate == speechRate) return

        speechRate = nextRate
        _uiState.update { it.copy(speechRate = nextRate) }

        val index = currentQueueIndex
        val segment = currentSegment
        if (!_uiState.value.isPlaying || index !in queue.indices || segment == null || segment.text.isBlank()) {
            appScope.launch {
                withContext(Dispatchers.Main) {
                    tts?.setSpeechRate(nextRate)
                }
            }
            return
        }

        val sessionId = playbackSessionId
        paused = false
        playbackJob?.cancel()
        playbackJob = appScope.launch {
            runCatching {
                stopTts()
                if (!isActivePlayback(sessionId, index)) return@launch
                speakSegment(sessionId, index, segment)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                if (isActivePlayback(sessionId, index)) {
                    showPlaybackError(error.message ?: "朗读调速失败", error)
                }
            }
        }
    }

    private suspend fun stopTts() {
        withContext(Dispatchers.Main) {
            tts?.stop()
        }
    }

    private fun clearAudioSpectrum() {
        audioSpectrumAnalyzer.clear()
        _audioSpectrumFrames.tryEmit(FloatArray(0))
        _uiState.update { it.copy(audioSpectrum = emptyList()) }
    }

    private fun updateAudioSpectrumFromPcm(
        audio: ByteArray,
        sampleRateHz: Int,
        audioFormat: Int,
        channelCount: Int
    ) {
        val emittedFrameCount = audioSpectrumAnalyzer.analyze(
            audio = audio,
            sampleRateHz = sampleRateHz,
            audioFormat = audioFormat,
            channelCount = channelCount
        ) { levels ->
            _audioSpectrumFrames.tryEmit(levels)
            if (BuildConfig.DEBUG && !audioSpectrumSampleLogged && levels.any { it > 0.01f }) {
                audioSpectrumSampleLogged = true
                AppLogger.d(
                    TAG,
                    "TTS PCM FFT sample=" +
                        levels.joinToString(prefix = "[", postfix = "]") { level ->
                            "%.2f".format(Locale.US, level)
                        }
                )
            }
        }
        if (BuildConfig.DEBUG && emittedFrameCount > 1) {
            AppLogger.d(
                TAG,
                "TTS PCM FFT frames=$emittedFrameCount chunkBytes=${audio.size}"
            )
        }
    }

    private fun startFallbackHighlightJob(
        sessionId: Long,
        queueIndex: Int,
        utteranceId: String,
        segment: ReadAloudSegment
    ) {
        val estimatedDurationMs = estimateFallbackSegmentDurationMs(segment.text)
        fallbackHighlightJob = appScope.launch {
            val startTimeNs = System.nanoTime()
            while (
                isActivePlayback(sessionId, queueIndex) &&
                isCurrentUtterance(utteranceId) &&
                !paused
            ) {
                val elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000L
                val progress = (elapsedMs.toFloat() / estimatedDurationMs.toFloat())
                    .coerceIn(0f, READ_ALOUD_FALLBACK_MAX_PROGRESS)
                val offsets = fallbackHighlightOffsets(segment.text, progress)
                setCurrentHighlightRange(
                    queueIndex = queueIndex,
                    segment = segment,
                    rangeStart = offsets.start,
                    rangeEnd = offsets.end,
                    isFallback = true
                )
                delay(READ_ALOUD_FALLBACK_HIGHLIGHT_INTERVAL_MS)
            }
        }
    }

    private fun cancelFallbackHighlightJob() {
        fallbackHighlightJob?.cancel()
        fallbackHighlightJob = null
    }

    private fun estimateFallbackSegmentDurationMs(text: String): Long {
        val units = fallbackSpeechUnits(text).coerceAtLeast(1.0)
        val unitsPerSecond = fallbackUnitsPerSecond *
            speechRate.coerceAtLeast(READ_ALOUD_MIN_SPEECH_RATE)
        return ((units / unitsPerSecond) * 1_000.0)
            .roundToLong()
            .coerceIn(
                READ_ALOUD_FALLBACK_MIN_DURATION_MS,
                READ_ALOUD_FALLBACK_MAX_DURATION_MS
            )
    }

    private fun recordFallbackTimingSample() {
        val startedAtNs = currentSegmentStartedAtNs
        val units = currentSegmentUnits
        if (startedAtNs <= 0L || units <= 0.0) return
        val elapsedMs = ((System.nanoTime() - startedAtNs) / 1_000_000L)
            .coerceAtLeast(READ_ALOUD_FALLBACK_MIN_SAMPLE_MS)
        val rate = currentSegmentSpeechRate.coerceAtLeast(READ_ALOUD_MIN_SPEECH_RATE)
        val measuredBaseUnitsPerSecond = units / (elapsedMs.toDouble() / 1_000.0) / rate
        if (!measuredBaseUnitsPerSecond.isFinite()) return
        val bounded = measuredBaseUnitsPerSecond.coerceIn(
            READ_ALOUD_FALLBACK_MIN_UNITS_PER_SECOND,
            READ_ALOUD_FALLBACK_MAX_UNITS_PER_SECOND
        )
        fallbackUnitsPerSecond =
            fallbackUnitsPerSecond * READ_ALOUD_FALLBACK_CALIBRATION_KEEP_WEIGHT +
                bounded * (1.0 - READ_ALOUD_FALLBACK_CALIBRATION_KEEP_WEIGHT)
        if (BuildConfig.DEBUG) {
            AppLogger.d(
                TAG,
                "fallback timing sample units=$units elapsedMs=$elapsedMs " +
                    "rate=$rate measured=$bounded calibrated=$fallbackUnitsPerSecond"
            )
        }
    }

    private fun fallbackSpeechUnits(segment: ReadAloudSegment): Double {
        return readAloudFallbackSpeechUnits(segment.text)
    }

    private fun fallbackSpeechUnits(text: String): Double {
        return readAloudFallbackSpeechUnits(text)
    }

    private fun fallbackHighlightOffsets(text: String, progress: Float): HighlightOffsets {
        if (text.isEmpty()) return HighlightOffsets(0, 0)
        val anchor = readAloudFallbackReadableOffsetForProgress(text, progress)
        val start = if (text[anchor].isCjkCharacter()) {
            anchor
        } else {
            previousTokenStart(text, anchor)
        }
        val end = if (text[start].isCjkCharacter()) {
            nextCjkFallbackEnd(text, start)
        } else {
            nextTokenEnd(text, start)
        }.coerceIn(start + 1, text.length)
        return HighlightOffsets(start, end)
    }

    private fun previousTokenStart(text: String, offset: Int): Int {
        var index = offset.coerceIn(0, text.lastIndex)
        while (index > 0 && text[index - 1].isLetterOrDigit() && !text[index - 1].isCjkCharacter()) {
            index--
        }
        return index
    }

    private fun nextTokenEnd(text: String, offset: Int): Int {
        var index = offset.coerceIn(0, text.length)
        while (
            index < text.length &&
            text[index].isLetterOrDigit() &&
            !text[index].isCjkCharacter()
        ) {
            index++
        }
        if (index == offset) {
            index++
        }
        return index.coerceAtMost(text.length)
    }

    private fun nextCjkFallbackEnd(text: String, offset: Int): Int {
        var index = offset.coerceIn(0, text.length)
        var visibleChars = 0
        while (index < text.length && visibleChars < READ_ALOUD_FALLBACK_CJK_WINDOW_CHARS) {
            val char = text[index]
            index++
            if (!char.isWhitespace()) {
                visibleChars++
            }
            if (visibleChars >= READ_ALOUD_FALLBACK_MIN_CJK_WINDOW_CHARS &&
                char in READ_ALOUD_FALLBACK_STOP_CHARS
            ) {
                break
            }
        }
        return index
    }

    private suspend fun ensureTtsReady(): TextToSpeech {
        val existing = tts
        if (existing != null && ttsReady) return existing

        return withContext(Dispatchers.Main) {
            val current = tts
            if (current != null && ttsReady) {
                current
            } else {
                val enginePackage = resolveTtsEnginePackage()
                val deferred = CompletableDeferred<Int>()
                val created = if (enginePackage == null) {
                    TextToSpeech(appContext) { status ->
                        deferred.complete(status)
                    }
                } else {
                    TextToSpeech(appContext, { status ->
                        deferred.complete(status)
                    }, enginePackage)
                }
                tts = created
                localEnginePackage = enginePackage
                created.setOnUtteranceProgressListener(utteranceListener)
                val status = withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) {
                    deferred.await()
                }
                require(status == TextToSpeech.SUCCESS) {
                    if (enginePackage.isNullOrBlank()) {
                        "本地 TTS 初始化失败：未找到可绑定的系统 TTS 引擎"
                    } else {
                        "本地 TTS 初始化失败：$enginePackage status=${status ?: "timeout"}"
                    }
                }
                localVoiceLabel = configureLocalVoice(created)
                ttsReady = true
                created
            }
        }
    }

    private fun configureLocalVoice(engine: TextToSpeech): String {
        val offlineVoice = engine.voices
            .orEmpty()
            .asSequence()
            .filter { voice -> !voice.isNetworkConnectionRequired }
            .sortedBy(::voicePriority)
            .firstOrNull { voice -> voicePriority(voice) < Int.MAX_VALUE }

        if (offlineVoice != null && engine.setVoice(offlineVoice) != TextToSpeech.ERROR) {
            return "本地 TTS · ${offlineVoice.locale.toLanguageTag()}"
        }

        val fallbackLocale = listOf(
            Locale.SIMPLIFIED_CHINESE,
            Locale.CHINESE,
            Locale.ENGLISH
        ).firstOrNull { locale ->
            engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
        } ?: error("设备没有可用的本地 TTS 语音")

        val result = engine.setLanguage(fallbackLocale)
        require(result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
            "设备未安装可用的本地 TTS 语音"
        }
        return "本地 TTS · ${fallbackLocale.toLanguageTag()}"
    }

    private fun resolveTtsEnginePackage(): String? {
        val enginePackages = queryTtsEnginePackages()
        if (enginePackages.isEmpty()) return null
        val defaultEngine = runCatching {
            Settings.Secure.getString(appContext.contentResolver, TTS_DEFAULT_ENGINE_SETTING)
        }.getOrNull()
        return defaultEngine
            ?.takeIf { it.isNotBlank() && it in enginePackages }
            ?: enginePackages.firstOrNull()
    }

    private fun queryTtsEnginePackages(): List<String> {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
        return services
            .mapNotNull { it.serviceInfo?.packageName }
            .distinct()
    }

    private fun voicePriority(voice: Voice): Int {
        val language = voice.locale.language.lowercase(Locale.US)
        return when (language) {
            Locale.CHINESE.language -> 0
            Locale.ENGLISH.language -> 1
            else -> Int.MAX_VALUE
        }
    }

    private suspend fun awaitReadableItem(entry: QueueEntry): RssItem {
        val item = entry.item
        return if (entry.useOriginalContent) {
            if (item.originalContent.isNullOrBlank()) {
                rssRepository.requestOriginalContent(item.id, force = true)
            }
            if (!item.originalContent.isNullOrBlank()) {
                item
            } else {
                withTimeoutOrNull(25_000L) {
                    rssRepository.observeItem(item.id)
                        .filterNotNull()
                        .first { latest ->
                            !latest.originalContent.isNullOrBlank()
                        }
                } ?: rssRepository.observeItem(item.id).filterNotNull().first()
            }
        } else {
            item
        }
    }

    private suspend fun awaitReadableText(entry: QueueEntry): String {
        val current = awaitReadableItem(entry)
        val raw = current.effectiveContent(entry.useOriginalContent)
            ?: current.summary
            ?: current.title
        val baseText = ReadAloudTextSegmenter.buildArticleText(current.title, raw)
        require(baseText.isNotBlank()) { "文章内容为空" }
        return baseText
    }

    private suspend fun buildQueue(
        item: RssItem,
        channel: RssChannel,
        startAnchor: ReadAloudStartAnchor?,
        preferOriginalContent: Boolean?
    ): List<QueueEntry> {
        val items = rssRepository.observeItemsPaged(channel.id, QUEUE_LIMIT).first()
        val initial = if (items.any { it.id == item.id }) items else listOf(item) + items
        return initial
            .distinctBy { it.id }
            .map { queueItem ->
                QueueEntry(
                    item = queueItem,
                    channelTitle = channel.title,
                    useOriginalContent = if (queueItem.id == item.id) {
                        preferOriginalContent ?: channel.useOriginalContent
                    } else {
                        channel.useOriginalContent
                    },
                    startAnchor = startAnchor.takeIf { queueItem.id == item.id }
                )
            }
    }

    private suspend fun buildSegmentTextSource(entry: QueueEntry): ReadAloudTextSegmentSource {
        val importedReader = rssRepository.getImportedTextReader(entry.item.id)
        if (importedReader != null && ImportedContentIds.isImportedTextItemUrl(entry.item.link)) {
            val anchor = entry.startAnchor
            val progressChunkIndex = anchor
                ?.progress
                ?.let { progress ->
                    (importedReader.chunkCount * progress.coerceIn(0f, 1f))
                        .toInt()
                        .coerceIn(0, (importedReader.chunkCount - 1).coerceAtLeast(0))
                }
            return ImportedTextSegmentSource(
                item = entry.item,
                reader = importedReader,
                repository = rssRepository,
                startChunkIndex = anchor?.importedChunkIndex ?: progressChunkIndex,
                startCharOffset = anchor?.importedCharOffset ?: 0
            )
        }
        val current = awaitReadableItem(entry)
        val contentSegments = buildContentBlockSegments(
            item = current,
            useOriginalContent = entry.useOriginalContent,
            startAnchor = entry.startAnchor
        )
        if (contentSegments.isNotEmpty()) {
            return ListTextSegmentSource(contentSegments)
        }

        val text = awaitReadableText(entry)
        val anchoredText = entry.startAnchor
            ?.let { anchor -> applyTextAnchor(text, anchor) }
            ?.takeIf { it.isNotBlank() }
            ?: text
        return ListTextSegmentSource(
            segments = readAloudSegmentSlices(anchoredText)
                .map { slice ->
                    ReadAloudSegment(
                        text = slice.text,
                        sourceOffsets = slice.sourceOffsets
                    )
                }
        )
    }

    private fun buildContentBlockSegments(
        item: RssItem,
        useOriginalContent: Boolean,
        startAnchor: ReadAloudStartAnchor?
    ): List<ReadAloudSegment> {
        val segments = mutableListOf<ReadAloudSegment>()
        val startsInsideContentBlock = startAnchor?.contentBlockIndex != null
        if (!startsInsideContentBlock) {
            buildTitleSegments(item.title).forEach { segments += it }
        }
        val blocks = buildContentBlocks(item, useOriginalContent)
        val anchorBlockIndex = startAnchor?.contentBlockIndex
        blocks.forEachIndexed { blockIndex, block ->
            val textBlock = block as? ContentBlock.Text ?: return@forEachIndexed
            if (anchorBlockIndex != null && blockIndex < anchorBlockIndex) {
                return@forEachIndexed
            }
            val sourceStartOffset = if (anchorBlockIndex == blockIndex) {
                startAnchor.contentCharOffset.coerceIn(0, textBlock.text.length)
            } else {
                0
            }
            readAloudSegmentSlices(
                text = textBlock.text.substring(sourceStartOffset),
                sourceBaseOffset = sourceStartOffset
            ).forEach { slice ->
                segments += ReadAloudSegment(
                    text = slice.text,
                    contentBlockIndex = blockIndex,
                    sourceOffsets = slice.sourceOffsets
                )
            }
        }
        return if (anchorBlockIndex == null) {
            applySegmentListAnchor(segments, startAnchor)
        } else {
            segments
        }
    }

    private fun buildTitleSegments(title: String): List<ReadAloudSegment> {
        val normalizedTitle = normalizeTextWithSourceOffsets(title)
        if (normalizedTitle.text.isBlank()) return emptyList()
        val titleText = if (normalizedTitle.text.last() in READ_ALOUD_SEGMENT_ENDINGS) {
            normalizedTitle.text
        } else {
            "${normalizedTitle.text}。"
        }
        val titleOffsets = if (titleText.length == normalizedTitle.text.length) {
            normalizedTitle.sourceOffsets
        } else {
            normalizedTitle.sourceOffsets + intArrayOf(
                (normalizedTitle.sourceOffsets.lastOrNull() ?: 0) + 1
            )
        }
        return listOf(
            ReadAloudSegment(
                text = titleText,
                isTitle = true,
                sourceOffsets = titleOffsets
            )
        )
    }

    private fun applySegmentListAnchor(
        segments: List<ReadAloudSegment>,
        startAnchor: ReadAloudStartAnchor?
    ): List<ReadAloudSegment> {
        val anchor = startAnchor ?: return segments
        val progress = anchor.progress ?: return segments
        if (segments.isEmpty()) return segments
        val totalChars = segments.sumOf { it.text.length }.coerceAtLeast(1)
        val targetChars = (totalChars * progress.coerceIn(0f, 1f)).toInt()
        var consumed = 0
        return segments.dropWhile { segment ->
            val shouldDrop = consumed + segment.text.length < targetChars
            consumed += segment.text.length
            shouldDrop
        }.ifEmpty { listOf(segments.last()) }
    }

    private fun applyTextAnchor(text: String, anchor: ReadAloudStartAnchor): String {
        val startOffset = findSnippetStartOffset(
            text = text,
            snippet = anchor.textSnippet,
            progress = anchor.progress
        ) ?: anchor.progress
            ?.let { progress -> offsetFromProgress(text, progress) }
            ?: 0
        return text.substring(startOffset.coerceIn(0, text.length)).trim()
    }

    private fun findSnippetStartOffset(
        text: String,
        snippet: String?,
        progress: Float?
    ): Int? {
        val normalized = snippet
            ?.let(ReadAloudTextSegmenter::normalizePlainText)
            ?.takeIf { it.length >= MIN_ANCHOR_SNIPPET_CHARS }
            ?: return null
        val candidates = listOf(160, 96, 48, 24)
            .mapNotNull { length ->
                normalized.take(length)
                    .takeIf { it.length >= MIN_ANCHOR_SNIPPET_CHARS }
            }
            .distinct()
        val targetOffset = progress
            ?.coerceIn(0f, 1f)
            ?.let { (text.length * it).toInt().coerceIn(0, text.length) }
        var bestOffset: Int? = null
        var bestDistance = Int.MAX_VALUE
        candidates.forEach { candidate ->
            var searchFrom = 0
            while (searchFrom <= text.length) {
                val index = text.indexOf(candidate, startIndex = searchFrom)
                if (index < 0) break
                val distance = targetOffset?.let { kotlin.math.abs(index - it) } ?: 0
                if (bestOffset == null || distance < bestDistance) {
                    bestOffset = index
                    bestDistance = distance
                }
                searchFrom = index + 1
            }
            if (bestOffset != null && targetOffset == null) return bestOffset
        }
        return bestOffset
    }

    private fun offsetFromProgress(text: String, progress: Float): Int {
        val rawOffset = (text.length * progress.coerceIn(0f, 1f))
            .toInt()
            .coerceIn(0, text.length)
        return firstBoundaryAfter(text, rawOffset) ?: rawOffset
    }

    private fun firstBoundaryAfter(text: String, startOffset: Int): Int? {
        var index = startOffset.coerceIn(0, text.length)
        while (index < text.length) {
            if (text[index] in READ_ALOUD_ANCHOR_BOUNDARIES) {
                return skipLeadingWhitespace(text, index + 1)
            }
            index++
        }
        return null
    }

    private fun isActivePlayback(sessionId: Long, queueIndex: Int): Boolean {
        return playbackSessionId == sessionId && currentQueueIndex == queueIndex
    }

    private fun isCurrentUtterance(utteranceId: String?): Boolean {
        return utteranceId != null && utteranceId == currentUtteranceId
    }

    private fun handleUtteranceError(utteranceId: String?, message: String) {
        appScope.launch {
            if (!isCurrentUtterance(utteranceId)) return@launch
            showPlaybackError(message, null)
        }
    }

    private fun setCurrentHighlightRange(
        queueIndex: Int,
        segment: ReadAloudSegment?,
        rangeStart: Int,
        rangeEnd: Int,
        isFallback: Boolean
    ) {
        val entry = queue.getOrNull(queueIndex) ?: return
        val text = segment?.text ?: return
        if (text.isBlank()) return
        val safeStart = rangeStart.coerceIn(0, text.length)
        val safeEnd = rangeEnd.coerceIn(safeStart, text.length)
        val sourceStart = segment.sourceOffsetForTextOffset(safeStart)
        val sourceEnd = segment.sourceEndOffsetForTextOffset(safeEnd)
        val range = ReadAloudHighlightRange(
            itemId = entry.item.id,
            segmentIndex = currentSegmentIndex,
            segmentText = text,
            rangeStart = safeStart,
            rangeEnd = safeEnd,
            isFallback = isFallback,
            useOriginalContent = entry.useOriginalContent,
            importedChunkIndex = segment.importedChunkIndex,
            importedCharOffset = segment.importedCharOffset + sourceStart,
            importedCharEndOffset = segment.importedCharOffset + sourceEnd,
            contentBlockIndex = segment.contentBlockIndex,
            contentCharOffset = segment.contentCharOffset + sourceStart,
            contentCharEndOffset = segment.contentCharOffset + sourceEnd,
            isTitle = segment.isTitle
        )
        currentHighlightRange = range
        _uiState.update {
            it.copy(
                highlightRange = range,
                segmentIndex = currentSegmentIndex,
                segmentCount = currentSegmentCount
            )
        }
    }

    private fun showPlaybackError(message: String, error: Throwable?) {
        if (error != null) {
            AppLogger.e(TAG, message, error)
        }
        clearAudioSpectrum()
        currentHighlightRange = null
        val entry = queue.getOrNull(currentQueueIndex)
        _uiState.update {
            it.copy(
                visible = entry != null || it.visible,
                phase = ReadAloudPhase.ERROR,
                currentItemId = entry?.item?.id ?: it.currentItemId,
                currentTitle = entry?.item?.title ?: it.currentTitle,
                currentChannelTitle = entry?.channelTitle ?: it.currentChannelTitle,
                queueIndex = if (currentQueueIndex >= 0) currentQueueIndex + 1 else it.queueIndex,
                queueSize = queue.size,
                segmentIndex = currentSegmentIndex,
                segmentCount = currentSegmentCount,
                providerLabel = localVoiceLabel,
                autoAdvanceEnabled = autoAdvanceEnabled,
                speechRate = speechRate,
                audioSpectrum = emptyList(),
                highlightRange = null,
                errorMessage = message,
                isPlaying = false
            )
        }
    }

    private fun updateState(
        entry: QueueEntry,
        phase: ReadAloudPhase,
        isPlaying: Boolean,
        errorMessage: String?
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
                isPlaying = isPlaying,
                progressMs = 0L,
                durationMs = 0L,
                segmentIndex = currentSegmentIndex,
                segmentCount = currentSegmentCount,
                providerLabel = localVoiceLabel,
                autoAdvanceEnabled = autoAdvanceEnabled,
                speechRate = speechRate,
                audioSpectrum = if (isPlaying) it.audioSpectrum else emptyList(),
                highlightRange = currentHighlightRange,
                errorMessage = errorMessage
            )
        }
    }

    private data class ReadAloudSegment(
        val text: String,
        val importedChunkIndex: Int? = null,
        val importedCharOffset: Int = 0,
        val contentBlockIndex: Int? = null,
        val contentCharOffset: Int = 0,
        val isTitle: Boolean = false,
        val sourceOffsets: IntArray? = null
    ) {
        fun sourceOffsetForTextOffset(textOffset: Int): Int {
            val offsets = sourceOffsets ?: return textOffset
            if (offsets.isEmpty()) return 0
            val index = textOffset.coerceIn(0, text.length)
            if (index >= offsets.size) {
                return offsets.last() + 1
            }
            return offsets[index]
        }

        fun sourceEndOffsetForTextOffset(textOffset: Int): Int {
            val offsets = sourceOffsets ?: return textOffset
            if (offsets.isEmpty()) return 0
            val index = textOffset.coerceIn(0, text.length)
            if (index >= offsets.size) {
                return offsets.last() + 1
            }
            return offsets[index].coerceAtLeast(sourceOffsetForTextOffset(textOffset))
        }
    }

    private data class HighlightOffsets(
        val start: Int,
        val end: Int
    )

    private data class QueueEntry(
        val item: RssItem,
        val channelTitle: String,
        val useOriginalContent: Boolean,
        val startAnchor: ReadAloudStartAnchor? = null
    )

    private interface ReadAloudTextSegmentSource {
        val segmentCount: Int
        suspend fun next(): ReadAloudSegment?
    }

    private class ListTextSegmentSource(
        private val segments: List<ReadAloudSegment>
    ) : ReadAloudTextSegmentSource {
        override val segmentCount: Int = segments.size
        private var index = 0

        override suspend fun next(): ReadAloudSegment? {
            if (index >= segments.size) return null
            return segments[index++]
        }
    }

    private class ImportedTextSegmentSource(
        item: RssItem,
        private val reader: ImportedTextReader,
        private val repository: RssRepository,
        startChunkIndex: Int?,
        startCharOffset: Int
    ) : ReadAloudTextSegmentSource {
        override val segmentCount: Int = 0
        private val title = item.title
        private val pendingSegments = ArrayDeque<ReadAloudSegment>()
        private val anchoredStart = startChunkIndex != null
        private var titleEmitted = anchoredStart
        private var chunkIndex = startChunkIndex
            ?.coerceIn(0, reader.chunkCount)
            ?: 0
        private var firstChunkCharOffset = startCharOffset.coerceAtLeast(0)

        override suspend fun next(): ReadAloudSegment? {
            while (pendingSegments.isEmpty()) {
                if (!titleEmitted) {
                    titleEmitted = true
                    val titleText = ReadAloudTextSegmenter.normalizePlainText(title)
                    if (titleText.isNotBlank()) {
                        val punctuatedTitle = if (titleText.last() in SENTENCE_ENDINGS) {
                            titleText
                        } else {
                            "$titleText。"
                        }
                        enqueueSegments(
                            text = punctuatedTitle,
                            importedChunkIndex = null,
                            importedCharOffset = 0
                        )
                    }
                    continue
                }
                if (chunkIndex >= reader.chunkCount) {
                    return null
                }
                val sourceChunkIndex = chunkIndex
                val chunk = repository.loadImportedTextChunk(reader.marker, sourceChunkIndex)
                chunkIndex += 1
                if (chunk.isNullOrBlank()) {
                    firstChunkCharOffset = 0
                    continue
                }
                val sourceCharOffset = firstChunkCharOffset.coerceAtMost(chunk.length)
                val readableChunk = if (firstChunkCharOffset > 0) {
                    chunk.substring(sourceCharOffset)
                } else {
                    chunk
                }
                firstChunkCharOffset = 0
                if (readableChunk.isBlank()) {
                    continue
                }
                enqueueSegments(
                    text = readableChunk,
                    importedChunkIndex = sourceChunkIndex,
                    importedCharOffset = sourceCharOffset
                )
            }
            return pendingSegments.removeFirst()
        }

        private fun enqueueSegments(
            text: String,
            importedChunkIndex: Int?,
            importedCharOffset: Int
        ) {
            readAloudSegmentSlices(
                text = text,
                sourceBaseOffset = importedCharOffset
            ).forEach { slice ->
                pendingSegments.add(
                    ReadAloudSegment(
                        text = slice.text,
                        importedChunkIndex = importedChunkIndex,
                        sourceOffsets = slice.sourceOffsets
                    )
                )
            }
        }

        private companion object {
            val SENTENCE_ENDINGS = setOf('。', '！', '？', '!', '?', ';', '；', '.', ':', '：')
        }
    }
}

private data class ReadAloudSegmentSlice(
    val text: String,
    val sourceOffsets: IntArray
)

private data class NormalizedSourceText(
    val text: String,
    val sourceOffsets: IntArray
)

private fun readAloudSegmentSlices(
    text: String,
    sourceBaseOffset: Int = 0,
    maxSegmentChars: Int = READ_ALOUD_TTS_SEGMENT_MAX_CHARS
): List<ReadAloudSegmentSlice> {
    require(maxSegmentChars > 0) { "maxSegmentChars must be positive" }
    val normalized = normalizeTextWithSourceOffsets(text, sourceBaseOffset)
    if (normalized.text.isBlank()) return emptyList()
    val parts = splitNormalizedTextIntoSentenceParts(normalized, maxSegmentChars)
    if (parts.isEmpty()) return emptyList()
    val result = mutableListOf<ReadAloudSegmentSlice>()
    var currentText = StringBuilder()
    val currentOffsets = mutableListOf<Int>()

    fun flushCurrent() {
        if (currentText.isNotEmpty()) {
            result += ReadAloudSegmentSlice(
                text = currentText.toString(),
                sourceOffsets = currentOffsets.toIntArray()
            )
            currentText = StringBuilder()
            currentOffsets.clear()
        }
    }

    parts.forEach { part ->
        val separatorLength = if (currentText.isNotEmpty()) 1 else 0
        if (currentText.length + separatorLength + part.text.length > maxSegmentChars) {
            flushCurrent()
        }
        if (currentText.isNotEmpty()) {
            currentText.append(' ')
            currentOffsets += currentOffsets.lastOrNull() ?: part.sourceOffsets.first()
        }
        currentText.append(part.text)
        part.sourceOffsets.forEach { offset -> currentOffsets += offset }
    }
    flushCurrent()
    return result
}

private fun splitNormalizedTextIntoSentenceParts(
    normalized: NormalizedSourceText,
    maxSegmentChars: Int
): List<ReadAloudSegmentSlice> {
    val result = mutableListOf<ReadAloudSegmentSlice>()
    var start = 0
    fun addRange(rawStart: Int, rawEnd: Int) {
        var rangeStart = rawStart
        var rangeEnd = rawEnd
        while (rangeStart < rangeEnd && normalized.text[rangeStart].isWhitespace()) {
            rangeStart++
        }
        while (rangeEnd > rangeStart && normalized.text[rangeEnd - 1].isWhitespace()) {
            rangeEnd--
        }
        if (rangeStart >= rangeEnd) return
        var chunkStart = rangeStart
        while (chunkStart < rangeEnd) {
            val chunkEnd = (chunkStart + maxSegmentChars).coerceAtMost(rangeEnd)
            result += ReadAloudSegmentSlice(
                text = normalized.text.substring(chunkStart, chunkEnd),
                sourceOffsets = normalized.sourceOffsets.copyOfRange(chunkStart, chunkEnd)
            )
            chunkStart = chunkEnd
        }
    }
    normalized.text.forEachIndexed { index, char ->
        if (char in READ_ALOUD_SEGMENT_ENDINGS) {
            addRange(start, index + 1)
            start = index + 1
        }
    }
    addRange(start, normalized.text.length)
    return result
}

private fun normalizeTextWithSourceOffsets(
    value: String,
    sourceBaseOffset: Int = 0
): NormalizedSourceText {
    val builder = StringBuilder(value.length)
    val offsets = ArrayList<Int>(value.length)
    var lastWasSpace = false
    value.forEachIndexed { index, char ->
        if (char.isWhitespace()) {
            if (builder.isNotEmpty() && !lastWasSpace) {
                builder.append(' ')
                offsets += sourceBaseOffset + index
                lastWasSpace = true
            }
        } else {
            builder.append(char)
            offsets += sourceBaseOffset + index
            lastWasSpace = false
        }
    }
    if (builder.isNotEmpty() && builder.last() == ' ') {
        builder.deleteAt(builder.length - 1)
        offsets.removeAt(offsets.lastIndex)
    }
    return NormalizedSourceText(
        text = builder.toString(),
        sourceOffsets = offsets.toIntArray()
    )
}

private fun skipLeadingWhitespace(text: String, startOffset: Int): Int {
    var index = startOffset.coerceIn(0, text.length)
    while (index < text.length && text[index].isWhitespace()) {
        index++
    }
    return index
}

internal fun readAloudFallbackSpeechUnits(text: String): Double {
    var units = 0.0
    text.forEachIndexed { index, _ ->
        units += readAloudFallbackCharUnits(text, index)
    }
    return units.coerceAtLeast(1.0)
}

internal fun readAloudFallbackOffsetForProgress(text: String, progress: Float): Int {
    if (text.isEmpty()) return 0
    val targetUnits = readAloudFallbackSpeechUnits(text) * progress.coerceIn(0f, 1f)
    if (targetUnits <= 0.0) return 0
    var accumulated = 0.0
    text.forEachIndexed { index, _ ->
        accumulated += readAloudFallbackCharUnits(text, index)
        if (accumulated >= targetUnits) {
            return index
        }
    }
    return text.lastIndex
}

internal fun readAloudFallbackReadableOffsetForProgress(text: String, progress: Float): Int {
    if (text.isEmpty()) return 0
    var anchor = readAloudFallbackOffsetForProgress(text, progress)
    anchor = skipFallbackHighlightAnchor(text, anchor)
    return anchor
}

private fun readAloudFallbackCharUnits(text: String, index: Int): Double {
    val char = text[index]
    return when {
        char.isWhitespace() -> 0.0
        char.isCjkCharacter() -> 1.0
        char.isDigit() -> 0.95
        char.isAsciiLetter() -> {
            if (text.hasDigitNeighbor(index)) {
                0.72
            } else {
                0.28
            }
        }
        char in READ_ALOUD_FALLBACK_MODEL_SEPARATORS && text.hasAlphanumericNeighbor(index) -> 0.04
        else -> 0.18
    }
}

private fun skipFallbackHighlightAnchor(text: String, offset: Int): Int {
    var anchor = skipLeadingWhitespace(text, offset)
    if (anchor >= text.length) {
        return text.indexOfLast { !it.isWhitespace() }
            .takeIf { it >= 0 }
            ?: 0
    }
    while (text[anchor] in READ_ALOUD_FALLBACK_SKIP_START_CHARS && anchor + 1 < text.length) {
        anchor = skipLeadingWhitespace(text, anchor + 1).coerceAtMost(text.length - 1)
    }
    return anchor
}

private fun Char.isAsciiLetter(): Boolean {
    return this in 'A'..'Z' || this in 'a'..'z'
}

private fun String.hasDigitNeighbor(index: Int): Boolean {
    return getOrNull(index - 1)?.isDigit() == true ||
        getOrNull(index + 1)?.isDigit() == true
}

private fun String.hasAlphanumericNeighbor(index: Int): Boolean {
    return getOrNull(index - 1)?.isLetterOrDigit() == true ||
        getOrNull(index + 1)?.isLetterOrDigit() == true
}

private const val MIN_ANCHOR_SNIPPET_CHARS = 8
private const val TTS_DEFAULT_ENGINE_SETTING = "tts_default_synth"
private const val READ_ALOUD_TTS_SEGMENT_MAX_CHARS = 240
private const val READ_ALOUD_MIN_SPEECH_RATE = 0.1f
private const val READ_ALOUD_FALLBACK_INITIAL_UNITS_PER_SECOND = 3.75
private const val READ_ALOUD_FALLBACK_MIN_UNITS_PER_SECOND = 1.8
private const val READ_ALOUD_FALLBACK_MAX_UNITS_PER_SECOND = 5.8
private const val READ_ALOUD_FALLBACK_CALIBRATION_KEEP_WEIGHT = 0.65
private const val READ_ALOUD_FALLBACK_MIN_SAMPLE_MS = 400L
private const val READ_ALOUD_FALLBACK_MIN_DURATION_MS = 1_200L
private const val READ_ALOUD_FALLBACK_MAX_DURATION_MS = 240_000L
private const val READ_ALOUD_FALLBACK_HIGHLIGHT_INTERVAL_MS = 900L
private const val READ_ALOUD_FALLBACK_MAX_PROGRESS = 0.985f
private const val READ_ALOUD_FALLBACK_CJK_WINDOW_CHARS = 6
private const val READ_ALOUD_FALLBACK_MIN_CJK_WINDOW_CHARS = 2
private const val READ_ALOUD_AUDIO_SPECTRUM_FRAME_BUFFER_CAPACITY = 96
private val SPEECH_RATE_OPTIONS = listOf(
    0.75f,
    0.9f,
    1f,
    1.15f,
    1.3f,
    1.5f,
    1.75f,
    2f,
    2.5f,
    3f,
    3.5f,
    4f
)
private val READ_ALOUD_SEGMENT_ENDINGS = setOf('。', '！', '？', '!', '?', ';', '；', '.', ':', '：')
private val READ_ALOUD_ANCHOR_BOUNDARIES = setOf(
    '，', ',',
    '。', '.',
    '；', ';',
    '：', ':',
    '！', '!',
    '？', '?'
)
private val READ_ALOUD_FALLBACK_MODEL_SEPARATORS = setOf(
    '(', ')',
    '[', ']',
    '{', '}',
    '/', '\\',
    '-', '+',
    '_', '.'
)
private val READ_ALOUD_FALLBACK_SKIP_START_CHARS = setOf(
    '，', ',',
    '。', '.',
    '；', ';',
    '：', ':',
    '！', '!',
    '？', '?',
    '、',
    '(', ')',
    '[', ']',
    '{', '}',
    '/', '\\',
    '-', '+',
    '_'
)
private val READ_ALOUD_FALLBACK_STOP_CHARS = setOf(
    '，', ',',
    '。', '.',
    '；', ';',
    '：', ':',
    '！', '!',
    '？', '?',
    '、'
)

private fun Char.isCjkCharacter(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES
}
