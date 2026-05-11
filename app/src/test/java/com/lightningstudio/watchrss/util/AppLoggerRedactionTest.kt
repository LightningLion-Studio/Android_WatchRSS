package com.lightningstudio.watchrss.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerRedactionTest {
    @Test
    fun redactsCookieHeaderFromExistingDouyinSignatureLog() {
        val raw = "signature=Cookie=sessionid=abc123; sid_guard=guard456; Referer=https://www.douyin.com/"

        val redacted = redactSensitiveLogContent(raw)

        assertFalse(redacted.contains("abc123"))
        assertFalse(redacted.contains("guard456"))
        assertTrue(redacted.contains("Cookie=<redacted>"))
    }

    @Test
    fun redactsStandaloneSensitiveCookieTokensAndBearerTokens() {
        val raw = "sid_guard=guard456 SESSDATA=bili789 upstream=Bearer token123"

        val redacted = redactSensitiveLogContent(raw)

        assertFalse(redacted.contains("guard456"))
        assertFalse(redacted.contains("bili789"))
        assertFalse(redacted.contains("token123"))
        assertTrue(redacted.contains("sid_guard=<redacted>"))
        assertTrue(redacted.contains("SESSDATA=<redacted>"))
        assertTrue(redacted.contains("Bearer <redacted>"))
    }
}
