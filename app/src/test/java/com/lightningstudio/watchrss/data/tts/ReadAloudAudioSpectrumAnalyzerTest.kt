package com.lightningstudio.watchrss.data.tts

import android.media.AudioFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class ReadAloudAudioSpectrumAnalyzerTest {
    @Test
    fun separatesLowAndHighToneIntoDifferentBands() {
        val sampleRateHz = 16_000
        val analyzer = ReadAloudAudioSpectrumAnalyzer()

        val lowFrames = mutableListOf<FloatArray>()
        analyzer.analyze(
            audio = sinePcm16(frequencyHz = 220.0, sampleRateHz = sampleRateHz),
            sampleRateHz = sampleRateHz,
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
            channelCount = 1,
            onFrame = lowFrames::add
        )
        val lowPeakBand = lowFrames.last().peakBand()

        analyzer.clear()

        val highFrames = mutableListOf<FloatArray>()
        analyzer.analyze(
            audio = sinePcm16(frequencyHz = 3_000.0, sampleRateHz = sampleRateHz),
            sampleRateHz = sampleRateHz,
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
            channelCount = 1,
            onFrame = highFrames::add
        )
        val highPeakBand = highFrames.last().peakBand()

        assertTrue("low tone should emit spectrum frames", lowFrames.isNotEmpty())
        assertTrue("high tone should emit spectrum frames", highFrames.isNotEmpty())
        assertTrue("low tone peak band was $lowPeakBand", lowPeakBand <= 2)
        assertTrue("high tone peak band was $highPeakBand", highPeakBand >= 3)
    }

    private fun FloatArray.peakBand(): Int {
        var peakIndex = 0
        var peakLevel = Float.NEGATIVE_INFINITY
        forEachIndexed { index, level ->
            if (level > peakLevel) {
                peakIndex = index
                peakLevel = level
            }
        }
        return peakIndex
    }

    private fun sinePcm16(
        frequencyHz: Double,
        sampleRateHz: Int,
        frameCount: Int = 2_048
    ): ByteArray {
        val output = ByteArray(frameCount * 2)
        repeat(frameCount) { frame ->
            val sample = (sin(2.0 * PI * frequencyHz * frame / sampleRateHz) * 0.72)
                .coerceIn(-1.0, 1.0)
            val pcm = (sample * Short.MAX_VALUE).roundToInt().toShort()
            output[frame * 2] = (pcm.toInt() and 0xFF).toByte()
            output[frame * 2 + 1] = ((pcm.toInt() ushr 8) and 0xFF).toByte()
        }
        return output
    }
}
