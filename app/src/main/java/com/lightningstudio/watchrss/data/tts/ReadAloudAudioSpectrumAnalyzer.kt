package com.lightningstudio.watchrss.data.tts

import android.media.AudioFormat
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

internal class ReadAloudAudioSpectrumAnalyzer {
    private val fft = DoubleFFT_1D(READ_ALOUD_AUDIO_FFT_SIZE.toLong())
    private val ringBuffer = FloatArray(READ_ALOUD_AUDIO_FFT_SIZE)
    private val fftBuffer = DoubleArray(READ_ALOUD_AUDIO_FFT_SIZE)
    private val window = DoubleArray(READ_ALOUD_AUDIO_FFT_SIZE) { index ->
        0.5 - 0.5 * cos(READ_ALOUD_AUDIO_TWO_PI * index / (READ_ALOUD_AUDIO_FFT_SIZE - 1))
    }
    private val smoothedLevels = FloatArray(READ_ALOUD_AUDIO_SPECTRUM_BANDS)
    private var writeIndex = 0
    private var bufferedSamples = 0
    private var samplesSinceAnalysis = 0
    private var currentSampleRateHz = 0
    private var currentHopSamples = READ_ALOUD_AUDIO_FFT_SIZE

    fun clear() {
        clearAnalysisWindow()
        currentSampleRateHz = 0
        currentHopSamples = READ_ALOUD_AUDIO_FFT_SIZE
    }

    private fun clearAnalysisWindow() {
        ringBuffer.fill(0f)
        fftBuffer.fill(0.0)
        smoothedLevels.fill(0f)
        writeIndex = 0
        bufferedSamples = 0
        samplesSinceAnalysis = 0
    }

    fun analyze(
        audio: ByteArray,
        sampleRateHz: Int,
        audioFormat: Int,
        channelCount: Int,
        onFrame: (FloatArray) -> Unit
    ): Int {
        if (sampleRateHz <= 0 || audio.isEmpty()) return 0
        configureSampleRate(sampleRateHz)
        val channels = channelCount.coerceAtLeast(1)
        var emittedFrames = 0
        forEachMonoSample(audio, audioFormat, channels) { sample ->
            appendSample(sample)
            if (bufferedSamples >= READ_ALOUD_AUDIO_FFT_SIZE &&
                samplesSinceAnalysis >= currentHopSamples
            ) {
                val levels = smoothLevels(computeBandLevels())
                onFrame(levels)
                samplesSinceAnalysis = 0
                emittedFrames += 1
            }
        }
        return emittedFrames
    }

    private fun configureSampleRate(sampleRateHz: Int) {
        if (currentSampleRateHz == sampleRateHz) return
        if (currentSampleRateHz != 0) {
            clearAnalysisWindow()
        }
        currentSampleRateHz = sampleRateHz
        currentHopSamples = (sampleRateHz / READ_ALOUD_AUDIO_SPECTRUM_KEYFRAMES_PER_SECOND)
            .coerceAtLeast(READ_ALOUD_AUDIO_ANALYSIS_MIN_SAMPLES)
    }

    private fun appendSample(sample: Float) {
        ringBuffer[writeIndex] = sample.coerceIn(-1f, 1f)
        writeIndex = (writeIndex + 1) % READ_ALOUD_AUDIO_FFT_SIZE
        if (bufferedSamples < READ_ALOUD_AUDIO_FFT_SIZE) {
            bufferedSamples += 1
        }
        samplesSinceAnalysis += 1
    }

