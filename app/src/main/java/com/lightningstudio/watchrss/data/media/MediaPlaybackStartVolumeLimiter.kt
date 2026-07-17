package com.lightningstudio.watchrss.data.media

import android.content.Context
import android.media.AudioManager
import com.lightningstudio.watchrss.util.AppLogger
import kotlin.math.roundToInt

interface MediaPlaybackStartVolumeLimiter {
    fun enforcePlaybackStartVolumeLimit(playbackStartVolumeLimitPercent: Int?)
}

class AudioManagerMediaPlaybackStartVolumeLimiter(
    context: Context
) : MediaPlaybackStartVolumeLimiter {
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun enforcePlaybackStartVolumeLimit(playbackStartVolumeLimitPercent: Int?) {
        val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            .coerceIn(minVolume, maxVolume)
        if (!shouldEnforcePlaybackStartGuard(
                playbackStartVolumeLimitPercent = playbackStartVolumeLimitPercent,
                dismissedByUser = false,
                currentVolume = currentVolume,
                minVolume = minVolume,
                maxVolume = maxVolume
            )
        ) {
            AppLogger.d(
                TAG,
                "skip playback start volume limit current=$currentVolume limitPercent=$playbackStartVolumeLimitPercent"
            )
            return
        }

        val targetVolume = playbackStartVolumeForPercent(
            targetPercent = playbackStartVolumeLimitPercent ?: return,
            minVolume = minVolume,
            maxVolume = maxVolume
        ).roundToInt().coerceIn(minVolume, maxVolume)
        if (!audioManager.isVolumeFixed && targetVolume != currentVolume) {
            AppLogger.d(TAG, "setStreamVolume $currentVolume->$targetVolume limitPercent=$playbackStartVolumeLimitPercent")
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        } else {
            AppLogger.d(
                TAG,
                "setStreamVolume skipped target=$targetVolume current=$currentVolume fixed=${audioManager.isVolumeFixed}"
            )
        }
    }
}

private const val TAG = "PlaybackStartVolume"
