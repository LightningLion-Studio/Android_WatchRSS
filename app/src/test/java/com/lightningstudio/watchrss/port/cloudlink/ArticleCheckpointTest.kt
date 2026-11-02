package com.lightningstudio.watchrss.port.cloudlink

import com.lightningstudio.watchrss.data.rss.*
import com.lightningstudio.watchrss.phoneconnection.bluetooth.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest

class ArticleCheckpointTest {
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val article = SyncedSavedArticle("article", "phone", "https://fixture.invalid/a", "fixture", "site", "", null, "", null,
        "content-hash", 1, 2, favoriteSaved = false, favoriteChangedAt = 0, favoriteSortOrder = 0,
        watchLaterSaved = false, watchLaterChangedAt = 0, watchLaterSortOrder = 0, deleted = false, deletedAt = 0)
    private val first = byteArrayOf(1, 2, 3, 4)
    private val second = byteArrayOf(5, 6)
    private val hashes = listOf(hash(first), hash(second))
    private val entry = ArticleSyncManifestEntry(article.articleId, article.sourceDeviceId, article.contentHash, article.updatedAt,
        0, 0, 0, 0, bodyHash = hash(first + second), bodyByteCount = 6, chunkSize = 4, chunkHashes = hashes,
        metadataHash = ArticleSyncBody.metadataHashFor(article))
    private fun payload(chunks: List<SyncedArticleBodyChunk>) = SyncedChunkedArticle(article, entry.bodyHash, 6, 4, hashes, chunks)

    @Test fun restartRequestsOnlyMissingChunksAndReassemblesVerifiedPrefix() {
        val root = Files.createTempDirectory("article-checkpoint").toFile()
        try {
            var store = ArticleReceiveCheckpoint(root, "owner", "phone", listOf(entry))
            store.stage(payload(listOf(SyncedArticleBodyChunk(0, hashes[0], first))))
            store = ArticleReceiveCheckpoint(root, "owner", "phone", listOf(entry))
            assertEquals(listOf(1), store.remaining(SyncedArticleBodyRequest("article", entry.bodyHash, listOf(0, 1))).chunkIndexes)
            val full = store.stage(payload(listOf(SyncedArticleBodyChunk(1, hashes[1], second))))
            assertEquals(listOf(0, 1), full.chunks.map { it.index })
            assertArrayEquals(first, full.chunks[0].bytes)
            store.applied("article")
            assertEquals(listOf(0, 1), store.remaining(SyncedArticleBodyRequest("article", entry.bodyHash, listOf(0, 1))).chunkIndexes)
        } finally { root.deleteRecursively() }
    }
    @Test fun corruptPrefixAndOtherOwnerNeverCountAsReceived() {
        val root = Files.createTempDirectory("article-checkpoint").toFile()
        try {
            val store = ArticleReceiveCheckpoint(root, "owner", "phone", listOf(entry))
            store.stage(payload(listOf(SyncedArticleBodyChunk(0, hashes[0], first))))
            val request = SyncedArticleBodyRequest("article", entry.bodyHash, listOf(0, 1))
            assertEquals(listOf(0, 1), ArticleReceiveCheckpoint(root, "other-owner", "phone", listOf(entry)).remaining(request).chunkIndexes)
            root.walkTopDown().first { it.isFile }.writeBytes(byteArrayOf(9, 9, 9, 9))
            assertEquals(listOf(0, 1), store.remaining(request).chunkIndexes)
        } finally { root.deleteRecursively() }
    }
    @Test fun changedManifestAndBadChunkNeverReplaceCheckpoint() {
        val root = Files.createTempDirectory("article-checkpoint").toFile()
        try {
            val store = ArticleReceiveCheckpoint(root, "owner", "phone", listOf(entry))
            store.stage(payload(listOf(SyncedArticleBodyChunk(0, hashes[0], first))))
            assertThrows(IllegalArgumentException::class.java) { store.stage(payload(emptyList()).copy(article = article.copy(updatedAt = 3))) }
            assertThrows(IllegalArgumentException::class.java) { store.stage(payload(emptyList()).copy(article = article.copy(title = "changed"))) }
            assertThrows(IllegalArgumentException::class.java) { store.stage(payload(listOf(SyncedArticleBodyChunk(0, hashes[0], byteArrayOf(9, 9, 9, 9))))) }
            assertEquals(listOf(1), store.remaining(SyncedArticleBodyRequest("article", entry.bodyHash, listOf(0, 1))).chunkIndexes)
        } finally { root.deleteRecursively() }
    }
}
