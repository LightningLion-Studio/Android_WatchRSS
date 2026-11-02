package com.lightningstudio.watchrss.port.readerrelease

import com.lightningstudio.watchrss.allowsPersistentResume
import com.lightningstudio.watchrss.data.rss.*
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ReaderReleasePolicyTest {
    @Test fun public36krFeedsUseOneDesktopIdentityWithoutRewritingAddresses() {
        listOf("feed", "feed-article", "feed-newsflash", "feed-moment").forEach { path ->
            val url = "https://36kr.com:443/$path?tag=public"
            assertEquals(RssRemoteRequestPolicy.PUBLIC_36KR_USER_AGENT, RssRemoteRequestPolicy.headerMapFor(url)["User-Agent"])
            assertEquals(RssRemoteRequestPolicy.PUBLIC_36KR_USER_AGENT, RssRemoteRequestPolicy.browserUserAgentFor(url))
            assertEquals(url.replace(":443", ""), RssRemoteRequestPolicy.newRequestBuilder(url).build().url.toString())
        }
        listOf("https://36kr.com/p/1", "https://m.36kr.com/feed", "http://36kr.com/feed",
            "https://36kr.com/feed#fragment", "https://36kr.com.evil.test/feed", "https://36kr.com/feed-other").forEach { url ->
            assertNull(RssRemoteRequestPolicy.browserUserAgentFor(url))
            assertEquals(RssRemoteRequestPolicy.DEFAULT_USER_AGENT, RssRemoteRequestPolicy.headerMapFor(url)["User-Agent"])
        }
        assertEquals("https://sspai.com/", RssRemoteRequestPolicy.headerMapFor("https://sspai.com/feed")["Referer"])
    }

    @Test fun savedChallengeAndFeedFixturesFlowThroughRealFetchAndParserWithoutTraffic() = runBlocking {
        val feed = fixture("36kr-feed.xml")
        val challenge = fixture("36kr-challenge.html")
        var body = challenge
        val client = RssFetchService.buildFeedClient().newBuilder().addInterceptor { chain ->
            assertEquals(RssRemoteRequestPolicy.PUBLIC_36KR_USER_AGENT, chain.request().header("User-Agent"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("fixture").body(body.toResponseBody()).build()
        }.build()
        val url = "https://36kr.com/feed-article"
        val error = runCatching { client.newCall(Request.Builder().url(url).build()).execute() }.exceptionOrNull()
        assertTrue(error is RssBrowserChallengeRequiredException)
        assertEquals(challenge, (error as RssBrowserChallengeRequiredException).challengeHtml)
        assertEquals(url, error.challengeUrl)
        body = feed
        val xml = client.newCall(Request.Builder().url(url).build()).execute().use { it.body!!.string() }
        val parsed = withFixtureXmlParser { fixtureRssFetchService().parseChannelContent(xml) }
        val preview = RssParseService()
        assertEquals("36氪", preview.channelTitle(parsed, url))
        assertEquals("测试资讯", preview.toPreviewItem(parsed.items.single()).title)
        assertTrue(parsed.items.single().content!!.contains("完整正文"))
    }

    @Test fun persistentNavigationRejectsEveryPlayerAndPermitsPassiveReaderOnly() {
        listOf("BiliPlayerActivity", "BiliAudioActivity", "DouyinEntryActivity", "DouyinPlayerActivity",
            "RssPlayerActivity", "ReadAloudActivity", "WebViewActivity", "FuturePlayerActivity").forEach {
            assertFalse(it, allowsPersistentResume("com.lightningstudio.watchrss.$it"))
        }
        assertFalse(allowsPersistentResume("other.package.DetailActivity"))
        assertTrue(allowsPersistentResume("com.lightningstudio.watchrss.DetailActivity"))
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/port/readerrelease/$name"))
        .bufferedReader().use { it.readText() }
}
