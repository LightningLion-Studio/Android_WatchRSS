package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import com.lightningstudio.watchrss.data.rss.SyncedRssSource
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
            independentSaved = true,
            independentChangedAt = 4L,
            independentSortOrder = 4L,
            rssSourceUrl = "https://example.com/feed.xml",
            rssSourceTitle = "示例源",
            favoriteSaved = false,
            favoriteChangedAt = 0L,
            favoriteSortOrder = 0L,
            watchLaterSaved = true,
            watchLaterChangedAt = 3L,
            watchLaterSortOrder = 3L,
            deleted = false,
            deletedAt = 0L
        )

        val source = SyncedRssSource(
            url = "https://example.com/feed.xml",
            sourceDeviceId = "watch",
            title = "示例源",
            description = "源描述",
            siteUrl = "https://example.com",
            imageUrl = null,
            createdAt = 1L,
            updatedAt = 2L,
            sortOrder = 2L,
            deleted = false,
            deletedAt = 0L
        )

        val response = LibrarySyncPayload.buildResponse(
            deviceId = "watch",
            articles = listOf(article),
            applied = 1,
            rssSources = listOf(source),
            sourcesApplied = 1
        )
        val parsed = LibrarySyncPayload.parseArticles(response).single()
        val parsedSource = LibrarySyncPayload.parseRssSources(response).single()

        assertEquals(article.contentHtml, parsed.contentHtml)
        assertEquals(article.contentText, parsed.contentText)
        assertTrue(parsed.independentSaved)
        assertEquals(article.rssSourceUrl, parsed.rssSourceUrl)
        assertEquals(source.title, parsedSource.title)
        assertTrue(parsed.watchLaterSaved)
        assertEquals(1, response.getJSONObject("stats").getInt("applied"))
        assertEquals(1, response.getJSONObject("stats").getInt("sourcesApplied"))
        assertEquals("complete", response.getString("phase"))
    }

    @Test
    fun buildManifestResponse_exchangesManifestAndSourcesWithoutArticleBodies() {
        val article = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 20L,
            favoriteChangedAt = 30L
        )
        val source = SyncedRssSource(
            url = "https://example.com/feed.xml",
            sourceDeviceId = "watch",
            title = "示例源",
            description = "源描述",
            siteUrl = "https://example.com",
            imageUrl = null,
            createdAt = 1L,
            updatedAt = 2L,
            sortOrder = 2L,
            deleted = false,
            deletedAt = 0L
        )

        val response = LibrarySyncPayload.buildManifestResponse(
            deviceId = "watch",
            articles = listOf(article),
            rssSources = listOf(source),
            sourcesApplied = 1
        )
        val manifest = LibrarySyncPayload.parseArticleManifest(response).single()
        val parsedSource = LibrarySyncPayload.parseRssSources(response).single()

        assertEquals(0, LibrarySyncPayload.parseArticles(response).size)
        assertEquals(article.articleId, manifest.articleId)
        assertEquals(article.contentHash, manifest.contentHash)
        assertEquals(article.favoriteChangedAt, manifest.favoriteChangedAt)
        assertEquals(source.title, parsedSource.title)
        assertEquals("manifest", response.getString("phase"))
    }

    @Test
    fun filterArticlesNeedingSync_usesManifestTimestampsAndHash() {
        val article = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 20L,
            favoriteChangedAt = 30L
        )
        val currentRemote = ArticleSyncManifestEntry(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 20L,
            independentChangedAt = 0L,
            favoriteChangedAt = 30L,
            watchLaterChangedAt = 0L,
            deletedAt = 0L
        )
        val staleRemote = currentRemote.copy(contentHash = "old-hash")

        assertEquals(
            emptyList<SyncedSavedArticle>(),
            LibrarySyncPayload.filterArticlesNeedingSync(listOf(article), listOf(currentRemote))
        )
        assertEquals(
            listOf(article),
            LibrarySyncPayload.filterArticlesNeedingSync(listOf(article), listOf(staleRemote))
        )
    }

    private fun syncedArticle(
        articleId: String,
        contentHash: String,
        updatedAt: Long,
        favoriteChangedAt: Long = 0L
    ): SyncedSavedArticle {
        return SyncedSavedArticle(
            articleId = articleId,
            sourceDeviceId = "watch",
            url = "https://example.com/$articleId",
            title = articleId,
            siteName = "example.com",
            excerpt = "",
            contentHtml = null,
            contentText = "正文",
            imageUrl = null,
            contentHash = contentHash,
            importedAt = 1L,
            updatedAt = updatedAt,
            independentSaved = false,
            independentChangedAt = 0L,
            independentSortOrder = 0L,
            rssSourceUrl = null,
            rssSourceTitle = null,
            favoriteSaved = favoriteChangedAt > 0L,
            favoriteChangedAt = favoriteChangedAt,
            favoriteSortOrder = favoriteChangedAt,
            watchLaterSaved = false,
            watchLaterChangedAt = 0L,
            watchLaterSortOrder = 0L,
            deleted = false,
            deletedAt = 0L
        )
    }
}
