package com.lightningstudio.watchrss.data.media

typealias AudioSpectrumFrame = FloatArray

class ReadAloudAudioSpectrumAnalyzer {
    fun analyze(
        audio: ByteArray,
        sampleRateHz: Int,
        audioFormat: Int,
        channelCount: Int,
        onFrame: (AudioSpectrumFrame) -> Unit
    ): Int {
        onFrame(FloatArray(0))
        return 0
    }

    fun analyze(buffer: ByteArray, sampleRate: Int): AudioSpectrumFrame = FloatArray(0)

    fun clear() {}
}
