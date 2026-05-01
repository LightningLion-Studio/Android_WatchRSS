package com.lightningstudio.watchrss.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmProviderCatalogTest {
    @Test
    fun publicWelfareOverloadedMessage_mapsKnownLimitResponses() {
        val message = LlmProviderCatalog.publicWelfareOverloadedMessage(
            provider = LlmProviderCatalog.PROVIDER_PUBLIC_WELFARE,
            httpCode = 429,
            responseBody = """{"error":{"code":"1305","message":"该模型当前访问量过大，请您稍后再试"}}"""
        )

        assertEquals(LlmProviderCatalog.PUBLIC_WELFARE_OVERLOADED_MESSAGE, message)
    }

    @Test
    fun publicWelfareOverloadedMessage_doesNotHideUserApiLimitResponses() {
        val message = LlmProviderCatalog.publicWelfareOverloadedMessage(
            provider = LlmProviderCatalog.PROVIDER_OPENAI,
            httpCode = 429,
            responseBody = """{"error":{"code":"rate_limit","message":"Too many requests"}}"""
        )

        assertNull(message)
    }
}
