package com.lightningstudio.watchrss.phoneconnection.ip

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean

/** Plays a silent low-rate stream only while an opted-in sync transport owns a lease. */
class SyncMediaKeepAlive {
    private val lock = Any()
    private val owners = mutableSetOf<String>()
    private var enabled = false
    private var playback: SilentPlayback? = null

    fun setEnabled(value: Boolean) = synchronized(lock) {
        enabled = value
        updatePlaybackLocked()
    }

    fun acquire(owner: String) = synchronized(lock) {
        owners += owner
        updatePlaybackLocked()
    }

    fun release(owner: String) = synchronized(lock) {
        owners -= owner
        updatePlaybackLocked()
    }

    private fun updatePlaybackLocked() {
        if (enabled && owners.isNotEmpty()) {
            if (playback == null) playback = SilentPlayback.startOrNull()
        } else {
            playback?.stop()
            playback = null
        }
    }

    private class SilentPlayback(
        private val audioTrack: AudioTrack,
        private val running: AtomicBoolean,
        private val thread: Thread
    ) {
        fun stop() {
            running.set(false)
            thread.interrupt()
            runCatching { thread.join(250L) }
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
            runCatching { audioTrack.release() }
        }

        companion object {
            private const val SAMPLE_RATE = 8_000

            fun startOrNull(): SilentPlayback? = runCatching {
                val minimum = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(SAMPLE_RATE / 2)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(minimum)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                val running = AtomicBoolean(true)
                val silence = ByteArray(minimum)
                val thread = Thread({
                    runCatching {
                        track.play()
                        while (running.get()) track.write(silence, 0, silence.size)
                    }
                }, "watchrss-sync-media-keepalive").apply {
                    isDaemon = true
                    start()
                }
                SilentPlayback(track, running, thread)
            }.getOrNull()
        }
    }
}
