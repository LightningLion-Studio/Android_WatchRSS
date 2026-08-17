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

private const val DEFAULT_REGION = "eastasia"

class AzureTtsEngine(
    context: Context,
    private val apiKeyProvider: TtsApiKeyProvider,
    private val voiceId: String,
    private val region: String = DEFAULT_REGION,
    private val audioPlayer: TtsAudioPlayer = ExoPlayerTtsAudioPlayer(context.applicationContext)
) : TtsEngine {

    override val label: String = "Azure"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun prepare(): Boolean {
        return apiKeyProvider.getApiKey(TtsEngineKeys.AZURE).isNotBlank()
    }

    override suspend fun speak(
        segment: ReadAloudSegment,
        rate: Float,
        utteranceId: String,
        listener: TtsUtteranceListener
    ) {
        val apiKey = apiKeyProvider.getApiKey(TtsEngineKeys.AZURE)
        if (apiKey.isBlank()) {
            throw IllegalStateException("未配置 Azure API Key")
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
        val token = fetchToken(apiKey)
        val url = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"
        val ssmlRate = (speed * 100).toInt()
        val ssml = """
            <speak version='1.0' xml:lang='zh-CN'>
                <voice xml:lang='zh-CN' name='$voiceId'>
                    <prosody rate='$ssmlRate%'>$text</prosody>
                </voice>
            </speak>
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/ssml+xml")
            .addHeader("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
            .post(ssml.toRequestBody("application/ssml+xml".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw RuntimeException(errBody.ifEmpty { "HTTP ${response.code}" })
        }

        return response.body?.bytes() ?: throw RuntimeException("空响应")
    }

    private fun fetchToken(apiKey: String): String {
        val url = "https://$region.api.cognitive.microsoft.com/sts/v1.0/issueToken"
        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", apiKey)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Azure token 请求失败：HTTP ${response.code}")
        }
        return response.body?.string()?.trim() ?: throw RuntimeException("Azure token 为空")
    }

    override fun stop() {
        audioPlayer.stop()
    }

    override fun release() {
        audioPlayer.release()
    }
}
