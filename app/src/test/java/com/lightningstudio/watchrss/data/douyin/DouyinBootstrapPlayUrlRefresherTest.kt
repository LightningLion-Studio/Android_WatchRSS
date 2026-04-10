package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
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
    fun refreshExpiredDouyinBootstrapPlayUrls_updatesExpiredItemsOnly() = runTest {
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
            setVideoResult(
                expired.awemeId,
                DouyinResult(
                    code = DouyinErrorCodes.OK,
                    data = DouyinContent.Video(
                        awemeId = expired.awemeId,
                        desc = "刷新后的标题",
                        authorName = "刷新后的作者",
                        diggCount = 99L,
                        playUrl = "https://example.com/refreshed.mp4",
                        coverUrl = "https://example.com/refreshed.jpg"
                    )
                )
            )
        }

        val result = refreshExpiredDouyinBootstrapPlayUrls(
            items = listOf(expired, fresh),
            repository = repository,
            nowMs = nowMs
        )

        assertEquals(listOf("expired-aweme"), repository.fetchVideoCalls)
        assertEquals(listOf("expired-aweme"), result.refreshedAwemeIds)
        assertEquals("https://example.com/refreshed.mp4", result.items[0].playUrl)
        assertEquals(nowMs, result.items[0].playUrlResolvedAtMs)
        assertEquals(DouyinSourceOrigin.VIDEO_REFRESH, result.items[0].sourceOrigin)
        assertEquals("https://example.com/fresh.mp4", result.items[1].playUrl)
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
            setVideoResult(
                expired.awemeId,
                DouyinResult(
                    code = DouyinErrorCodes.REQUEST_FAILED,
                    message = "network_failed"
                )
            )
        }

        val result = refreshExpiredDouyinBootstrapPlayUrls(
            items = listOf(expired),
            repository = repository,
            nowMs = nowMs
        )

        assertEquals(listOf("expired-aweme"), repository.fetchVideoCalls)
        assertTrue(result.refreshedAwemeIds.isEmpty())
        assertEquals(expired, result.items.single())
    }
}
