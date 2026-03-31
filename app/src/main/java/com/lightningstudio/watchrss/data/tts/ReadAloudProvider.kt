package com.lightningstudio.watchrss.data.tts

enum class ReadAloudProvider(
    val persistedValue: String,
    val displayName: String,
    val defaultModel: String,
    val defaultVoice: String,
    val defaultBaseUrl: String,
    val defaultResourceId: String = "",
    val apiKeyLabel: String,
    val voiceLabel: String = "语音",
    val requiresRegion: Boolean = false,
    val usesVoiceId: Boolean = false,
    val allowsCustomBaseUrl: Boolean = false,
    val requiresAppId: Boolean = false,
    val requiresResourceId: Boolean = false
) {
    OPENAI(
        persistedValue = "openai",
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini-tts",
        defaultVoice = "alloy",
        defaultBaseUrl = "https://api.openai.com/v1",
        apiKeyLabel = "API Key",
        voiceLabel = "语音"
    ),
    MICROSOFT_AZURE(
        persistedValue = "azure",
        displayName = "微软 Azure Speech",
        defaultModel = "neural",
        defaultVoice = "zh-CN-XiaoxiaoNeural",
        defaultBaseUrl = "",
        apiKeyLabel = "资源 Key",
        voiceLabel = "语音",
        requiresRegion = true
    ),
    ELEVENLABS(
        persistedValue = "elevenlabs",
        displayName = "ElevenLabs",
        defaultModel = "eleven_multilingual_v2",
        defaultVoice = "JBFqnCBsd6RMkjVDRZzb",
        defaultBaseUrl = "https://api.elevenlabs.io/v1",
        apiKeyLabel = "API Key",
        voiceLabel = "Voice ID",
        usesVoiceId = true
    ),
    VOLCENGINE(
        persistedValue = "volcengine",
        displayName = "火山引擎语音",
        defaultModel = "seed-tts-1.1",
        defaultVoice = "zh_female_shuangkuaisisi_moon_bigtts",
        defaultBaseUrl = "https://openspeech.bytedance.com/api/v3/tts/unidirectional",
        defaultResourceId = "seed-tts-1.0",
        apiKeyLabel = "Access Key",
        voiceLabel = "音色 / Speaker",
        requiresAppId = true,
        requiresResourceId = true
    ),
    CUSTOM_OPENAI(
        persistedValue = "custom_openai",
        displayName = "自定义 OpenAI 兼容",
        defaultModel = "gpt-4o-mini-tts",
        defaultVoice = "alloy",
        defaultBaseUrl = "",
        apiKeyLabel = "API Key",
        voiceLabel = "语音",
        allowsCustomBaseUrl = true
    );

    companion object {
        val orderedValues: List<ReadAloudProvider> = listOf(
            OPENAI,
            MICROSOFT_AZURE,
            ELEVENLABS,
            VOLCENGINE,
            CUSTOM_OPENAI
        )

        fun fromPersistedValue(value: String?): ReadAloudProvider {
            return orderedValues.firstOrNull { it.persistedValue == value } ?: OPENAI
        }
    }
}
