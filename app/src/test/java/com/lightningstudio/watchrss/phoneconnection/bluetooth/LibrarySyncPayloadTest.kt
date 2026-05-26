package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import com.lightningstudio.watchrss.data.rss.ArticleSyncBody
import com.lightningstudio.watchrss.data.rss.SyncedArticleBodyRequest
import com.lightningstudio.watchrss.data.rss.SyncedArticleManifest
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
            isPinned = true,
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
        assertEquals(source.isPinned, parsedSource.isPinned)
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
            isPinned = true,
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
        assertEquals(source.isPinned, parsedSource.isPinned)
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

    @Test
    fun chunkedResponse_sendsOnlyRequestedChunksAndRebuildsBody() {
        val chunkSize = ArticleSyncBody.CHUNK_SIZE_BYTES
        val oldArticle = syncedArticle(
            articleId = "article-large",
            contentHash = "old",
            updatedAt = 20L
        ).copy(contentText = "A".repeat(chunkSize) + "B".repeat(chunkSize) + "C".repeat(64))
        val newArticle = oldArticle.copy(
            contentHash = "new",
            contentText = "A".repeat(chunkSize) + "D".repeat(chunkSize) + "C".repeat(64),
            updatedAt = 21L
        )
        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = listOf(oldArticle.toManifestEntry()),
            remoteManifest = listOf(newArticle.toRemoteManifestEntry())
        )
        val frames = LibrarySyncPayload.buildChunkedResponseFrames(
            deviceId = "watch",
            articles = listOf(newArticle),
            articleRequests = requests,
            applied = 0,
            useBatches = true
        )
        val parsed = LibrarySyncPayload.parseChunkedArticles(
            LibrarySyncPayload.combineArticlePayloads(frames)
        ).single()
        val rebuilt = ArticleSyncBody.rebuildBody(
            localArticle = oldArticle,
            payload = parsed,
            localBodyHash = oldArticle.toManifestEntry().bodyHash
        )

        assertTrue(requests.single().chunkIndexes.isNotEmpty())
        assertTrue(requests.single().chunkIndexes.size < newArticle.toRemoteManifestEntry().chunkHashes.size)
        assertEquals(requests.single().chunkIndexes, parsed.chunks.map { it.index })
        assertEquals(newArticle.contentText, rebuilt.second)
    }

    @Test
    fun chunkedResponse_withMetadataOnlyRequestSendsNoChunks() {
        val article = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 20L
        )
        val metadata = ArticleSyncBody.metadataFor(article)
        val frames = LibrarySyncPayload.buildChunkedResponseFrames(
            deviceId = "watch",
            articles = listOf(article),
            articleRequests = listOf(
                SyncedArticleBodyRequest(
                    articleId = article.articleId,
                    bodyHash = metadata.bodyHash,
                    chunkIndexes = emptyList()
                )
            ),
            applied = 0,
            useBatches = true
        )

        val parsed = LibrarySyncPayload.parseChunkedArticles(
            LibrarySyncPayload.combineArticlePayloads(frames)
        ).single()

        assertEquals(emptyList<Int>(), parsed.chunks.map { it.index })
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

    private fun SyncedSavedArticle.toManifestEntry(): SyncedArticleManifest {
        val metadata = ArticleSyncBody.metadataFor(this)
        return SyncedArticleManifest(
            articleId = articleId,
            sourceDeviceId = sourceDeviceId,
            contentHash = contentHash,
            updatedAt = updatedAt,
            independentChangedAt = independentChangedAt,
            favoriteChangedAt = favoriteChangedAt,
            watchLaterChangedAt = watchLaterChangedAt,
            deletedAt = deletedAt,
            bodyHash = metadata.bodyHash,
            bodyByteCount = metadata.bodyByteCount,
            chunkSize = metadata.chunkSize,
            chunkHashes = metadata.chunkHashes,
            metadataHash = metadata.metadataHash
        )
    }

    private fun SyncedSavedArticle.toRemoteManifestEntry(): ArticleSyncManifestEntry {
        val metadata = ArticleSyncBody.metadataFor(this)
        return ArticleSyncManifestEntry(
            articleId = articleId,
            sourceDeviceId = sourceDeviceId,
            contentHash = contentHash,
            updatedAt = updatedAt,
            independentChangedAt = independentChangedAt,
            favoriteChangedAt = favoriteChangedAt,
            watchLaterChangedAt = watchLaterChangedAt,
            deletedAt = deletedAt,
            bodyHash = metadata.bodyHash,
            bodyByteCount = metadata.bodyByteCount,
            chunkSize = metadata.chunkSize,
            chunkHashes = metadata.chunkHashes,
            metadataHash = metadata.metadataHash
        )
    }
}
