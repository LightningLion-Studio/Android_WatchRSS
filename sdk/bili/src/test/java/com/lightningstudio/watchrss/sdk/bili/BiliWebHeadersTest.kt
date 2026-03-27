package com.lightningstudio.watchrss.sdk.bili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliWebHeadersTest {
    private val config = BiliSdkConfig()

    @Test
    fun build_apiPostHeadersLookLikeBrowserFetch() {
        val account = BiliAccount(
            cookies = mapOf(
                "SESSDATA" to "sess",
                "bili_jct" to "csrf"
            ),
            browserProfile = config.defaultWebBrowserProfile()
        )

        val headers = BiliWebHeaders.build(
            config = config,
            account = account,
            method = "POST",
            url = "https://api.bilibili.com/x/web-interface/archive/like",
            headers = emptyMap(),
            includeCookies = true
        )

        assertEquals(config.webUserAgent, headers["User-Agent"])
        assertEquals(config.webReferer, headers["Referer"])
        assertEquals(config.webAcceptLanguage, headers["Accept-Language"])
        assertEquals("application/json, text/plain, */*", headers["Accept"])
        assertEquals("https://www.bilibili.com", headers["Origin"])
        assertEquals("same-site", headers["Sec-Fetch-Site"])
        assertEquals("cors", headers["Sec-Fetch-Mode"])
        assertEquals("empty", headers["Sec-Fetch-Dest"])
        assertEquals("?0", headers["Sec-CH-UA-Mobile"])
        assertEquals("\"Windows\"", headers["Sec-CH-UA-Platform"])
        assertTrue(headers["Sec-CH-UA"].orEmpty().contains("Google Chrome"))
        assertEquals("SESSDATA=sess; bili_jct=csrf", headers["Cookie"])
    }

    @Test
    fun build_videoApiHeadersUseVideoPageRefererWhenBvidPresent() {
        val headers = BiliWebHeaders.build(
            config = config,
            account = null,
            method = "GET",
            url = "https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD",
            headers = emptyMap(),
            includeCookies = false
        )

        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", headers["Referer"])
        assertFalse(headers.containsKey("Origin"))
    }

    @Test
    fun build_wbiHeadersOmitRefererByDefault() {
        val headers = BiliWebHeaders.build(
            config = config,
            account = null,
            method = "GET",
            url = "https://api.bilibili.com/x/web-interface/wbi/view?bvid=BV1xx411c7mD",
            headers = emptyMap(),
            includeCookies = false
        )

        assertFalse(headers.containsKey("Referer"))
    }

    @Test
    fun build_documentHeadersLookLikeNavigation() {
        val headers = BiliWebHeaders.build(
            config = config,
            account = null,
            method = "GET",
            url = "https://www.bilibili.com/",
            headers = emptyMap(),
            includeCookies = false
        )

        assertEquals(config.webUserAgent, headers["User-Agent"])
        assertEquals(config.webReferer, headers["Referer"])
        assertEquals("text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8", headers["Accept"])
        assertEquals("none", headers["Sec-Fetch-Site"])
        assertEquals("navigate", headers["Sec-Fetch-Mode"])
        assertEquals("document", headers["Sec-Fetch-Dest"])
        assertEquals("?1", headers["Sec-Fetch-User"])
        assertEquals("1", headers["Upgrade-Insecure-Requests"])
        assertFalse(headers.containsKey("Origin"))
        assertNull(headers["Cookie"])
    }
}
