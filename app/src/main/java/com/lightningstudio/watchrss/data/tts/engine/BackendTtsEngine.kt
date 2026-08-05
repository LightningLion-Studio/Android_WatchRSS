package com.lightningstudio.watchrss.data.tts.engine

import android.content.Context
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.tts.ReadAloudSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "BackendTtsEngine"

class BackendTtsEngine(
    context: Context,
    private val watchAccountStore: AccountStore,
    private val audioPlayer: TtsAudioPlayer = ExoPlayerTtsAudioPlayer(context.applicationContext)
) : TtsEngine {

    override val label: String = "应用默认语音"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun prepare(): Boolean {
        return watchAccountStore.read()?.watchDeviceToken?.isNotBlank() == true
    }

    override suspend fun speak(segment: ReadAloudSegment, rate: Float, listener: TtsUtteranceListener): String {
        val utteranceId = "backend:${System.nanoTime()}"
        val account = watchAccountStore.read()
            ?: throw IllegalStateException("使用默认语音前请先登录")
        val backendBaseUrl = account.backendBaseUrl
        if (backendBaseUrl.isBlank()) {
            throw IllegalStateException("账号后端地址未配置")
        }
        val token = account.watchDeviceToken
        if (token.isBlank()) {
            throw IllegalStateException("账号令牌无效")
        }

        val audioBytes = withContext(Dispatchers.IO) {
            synthesize(backendBaseUrl, token, segment.text)
        }

        audioPlayer.setSpeed(rate)
        audioPlayer.prepare(
            audioBytes = audioBytes,
            onCompletion = { listener.onDone(utteranceId) },
            onError = { message -> listener.onError(utteranceId) }
        )
        listener.onStart(utteranceId)
        audioPlayer.play()
        return utteranceId
    }

    private fun synthesize(backendBaseUrl: String, token: String, text: String): ByteArray {
        val url = "${backendBaseUrl.trimEnd('/')}/api/v1/tts/default-model/speech"
        val body = JSONObject().apply {
            put("text", text)
            put("speed", 1.0)
            put("format", "mp3")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            val errMsg = runCatching {
                JSONObject(errBody).optJSONObject("error")?.optString("message") ?: ""
            }.getOrDefault("").ifEmpty { "HTTP ${response.code}" }
            throw RuntimeException(errMsg)
        }

        val responseBody = response.body?.string() ?: throw RuntimeException("空响应")
        val hex = runCatching {
            JSONObject(responseBody).optString("audio", "")
        }.getOrDefault("")
        if (hex.isBlank()) {
            throw RuntimeException("响应缺少音频数据")
        }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    override fun stop() {
        audioPlayer.stop()
    }

    override fun release() {
        audioPlayer.release()
    }
}
