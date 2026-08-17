package com.lightningstudio.watchrss.data.tts.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.lightningstudio.watchrss.data.tts.ReadAloudSegment
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private const val TAG = "LocalTtsEngine"
private const val TTS_INIT_TIMEOUT_MS = 10_000L
private const val TTS_DEFAULT_ENGINE_SETTING = "tts_default_synth"

class LocalTtsEngine(context: Context) : TtsEngine {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var localEnginePackage: String? = null

    override var label: String = "本地 TTS"
        private set

    override suspend fun prepare(): Boolean {
        val existing = tts
        if (existing != null && ttsReady) return true

        return withContext(Dispatchers.Main) {
            val current = tts
            if (current != null && ttsReady) {
                true
            } else {
                runCatching {
                    val enginePackage = resolveTtsEnginePackage()
                    val deferred = CompletableDeferred<Int>()
                    val created = if (enginePackage == null) {
                        TextToSpeech(appContext) { status ->
                            deferred.complete(status)
                        }
                    } else {
                        TextToSpeech(appContext, { status ->
                            deferred.complete(status)
                        }, enginePackage)
                    }
                    tts = created
                    localEnginePackage = enginePackage
                    val status = withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) {
                        deferred.await()
                    }
                    require(status == TextToSpeech.SUCCESS) {
                        if (enginePackage.isNullOrBlank()) {
                            "本地 TTS 初始化失败：未找到可绑定的系统 TTS 引擎"
                        } else {
                            "本地 TTS 初始化失败：$enginePackage status=${status ?: "timeout"}"
                        }
                    }
                    label = configureLocalVoice(created)
                    ttsReady = true
                    true
                }.getOrElse { error ->
                    AppLogger.e(TAG, "TTS prepare failed", error)
                    false
                }
            }
        }
    }

    override suspend fun speak(
        segment: ReadAloudSegment,
        rate: Float,
        utteranceId: String,
        listener: TtsUtteranceListener
    ) {
        val engine = prepare()
        require(engine) { "本地 TTS 未就绪" }

        withContext(Dispatchers.Main) {
            val currentTts = tts ?: error("TTS 未初始化")
            currentTts.setOnUtteranceProgressListener(LocalUtteranceListener(listener))

            val result = if (currentTts.setSpeechRate(rate) == TextToSpeech.ERROR) {
                TextToSpeech.ERROR
            } else {
                currentTts.speak(segment.text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
            }
            require(result != TextToSpeech.ERROR) { "本地 TTS 无法朗读当前段落" }
        }
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun configureLocalVoice(engine: TextToSpeech): String {
        val offlineVoice = engine.voices
            .orEmpty()
            .asSequence()
            .filter { voice -> !voice.isNetworkConnectionRequired }
            .sortedBy(::voicePriority)
            .firstOrNull { voice -> voicePriority(voice) < Int.MAX_VALUE }

        if (offlineVoice != null && engine.setVoice(offlineVoice) != TextToSpeech.ERROR) {
            return "本地 TTS · ${offlineVoice.locale.toLanguageTag()}"
        }

        val fallbackLocale = listOf(
            Locale.SIMPLIFIED_CHINESE,
            Locale.CHINESE,
            Locale.ENGLISH
        ).firstOrNull { locale ->
            engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
        } ?: error("设备没有可用的本地 TTS 语音")

        val result = engine.setLanguage(fallbackLocale)
        require(result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
            "设备未安装可用的本地 TTS 语音"
        }
        return "本地 TTS · ${fallbackLocale.toLanguageTag()}"
    }

    private fun resolveTtsEnginePackage(): String? {
        val enginePackages = queryTtsEnginePackages()
        if (enginePackages.isEmpty()) return null
        val defaultEngine = runCatching {
            Settings.Secure.getString(appContext.contentResolver, TTS_DEFAULT_ENGINE_SETTING)
        }.getOrNull()
        return defaultEngine
            ?.takeIf { it.isNotBlank() && it in enginePackages }
            ?: enginePackages.firstOrNull()
    }

    private fun queryTtsEnginePackages(): List<String> {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
        return services
            .mapNotNull { it.serviceInfo?.packageName }
            .distinct()
    }

    private fun voicePriority(voice: Voice): Int {
        val language = voice.locale.language.lowercase(Locale.US)
        return when (language) {
            Locale.CHINESE.language -> 0
            Locale.ENGLISH.language -> 1
            else -> Int.MAX_VALUE
        }
    }

    private class LocalUtteranceListener(
        private val listener: TtsUtteranceListener
    ) : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            utteranceId?.let { listener.onStart(it) }
        }

        override fun onBeginSynthesis(
            utteranceId: String?,
            sampleRateInHz: Int,
            audioFormat: Int,
            channelCount: Int
        ) {
            utteranceId?.let {
                listener.onBeginSynthesis(it, sampleRateInHz, audioFormat, channelCount)
            }
        }

        override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
            if (utteranceId != null && audio != null) {
                listener.onAudioAvailable(utteranceId, audio)
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            utteranceId?.let { listener.onRangeStart(it, start, end, frame) }
        }

        override fun onDone(utteranceId: String?) {
            utteranceId?.let { listener.onDone(it) }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            utteranceId?.let { listener.onError(it) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            utteranceId?.let { listener.onError(it, errorCode) }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            utteranceId?.let { listener.onStop(it, interrupted) }
        }
    }
}
