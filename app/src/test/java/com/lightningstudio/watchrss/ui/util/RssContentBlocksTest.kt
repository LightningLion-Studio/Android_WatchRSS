package com.lightningstudio.watchrss.ui.util

import com.lightningstudio.watchrss.data.rss.RssItem
import org.junit.Assert.assertTrue
import org.junit.Test

class RssContentBlocksTest {
    @Test
    fun buildContentBlocks_addsDouyinVideoBlock_whenOnlyDetailLinkExists() {
        val item = RssItem(
            id = 1L,
            channelId = 1L,
            title = "抖音视频",
            description = "作者：测试作者",
            content = null,
            originalContent = null,
            link = "https://www.douyin.com/video/7357000000000000001",
            pubDate = null,
            imageUrl = null,
            audioUrl = null,
            videoUrl = null,
            summary = null,
            previewImageUrl = null,
            isRead = false,
            isLiked = false,
            readingProgress = 0f,
            fetchedAt = 0L
        )

        val blocks = buildContentBlocks(item = item, useOriginalContent = false)

        assertTrue(
            blocks.any { block ->
                block is ContentBlock.Video &&
                    block.url == "https://www.douyin.com/video/7357000000000000001"
            }
        )
    }
}
