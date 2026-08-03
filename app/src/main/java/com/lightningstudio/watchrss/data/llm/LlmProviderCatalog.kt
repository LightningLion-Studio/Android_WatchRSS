package com.lightningstudio.watchrss.data.llm

object LlmProviderCatalog {
    const val PROVIDER_OPENAI = "openai"
    const val PROVIDER_DEEPSEEK = "deepseek"
    const val PROVIDER_QWEN = "qwen"
    const val PROVIDER_ZHIPU = "zhipu"
    const val PROVIDER_CUSTOM = "custom"
    const val PROVIDER_DEFAULT_MODEL = "default_model"

    fun resolveBaseUrl(provider: String, customBaseUrl: String): String = when (provider) {
        PROVIDER_OPENAI -> "https://api.openai.com/v1"
        PROVIDER_DEEPSEEK -> "https://api.deepseek.com/v1"
        PROVIDER_QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        PROVIDER_ZHIPU -> "https://open.bigmodel.cn/api/paas/v4"
        PROVIDER_CUSTOM -> customBaseUrl
        PROVIDER_DEFAULT_MODEL -> "" // 由后端账号状态里的 backendBaseUrl 动态填充
        else -> ""
    }

    fun defaultModel(provider: String): String = when (provider) {
        PROVIDER_OPENAI -> "gpt-4o-mini"
        PROVIDER_DEEPSEEK -> "deepseek-chat"
        PROVIDER_QWEN -> "qwen-turbo"
        PROVIDER_ZHIPU -> "glm-4-flash"
        PROVIDER_DEFAULT_MODEL -> "" // 后端选择默认模型
        else -> ""
    }

    fun displayName(provider: String): String = when (provider) {
        PROVIDER_OPENAI -> "OpenAI (ChatGPT)"
        PROVIDER_DEEPSEEK -> "DeepSeek"
        PROVIDER_QWEN -> "通义千问"
        PROVIDER_ZHIPU -> "智谱 GLM"
        PROVIDER_DEFAULT_MODEL -> "默认模型"
        PROVIDER_CUSTOM -> "自定义"
        else -> provider
    }

    fun isDefaultModel(provider: String): Boolean = provider == PROVIDER_DEFAULT_MODEL

    fun needsApiKey(provider: String): Boolean = !isDefaultModel(provider)
}
