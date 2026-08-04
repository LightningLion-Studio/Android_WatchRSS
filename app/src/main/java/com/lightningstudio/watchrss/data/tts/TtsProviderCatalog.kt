package com.lightningstudio.watchrss.data.tts

object TtsProviderCatalog {
    const val ENGINE_LOCAL = "local"
    const val ENGINE_BACKEND_DEFAULT = "backend_default"
    const val ENGINE_MINIMAX = "minimax"
    const val ENGINE_AZURE = "azure"
    const val ENGINE_DOUBAO = "doubao"

    fun displayName(engine: String): String = when (engine) {
        ENGINE_LOCAL -> "本地 TTS"
        ENGINE_BACKEND_DEFAULT -> "应用默认语音"
        ENGINE_MINIMAX -> "MiniMax"
        ENGINE_AZURE -> "Azure"
        ENGINE_DOUBAO -> "豆包"
        else -> engine
    }

    fun defaultModel(engine: String): String = when (engine) {
        ENGINE_MINIMAX -> "speech-2.8-hd"
        ENGINE_AZURE -> "zh-CN-XiaoxiaoNeural"
        ENGINE_DOUBAO -> "zh_female_wanwanxin_moon_bigtts"
        else -> ""
    }

    fun defaultVoiceId(engine: String): String = when (engine) {
        ENGINE_MINIMAX -> "male-qn-qingse"
        ENGINE_AZURE -> "zh-CN-XiaoxiaoNeural"
        ENGINE_DOUBAO -> "zh_female_wanwanxin_moon_bigtts"
        else -> ""
    }

    fun needsApiKey(engine: String): Boolean = when (engine) {
        ENGINE_MINIMAX, ENGINE_AZURE, ENGINE_DOUBAO -> true
        else -> false
    }

    fun isBackendDefault(engine: String): Boolean = engine == ENGINE_BACKEND_DEFAULT

    fun isCloudEngine(engine: String): Boolean = engine != ENGINE_LOCAL

    fun supportedEngines(): List<String> = listOf(
        ENGINE_LOCAL,
        ENGINE_BACKEND_DEFAULT,
        ENGINE_MINIMAX,
        ENGINE_AZURE,
        ENGINE_DOUBAO
    )
}
