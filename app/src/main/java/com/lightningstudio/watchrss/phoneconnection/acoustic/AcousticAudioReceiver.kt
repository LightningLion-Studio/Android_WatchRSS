package com.lightningstudio.watchrss.phoneconnection.acoustic

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.coroutines.coroutineContext

class AcousticAudioReceiver {
    suspend fun listen(timeoutMs: Long): ByteArray? = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            AcousticCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val readBufferSize = maxOf(minBufferSize / 2, AcousticCodec.SAMPLE_RATE / 4)
        val readBuffer = ShortArray(readBufferSize)
        val accumulator = ShortAccumulator()
        var totalSamples = 0L
        var totalSquares = 0.0
        var maxAbs = 0
        var nextStatsAt = System.currentTimeMillis() + STATS_INTERVAL_MS
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AcousticCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, readBufferSize * 2)
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("麦克风初始化失败")
        }

        Log.i(TAG, "listen start timeoutMs=$timeoutMs minBufferSize=$minBufferSize readBufferSize=$readBufferSize")
        val deadline = System.currentTimeMillis() + timeoutMs
        try {
            record.startRecording()
            while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
                val read = record.read(readBuffer, 0, readBuffer.size)
                if (read <= 0) {
                    continue
                }
                for (index in 0 until read) {
                    val value = readBuffer[index].toInt()
                    val abs = kotlin.math.abs(value)
                    if (abs > maxAbs) {
                        maxAbs = abs
                    }
                    totalSquares += value.toDouble() * value
                    totalSamples++
                }
                accumulator.append(readBuffer, read)
                AcousticCodec.decode(accumulator.toShortArray())?.let {
                    Log.i(TAG, "listen decoded bytes=${it.size} samples=${accumulator.size} maxAbs=$maxAbs rms=${formatRms(totalSquares, totalSamples)}")
                    return@withContext it
                }
                val now = System.currentTimeMillis()
                if (now >= nextStatsAt) {
                    Log.i(TAG, "listen stats samples=${accumulator.size} maxAbs=$maxAbs rms=${formatRms(totalSquares, totalSamples)}")
                    nextStatsAt = now + STATS_INTERVAL_MS
                }
            }
            Log.w(TAG, "listen timeout samples=${accumulator.size} maxAbs=$maxAbs rms=${formatRms(totalSquares, totalSamples)}")
            null
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    private fun formatRms(totalSquares: Double, totalSamples: Long): String {
        if (totalSamples <= 0L) return "0.0"
        return "%.1f".format(sqrt(totalSquares / totalSamples))
    }

    companion object {
        private const val TAG = "WatchRSS_AcousticReceiver"
        private const val STATS_INTERVAL_MS = 2_000L
    }
}

private class ShortAccumulator {
    private var buffer = ShortArray(16_384)
    var size = 0
        private set

    fun append(data: ShortArray, count: Int) {
        ensureCapacity(size + count)
        System.arraycopy(data, 0, buffer, size, count)
        size += count
    }

    fun toShortArray(): ShortArray = buffer.copyOf(size)

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) {
            return
        }
        var nextSize = buffer.size
        while (nextSize < required) {
            nextSize *= 2
        }
        buffer = buffer.copyOf(nextSize)
    }
}