    private inline fun forEachMonoSample(
        audio: ByteArray,
        audioFormat: Int,
        channelCount: Int,
        consume: (Float) -> Unit
    ) {
        when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> forEachPcm16MonoSample(audio, channelCount, consume)
            AudioFormat.ENCODING_PCM_8BIT -> forEachPcm8MonoSample(audio, channelCount, consume)
            AudioFormat.ENCODING_PCM_FLOAT -> forEachPcmFloatMonoSample(audio, channelCount, consume)
        }
    }

    private inline fun forEachPcm16MonoSample(
        audio: ByteArray,
        channelCount: Int,
        consume: (Float) -> Unit
    ) {
        val frameSize = channelCount * 2
        if (frameSize <= 0 || audio.size < frameSize) return
        val frameCount = audio.size / frameSize
        for (frame in 0 until frameCount) {
            var mono = 0f
            repeat(channelCount) { channel ->
                val byteIndex = frame * frameSize + channel * 2
                val low = audio[byteIndex].toInt() and 0xFF
                val high = audio[byteIndex + 1].toInt()
                mono += ((high shl 8) or low).toShort().toFloat() / Short.MAX_VALUE
            }
            consume(mono / channelCount)
        }
    }

    private inline fun forEachPcm8MonoSample(
        audio: ByteArray,
        channelCount: Int,
        consume: (Float) -> Unit
    ) {
        if (audio.size < channelCount) return
        val frameCount = audio.size / channelCount
        for (frame in 0 until frameCount) {
            var mono = 0f
            repeat(channelCount) { channel ->
                val byteIndex = frame * channelCount + channel
                mono += ((audio[byteIndex].toInt() and 0xFF) - 128) / 128f
            }
            consume(mono / channelCount)
        }
    }

    private inline fun forEachPcmFloatMonoSample(
        audio: ByteArray,
        channelCount: Int,
        consume: (Float) -> Unit
    ) {
        val frameSize = channelCount * 4
        if (frameSize <= 0 || audio.size < frameSize) return
        val frameCount = audio.size / frameSize
        for (frame in 0 until frameCount) {
            var mono = 0f
            repeat(channelCount) { channel ->
                val byteIndex = frame * frameSize + channel * 4
                val bits =
                    (audio[byteIndex].toInt() and 0xFF) or
                        ((audio[byteIndex + 1].toInt() and 0xFF) shl 8) or
                        ((audio[byteIndex + 2].toInt() and 0xFF) shl 16) or
                        ((audio[byteIndex + 3].toInt() and 0xFF) shl 24)
                mono += Float.fromBits(bits).coerceIn(-1f, 1f)
            }
            consume(mono / channelCount)
        }
    }

    private fun computeBandLevels(): FloatArray {
        fillFftBuffer()
        fft.realForward(fftBuffer)

        val nyquist = currentSampleRateHz / 2f
        val minFrequency = READ_ALOUD_AUDIO_MIN_FREQUENCY_HZ.coerceAtMost(nyquist * 0.5f)
        val maxFrequency = READ_ALOUD_AUDIO_MAX_FREQUENCY_HZ
            .coerceAtMost(nyquist * 0.92f)
            .coerceAtLeast(minFrequency * 1.5f)
        return FloatArray(READ_ALOUD_AUDIO_SPECTRUM_BANDS) { band ->
            val startFrequency = spectrumBandEdgeFrequency(
                edge = band,
                minFrequency = minFrequency,
                maxFrequency = maxFrequency
            )
            val endFrequency = spectrumBandEdgeFrequency(
                edge = band + 1,
                minFrequency = minFrequency,
                maxFrequency = maxFrequency
            )
            val bandRms = bandMagnitudeRms(startFrequency, endFrequency)
            spectrumMagnitudeToLevel(bandRms)
        }
    }

    private fun fillFftBuffer() {
        repeat(READ_ALOUD_AUDIO_FFT_SIZE) { index ->
            val sourceIndex = (writeIndex + index) % READ_ALOUD_AUDIO_FFT_SIZE
            fftBuffer[index] = ringBuffer[sourceIndex] * window[index]
        }
    }

    private fun bandMagnitudeRms(startFrequency: Float, endFrequency: Float): Double {
        val halfSize = READ_ALOUD_AUDIO_FFT_SIZE / 2
        val startBin = ceil(startFrequency * READ_ALOUD_AUDIO_FFT_SIZE / currentSampleRateHz)
            .toInt()
            .coerceIn(1, halfSize)
        val endBin = floor(endFrequency * READ_ALOUD_AUDIO_FFT_SIZE / currentSampleRateHz)
            .toInt()
            .coerceIn(startBin, halfSize)
        var squareSum = 0.0
        var count = 0
        for (bin in startBin..endBin) {
            val magnitude = fftMagnitude(bin) * READ_ALOUD_AUDIO_FFT_MAGNITUDE_SCALE
            squareSum += magnitude * magnitude
            count += 1
        }
        return if (count > 0) sqrt(squareSum / count) else 0.0
    }

    private fun fftMagnitude(bin: Int): Double {
        val halfSize = READ_ALOUD_AUDIO_FFT_SIZE / 2
        return when (bin) {
            0 -> abs(fftBuffer[0])
            halfSize -> abs(fftBuffer[1])
            else -> hypot(fftBuffer[bin * 2], fftBuffer[bin * 2 + 1])
        }
    }

    private fun spectrumMagnitudeToLevel(magnitude: Double): Float {
        if (magnitude <= 0.0) return 0f
        val db = 20.0 * log10(magnitude.coerceAtLeast(READ_ALOUD_AUDIO_SPECTRUM_MIN_MAGNITUDE))
        return ((db - READ_ALOUD_AUDIO_SPECTRUM_MIN_DB) /
            (READ_ALOUD_AUDIO_SPECTRUM_MAX_DB - READ_ALOUD_AUDIO_SPECTRUM_MIN_DB))
            .toFloat()
            .coerceIn(0f, 1f)
    }

    private fun smoothLevels(targetLevels: FloatArray): FloatArray {
        val output = FloatArray(READ_ALOUD_AUDIO_SPECTRUM_BANDS)
        targetLevels.forEachIndexed { index, target ->
            val current = smoothedLevels[index]
            val coefficient = if (target >= current) {
                READ_ALOUD_AUDIO_SPECTRUM_ATTACK
            } else {
                READ_ALOUD_AUDIO_SPECTRUM_RELEASE
            }
            val next = current + (target - current) * coefficient
            smoothedLevels[index] = next.coerceIn(0f, 1f)
            output[index] = smoothedLevels[index]
        }
        return output
    }

    private fun spectrumBandEdgeFrequency(edge: Int, minFrequency: Float, maxFrequency: Float): Float {
        val ratio = edge / READ_ALOUD_AUDIO_SPECTRUM_BANDS.toDouble()
        val logMin = ln(minFrequency.toDouble())
        val logMax = ln(maxFrequency.toDouble())
        return exp(logMin + (logMax - logMin) * ratio).toFloat()
    }
}

