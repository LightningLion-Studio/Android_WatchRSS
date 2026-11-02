package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinLoginProbeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Endpoint cooldowns belong to one cookie generation. A challenge never deletes a cookie. */
internal data class DouyinCooldown(val until: Long, val strikes: Int)

internal class DouyinRequestPolicy(
    private val now: () -> Long = System::currentTimeMillis,
    initial: Map<String, DouyinCooldown> = emptyMap(),
    private val persist: (Map<String, DouyinCooldown>) -> Unit = {}
) {
    private val mutex = Mutex()
    private val until = mutableMapOf<String, Long>()
    private val strikes = mutableMapOf<String, Int>()

    init {
        initial.filterKeys { it in setOf("feed", "detail") }.forEach { (endpoint, value) ->
            if (value.until > now() && value.strikes in 1..6) {
                until[endpoint] = value.until.coerceAtMost(now() + 900_000)
                strikes[endpoint] = value.strikes
            }
        }
    }

    private fun save() = persist(until.mapValues { (endpoint, value) -> DouyinCooldown(value, strikes[endpoint] ?: 1) })

    suspend fun coolingDown(endpoint: String): Boolean = mutex.withLock { (until[endpoint] ?: 0) > now() }

    suspend fun challenged(endpoint: String, probe: suspend () -> DouyinLoginProbeResult): DouyinLoginProbeResult =
        mutex.withLock {
            if ((until[endpoint] ?: 0) > now()) return@withLock DouyinLoginProbeResult.UNKNOWN
            val count = ((strikes[endpoint] ?: 0) + 1).coerceAtMost(6)
            strikes[endpoint] = count
            until[endpoint] = now() + (30_000L * (1L shl (count - 1))).coerceAtMost(900_000)
            save() // Enter cooldown before the probe, so cancellation cannot bypass backoff.
            val result = try {
                withTimeoutOrNull(5_000) { probe() } ?: DouyinLoginProbeResult.UNKNOWN
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DouyinLoginProbeResult.UNKNOWN
            }
            result // Even a definitive negative probe is a UI hint, never automatic logout.
        }

    suspend fun successful(endpoint: String) = mutex.withLock {
        until.remove(endpoint)
        strikes.remove(endpoint)
        save()
    }
}
