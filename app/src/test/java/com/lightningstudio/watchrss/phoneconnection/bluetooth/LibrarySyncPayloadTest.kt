package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncPayloadTest {
    @Test
    fun buildResponse_roundTripsCompressedArticleContent() {
        val article = SyncedSavedArticle(
            articleId = "article-1",
            sourceDeviceId = "watch",
            url = "https://example.com/a",
            title = "标题",
            siteName = "example.com",
            excerpt = "摘要",
            contentHtml = "<article><p>正文</p></article>",
            contentText = "正文",
            imageUrl = null,
            contentHash = "hash",
            importedAt = 1L,
            updatedAt = 2L,
            favoriteSaved = false,
            favoriteChangedAt = 0L,
            favoriteSortOrder = 0L,
            watchLaterSaved = true,
            watchLaterChangedAt = 3L,
            watchLaterSortOrder = 3L,
            deleted = false,
            deletedAt = 0L
        )

        val response = LibrarySyncPayload.buildResponse("watch", listOf(article), applied = 1)
        val parsed = LibrarySyncPayload.parseArticles(response).single()

        assertEquals(article.contentHtml, parsed.contentHtml)
        assertEquals(article.contentText, parsed.contentText)
        assertTrue(parsed.watchLaterSaved)
        assertEquals(1, response.getJSONObject("stats").getInt("applied"))
    }
}
