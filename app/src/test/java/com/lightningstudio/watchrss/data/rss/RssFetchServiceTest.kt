package com.lightningstudio.watchrss.data.rss

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class RssFetchServiceTest {
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
}
