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
}
