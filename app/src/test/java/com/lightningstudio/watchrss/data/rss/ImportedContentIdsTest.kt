package com.lightningstudio.watchrss.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedContentIdsTest {
    @Test
    fun isImportedContentUrl_matchesOnlyReservedImportPrefix() {
        assertTrue(ImportedContentIds.isImportedContentUrl("https://watchrss.local/import-content"))
        assertTrue(ImportedContentIds.isImportedContentUrl("https://watchrss.local/import-content/epub/book"))
        assertTrue(ImportedContentIds.isImportedContentUrl("https://watchrss.local/import-epub/book"))
        assertTrue(ImportedContentIds.isImportedContentUrl("watchrss://phone-imports"))
        assertFalse(ImportedContentIds.isImportedContentUrl("https://example.com/feed.xml"))
    }

    @Test
    fun syncedArticleFetchedAt_preservesRemoteChapterOrderTime() {
        val article = syncedArticle(
            importedAt = 100L,
            updatedAt = 120L
        )

        assertEquals(120L, syncedArticleFetchedAt(article, fallbackNow = 1_000L))
    }

    @Test
    fun syncedArticleFetchedAt_usesFallbackOnlyWhenRemoteTimeMissing() {
        val article = syncedArticle(
            importedAt = 0L,
            updatedAt = 0L
        )

        assertEquals(1_000L, syncedArticleFetchedAt(article, fallbackNow = 1_000L))
    }

    private fun syncedArticle(
        importedAt: Long,
        updatedAt: Long
    ): SyncedSavedArticle {
        return SyncedSavedArticle(
            articleId = "article",
            sourceDeviceId = "phone",
            url = "https://watchrss.local/import-epub/book/chapter/0001",
            title = "第一章",
            siteName = "示例书",
            excerpt = "",
            contentHtml = null,
            contentText = "正文",
            imageUrl = null,
            contentHash = "hash",
            importedAt = importedAt,
            updatedAt = updatedAt,
            independentSaved = false,
            independentChangedAt = 0L,
            independentSortOrder = 0L,
            rssSourceUrl = "https://watchrss.local/import-epub/book",
            rssSourceTitle = "示例书",
            favoriteSaved = false,
            favoriteChangedAt = 0L,
            favoriteSortOrder = 0L,
            watchLaterSaved = false,
            watchLaterChangedAt = 0L,
            watchLaterSortOrder = 0L,
            deleted = false,
            deletedAt = 0L
        )
    }
}
