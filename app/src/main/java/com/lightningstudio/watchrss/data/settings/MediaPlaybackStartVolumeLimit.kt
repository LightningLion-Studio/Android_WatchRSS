package com.lightningstudio.watchrss.data.settings

internal const val MEDIA_PLAYBACK_START_VOLUME_UNLIMITED_PERSISTED_VALUE: Int = -1

val DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT: Int? = 10
val MEDIA_PLAYBACK_START_VOLUME_LIMIT_OPTIONS_PERCENT: List<Int?> =
    listOf<Int?>(null) + (0..100 step 5).toList()

fun normalizeMediaPlaybackStartVolumeLimitPercent(value: Int?): Int? {
    if (value == null) return null
    return if (value in 0..100 && value % 5 == 0) {
        value
    } else {
        DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT
    }
}

internal fun decodeMediaPlaybackStartVolumeLimitPercent(value: Int): Int? {
    if (value == MEDIA_PLAYBACK_START_VOLUME_UNLIMITED_PERSISTED_VALUE) return null
    return normalizeMediaPlaybackStartVolumeLimitPercent(value)
}

internal fun encodeMediaPlaybackStartVolumeLimitPercent(value: Int?): Int {
    return normalizeMediaPlaybackStartVolumeLimitPercent(value)
        ?: MEDIA_PLAYBACK_START_VOLUME_UNLIMITED_PERSISTED_VALUE
}

fun defaultMediaPlaybackStartVolumeLimitPercentForGuard(
    mediaVolumeGuardEnabled: Boolean
): Int? {
    return if (mediaVolumeGuardEnabled) {
        DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT
    } else {
        null
    }
}

fun formatMediaPlaybackStartVolumeLimitPercent(value: Int?): String {
    return normalizeMediaPlaybackStartVolumeLimitPercent(value)?.let { "$it%" } ?: "无限制"
}

fun previousMediaPlaybackStartVolumeLimitPercent(current: Int?): Int? {
    return mediaPlaybackStartVolumeLimitNeighbor(current, offset = -1)
}

fun nextMediaPlaybackStartVolumeLimitPercent(current: Int?): Int? {
    return mediaPlaybackStartVolumeLimitNeighbor(current, offset = 1)
}

private fun mediaPlaybackStartVolumeLimitNeighbor(current: Int?, offset: Int): Int? {
    val options = MEDIA_PLAYBACK_START_VOLUME_LIMIT_OPTIONS_PERCENT
    val normalized = normalizeMediaPlaybackStartVolumeLimitPercent(current)
    val index = options.indexOf(normalized).takeIf { it >= 0 } ?: 0
    val nextIndex = (index + offset + options.size) % options.size
    return options[nextIndex]
}
