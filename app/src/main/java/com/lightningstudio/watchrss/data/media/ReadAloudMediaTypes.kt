package com.lightningstudio.watchrss.data.media

const val READ_ALOUD_AUDIO_SPECTRUM_FRAME_BUFFER_CAPACITY = 16
const val READ_ALOUD_FALLBACK_INITIAL_UNITS_PER_SECOND = 5.0

data class ReadAloudSegment(
    val text: String,
    val index: Int
)

sealed class ReadAloudTextSegmentSource {
    data object Original : ReadAloudTextSegmentSource()
    data object Imported : ReadAloudTextSegmentSource()
}

fun fallbackSpeechUnits(segment: ReadAloudSegment): Double = segment.text.length.toDouble()
