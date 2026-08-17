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

private const val DEFAULT_BASE_URL = "https://api.minimaxi.com"

class MiniMaxTtsEngine(
    context: Context,
    private val apiKeyProvider: TtsApiKeyProvider,
    private val model: String,
    private val voiceId: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val audioPlayer: TtsAudioPlayer = ExoPlayerTtsAudioPlayer(context.applicationContext)
) : TtsEngine {

    override val label: String = "MiniMax"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun prepare(): Boolean {
        return apiKeyProvider.getApiKey(TtsEngineKeys.MINIMAX).isNotBlank()
    }

    override suspend fun speak(
        segment: ReadAloudSegment,
        rate: Float,
        utteranceId: String,
        listener: TtsUtteranceListener
    ) {
        val apiKey = apiKeyProvider.getApiKey(TtsEngineKeys.MINIMAX)
        if (apiKey.isBlank()) {
            throw IllegalStateException("未配置 MiniMax API Key")
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
        val url = "${baseUrl.trimEnd('/')}/v1/t2a_v2"
        val body = JSONObject().apply {
            put("model", model)
            put("text", text)
            put("stream", false)
            put("voice_setting", JSONObject().apply {
                put("voice_id", voiceId)
                put("speed", speed.toDouble())
                put("vol", 1)
                put("pitch", 0)
            })
            put("audio_setting", JSONObject().apply {
                put("sample_rate", 32000)
                put("bitrate", 128000)
                put("format", "mp3")
                put("channel", 1)
            })
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            val errMsg = runCatching {
                JSONObject(errBody).optJSONObject("base_resp")?.optString("status_msg")
                    ?: JSONObject(errBody).optString("message", "")
            }.getOrDefault("").ifEmpty { "HTTP ${response.code}" }
            throw RuntimeException(errMsg)
        }

        val responseBody = response.body?.string() ?: throw RuntimeException("空响应")
        val json = JSONObject(responseBody)
        val hex = json.optJSONObject("data")?.optString("audio", "") ?: ""
        if (hex.isBlank()) {
            throw RuntimeException("响应缺少音频数据")
        }
        return hexToBytes(hex)
    }

    override fun stop() {
        audioPlayer.stop()
    }

    override fun release() {
        audioPlayer.release()
    }
}

object TtsEngineKeys {
    const val MINIMAX = "minimax"
    const val AZURE = "azure"
    const val DOUBAO = "doubao"
}

internal fun hexToBytes(hex: String): ByteArray {
    return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
