package com.lightningstudio.watchrss.data.rss

import com.lightningstudio.watchrss.data.db.RssItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssPreviewUpdatePlannerTest {
    @Test
    fun textItemWithSummaryButNoImage_skipsNoopWrite() {
        val item = sampleItem(
            summary = "现有摘要",
            previewImageUrl = null,
            imageUrl = null,
            description = "<p>现有摘要</p>"
        )

        assertTrue(RssPreviewUpdatePlanner.needsPreviewUpdate(item))
        assertNull(
            RssPreviewUpdatePlanner.buildWritePayload(
                item,
                RssPreview(summary = "现有摘要", previewImageUrl = null)
            )
        )
    }

    @Test
    fun missingSummary_writesOnce_thenSecondPassIsNoop() {
        val item = sampleItem(
            summary = null,
            previewImageUrl = null,
            imageUrl = null,
            description = "<p>补出来的摘要</p>"
        )

        val firstPayload = RssPreviewUpdatePlanner.buildWritePayload(
            item,
            RssPreview(summary = "补出来的摘要", previewImageUrl = null)
        )
        assertEquals("补出来的摘要", firstPayload?.summary)
        assertNull(firstPayload?.previewImageUrl)

        val updatedItem = item.copy(summary = firstPayload?.summary)
        val secondPayload = RssPreviewUpdatePlanner.buildWritePayload(
            updatedItem,
            RssPreview(summary = "补出来的摘要", previewImageUrl = null)
        )
        assertNull(secondPayload)
    }

    @Test
    fun derivedPreviewImage_writesWhenFeedItemHasNoDirectImage() {
        val item = sampleItem(
            summary = "已有摘要",
            previewImageUrl = null,
            imageUrl = null
        )

        val payload = RssPreviewUpdatePlanner.buildWritePayload(
            item,
            RssPreview(summary = "已有摘要", previewImageUrl = " https://example.com/cover.jpg ")
        )
        assertEquals("https://example.com/cover.jpg", payload?.previewImageUrl)
        assertNull(payload?.summary)
    }

    @Test
    fun sourceChange_invalidatesAttemptKey() {
        val oldItem = sampleItem(
            summary = "摘要",
            previewImageUrl = null,
            imageUrl = null,
            description = "<p>第一版</p>"
        )
        val newItem = oldItem.copy(description = "<p>第二版</p>")

        assertNotEquals(
            RssPreviewUpdatePlanner.attemptKeyFor(oldItem),
            RssPreviewUpdatePlanner.attemptKeyFor(newItem)
        )
    }

    private fun sampleItem(
        summary: String?,
        previewImageUrl: String?,
        imageUrl: String?,
        description: String? = null
    ): RssItemEntity {
        return RssItemEntity(
            id = 1L,
            channelId = 3L,
            title = "标题",
            description = description,
            content = null,
            originalContent = null,
            link = "https://example.com/post",
            guid = "guid-1",
            pubDate = null,
            imageUrl = imageUrl,
            audioUrl = null,
            videoUrl = null,
            summary = summary,
            previewImageUrl = previewImageUrl,
            isRead = false,
            isLiked = false,
            readingProgress = 0f,
            dedupKey = "dedup-1",
            fetchedAt = 1L,
            contentSizeBytes = 0L
        )
    }
}
