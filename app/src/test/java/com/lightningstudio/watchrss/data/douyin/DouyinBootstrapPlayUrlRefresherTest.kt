package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.testutil.sampleDouyinVideo
import com.lightningstudio.watchrss.testutil.TestDouyinRepository
import com.lightningstudio.watchrss.testutil.sampleDouyinStreamItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DouyinBootstrapPlayUrlRefresherTest {
    @Test
    fun refreshExpiredDouyinBootstrapPlayUrls_replacesExpiredEntriesFromFeedAndRetainsFreshEntries() = runTest {
        val nowMs = 1_800_000_000_000L
        val expired = sampleDouyinStreamItem(
            awemeId = "expired-aweme",
            playUrl = "https://example.com/old.mp4",
            playUrlResolvedAtMs = nowMs - DOUYIN_PLAY_URL_TTL_MS - 1L
        )
        val fresh = sampleDouyinStreamItem(
            awemeId = "fresh-aweme",
            playUrl = "https://example.com/fresh.mp4",
            playUrlResolvedAtMs = nowMs - 1_000L
        )
        val repository = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(listOf(DouyinResult(DouyinErrorCodes.OK, data = DouyinFeedPage(
                items = listOf(sampleDouyinVideo(awemeId = "replacement-aweme").apply {
                    desc = "刷新后的标题"
                    playUrl = "https://example.com/refreshed.mp4"
                }), nextCursor = null, hasMore = false
            ))))
        }

        val result = refreshExpiredDouyinBootstrapPlayUrls(
            items = listOf(expired, fresh),
            repository = repository,
            nowMs = nowMs
        )

        assertTrue(repository.fetchVideoCalls.isEmpty())
        assertEquals(listOf<String?>(null), repository.fetchFeedPageCursors)
        assertEquals(listOf("replacement-aweme"), result.refreshedAwemeIds)
        assertEquals(fresh, result.items[0])
        assertEquals("replacement-aweme", result.items[1].awemeId)
        assertEquals("https://example.com/refreshed.mp4", result.items[1].playUrl)
        assertEquals(nowMs, result.items[1].playUrlResolvedAtMs)
        assertEquals(DouyinSourceOrigin.NETWORK_FEED, result.items[1].sourceOrigin)
    }

    @Test
    fun refreshExpiredDouyinBootstrapPlayUrls_keepsOriginalItemWhenRefreshFails() = runTest {
        val nowMs = 1_800_000_000_000L
        val expired = sampleDouyinStreamItem(
            awemeId = "expired-aweme",
            playUrl = "https://example.com/old.mp4",
            playUrlResolvedAtMs = nowMs - DOUYIN_PLAY_URL_TTL_MS - 1L
        )
        val repository = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(listOf(DouyinResult(DouyinErrorCodes.REQUEST_FAILED, "network_failed")))
        }

        val result = refreshExpiredDouyinBootstrapPlayUrls(
            items = listOf(expired),
            repository = repository,
            nowMs = nowMs
        )

        assertTrue(repository.fetchVideoCalls.isEmpty())
        assertEquals(listOf<String?>(null), repository.fetchFeedPageCursors)
        assertTrue(result.refreshedAwemeIds.isEmpty())
        assertEquals(expired, result.items.single())
    }
}
