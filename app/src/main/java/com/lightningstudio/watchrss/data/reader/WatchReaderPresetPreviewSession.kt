package com.lightningstudio.watchrss.data.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class WatchReaderPresetPreviewState(
    val sessionId: String,
    val sequence: Long,
    val preset: ReaderPreset,
    val resourceTransferInProgress: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

class WatchReaderPresetPreviewSession(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val publishIntervalMs: Long = DEFAULT_PUBLISH_INTERVAL_MS
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow<WatchReaderPresetPreviewState?>(null)
    private var timeoutJob: Job? = null
    private var publishJob: Job? = null
    private var pendingState: WatchReaderPresetPreviewState? = null
    private var acceptedSessionId: String? = null
    private var acceptedSequence: Long = -1L
    private var acceptedPreset: ReaderPreset? = null
    private var lastPublishedAt = 0L

    val state: StateFlow<WatchReaderPresetPreviewState?> = mutableState.asStateFlow()

    fun update(
        sessionId: String,
        sequence: Long,
        preset: ReaderPreset,
        resourceTransferInProgress: Boolean = false
    ): Boolean {
        require(sessionId.isNotBlank()) { "缺少预览会话 ID" }
        require(sequence >= 0L) { "预览更新序号无效" }
        synchronized(lock) {
            if (acceptedSessionId == sessionId && sequence <= acceptedSequence) return false
            val normalized = preset.normalized()
            acceptedSessionId = sessionId
            acceptedSequence = sequence
            acceptedPreset = normalized
            publishImmediatelyLocked(
                WatchReaderPresetPreviewState(
                    sessionId = sessionId,
                    sequence = sequence,
                    preset = normalized,
                    resourceTransferInProgress = resourceTransferInProgress
                )
            )
            scheduleTimeout(
                sessionId = sessionId,
                delayMs = if (resourceTransferInProgress) RESOURCE_TRANSFER_TIMEOUT_MS else timeoutMs
            )
            return true
        }
    }

    fun updateDelta(
        sessionId: String,
        sequence: Long,
        changes: JSONObject,
        resourceTransferInProgress: Boolean = false
    ): Boolean {
        require(sessionId.isNotBlank()) { "缺少预览会话 ID" }
        require(sequence >= 0L) { "预览更新序号无效" }
        synchronized(lock) {
            require(acceptedSessionId == sessionId && acceptedPreset != null) {
                "预览差量缺少完整基线"
            }
            if (sequence <= acceptedSequence) return false
            acceptedSequence = sequence
            if (changes.length() == 0) {
                scheduleTimeout(sessionId, timeoutMs)
                return true
            }
            val preset = ReaderPresetCodec.applyChanges(checkNotNull(acceptedPreset), changes)
            acceptedPreset = preset
            offerForPublishLocked(
                WatchReaderPresetPreviewState(
                    sessionId = sessionId,
                    sequence = sequence,
                    preset = preset,
                    resourceTransferInProgress = resourceTransferInProgress
                )
            )
            scheduleTimeout(
                sessionId,
                if (resourceTransferInProgress) RESOURCE_TRANSFER_TIMEOUT_MS else timeoutMs
            )
            return true
        }
    }

    fun heartbeat(sessionId: String, sequence: Long): Long? = synchronized(lock) {
        if (acceptedSessionId != sessionId) return null
        if (sequence > acceptedSequence) acceptedSequence = sequence
        scheduleTimeout(sessionId, timeoutMs)
        acceptedSequence
    }

    fun refreshResourceTransfer(): Boolean = synchronized(lock) {
        val current = mutableState.value?.takeIf { it.resourceTransferInProgress } ?: return false
        scheduleTimeout(current.sessionId, RESOURCE_TRANSFER_TIMEOUT_MS)
        true
    }

    fun stop(sessionId: String): Boolean = synchronized(lock) {
        if (acceptedSessionId != sessionId) return false
        timeoutJob?.cancel()
        timeoutJob = null
        publishJob?.cancel()
        publishJob = null
        pendingState = null
        acceptedSessionId = null
        acceptedSequence = -1L
        acceptedPreset = null
        mutableState.value = null
        true
    }

    private fun offerForPublishLocked(next: WatchReaderPresetPreviewState) {
        val now = monotonicTimeMs()
        val remainingMs = publishIntervalMs - (now - lastPublishedAt)
        if (publishIntervalMs <= 0L || mutableState.value == null || remainingMs <= 0L) {
            publishImmediatelyLocked(next)
            return
        }
        pendingState = next
        if (publishJob != null) return
        publishJob = scope.launch {
            delay(remainingMs)
            synchronized(lock) {
                publishJob = null
                val pending = pendingState ?: return@synchronized
                pendingState = null
                if (pending.sessionId == acceptedSessionId) {
                    publishImmediatelyLocked(pending)
                }
            }
        }
    }

    private fun publishImmediatelyLocked(next: WatchReaderPresetPreviewState) {
        publishJob?.cancel()
        publishJob = null
        pendingState = null
        mutableState.value = next
        lastPublishedAt = monotonicTimeMs()
    }

    private fun scheduleTimeout(sessionId: String, delayMs: Long) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(delayMs)
            stop(sessionId)
        }
    }

    private fun monotonicTimeMs(): Long = System.nanoTime() / 1_000_000L

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        const val DEFAULT_PUBLISH_INTERVAL_MS = 33L
        private const val RESOURCE_TRANSFER_TIMEOUT_MS = 120_000L
    }
}
