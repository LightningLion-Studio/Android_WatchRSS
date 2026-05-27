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
    fun chunkedResponse_sendsRequestedChunksAndRebuildsCompressedBody() {
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
        assertEquals(requests.single().chunkIndexes, parsed.chunks.map { it.index })
        assertEquals(newArticle.contentText, rebuilt.second)
    }

    @Test
    fun chunkedResponse_requestsFullBodyWhenLocalChunkHashesAreMissing() {
        val article = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 20L
        )
        val remoteManifest = article.toRemoteManifestEntry()
        val localManifest = article.toManifestEntry().copy(chunkHashes = emptyList())

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = listOf(localManifest),
            remoteManifest = listOf(remoteManifest)
        )

        assertEquals(remoteManifest.chunkHashes.indices.toList(), requests.single().chunkIndexes)
    }

    @Test
    fun chunkedResponse_limitsBodyRequestsByWholeArticles() {
        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = emptyList(),
            remoteManifest = listOf(
                remoteManifestWithChunks("article-1", listOf("a", "b")),
                remoteManifestWithChunks("article-2", listOf("c", "d")),
                remoteManifestWithChunks("deleted", emptyList()).copy(deleted = true, deletedAt = 30L),
                remoteManifestWithChunks("article-3", listOf("e"))
            ),
            maxBodyRequestChunks = 3
        )

        assertEquals(listOf("article-1", "deleted", "article-3"), requests.map { it.articleId })
        assertEquals(listOf(0, 1), requests[0].chunkIndexes)
        assertEquals(emptyList<Int>(), requests[1].chunkIndexes)
        assertEquals(listOf(0), requests[2].chunkIndexes)
    }

    @Test
    fun chunkedResponse_doesNotRequestBodyForDeletedRemoteArticle() {
        val article = syncedArticle(
            articleId = "article-deleted",
            contentHash = "hash",
            updatedAt = 20L
        )
        val remoteManifest = article.toRemoteManifestEntry().copy(
            deleted = true,
            deletedAt = 30L
        )

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = emptyList(),
            remoteManifest = listOf(remoteManifest)
        )

        assertEquals(emptyList<Int>(), requests.single().chunkIndexes)
    }

    @Test
    fun chunkedResponse_ignoresDeletedTombstoneMetadataDrift() {
        val remoteManifest = syncedArticle(
            articleId = "article-deleted",
            contentHash = "hash",
            updatedAt = 20L
        ).toRemoteManifestEntry().copy(
            updatedAt = 100L,
            deleted = true,
            deletedAt = 30L,
            metadataHash = "remote-metadata"
        )
        val localManifest = syncedArticle(
            articleId = "article-deleted",
            contentHash = "local-hash",
            updatedAt = 1L
        ).toManifestEntry().copy(
            deleted = true,
            deletedAt = 30L,
            bodyHash = "local-body",
            chunkHashes = emptyList(),
            metadataHash = "local-metadata"
        )

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = listOf(localManifest),
            remoteManifest = listOf(remoteManifest)
        )

        assertEquals(emptyList<SyncedArticleBodyRequest>(), requests)
    }

    @Test
    fun chunkedResponse_ignoresOlderRemoteMetadataDrift() {
        val localManifest = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 100L
        ).toManifestEntry().copy(metadataHash = "local-metadata")
        val remoteManifest = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 90L
        ).toRemoteManifestEntry().copy(
            bodyHash = localManifest.bodyHash,
            bodyByteCount = localManifest.bodyByteCount,
            chunkSize = localManifest.chunkSize,
            chunkHashes = localManifest.chunkHashes,
            metadataHash = "remote-metadata"
        )

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = listOf(localManifest),
            remoteManifest = listOf(remoteManifest)
        )

        assertEquals(emptyList<SyncedArticleBodyRequest>(), requests)
    }

    @Test
    fun chunkedResponse_ignoresSameTimestampMetadataDrift() {
        val localManifest = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 100L
        ).toManifestEntry().copy(
            sourceDeviceId = "a-device",
            metadataHash = "local-metadata"
        )
        val remoteManifest = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 100L
        ).toRemoteManifestEntry().copy(
            sourceDeviceId = "z-device",
            bodyHash = localManifest.bodyHash,
            bodyByteCount = localManifest.bodyByteCount,
            chunkSize = localManifest.chunkSize,
            chunkHashes = localManifest.chunkHashes,
            metadataHash = "remote-metadata"
        )

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = listOf(localManifest),
            remoteManifest = listOf(remoteManifest)
        )

        assertEquals(emptyList<SyncedArticleBodyRequest>(), requests)
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

    @Test
    fun chunkedResponse_splitsHugeArticleAcrossFramesAndRebuildsBody() {
        val article = syncedArticle(
            articleId = "article-huge",
            contentHash = "hash",
            updatedAt = 20L
        ).copy(
            contentText = pseudoRandomText(
                seed = 42,
                length = BluetoothSyncProtocol.MAX_FRAME_BYTES + ArticleSyncBody.CHUNK_SIZE_BYTES
            )
        )
        val metadata = ArticleSyncBody.metadataFor(article)

        val frames = LibrarySyncPayload.buildChunkedResponseFrames(
            deviceId = "watch",
            articles = listOf(article),
            articleRequests = listOf(
                SyncedArticleBodyRequest(
                    articleId = article.articleId,
                    bodyHash = metadata.bodyHash,
                    chunkIndexes = metadata.chunkHashes.indices.toList()
                )
            ),
            applied = 0,
            useBatches = true
        )
        val combined = LibrarySyncPayload.combineArticlePayloads(frames)
        val parsed = LibrarySyncPayload.parseChunkedArticles(combined).single()
        val rebuilt = ArticleSyncBody.rebuildBody(
            localArticle = null,
            payload = parsed,
            localBodyHash = ""
        )

        assertTrue(frames.size > 1)
        assertTrue(frames.all { BluetoothSyncProtocol.encodedSize(it) <= BluetoothSyncProtocol.MAX_FRAME_BYTES })
        assertEquals(article.contentText, rebuilt.second)
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

    private fun pseudoRandomText(seed: Int, length: Int): String {
        var value = seed
        return buildString(length) {
            repeat(length) {
                value = value * 1103515245 + 12345
                append((33 + ((value ushr 16) % 90)).toChar())
            }
        }
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

    private fun remoteManifestWithChunks(articleId: String, chunkHashes: List<String>): ArticleSyncManifestEntry {
        return ArticleSyncManifestEntry(
            articleId = articleId,
            sourceDeviceId = "phone",
            contentHash = "content-$articleId",
            updatedAt = 20L,
            independentChangedAt = 20L,
            favoriteChangedAt = 0L,
            watchLaterChangedAt = 0L,
            deletedAt = 0L,
            bodyHash = "body-$articleId",
            bodyByteCount = chunkHashes.size.toLong(),
            chunkSize = ArticleSyncBody.CHUNK_SIZE_BYTES,
            chunkHashes = chunkHashes,
            metadataHash = "metadata-$articleId"
        )
    }
}