private const val READ_ALOUD_AUDIO_SPECTRUM_BANDS = 5
private const val READ_ALOUD_AUDIO_FFT_SIZE = 512
private const val READ_ALOUD_AUDIO_FFT_MAGNITUDE_SCALE = 2.0 / READ_ALOUD_AUDIO_FFT_SIZE
private const val READ_ALOUD_AUDIO_ANALYSIS_MIN_SAMPLES = 32
private const val READ_ALOUD_AUDIO_SPECTRUM_KEYFRAMES_PER_SECOND = 60
private const val READ_ALOUD_AUDIO_MIN_FREQUENCY_HZ = 90f
private const val READ_ALOUD_AUDIO_MAX_FREQUENCY_HZ = 4_200f
private const val READ_ALOUD_AUDIO_SPECTRUM_MIN_MAGNITUDE = 0.000001
private const val READ_ALOUD_AUDIO_SPECTRUM_MIN_DB = -58.0
private const val READ_ALOUD_AUDIO_SPECTRUM_MAX_DB = -16.0
private const val READ_ALOUD_AUDIO_SPECTRUM_ATTACK = 0.64f
private const val READ_ALOUD_AUDIO_SPECTRUM_RELEASE = 0.24f
private val READ_ALOUD_AUDIO_TWO_PI = PI * 2.0
