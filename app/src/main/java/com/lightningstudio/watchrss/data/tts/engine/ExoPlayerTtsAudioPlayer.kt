package com.lightningstudio.watchrss.data.tts.engine

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.ByteArrayDataSource
import com.lightningstudio.watchrss.util.AppLogger
import java.io.File

private const val TAG = "ExoPlayerTtsAudioPlayer"

@UnstableApi
class ExoPlayerTtsAudioPlayer(context: Context) : TtsAudioPlayer {

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var currentTempFile: File? = null
    private var onCompletion: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var pendingSpeed: Float = 1f

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                onCompletion?.invoke()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLogger.e(TAG, "ExoPlayer error", error)
            onError?.invoke(error.message ?: "音频播放失败")
        }
    }

    override fun prepare(audioBytes: ByteArray, onCompletion: () -> Unit, onError: (String) -> Unit) {
        this.onCompletion = onCompletion
        this.onError = onError
        release()

        val file = File(appContext.cacheDir, "tts_${System.currentTimeMillis()}.mp3").apply {
            writeBytes(audioBytes)
            deleteOnExit()
        }
        currentTempFile = file

        val exoPlayer = ExoPlayer.Builder(appContext).build().apply {
            addListener(listener)
            setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            prepare()
            setPlaybackSpeed(pendingSpeed)
        }
        player = exoPlayer
    }

    override fun play() {
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun stop() {
        player?.stop()
        player?.clearMediaItems()
        cleanupTempFile()
    }

    override fun release() {
        player?.removeListener(listener)
        player?.release()
        player = null
        cleanupTempFile()
    }

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        player?.setPlaybackSpeed(speed)
    }

    private fun cleanupTempFile() {
        currentTempFile?.delete()
        currentTempFile = null
    }
}
