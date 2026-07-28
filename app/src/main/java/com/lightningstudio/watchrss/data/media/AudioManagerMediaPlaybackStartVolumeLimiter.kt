package com.lightningstudio.watchrss.data.media

import android.content.Context
import android.media.AudioManager

class AudioManagerMediaPlaybackStartVolumeLimiter(
    context: Context
) : MediaPlaybackStartVolumeLimiter {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun shouldEnforcePlaybackStartGuard(): Boolean = false

    override fun enforcePlaybackStartVolumeLimit() {
        // stub: no-op
    }

    companion object {
        fun volumeForPercent(percent: Float, minVolume: Int, maxVolume: Int): Float {
            return minVolume + (maxVolume - minVolume) * (percent / 100f)
        }
        fun playbackStartVolumeForPercent(percent: Float, minVolume: Int, maxVolume: Int): Float {
            return volumeForPercent(percent, minVolume, maxVolume)
        }
    }
}
