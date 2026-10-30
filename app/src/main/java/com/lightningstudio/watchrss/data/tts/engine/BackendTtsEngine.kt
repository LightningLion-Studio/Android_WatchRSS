package com.lightningstudio.watchrss.data.tts.engine

import android.content.Context
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.network.withWatchRssAppVersionHeader
import com.lightningstudio.watchrss.data.tts.ReadAloudSegment
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

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
    private val callLock = Any()
    private var activeCall: Call? = null

    override suspend fun prepare(): Boolean {
        return watchAccountStore.read()?.watchDeviceToken?.isNotBlank() == true
    }

    override suspend fun speak(
        segment: ReadAloudSegment,
        rate: Float,
        utteranceId: String,
        listener: TtsUtteranceListener
    ) {
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

        val audioBytes = synthesize(backendBaseUrl, token, segment.text)

        audioPlayer.setSpeed(rate)
        audioPlayer.prepare(
            audioBytes = audioBytes,
            onCompletion = { listener.onDone(utteranceId) },
            onError = { message -> listener.onError(utteranceId) }
        )
        listener.onStart(utteranceId)
        audioPlayer.play()
    }

    private suspend fun synthesize(backendBaseUrl: String, token: String, text: String): ByteArray =
        suspendCancellableCoroutine { continuation ->
        val url = "${backendBaseUrl.trimEnd('/')}/api/v1/tts/default-model/speech"
        val body = JSONObject().apply {
            put("text", text)
            put("speed", 1.0)
            put("format", "mp3")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .withWatchRssAppVersionHeader()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        synchronized(callLock) {
            activeCall?.cancel()
            activeCall = call
        }
        continuation.invokeOnCancellation {
            call.cancel()
            synchronized(callLock) {
                if (activeCall === call) activeCall = null
            }
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                clearActiveCall(call)
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                clearActiveCall(call)
                val result = runCatching { decodeResponse(response) }
                result.fold(
                    onSuccess = { bytes -> continuation.resumeWith(Result.success(bytes)) },
                    onFailure = continuation::resumeWithException
                )
            }
        })
    }

    private fun decodeResponse(response: Response): ByteArray = response.use {
        if (!it.isSuccessful) {
            val errBody = it.body?.string() ?: ""
            val errMsg = runCatching {
                JSONObject(errBody).optJSONObject("error")?.optString("message") ?: ""
            }.getOrDefault("").ifEmpty { "HTTP ${it.code}" }
            throw RuntimeException(errMsg)
        }

        val responseBody = it.body?.string() ?: throw RuntimeException("空响应")
        val hex = runCatching {
            JSONObject(responseBody).optString("audio", "")
        }.getOrDefault("")
        if (hex.isBlank()) throw RuntimeException("响应缺少音频数据")
        try {
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (error: NumberFormatException) {
            throw RuntimeException("响应音频数据格式错误", error)
        }
    }

    private fun clearActiveCall(call: Call) = synchronized(callLock) {
        if (activeCall === call) activeCall = null
    }

    override fun stop() {
        val call = synchronized(callLock) {
            activeCall.also { activeCall = null }
        }
        call?.cancel()
        audioPlayer.stop()
    }

    override fun release() {
        stop()
        audioPlayer.release()
    }
}
