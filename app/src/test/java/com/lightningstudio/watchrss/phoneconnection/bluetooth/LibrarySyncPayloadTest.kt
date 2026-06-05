package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.rss.SyncedSavedArticle
import com.lightningstudio.watchrss.data.rss.ArticleSyncBody
import com.lightningstudio.watchrss.data.rss.ARTICLE_BODY_SYNC_MODE_FULL
import com.lightningstudio.watchrss.data.rss.ARTICLE_BODY_SYNC_MODE_SAVED
import com.lightningstudio.watchrss.data.rss.SyncedArticleBodyRequest
import com.lightningstudio.watchrss.data.rss.SyncedArticleManifest
import com.lightningstudio.watchrss.data.rss.SyncedRssSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.json.JSONObject

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
            deletedAt = 0L,
            readingProgress = 0.42f
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
        assertEquals(article.readingProgress, parsed.readingProgress, 0.0001f)
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
            favoriteChangedAt = 30L,
            readingProgress = 0.37f
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
            sourcesApplied = 1,
            changeSequence = LibraryChangeSequence(
                fromSeqExclusive = 4L,
                toSeqInclusive = 8L,
                fullSnapshot = false,
                fallbackReason = ""
            )
        )
        val manifest = LibrarySyncPayload.parseArticleManifest(response).single()
        val parsedSource = LibrarySyncPayload.parseRssSources(response).single()
        val changeSequence = LibrarySyncPayload.parseChangeSequence(response)

        assertEquals(0, LibrarySyncPayload.parseArticles(response).size)
        assertEquals(article.articleId, manifest.articleId)
        assertEquals(article.contentHash, manifest.contentHash)
        assertEquals(article.favoriteChangedAt, manifest.favoriteChangedAt)
        assertEquals(article.readingProgress, manifest.readingProgress, 0.0001f)
        assertEquals(source.title, parsedSource.title)
        assertEquals(source.isPinned, parsedSource.isPinned)
        assertTrue(LibrarySyncPayload.supportsChangeSequences(response))
        assertEquals(4L, changeSequence.fromSeqExclusive)
        assertEquals(8L, changeSequence.toSeqInclusive)
        assertEquals(false, changeSequence.fullSnapshot)
        assertEquals("manifest", response.getString("phase"))
    }

    @Test
    fun protocolFeatureChecks_acceptVersion8Peers() {
        val payload = JSONObject().apply {
            put("version", 8)
            put("supportsChangeSequences", true)
            put("supportsMetadataOnlyArticles", true)
        }

        assertTrue(LibrarySyncPayload.supportsChangeSequences(payload))
        assertTrue(LibrarySyncPayload.supportsMetadataOnlyArticles(payload))
    }

    @Test
    fun parseAck_treatsLegacySuccessAckAsApplied() {
        val ack = BluetoothSyncProtocol.parseAck(
            JSONObject().apply {
                put("action", BluetoothSyncProtocol.ACTION_ACK)
                put("success", true)
            }
        )

        assertEquals(BluetoothSyncProtocol.ACK_PHASE_APPLIED, ack?.phase)
        assertTrue(ack?.applied == true)
        assertTrue(ack?.applicationSucceeded == true)
    }

    @Test
    fun parseAck_distinguishesTransportReceiptFromApplicationSuccess() {
        val received = BluetoothSyncProtocol.parseAck(
            JSONObject().apply {
                put("action", BluetoothSyncProtocol.ACTION_ACK)
                put("success", true)
                put("phase", BluetoothSyncProtocol.ACK_PHASE_RECEIVED)
                put("message", "frames received")
            }
        )
        val failedApply = BluetoothSyncProtocol.parseAck(
            JSONObject().apply {
                put("action", BluetoothSyncProtocol.ACTION_ACK)
                put("success", false)
                put("phase", BluetoothSyncProtocol.ACK_PHASE_APPLIED)
                put("applied", false)
                put("message", "merge failed")
            }
        )

        assertFalse(received?.applied == true)
        assertFalse(received?.applicationSucceeded == true)
        assertEquals("frames received", received?.message)
        assertFalse(failedApply?.applicationSucceeded == true)
        assertEquals("merge failed", failedApply?.message)
    }

    @Test
    fun validateArticleRequestFrame_acceptsOrderedBatches() {
        val first = articleRequestFrame(batchIndex = 0, batchCount = 2)
        val second = articleRequestFrame(batchIndex = 1, batchCount = 2)

        val batchCount = LibrarySyncPayload.validateArticleRequestFrame(
            frame = first,
            expectedBatchIndex = 0
        )
        LibrarySyncPayload.validateArticleRequestFrame(
            frame = second,
            expectedBatchIndex = 1,
            expectedBatchCount = batchCount
        )

        assertEquals(2, batchCount)
    }

    @Test
    fun validateArticleRequestFrame_rejectsWrongActionAndPhase() {
        assertIllegalArgumentContains("action 异常") {
            LibrarySyncPayload.validateArticleRequestFrame(
                frame = articleRequestFrame(batchIndex = 0, batchCount = 1).apply {
                    put("action", BluetoothSyncProtocol.ACTION_REMOTE_INPUT)
                },
                expectedBatchIndex = 0
            )
        }
        assertIllegalArgumentContains("phase 异常") {
            LibrarySyncPayload.validateArticleRequestFrame(
                frame = articleRequestFrame(batchIndex = 0, batchCount = 1).apply {
                    put("phase", LibrarySyncPayload.PHASE_COMPLETE)
                },
                expectedBatchIndex = 0
            )
        }
    }

    @Test
    fun validateArticleRequestFrame_rejectsUnreasonableOrInconsistentBatchFields() {
        assertIllegalArgumentContains("批次数异常") {
            LibrarySyncPayload.validateArticleRequestFrame(
                frame = articleRequestFrame(
                    batchIndex = 0,
                    batchCount = LibrarySyncPayload.MAX_ARTICLE_REQUEST_BATCH_COUNT + 1
                ),
                expectedBatchIndex = 0
            )
        }
        assertIllegalArgumentContains("批次索引异常") {
            LibrarySyncPayload.validateArticleRequestFrame(
                frame = articleRequestFrame(batchIndex = 0, batchCount = 2),
                expectedBatchIndex = 1,
                expectedBatchCount = 2
            )
        }
        assertIllegalArgumentContains("批次数不一致") {
            LibrarySyncPayload.validateArticleRequestFrame(
                frame = articleRequestFrame(batchIndex = 1, batchCount = 3),
                expectedBatchIndex = 1,
                expectedBatchCount = 2
            )
        }
    }

    @Test
    fun filterArticlesNeedingSync_usesManifestTimestampsAndHash() {
        val article = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 20L,
            favoriteChangedAt = 30L,
            readingProgress = 0.37f
        )
        val currentRemote = ArticleSyncManifestEntry(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 20L,
            independentChangedAt = 0L,
            favoriteChangedAt = 30L,
            watchLaterChangedAt = 0L,
            deletedAt = 0L,
            readingProgress = 0.37f
        )
        val staleRemote = currentRemote.copy(contentHash = "old-hash")
        val staleProgressRemote = currentRemote.copy(readingProgress = 0.21f)

        assertEquals(
            emptyList<SyncedSavedArticle>(),
            LibrarySyncPayload.filterArticlesNeedingSync(listOf(article), listOf(currentRemote))
        )
        assertEquals(
            listOf(article),
            LibrarySyncPayload.filterArticlesNeedingSync(listOf(article), listOf(staleRemote))
        )
        assertEquals(
            listOf(article),
            LibrarySyncPayload.filterArticlesNeedingSync(listOf(article), listOf(staleProgressRemote))
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
        assertBatchWireByteHints(frames)
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
    fun chunkedResponse_requestsFullBodyWhenLocalBodyIsUnavailable() {
        val article = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 20L
        )
        val remoteManifest = article.toRemoteManifestEntry()
        val localManifest = article.toManifestEntry().copy(bodyAvailable = false)

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = listOf(localManifest),
            remoteManifest = listOf(remoteManifest)
        )

        assertEquals(remoteManifest.chunkHashes.indices.toList(), requests.single().chunkIndexes)
    }

    @Test
    fun chunkedResponse_usesMetadataOnlyForSavedArticleWhenLocalBodyExists() {
        val localArticle = syncedArticle(
            articleId = "article-1",
            contentHash = "local-hash",
            updatedAt = 20L,
            favoriteChangedAt = 10L
        ).copy(contentText = "本机正文")
        val remoteArticle = localArticle.copy(
            contentHash = "remote-hash",
            contentText = "远端正文不同",
            updatedAt = 30L,
            favoriteChangedAt = 30L,
            favoriteSortOrder = 30L
        )
        val localManifest = localArticle.toManifestEntry().copy(
            bodySyncMode = ARTICLE_BODY_SYNC_MODE_SAVED
        )
        val remoteManifest = remoteArticle.toRemoteManifestEntry().copy(
            bodySyncMode = ARTICLE_BODY_SYNC_MODE_SAVED
        )

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = listOf(localManifest),
            remoteManifest = listOf(remoteManifest),
            supportsMetadataOnlyArticles = true
        )
        val fallbackRequests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = listOf(localManifest),
            remoteManifest = listOf(remoteManifest),
            supportsMetadataOnlyArticles = false
        )
        val afterStateSyncRequests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = listOf(
                localManifest.copy(
                    updatedAt = remoteManifest.updatedAt,
                    favoriteChangedAt = remoteManifest.favoriteChangedAt,
                    metadataHash = remoteManifest.metadataHash
                )
            ),
            remoteManifest = listOf(remoteManifest),
            supportsMetadataOnlyArticles = true
        )

        assertTrue(requests.single().metadataOnly)
        assertEquals(emptyList<Int>(), requests.single().chunkIndexes)
        assertTrue(fallbackRequests.single().chunkIndexes.isNotEmpty())
        assertEquals(emptyList<SyncedArticleBodyRequest>(), afterStateSyncRequests)
    }

    @Test
    fun chunkedResponse_usesMetadataOnlyForSavedArticleWhenLocalBodyIsMissing() {
        val remoteManifest = remoteManifestWithChunks("saved-missing", listOf("a", "b")).copy(
            bodySyncMode = ARTICLE_BODY_SYNC_MODE_SAVED,
            updatedAt = 30L,
            favoriteChangedAt = 30L,
            metadataHash = "remote-metadata"
        )

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = emptyList(),
            remoteManifest = listOf(remoteManifest),
            supportsMetadataOnlyArticles = true
        )
        val fallbackRequests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = emptyList(),
            remoteManifest = listOf(remoteManifest),
            supportsMetadataOnlyArticles = false
        )

        assertTrue(requests.single().metadataOnly)
        assertEquals(emptyList<Int>(), requests.single().chunkIndexes)
        assertEquals(listOf(0, 1), fallbackRequests.single().chunkIndexes)
    }

    @Test
    fun chunkedResponse_usesMetadataOnlyForFullArticle() {
        val remoteManifest = remoteManifestWithChunks("full-article", listOf("a", "b")).copy(
            bodySyncMode = ARTICLE_BODY_SYNC_MODE_FULL,
            updatedAt = 30L,
            metadataHash = "remote-metadata"
        )

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = emptyList(),
            remoteManifest = listOf(remoteManifest),
            supportsMetadataOnlyArticles = true
        )
        val fallbackRequests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = emptyList(),
            remoteManifest = listOf(remoteManifest),
            supportsMetadataOnlyArticles = false
        )

        assertTrue(requests.single().metadataOnly)
        assertEquals(emptyList<Int>(), requests.single().chunkIndexes)
        assertEquals(listOf(0, 1), fallbackRequests.single().chunkIndexes)
    }

    @Test
    fun chunkedResponse_requestsBodyForFullArticleWhenPeerDoesNotSupportMetadataOnly() {
        val remoteManifest = remoteManifestWithChunks("small-full", listOf("a", "b")).copy(
            bodySyncMode = ARTICLE_BODY_SYNC_MODE_FULL
        )

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = emptyList(),
            remoteManifest = listOf(remoteManifest),
            supportsMetadataOnlyArticles = false
        )

        assertFalse(requests.single().metadataOnly)
        assertEquals(listOf(0, 1), requests.single().chunkIndexes)
    }

    @Test
    fun chunkedResponse_doesNotRequestUnavailableRemoteBody() {
        val article = syncedArticle(
            articleId = "article-1",
            contentHash = "hash",
            updatedAt = 20L
        )
        val remoteManifest = article.toRemoteManifestEntry().copy(bodyAvailable = false)

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = emptyList(),
            remoteManifest = listOf(remoteManifest)
        )

        assertEquals(emptyList<SyncedArticleBodyRequest>(), requests)
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
    fun chunkedResponse_syncLimitRequestsEveryMissingBody() {
        val remoteManifest = (1..30).map { index ->
            remoteManifestWithChunks("article-$index", listOf("hash-$index"))
        }

        val requests = LibrarySyncPayload.buildBodyRequestsForRemoteArticles(
            localManifest = emptyList(),
            remoteManifest = remoteManifest,
            maxBodyRequestChunks = LibrarySyncPayload.MAX_BODY_REQUEST_CHUNKS_PER_SYNC
        )

        assertEquals(remoteManifest.map { it.articleId }, requests.map { it.articleId })
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
                    chunkIndexes = emptyList(),
                    metadataOnly = true
                )
            ),
            applied = 0,
            useBatches = true
        )

        val parsed = LibrarySyncPayload.parseChunkedArticles(
            LibrarySyncPayload.combineArticlePayloads(frames)
        ).single()

        assertEquals(emptyList<Int>(), parsed.chunks.map { it.index })
        assertTrue(parsed.metadataOnly)
    }

    @Test
    fun chunkedResponse_respondsMetadataOnlyForSavedArticleWhenPeerSupportsIt() {
        val article = syncedArticle(
            articleId = "saved-article",
            contentHash = "hash",
            updatedAt = 20L,
            favoriteChangedAt = 30L
        ).copy(contentText = "正文".repeat(4096))
        val metadata = ArticleSyncBody.metadataFor(article)
        val frames = LibrarySyncPayload.buildChunkedResponseFrames(
            deviceId = "watch",
            articles = listOf(article),
            articleRequests = listOf(
                SyncedArticleBodyRequest(
                    articleId = article.articleId,
                    bodyHash = metadata.bodyHash,
                    chunkIndexes = metadata.chunkHashes.indices.toList(),
                    metadataOnly = false
                )
            ),
            applied = 0,
            useBatches = true,
            allowMetadataOnlyArticles = true
        )

        val parsed = LibrarySyncPayload.parseChunkedArticles(
            LibrarySyncPayload.combineArticlePayloads(frames)
        ).single()

        assertTrue(parsed.metadataOnly)
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

        assertTrue(frames.size > 2)
        assertResponseProgressHeader(frames, totalArticles = 1)
        assertTrue(frames.all { BluetoothSyncProtocol.encodedSize(it) <= BluetoothSyncProtocol.MAX_FRAME_BYTES })
        assertBatchWireByteHints(frames)
        assertEquals(article.contentText, rebuilt.second)
    }

    private fun assertResponseProgressHeader(frames: List<JSONObject>, totalArticles: Int) {
        val header = frames.first()
        assertEquals(0, header.getInt("batchIndex"))
        assertEquals(frames.size, header.getInt("batchCount"))
        assertEquals(totalArticles, header.getInt("totalArticles"))
        assertEquals(0, header.getJSONArray("articles").length())
        assertTrue(header.has(LibrarySyncPayload.FIELD_BATCH_WIRE_BYTES))
        assertTrue(header.has(LibrarySyncPayload.FIELD_BATCH_TOTAL_WIRE_BYTES))
        frames.drop(1).forEachIndexed { index, frame ->
            assertEquals(index + 1, frame.getInt("batchIndex"))
            assertEquals(frames.size, frame.getInt("batchCount"))
        }
    }

    private fun assertBatchWireByteHints(frames: List<JSONObject>) {
        val totalWireBytes = frames.sumOf { BluetoothSyncProtocol.wireSize(it) }
        frames.forEach { frame ->
            assertEquals(
                BluetoothSyncProtocol.wireSize(frame),
                frame.getLong(LibrarySyncPayload.FIELD_BATCH_WIRE_BYTES)
            )
            assertEquals(
                totalWireBytes,
                frame.getLong(LibrarySyncPayload.FIELD_BATCH_TOTAL_WIRE_BYTES)
            )
        }
    }

    private fun syncedArticle(
        articleId: String,
        contentHash: String,
        updatedAt: Long,
        favoriteChangedAt: Long = 0L,
        readingProgress: Float = 0f
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
            deletedAt = 0L,
            readingProgress = readingProgress
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

    private fun articleRequestFrame(batchIndex: Int, batchCount: Int): JSONObject {
        return JSONObject().apply {
            put("version", LibrarySyncPayload.PROTOCOL_VERSION)
            put("action", BluetoothSyncProtocol.ACTION_SYNC_LIBRARY)
            put("phase", LibrarySyncPayload.PHASE_ARTICLES)
            put("deviceId", "phone")
            put("articles", org.json.JSONArray())
            put("batchIndex", batchIndex)
            put("batchCount", batchCount)
        }
    }

    private fun assertIllegalArgumentContains(expected: String, block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException containing $expected")
        } catch (exception: IllegalArgumentException) {
            assertTrue(exception.message.orEmpty().contains(expected))
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
