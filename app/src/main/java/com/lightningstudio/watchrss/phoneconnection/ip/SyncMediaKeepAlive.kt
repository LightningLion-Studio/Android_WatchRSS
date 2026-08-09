package com.lightningstudio.watchrss.phoneconnection.ip

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.lightningstudio.watchrss.util.AppLogger

/**
 * Keeps the watch audio pipeline active only while a phone sync owns a session lease.
 *
 * Some watch firmwares aggressively suspend ordinary app networking after the display turns off,
 * while an active media output is allowed to continue. The samples are real PCM silence rather
 * than a muted track so the framework still observes active media playback without audible output.
 * RFCOMM bootstrap and IP transfer use separate leases so switching transports cannot briefly stop
 * playback. The user setting gates playback and defaults to disabled.
 */
internal class SyncMediaKeepAlive {
    private var audioTrack: AudioTrack? = null
    private var enabled = false
    private val sessionOwners = mutableSetOf<String>()

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
        updatePlayback()
    }

    @Synchronized
    fun acquire(owner: String) {
        sessionOwners += owner
        updatePlayback()
    }

    @Synchronized
    fun release(owner: String) {
        sessionOwners -= owner
        updatePlayback()
    }

    private fun updatePlayback() {
        if (shouldPlaySyncMediaKeepAlive(enabled, sessionOwners.size)) {
            startPlayback()
        } else {
            stopPlayback()
        }
    }

    private fun startPlayback() {
        if (audioTrack != null) return

        var candidate: AudioTrack? = null
        runCatching {
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE_HZ)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val minimumBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minimumBytes > 0) { "无法创建同步媒体保活缓冲区：$minimumBytes" }
            val frameCount = maxOf(SAMPLE_RATE_HZ, minimumBytes / BYTES_PER_FRAME)
            val silence = ByteArray(frameCount * BYTES_PER_FRAME)

            candidate = AudioTrack(
                attributes,
                format,
                silence.size,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            ).also { track ->
                check(track.state == AudioTrack.STATE_INITIALIZED) { "同步媒体保活音轨初始化失败" }
                check(track.write(silence, 0, silence.size) == silence.size) {
                    "同步媒体保活静音数据写入失败"
                }
                check(track.setLoopPoints(0, frameCount, -1) == AudioTrack.SUCCESS) {
                    "同步媒体保活循环设置失败"
                }
                track.play()
                audioTrack = track
            }
            AppLogger.i(TAG, "同步媒体保活已启动")
        }.onFailure { error ->
            runCatching { candidate?.release() }
            candidate = null
            AppLogger.w(TAG, "同步媒体保活启动失败", error)
        }
    }

    private fun stopPlayback() {
        val track = audioTrack ?: return
        audioTrack = null
        runCatching { track.stop() }
        runCatching { track.release() }
        AppLogger.i(TAG, "同步媒体保活已停止")
    }

    private companion object {
        private const val TAG = "WatchRSS_SyncMediaKeepAlive"
        private const val SAMPLE_RATE_HZ = 8_000
        private const val BYTES_PER_FRAME = 2
    }
}

internal fun shouldPlaySyncMediaKeepAlive(enabled: Boolean, activeSessionOwners: Int): Boolean =
    enabled && activeSessionOwners > 0
