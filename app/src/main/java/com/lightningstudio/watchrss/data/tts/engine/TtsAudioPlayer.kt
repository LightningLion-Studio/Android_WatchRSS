package com.lightningstudio.watchrss.data.tts.engine

/**
 * 云端 TTS 音频播放器抽象。
 */
interface TtsAudioPlayer {
    /**
     * 准备播放音频数据。
     *
     * @param audioBytes 音频二进制数据（如 MP3）
     * @param onCompletion 播放完成回调
     * @param onError 播放错误回调，参数为错误信息
     */
    fun prepare(audioBytes: ByteArray, onCompletion: () -> Unit, onError: (String) -> Unit)

    /** 开始或继续播放。 */
    fun play()

    /** 暂停播放。 */
    fun pause()

    /** 停止播放并释放当前资源。 */
    fun stop()

    /** 释放播放器。 */
    fun release()

    /** 设置播放速度。 */
    fun setSpeed(speed: Float)
}
