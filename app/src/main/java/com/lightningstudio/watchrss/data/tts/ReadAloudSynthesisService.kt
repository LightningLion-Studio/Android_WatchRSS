package com.lightningstudio.watchrss.data.tts

import android.util.Base64
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "ReadAloudSynthesis"
private const val USER_AGENT = "WatchRSS/1.0"

class ReadAloudSynthesisService(
    private val cacheDir: File
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun synthesizeToFile(
        config: ReadAloudConfig,
        apiKey: String,
        text: String,
        targetFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val trimmed = text.trim()
            require(trimmed.isNotEmpty()) { "朗读内容为空" }
            require(apiKey.isNotBlank()) { "未配置朗读 API Key" }

            val request = when (config.provider) {
                ReadAloudProvider.OPENAI,
                ReadAloudProvider.CUSTOM_OPENAI -> buildOpenAiSpeechRequest(config, apiKey, trimmed)
                ReadAloudProvider.MICROSOFT_AZURE -> buildAzureSpeechRequest(config, apiKey, trimmed)
                ReadAloudProvider.ELEVENLABS -> buildElevenLabsSpeechRequest(config, apiKey, trimmed)
                ReadAloudProvider.VOLCENGINE -> buildVolcengineSpeechRequest(config, apiKey, trimmed)
            }

            when (config.provider) {
                ReadAloudProvider.VOLCENGINE -> {
                    synthesizeVolcengineToFile(request, targetFile)
                }
                else -> {
                    client.newCall(request).execute().use { response ->
                        val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                        if (!response.isSuccessful) {
                            val message = runCatching {
                                JSONObject(String(bodyBytes)).optJSONObject("error")?.optString("message")
                            }.getOrNull().orEmpty().ifBlank {
                                "HTTP ${response.code}"
                            }
                            error(message)
                        }
                        targetFile.parentFile?.mkdirs()
                        targetFile.writeBytes(bodyBytes)
                        targetFile
                    }
                }
            }
        }.onFailure { error ->
            AppLogger.e(TAG, "Synthesize failed", error)
        }
    }

    private fun buildOpenAiSpeechRequest(
        config: ReadAloudConfig,
        apiKey: String,
        text: String
    ): Request {
        val url = "${config.baseUrl.trimEnd('/')}/audio/speech"
        val body = JSONObject().apply {
            put("model", config.model.ifBlank { config.provider.defaultModel })
            put("voice", config.voice.ifBlank { config.provider.defaultVoice })
            put("input", text)
            put("response_format", "mp3")
        }.toString()
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", USER_AGENT)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildAzureSpeechRequest(
        config: ReadAloudConfig,
        apiKey: String,
        text: String
    ): Request {
        val region = config.region.trim()
        require(region.isNotEmpty()) { "微软朗读缺少区域" }
        val url = "https://${region}.tts.speech.microsoft.com/cognitiveservices/v1"
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        val ssml = """
            <speak version="1.0" xml:lang="zh-CN">
                <voice name="${config.voice.ifBlank { config.provider.defaultVoice }}">$escaped</voice>
            </speak>
        """.trimIndent()
        return Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", apiKey)
            .addHeader("Content-Type", "application/ssml+xml")
            .addHeader("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
            .addHeader("User-Agent", USER_AGENT)
            .post(ssml.toRequestBody("application/ssml+xml".toMediaType()))
            .build()
    }

    private fun buildElevenLabsSpeechRequest(
        config: ReadAloudConfig,
        apiKey: String,
        text: String
    ): Request {
        val voiceId = config.voice.ifBlank { config.provider.defaultVoice }
        require(voiceId.isNotBlank()) { "ElevenLabs 需要 Voice ID" }
        val baseUrl = config.baseUrl.ifBlank { config.provider.defaultBaseUrl }.trimEnd('/')
        val url = "$baseUrl/text-to-speech/$voiceId?output_format=mp3_44100_128"
        val body = JSONObject().apply {
            put("text", text)
            put("model_id", config.model.ifBlank { config.provider.defaultModel })
        }.toString()
        return Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", USER_AGENT)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildVolcengineSpeechRequest(
        config: ReadAloudConfig,
        apiKey: String,
        text: String
    ): Request {
        require(config.appId.isNotBlank()) { "火山引擎朗读缺少 App ID" }
        require(config.resourceId.isNotBlank()) { "火山引擎朗读缺少 Resource ID" }
        val url = config.baseUrl.ifBlank { config.provider.defaultBaseUrl }
        val body = JSONObject().apply {
            put("user", JSONObject().apply {
                put("uid", "watchrss")
            })
            put("req_params", JSONObject().apply {
                put("text", text)
                if (config.model.isNotBlank()) {
                    put("model", config.model)
                }
                put("speaker", config.voice.ifBlank { config.provider.defaultVoice })
                put("audio_params", JSONObject().apply {
                    put("format", "mp3")
                    put("sample_rate", 24000)
                })
                put("additions", JSONObject().apply {
                    put("disable_markdown_filter", true)
                })
            })
        }.toString()
        return Request.Builder()
            .url(url)
            .addHeader("X-Api-App-Id", config.appId)
            .addHeader("X-Api-Access-Key", apiKey)
            .addHeader("X-Api-Resource-Id", config.resourceId)
            .addHeader("X-Api-Request-Id", UUID.randomUUID().toString())
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", USER_AGENT)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun synthesizeVolcengineToFile(
        request: Request,
        targetFile: File
    ): File {
        return client.newCall(request).execute().use { response ->
            val body = response.body ?: error("火山引擎响应为空")
            if (!response.isSuccessful) {
                val errorBody = body.string()
                val message = runCatching {
                    val json = JSONObject(errorBody)
                    val code = json.optInt("code")
                    val detail = json.optString("message")
                    if (detail.isNotBlank()) "$detail (code=$code)" else "HTTP ${response.code}"
                }.getOrElse {
                    errorBody.ifBlank { "HTTP ${response.code}" }
                }
                error(message)
            }

            val audioBytes = ByteArrayOutputStream()
            var hasFinishSignal = false
            body.byteStream().bufferedReader(Charsets.UTF_8).use { reader ->
                readJsonObjectStream(reader) { json ->
                    when (json.optInt("code", Int.MIN_VALUE)) {
                        0 -> {
                            val data = json.optString("data")
                            if (data.isNotBlank() && data != "null") {
                                audioBytes.write(Base64.decode(data, Base64.DEFAULT))
                            }
                        }
                        20000000 -> {
                            hasFinishSignal = true
                        }
                        else -> {
                            val code = json.optInt("code")
                            val message = json.optString("message").ifBlank {
                                "火山引擎朗读失败"
                            }
                            error("$message (code=$code)")
                        }
                    }
                }
            }

            require(audioBytes.size() > 0) {
                if (hasFinishSignal) {
                    "火山引擎未返回音频数据"
                } else {
                    "火山引擎流式响应不完整"
                }
            }
            targetFile.parentFile?.mkdirs()
            targetFile.writeBytes(audioBytes.toByteArray())
            targetFile
        }
    }

    private fun readJsonObjectStream(
        reader: java.io.Reader,
        onObject: (JSONObject) -> Unit
    ) {
        val buffer = CharArray(2048)
        val current = StringBuilder()
        var depth = 0
        var inString = false
        var escaping = false

        while (true) {
            val read = reader.read(buffer)
            if (read == -1) break
            for (index in 0 until read) {
                val char = buffer[index]
                if (depth == 0) {
                    if (char.isWhitespace()) {
                        continue
                    }
                    if (char != '{') {
                        continue
                    }
                }

                current.append(char)

                if (escaping) {
                    escaping = false
                    continue
                }

                when (char) {
                    '\\' -> if (inString) escaping = true
                    '"' -> inString = !inString
                    '{' -> if (!inString) depth += 1
                    '}' -> if (!inString) {
                        depth -= 1
                        if (depth == 0) {
                            onObject(JSONObject(current.toString()))
                            current.clear()
                            inString = false
                            escaping = false
                        }
                    }
                }
            }
        }

        require(depth == 0 && current.isEmpty()) { "流式响应解析失败" }
    }
}
