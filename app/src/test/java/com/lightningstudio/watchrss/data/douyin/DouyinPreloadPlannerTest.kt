package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.testutil.sampleDouyinStreamItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DouyinPreloadPlannerTest {
    @Test
    fun buildDouyinRecentWindow_keepsAnchorAndForwardItemsOnly() {
        val items = (0 until 6).map { index ->
            sampleDouyinStreamItem(awemeId = "aweme-$index")
        }

        val window = buildDouyinRecentWindow(items, anchorIndex = 2)

        assertEquals(
            listOf("aweme-2", "aweme-3", "aweme-4", "aweme-5"),
            window.map { it.awemeId }
        )
    }

    @Test
    fun dropDouyinItemsBeforeAwemeId_removesAlreadyWatchedPrefix() {
        val items = (0 until 5).map { index ->
            sampleDouyinStreamItem(awemeId = "aweme-$index")
        }

        val dropped = dropDouyinItemsBeforeAwemeId(items, anchorAwemeId = "aweme-2")

        assertEquals(
            listOf("aweme-2", "aweme-3", "aweme-4"),
            dropped.map { it.awemeId }
        )
    }

    @Test
    fun mergeDouyinBootstrapItems_keepsRecentOrder_butUsesFreshestPlaybackMetadata() {
        val staleRecent = sampleDouyinStreamItem(
            awemeId = "aweme-b",
            playUrl = "https://example.com/stale.mp4",
            playUrlResolvedAtMs = 100L
        )
        val freshFeed = staleRecent.copy(
            playUrl = "https://example.com/fresh.mp4",
            playUrlResolvedAtMs = 200L
        )

        val merged = mergeDouyinBootstrapItems(
            feedItems = listOf(
                sampleDouyinStreamItem(awemeId = "aweme-a"),
                freshFeed,
                sampleDouyinStreamItem(awemeId = "aweme-c")
            ),
            recentItems = listOf(
                staleRecent,
                sampleDouyinStreamItem(awemeId = "aweme-c")
            )
        )

        assertEquals(listOf("aweme-a", "aweme-b", "aweme-c"), merged.map { it.awemeId })
        assertEquals("https://example.com/fresh.mp4", merged[1].playUrl)
        assertEquals(200L, merged[1].playUrlResolvedAtMs)
    }
}
