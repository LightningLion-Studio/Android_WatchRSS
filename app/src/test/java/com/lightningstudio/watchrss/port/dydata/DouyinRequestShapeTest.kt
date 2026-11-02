package com.lightningstudio.watchrss.port.dydata

import com.lightningstudio.watchrss.sdk.douyin.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DouyinRequestShapeTest {
    @Test fun feedIsSignedEmptyPostWithMatchingBrowserProfileAndProbeIsBoundedReadOnlyGet() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("{\"status_code\":0,\"aweme_list\":[]}".toResponseBody()).build()
        }.build()
        val crawler = DouyinWebCrawler(ABogus(), client = client)
        crawler.fetchJingxuanFeed("fixture-cookie", "123", 10)
        assertEquals(DouyinLoginProbeResult.AUTHENTICATED, crawler.probeLogin("fixture-cookie"))
        val feed = requests[0]
        assertEquals("POST", feed.method)
        assertEquals("/aweme/v2/web/module/feed/", feed.url.encodedPath)
        assertEquals(0L, feed.body!!.contentLength())
        assertEquals(DouyinWebCrawler.USER_AGENT, feed.header("User-Agent"))
        assertEquals("155.0.0.0", feed.url.queryParameter("browser_version"))
        assertEquals(feed.url.queryParameter("browser_version"), feed.url.queryParameter("engine_version"))
        assertFalse(feed.url.queryParameter("a_bogus").isNullOrBlank())
        assertEquals("123", feed.url.queryParameter("cursor"))
        assertEquals("GET", requests[1].method)
        assertEquals("/aweme/v1/web/notice/count/", requests[1].url.encodedPath)
    }

    @Test fun http403AndHtmlDoNotBecomeAuthenticationFailure() = runTest {
        for ((status, body) in listOf(403 to "forbidden", 200 to "<html>challenge</html>", 200 to "blocked")) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                    .body(body.toResponseBody()).build()
            }.build()
            try { DouyinWebCrawler(ABogus(), client = client).fetchJingxuanFeed("fixture"); fail("challenge accepted") }
            catch (_: DouyinChallengeException) { }
        }
    }
}
