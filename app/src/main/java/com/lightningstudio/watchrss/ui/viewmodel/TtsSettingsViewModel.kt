package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.data.settings.TtsApiKeyStore
import com.lightningstudio.watchrss.data.tts.TtsProviderCatalog
import com.lightningstudio.watchrss.data.tts.engine.TtsEngineKeys
import com.lightningstudio.watchrss.util.AppLogger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import com.lightningstudio.watchrss.data.network.withWatchRssAppVersionHeader
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val TAG = "TtsSettingsVM"

private const val MIN_TTS_SPEED = 0.5f
private const val MAX_TTS_SPEED = 2.0f
private const val TTS_SPEED_STEP = 0.1f

data class TtsSettingsState(
    val engine: String = TtsProviderCatalog.ENGINE_LOCAL,
    val model: String = "",
    val voiceId: String = "",
    val speed: Float = 1.0f,
    val baseUrl: String = "",
    val hasApiKey: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isBackendDefault: Boolean = false,
    val needsApiKey: Boolean = false,
    val testStatus: TtsTestStatus = TtsTestStatus.Idle,
    val configMessage: String = ""
)

sealed interface TtsTestStatus {
    data object Idle : TtsTestStatus
    data object Testing : TtsTestStatus
    data class Success(val latencyMs: Long, val message: String) : TtsTestStatus
    data class Failure(val message: String) : TtsTestStatus
}

class TtsSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val ttsApiKeyStore: TtsApiKeyStore,
    private val watchAccountStore: AccountStore
) : ViewModel() {

    private val _state = MutableStateFlow(TtsSettingsState())
    val state: StateFlow<TtsSettingsState> = _state

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.ttsEngine,
                settingsRepository.ttsModel,
                settingsRepository.ttsVoiceId,
                settingsRepository.ttsSpeed,
                settingsRepository.ttsBaseUrl
            ) { engine, model, voiceId, speed, baseUrl ->
                Quintuple(engine, model, voiceId, speed, baseUrl)
            }.collect { (engine, model, voiceId, speed, baseUrl) ->
                val account = watchAccountStore.read()
                val isLoggedIn = account != null
                val effectiveEngine = engine.ifBlank { TtsProviderCatalog.ENGINE_LOCAL }
                val isBackendDefault = TtsProviderCatalog.isBackendDefault(effectiveEngine)
                val needsApiKey = TtsProviderCatalog.needsApiKey(effectiveEngine)
                val hasKey = when (effectiveEngine) {
                    TtsProviderCatalog.ENGINE_BACKEND_DEFAULT -> isLoggedIn
                    else -> if (needsApiKey) ttsApiKeyStore.hasApiKey(effectiveEngine) else false
                }
                _state.update {
                    it.copy(
                        engine = effectiveEngine,
                        model = model,
                        voiceId = voiceId,
                        speed = speed.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED),
                        baseUrl = baseUrl,
                        hasApiKey = hasKey,
                        isLoggedIn = isLoggedIn,
                        isBackendDefault = isBackendDefault,
                        needsApiKey = needsApiKey
                    )
                }
            }
        }
    }

    fun selectEngine(engine: String) {
        viewModelScope.launch {
            val model = TtsProviderCatalog.defaultModel(engine)
            val voiceId = TtsProviderCatalog.defaultVoiceId(engine)
            val baseUrl = when (engine) {
                TtsProviderCatalog.ENGINE_MINIMAX -> "https://api.minimaxi.com"
                TtsProviderCatalog.ENGINE_DOUBAO -> "https://openspeech.bytedance.com"
                else -> ""
            }
            settingsRepository.setTtsConfig(
                engine = engine,
                model = model,
                voiceId = voiceId,
                speed = _state.value.speed,
                baseUrl = baseUrl
            )
            _state.update {
                it.copy(
                    engine = engine,
                    model = model,
                    voiceId = voiceId,
                    baseUrl = baseUrl,
                    testStatus = TtsTestStatus.Idle,
                    configMessage = ""
                )
            }
        }
    }

    fun useLocal() {
        selectEngine(TtsProviderCatalog.ENGINE_LOCAL)
    }

    fun useBackendDefault() {
        viewModelScope.launch {
            runCatching {
                val account = watchAccountStore.read()
                    ?: throw IllegalStateException("使用默认语音前请先登录")
                if (account.backendBaseUrl.isBlank()) {
                    throw IllegalStateException("账号后端地址未配置")
                }
                settingsRepository.setTtsConfig(
                    engine = TtsProviderCatalog.ENGINE_BACKEND_DEFAULT,
                    model = "",
                    voiceId = "",
                    speed = _state.value.speed,
                    baseUrl = account.backendBaseUrl
                )
                _state.update {
                    it.copy(
                        engine = TtsProviderCatalog.ENGINE_BACKEND_DEFAULT,
                        model = "",
                        voiceId = "",
                        baseUrl = account.backendBaseUrl,
                        isBackendDefault = true,
                        needsApiKey = false,
                        hasApiKey = it.isLoggedIn,
                        testStatus = TtsTestStatus.Idle,
                        configMessage = "已使用应用默认语音"
                    )
                }
            }.onFailure { error ->
                AppLogger.e(TAG, "Apply default voice failed", error)
                _state.update {
                    it.copy(configMessage = error.message ?: "应用默认语音失败")
                }
            }
        }
    }

    fun updateModel(value: String) {
        viewModelScope.launch {
            settingsRepository.setTtsConfig(
                engine = _state.value.engine,
                model = value,
                voiceId = _state.value.voiceId,
                speed = _state.value.speed,
                baseUrl = _state.value.baseUrl
            )
            _state.update { it.copy(model = value) }
        }
    }

    fun updateVoiceId(value: String) {
        viewModelScope.launch {
            settingsRepository.setTtsConfig(
                engine = _state.value.engine,
                model = _state.value.model,
                voiceId = value,
                speed = _state.value.speed,
                baseUrl = _state.value.baseUrl
            )
            _state.update { it.copy(voiceId = value) }
        }
    }

    fun updateSpeed(value: Float) {
        val clamped = value.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED)
        viewModelScope.launch {
            settingsRepository.setTtsSpeed(clamped)
            _state.update { it.copy(speed = clamped) }
        }
    }

    fun increaseSpeed() {
        val next = (_state.value.speed + TTS_SPEED_STEP).coerceAtMost(MAX_TTS_SPEED)
        updateSpeed(next)
    }

    fun decreaseSpeed() {
        val next = (_state.value.speed - TTS_SPEED_STEP).coerceAtLeast(MIN_TTS_SPEED)
        updateSpeed(next)
    }

    fun updateBaseUrl(value: String) {
        viewModelScope.launch {
            settingsRepository.setTtsConfig(
                engine = _state.value.engine,
                model = _state.value.model,
                voiceId = _state.value.voiceId,
                speed = _state.value.speed,
                baseUrl = value
            )
            _state.update { it.copy(baseUrl = value) }
        }
    }

    fun setApiKey(engine: String, apiKey: String) {
        ttsApiKeyStore.setApiKey(engine, apiKey)
        if (_state.value.engine == engine) {
            _state.update { it.copy(hasApiKey = apiKey.isNotBlank()) }
        }
    }

    fun runTest() {
        val current = _state.value
        if (current.testStatus is TtsTestStatus.Testing) return

        _state.update { it.copy(configMessage = "", testStatus = TtsTestStatus.Testing) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                doConnectivityTest(current)
            }
            _state.update { it.copy(testStatus = result) }
        }
    }

    private fun doConnectivityTest(state: TtsSettingsState): TtsTestStatus {
        return when (state.engine) {
            TtsProviderCatalog.ENGINE_LOCAL -> doLocalTest()
            TtsProviderCatalog.ENGINE_BACKEND_DEFAULT -> doBackendDefaultTest()
            TtsProviderCatalog.ENGINE_MINIMAX -> doMiniMaxTest(state)
            TtsProviderCatalog.ENGINE_AZURE -> doAzureTest(state)
            TtsProviderCatalog.ENGINE_DOUBAO -> doDoubaoTest(state)
            else -> TtsTestStatus.Failure("未知引擎: ${state.engine}")
        }
    }

    private fun doLocalTest(): TtsTestStatus {
        return TtsTestStatus.Success(0, "本地 TTS 已启用")
    }

    private fun doBackendDefaultTest(): TtsTestStatus {
        val account = watchAccountStore.read()
            ?: return TtsTestStatus.Failure("使用默认语音前请先登录")
        val backendBaseUrl = account.backendBaseUrl
        if (backendBaseUrl.isBlank()) {
            return TtsTestStatus.Failure("账号后端地址未配置")
        }
        val token = account.watchDeviceToken
        if (token.isBlank()) {
            return TtsTestStatus.Failure("账号令牌无效")
        }

        return try {
            val url = "${backendBaseUrl.trimEnd('/')}/api/v1/tts/default-model/speech"
            val body = JSONObject().apply {
                put("text", "你好")
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

            val startMs = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startMs
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: ""
                }.getOrDefault("").ifEmpty { "HTTP ${response.code}" }
                return TtsTestStatus.Failure(errMsg)
            }

            val hex = runCatching {
                JSONObject(responseBody).optString("audio", "")
            }.getOrDefault("")
            if (hex.isBlank()) {
                return TtsTestStatus.Failure("响应缺少音频数据")
            }
            TtsTestStatus.Success(latencyMs, "默认语音连接成功")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Backend default TTS test failed", e)
            TtsTestStatus.Failure(e.message ?: "网络异常")
        }
    }

    private fun doMiniMaxTest(state: TtsSettingsState): TtsTestStatus {
        val apiKey = ttsApiKeyStore.getApiKey(TtsEngineKeys.MINIMAX)
        if (apiKey.isBlank()) {
            return TtsTestStatus.Failure("未配置 MiniMax API Key")
        }
        val model = state.model.ifBlank { TtsProviderCatalog.defaultModel(TtsProviderCatalog.ENGINE_MINIMAX) }
        val voiceId = state.voiceId.ifBlank { TtsProviderCatalog.defaultVoiceId(TtsProviderCatalog.ENGINE_MINIMAX) }
        val baseUrl = state.baseUrl.ifBlank { "https://api.minimaxi.com" }

        return try {
            val url = "${baseUrl.trimEnd('/')}/v1/t2a_v2"
            val requestBody = JSONObject().apply {
                put("model", model)
                put("text", "你好")
                put("stream", false)
                put("voice_setting", JSONObject().apply {
                    put("voice_id", voiceId)
                    put("speed", state.speed.toDouble())
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
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val startMs = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startMs
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = runCatching {
                    JSONObject(responseBody).optJSONObject("base_resp")?.optString("status_msg")
                        ?: JSONObject(responseBody).optString("message", "")
                }.getOrDefault("").ifEmpty { "HTTP ${response.code}" }
                return TtsTestStatus.Failure(errMsg)
            }

            val hex = JSONObject(responseBody).optJSONObject("data")?.optString("audio", "")
            if (hex.isNullOrBlank()) {
                return TtsTestStatus.Failure("响应缺少音频数据")
            }
            TtsTestStatus.Success(latencyMs, "MiniMax 连接成功")
        } catch (e: Exception) {
            AppLogger.e(TAG, "MiniMax TTS test failed", e)
            TtsTestStatus.Failure(e.message ?: "网络异常")
        }
    }

    private fun doAzureTest(state: TtsSettingsState): TtsTestStatus {
        val apiKey = ttsApiKeyStore.getApiKey(TtsEngineKeys.AZURE)
        if (apiKey.isBlank()) {
            return TtsTestStatus.Failure("未配置 Azure API Key")
        }
        val region = state.baseUrl.ifBlank { "eastasia" }
        val voiceId = state.voiceId.ifBlank { TtsProviderCatalog.defaultVoiceId(TtsProviderCatalog.ENGINE_AZURE) }

        return try {
            val tokenUrl = "https://$region.api.cognitive.microsoft.com/sts/v1.0/issueToken"
            val tokenRequest = Request.Builder()
                .url(tokenUrl)
                .addHeader("Ocp-Apim-Subscription-Key", apiKey)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            val tokenResponse = client.newCall(tokenRequest).execute()
            if (!tokenResponse.isSuccessful) {
                return TtsTestStatus.Failure("Azure token 请求失败：HTTP ${tokenResponse.code}")
            }
            val token = tokenResponse.body?.string()?.trim()
                ?: return TtsTestStatus.Failure("Azure token 为空")

            val ttsUrl = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"
            val ssmlRate = (state.speed * 100).toInt()
            val ssml = """
                <speak version='1.0' xml:lang='zh-CN'>
                    <voice xml:lang='zh-CN' name='$voiceId'>
                        <prosody rate='$ssmlRate%'>你好</prosody>
                    </voice>
                </speak>
            """.trimIndent()

            val request = Request.Builder()
                .url(ttsUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/ssml+xml")
                .addHeader("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                .post(ssml.toRequestBody("application/ssml+xml".toMediaType()))
                .build()

            val startMs = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startMs

            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                return TtsTestStatus.Failure(errBody.ifEmpty { "HTTP ${response.code}" })
            }
            response.body?.bytes() ?: return TtsTestStatus.Failure("空响应")
            TtsTestStatus.Success(latencyMs, "Azure 连接成功")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Azure TTS test failed", e)
            TtsTestStatus.Failure(e.message ?: "网络异常")
        }
    }

    private fun doDoubaoTest(state: TtsSettingsState): TtsTestStatus {
        val apiKey = ttsApiKeyStore.getApiKey(TtsEngineKeys.DOUBAO)
        if (apiKey.isBlank()) {
            return TtsTestStatus.Failure("未配置豆包 API Key")
        }
        val voiceId = state.voiceId.ifBlank { TtsProviderCatalog.defaultVoiceId(TtsProviderCatalog.ENGINE_DOUBAO) }
        val baseUrl = state.baseUrl.ifBlank { "https://openspeech.bytedance.com" }

        return try {
            val url = "${baseUrl.trimEnd('/')}/api/v1/tts"
            val requestJson = JSONObject().apply {
                put("app", JSONObject().apply {
                    put("appid", "")
                    put("token", "access_token")
                    put("cluster", "volcano_tts")
                })
                put("user", JSONObject().apply {
                    put("uid", "watchrss")
                })
                put("audio", JSONObject().apply {
                    put("voice_type", voiceId)
                    put("encoding", "mp3")
                    put("speed_ratio", state.speed.toDouble())
                })
                put("request", JSONObject().apply {
                    put("reqid", System.currentTimeMillis().toString())
                    put("text", "你好")
                    put("operation", "query")
                })
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer; $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            val startMs = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val latencyMs = System.currentTimeMillis() - startMs
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return TtsTestStatus.Failure(responseBody.ifEmpty { "HTTP ${response.code}" })
            }

            val data = JSONObject(responseBody).optJSONObject("data")
                ?: return TtsTestStatus.Failure("响应缺少 data 字段")
            val base64Audio = data.optString("", "")
            val audio = if (base64Audio.isBlank()) {
                data.optString("audio", "")
            } else {
                base64Audio
            }
            if (audio.isBlank()) {
                return TtsTestStatus.Failure("响应缺少音频数据")
            }
            TtsTestStatus.Success(latencyMs, "豆包连接成功")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Doubao TTS test failed", e)
            TtsTestStatus.Failure(e.message ?: "网络异常")
        }
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
