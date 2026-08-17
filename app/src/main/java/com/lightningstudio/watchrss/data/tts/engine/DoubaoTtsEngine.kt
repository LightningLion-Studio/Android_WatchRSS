package com.lightningstudio.watchrss.data.tts.engine

import android.content.Context
import com.lightningstudio.watchrss.data.settings.TtsApiKeyProvider
import com.lightningstudio.watchrss.data.tts.ReadAloudSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val DEFAULT_BASE_URL = "https://openspeech.bytedance.com"

class DoubaoTtsEngine(
    context: Context,
    private val apiKeyProvider: TtsApiKeyProvider,
    private val voiceId: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val appId: String = "",
    private val audioPlayer: TtsAudioPlayer = ExoPlayerTtsAudioPlayer(context.applicationContext)
) : TtsEngine {

    override val label: String = "豆包"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun prepare(): Boolean {
        return apiKeyProvider.getApiKey(TtsEngineKeys.DOUBAO).isNotBlank()
    }

    override suspend fun speak(
        segment: ReadAloudSegment,
        rate: Float,
        utteranceId: String,
        listener: TtsUtteranceListener
    ) {
        val apiKey = apiKeyProvider.getApiKey(TtsEngineKeys.DOUBAO)
        if (apiKey.isBlank()) {
            throw IllegalStateException("未配置豆包 API Key")
        }

        val audioBytes = withContext(Dispatchers.IO) {
            synthesize(apiKey, segment.text, rate)
        }

        audioPlayer.setSpeed(rate)
        audioPlayer.prepare(
            audioBytes = audioBytes,
            onCompletion = { listener.onDone(utteranceId) },
            onError = { listener.onError(utteranceId) }
        )
        listener.onStart(utteranceId)
        audioPlayer.play()
    }

    private fun synthesize(apiKey: String, text: String, speed: Float): ByteArray {
        val url = "${baseUrl.trimEnd('/')}/api/v1/tts"
        val requestJson = JSONObject().apply {
            put("app", JSONObject().apply {
                put("appid", appId)
                put("token", "access_token")
                put("cluster", "volcano_tts")
            })
            put("user", JSONObject().apply {
                put("uid", "watchrss")
            })
            put("audio", JSONObject().apply {
                put("voice_type", voiceId)
                put("encoding", "mp3")
                put("speed_ratio", speed.toDouble())
            })
            put("request", JSONObject().apply {
                put("reqid", System.currentTimeMillis().toString())
                put("text", text)
                put("operation", "query")
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer; $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw RuntimeException(errBody.ifEmpty { "HTTP ${response.code}" })
        }

        val responseBody = response.body?.string() ?: throw RuntimeException("空响应")
        val json = JSONObject(responseBody)
        val data = json.optJSONObject("data")
            ?: throw RuntimeException("响应缺少 data 字段")
        val base64Audio = data.optString("", "")
        if (base64Audio.isBlank()) {
            // Try alternative field names
            val alternative = data.optString("audio", "")
            if (alternative.isBlank()) {
                throw RuntimeException("响应缺少音频数据")
            }
            return android.util.Base64.decode(alternative, android.util.Base64.DEFAULT)
        }
        return android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
    }

    override fun stop() {
        audioPlayer.stop()
    }

    override fun release() {
        audioPlayer.release()
    }
}
