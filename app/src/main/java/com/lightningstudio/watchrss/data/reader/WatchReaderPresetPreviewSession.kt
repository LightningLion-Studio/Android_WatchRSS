package com.lightningstudio.watchrss.data.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WatchReaderPresetPreviewState(
    val sessionId: String,
    val sequence: Long,
    val preset: ReaderPreset,
    val resourceTransferInProgress: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

class WatchReaderPresetPreviewSession(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow<WatchReaderPresetPreviewState?>(null)
    private var timeoutJob: Job? = null

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
            val current = mutableState.value
            if (current?.sessionId == sessionId && sequence <= current.sequence) return false
            mutableState.value = WatchReaderPresetPreviewState(
                sessionId = sessionId,
                sequence = sequence,
                preset = preset.normalized(),
                resourceTransferInProgress = resourceTransferInProgress
            )
            scheduleTimeout(
                sessionId = sessionId,
                delayMs = if (resourceTransferInProgress) RESOURCE_TRANSFER_TIMEOUT_MS else timeoutMs
            )
            return true
        }
    }

    fun refreshResourceTransfer(): Boolean = synchronized(lock) {
        val current = mutableState.value?.takeIf { it.resourceTransferInProgress } ?: return false
        mutableState.value = current.copy(updatedAt = System.currentTimeMillis())
        scheduleTimeout(current.sessionId, RESOURCE_TRANSFER_TIMEOUT_MS)
        true
    }

    fun stop(sessionId: String): Boolean = synchronized(lock) {
        val current = mutableState.value ?: return false
        if (current.sessionId != sessionId) return false
        timeoutJob?.cancel()
        timeoutJob = null
        mutableState.value = null
        true
    }

    private fun scheduleTimeout(sessionId: String, delayMs: Long) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(delayMs)
            stop(sessionId)
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        private const val RESOURCE_TRANSFER_TIMEOUT_MS = 120_000L
    }
}
