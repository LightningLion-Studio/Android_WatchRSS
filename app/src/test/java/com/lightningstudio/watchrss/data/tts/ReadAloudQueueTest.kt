package com.lightningstudio.watchrss.data.tts

import com.lightningstudio.watchrss.data.rss.RssItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReadAloudQueueTest {
    @Test
    fun replaceCurrentQueueItemSnapshot_preservesOrderAndUsesFullCurrentItem() {
        val compactCurrent = item(id = 2L, content = "尾注")
        val fullCurrent = item(id = 2L, content = "完整正文")
        val queue = listOf(item(1L), compactCurrent, item(3L))

        val result = replaceCurrentQueueItemSnapshot(queue, fullCurrent)

        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
        assertSame(fullCurrent, result[1])
        assertEquals("完整正文", result[1].content)
    }

    @Test
    fun replaceCurrentQueueItemSnapshot_prependsItemWhenMissingFromPage() {
        val current = item(id = 9L, content = "完整正文")

        val result = replaceCurrentQueueItemSnapshot(listOf(item(1L), item(2L)), current)

        assertEquals(listOf(9L, 1L, 2L), result.map { it.id })
        assertSame(current, result.first())
    }

    private fun item(id: Long, content: String? = null) = RssItem(
        id = id,
        channelId = 1L,
        title = "文章 $id",
        description = null,
        content = content,
        originalContent = null,
        link = "https://example.com/$id",
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
}
