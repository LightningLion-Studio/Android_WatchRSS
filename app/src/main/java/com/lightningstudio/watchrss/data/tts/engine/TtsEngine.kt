package com.lightningstudio.watchrss.data.tts.engine

import com.lightningstudio.watchrss.data.tts.ReadAloudSegment

/**
 * 朗读引擎抽象。不同实现可以基于本地系统 TTS、后端代理 TTS 或各厂商 BYOK TTS。
 */
interface TtsEngine {
    /** 展示给用户的引擎名称，如"本地 TTS · 中文"。 */
    val label: String

    /**
     * 初始化引擎。可重复调用，已初始化时应快速返回。
     * 返回 true 表示可用。
     */
    suspend fun prepare(): Boolean

    /**
     * 朗读一段文本。
     *
     * @param segment 要朗读的段落
     * @param rate 语速，1.0 为正常语速
     * @param listener 朗读进度与生命周期回调
     * @return 本次朗读的 utterance id
     */
    suspend fun speak(
        segment: ReadAloudSegment,
        rate: Float,
        listener: TtsUtteranceListener
    ): String

    /** 停止当前朗读。 */
    fun stop()

    /** 释放引擎资源。 */
    fun release()
}

/**
 * 朗读段落生命周期回调。
 *
 * 云端引擎可能不支持 [onBeginSynthesis]、[onAudioAvailable]、[onRangeStart]，
 * 此时仅保证 [onStart]、[onDone]/[onError]/[onStop] 被调用。
 */
interface TtsUtteranceListener {
    fun onStart(utteranceId: String)
    fun onBeginSynthesis(utteranceId: String, sampleRateInHz: Int, audioFormat: Int, channelCount: Int)
    fun onAudioAvailable(utteranceId: String, audio: ByteArray)
    fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int)
    fun onDone(utteranceId: String)
    fun onError(utteranceId: String, errorCode: Int? = null)
    fun onStop(utteranceId: String, interrupted: Boolean)
}
