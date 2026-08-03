package com.lightningstudio.watchrss.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProviderCatalogTest {
    @Test
    fun defaultModel_isRecognizedAndNeedsNoApiKey() {
        assertTrue(LlmProviderCatalog.isDefaultModel(LlmProviderCatalog.PROVIDER_DEFAULT_MODEL))
        assertFalse(LlmProviderCatalog.needsApiKey(LlmProviderCatalog.PROVIDER_DEFAULT_MODEL))
        assertEquals("默认模型", LlmProviderCatalog.displayName(LlmProviderCatalog.PROVIDER_DEFAULT_MODEL))
    }

    @Test
    fun byokProviders_needApiKey() {
        assertTrue(LlmProviderCatalog.needsApiKey(LlmProviderCatalog.PROVIDER_OPENAI))
        assertTrue(LlmProviderCatalog.needsApiKey(LlmProviderCatalog.PROVIDER_DEEPSEEK))
        assertTrue(LlmProviderCatalog.needsApiKey(LlmProviderCatalog.PROVIDER_QWEN))
        assertTrue(LlmProviderCatalog.needsApiKey(LlmProviderCatalog.PROVIDER_ZHIPU))
        assertTrue(LlmProviderCatalog.needsApiKey(LlmProviderCatalog.PROVIDER_CUSTOM))
    }
}
