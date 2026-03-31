package com.lightningstudio.watchrss.data.tts

data class ReadAloudConfig(
    val provider: ReadAloudProvider = ReadAloudProvider.OPENAI,
    val model: String = provider.defaultModel,
    val voice: String = provider.defaultVoice,
    val baseUrl: String = provider.defaultBaseUrl,
    val region: String = "",
    val appId: String = "",
    val resourceId: String = provider.defaultResourceId,
    val enabled: Boolean = false,
    val hasApiKey: Boolean = false
) {
    fun isComplete(): Boolean {
        if (!hasApiKey) return false
        if (provider.requiresRegion && region.isBlank()) return false
        if (provider.allowsCustomBaseUrl && baseUrl.isBlank()) return false
        if (provider.requiresAppId && appId.isBlank()) return false
        if (provider.requiresResourceId && resourceId.isBlank()) return false
        return voice.isNotBlank()
    }
}
