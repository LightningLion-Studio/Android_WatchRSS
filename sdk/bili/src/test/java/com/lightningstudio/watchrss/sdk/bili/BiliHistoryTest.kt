package com.lightningstudio.watchrss.sdk.bili

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliHistoryTest {
    @Test
    fun reportHistory_postsCookieBackedPayloadWithVideoHeaders() = runTest {
        val store = TestBiliAccountStore(
            BiliAccount(
                cookies = mapOf(
                    "SESSDATA" to "sess",
                    "bili_jct" to "csrf"
                ),
                browserProfile = BiliSdkConfig().defaultWebBrowserProfile()
            )
        )
        val http = TestBiliHttpService().apply {
            enqueue(
                method = "POST",
                urlContains = "/x/v2/history/report",
                body = """{"code":0}"""
            )
        }
        val client = BiliClient(BiliSdkConfig(), store, http)

        val result = client.history.reportHistory(
            aid = 12L,
            cid = 34L,
            progressSeconds = 1248L,
            bvid = "BV12"
        )

        val request = http.recordedRequests.single()
        assertTrue(result.isSuccess)
        assertEquals("web", result.requestMode)
        assertEquals("12", request.body["aid"])
        assertEquals("34", request.body["cid"])
        assertEquals("1248", request.body["progress"])
        assertEquals("android", request.body["platform"])
        assertEquals("csrf", request.body["csrf"])
        assertEquals("https://www.bilibili.com/video/BV12", request.headers["Referer"])
        assertEquals("https://www.bilibili.com", request.headers["Origin"])
        assertTrue(request.includeCookies)
    }

    @Test
    fun reportHistory_returnsMissingCsrfWhenCookieModeIsUnavailable() = runTest {
        val client = BiliClient(
            BiliSdkConfig(),
            TestBiliAccountStore(
                BiliAccount(cookies = mapOf("SESSDATA" to "sess"))
            ),
            TestBiliHttpService()
        )

        val result = client.history.reportHistory(aid = 12L, cid = 34L, progressSeconds = 0L)

        assertEquals(-111, result.code)
        assertEquals("missing_csrf", result.message)
        assertEquals("web", result.requestMode)
    }
}
