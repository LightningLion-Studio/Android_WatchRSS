package com.lightningstudio.watchrss.data.llm

object LlmProviderCatalog {
    const val PROVIDER_OPENAI = "openai"
    const val PROVIDER_DEEPSEEK = "deepseek"
    const val PROVIDER_QWEN = "qwen"
    const val PROVIDER_ZHIPU = "zhipu"
    const val PROVIDER_CUSTOM = "custom"
    const val PROVIDER_PUBLIC_WELFARE = "public_welfare"

    const val PUBLIC_WELFARE_BASE_URL = "https://szkpxqzqzj.coze.site/api/v1"
    const val PUBLIC_WELFARE_API_KEY = "5e3a2a3ec3a34b1694ea098e4b516795.XgKAcYuoCKjq3bXM"
    const val PUBLIC_WELFARE_MODEL = "glm-4.7-flash"
    const val PUBLIC_WELFARE_OVERLOADED_MESSAGE = "公益站点使用量过大，请使用自己的API Key以保证稳定性"

    fun resolveBaseUrl(provider: String, customBaseUrl: String): String = when (provider) {
        PROVIDER_OPENAI -> "https://api.openai.com/v1"
        PROVIDER_DEEPSEEK -> "https://api.deepseek.com/v1"
        PROVIDER_QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        PROVIDER_ZHIPU -> "https://open.bigmodel.cn/api/paas/v4"
        PROVIDER_PUBLIC_WELFARE -> PUBLIC_WELFARE_BASE_URL
        PROVIDER_CUSTOM -> customBaseUrl
        else -> ""
    }

    fun defaultModel(provider: String): String = when (provider) {
        PROVIDER_OPENAI -> "gpt-4o-mini"
        PROVIDER_DEEPSEEK -> "deepseek-chat"
        PROVIDER_QWEN -> "qwen-turbo"
        PROVIDER_ZHIPU -> "glm-4-flash"
        PROVIDER_PUBLIC_WELFARE -> PUBLIC_WELFARE_MODEL
        else -> ""
    }

    fun displayName(provider: String): String = when (provider) {
        PROVIDER_OPENAI -> "OpenAI (ChatGPT)"
        PROVIDER_DEEPSEEK -> "DeepSeek"
        PROVIDER_QWEN -> "通义千问"
        PROVIDER_ZHIPU -> "智谱 GLM"
        PROVIDER_PUBLIC_WELFARE -> "公益站点"
        PROVIDER_CUSTOM -> "自定义"
        else -> provider
    }

    fun isPublicWelfare(provider: String): Boolean = provider == PROVIDER_PUBLIC_WELFARE

    @Suppress("UNUSED_PARAMETER")
    fun publicWelfareOverloadedMessage(provider: String, httpCode: Int, responseBody: String): String? {
        if (!isPublicWelfare(provider) || httpCode != 429) return null

        /*
         * 公益站正常响应：HTTP 200 + Content-Type text/event-stream，响应体是 OpenAI
         * 兼容 SSE：多行 `data: {...}` chunk，末尾 `data: [DONE]`。chunk 里可能同时
         * 出现 reasoning_content 与 content，Gateway 会把模型名改写为 glm-4.7-flash。
         *
         * 公益站爆量/限流响应：HTTP 429 + Content-Type application/json，不再是 SSE 流，
         * 而是一次性 JSON，例如：
         * {"error":{"code":"1302","message":"您的账户已达到速率限制，请您控制请求频率"}}
         * {"error":{"code":"1305","message":"该模型当前访问量过大，请您稍后再试"}}
         *
         * 所以这里只在“已选择公益站 + HTTP 429”时把底层限流文案收敛成用户能行动的提示；
         * 用户自有 API Key 的 429 仍按原服务商错误透传。
         */
        return PUBLIC_WELFARE_OVERLOADED_MESSAGE
    }
}
