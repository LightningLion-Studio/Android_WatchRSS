package com.lightningstudio.watchrss.debug

import android.os.SystemClock
import android.util.Log
import com.lightningstudio.watchrss.BuildConfig
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object PerfTrace {
    private const val TAG = "PerfTrace"
    private val processStartNanos = monotonicNowNanos()
    private val sequence = AtomicLong(0)

    fun now(): Long = monotonicNowNanos()

    fun elapsedMs(startNanos: Long): Double {
        val endNanos = monotonicNowNanos()
        return (endNanos - startNanos).coerceAtLeast(0L) / 1_000_000.0
    }

    fun log(category: String, message: String) {
        if (!BuildConfig.DEBUG) return
        val nowNanos = monotonicNowNanos()
        val monotonicMs = nowNanos / 1_000_000.0
        val sinceProcessStartMs = (nowNanos - processStartNanos) / 1_000_000.0
        val fullMessage = buildString {
            append("seq=").append(sequence.incrementAndGet()).append(' ')
            append("cat=").append(category).append(' ')
            append("monoMs=").append(format(monotonicMs)).append(' ')
            append("sinceStartMs=").append(format(sinceProcessStartMs)).append(' ')
            append("thread=").append(Thread.currentThread().name).append(' ')
            append(message)
        }
        DebugLogBuffer.log("perf/$category", fullMessage)
        runCatching { Log.d(TAG, fullMessage) }
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.3f", value)

    private fun monotonicNowNanos(): Long {
        return runCatching { SystemClock.elapsedRealtimeNanos() }
            .getOrElse { System.nanoTime() }
    }
}
