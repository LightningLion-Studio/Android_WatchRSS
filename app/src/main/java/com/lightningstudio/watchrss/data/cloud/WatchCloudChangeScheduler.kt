package com.lightningstudio.watchrss.data.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

internal class WatchCloudChangeScheduler(
    private val enabled: Flow<Boolean>,
    private val changes: Flow<Any>,
    private val scope: CoroutineScope,
    private val transfer: suspend () -> Unit,
    private val onEnabled: (Boolean) -> Unit,
    private val coalesceMillis: Long = 5_000,
    private val retryDelays: List<Long> = listOf(15_000, 60_000, 120_000)
) {
    private val wakeups = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var job: Job? = null

    @Synchronized @OptIn(FlowPreview::class)
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            enabled.distinctUntilChanged().collectLatest { active ->
                onEnabled(active)
                if (!active) return@collectLatest
                var first = true
                merge(changes.map { Unit }, wakeups)
                    .debounce { if (first) { first = false; 0L } else coalesceMillis }
                    .buffer(Channel.CONFLATED)
                    .collect {
                        var attempt = 0
                        while (true) {
                            try { transfer(); break
                            } catch (cancelled: CancellationException) { throw cancelled
                            } catch (error: Exception) {
                                if (error is IllegalArgumentException || error is SecurityException || attempt >= retryDelays.size) break
                                delay(retryDelays[attempt++])
                            }
                        }
                    }
            }
        }
    }
    fun requestCatchUp() { wakeups.tryEmit(Unit) }
    @Synchronized fun stop() { job?.cancel(); job = null }
}
