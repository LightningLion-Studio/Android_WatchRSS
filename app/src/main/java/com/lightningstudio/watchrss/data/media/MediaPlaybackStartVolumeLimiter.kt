package com.lightningstudio.watchrss.data.media

interface MediaPlaybackStartVolumeLimiter {
    fun shouldEnforcePlaybackStartGuard(): Boolean
    fun enforcePlaybackStartVolumeLimit()
}
