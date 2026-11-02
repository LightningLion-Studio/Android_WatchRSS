package com.lightningstudio.watchrss.data.rss

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssFetchServiceTest {
    @Test
    fun detectsBrowserChallengeMarkerWithoutMisclassifyingNormalFeeds() {
        assertEquals(
            true,
            isRssBrowserChallengePage("<html><div id=\"__waf_verification_challenge\"></div></html>")
        )
        assertEquals(
            false,
            isRssBrowserChallengePage("<rss><channel><title>36氪</title></channel></rss>")
        )
    }

    @Test
    fun feedClientAppliesBrowserUserAgentBeforeFetchingXml() {
        var observedUserAgent: String? = null
        val client = RssFetchService.buildFeedClient()
            .newBuilder()
            .addInterceptor { chain ->
                observedUserAgent = chain.request().header("User-Agent")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        client.newCall(
            Request.Builder()
                .url("https://www.jpl.nasa.gov/feeds/podcasts/")
                .build()
        ).execute().close()

        assertEquals(RssRemoteRequestPolicy.DEFAULT_USER_AGENT, observedUserAgent)
    }

    @Test
    fun feedClientReturnsChallengeHtmlForBrowserVerification() {
        val challengeHtml = "<html><div id=\"__waf_verification_challenge\"></div><script>proof()</script></html>"
        val client = RssFetchService.buildFeedClient()
            .newBuilder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(challengeHtml.toResponseBody())
                    .build()
            }
            .build()

        val error = runCatching {
            client.newCall(
                Request.Builder().url("https://36kr.com/feed-article").build()
            ).execute()
        }.exceptionOrNull()

        assertTrue("Expected browser challenge exception, got $error", error is RssBrowserChallengeRequiredException)
        assertEquals(challengeHtml, (error as RssBrowserChallengeRequiredException).challengeHtml)
        assertEquals("https://36kr.com/feed-article", error.challengeUrl)
    }
}
