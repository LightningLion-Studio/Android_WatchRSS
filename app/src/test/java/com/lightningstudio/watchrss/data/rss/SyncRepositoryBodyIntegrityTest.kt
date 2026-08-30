package com.lightningstudio.watchrss.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryBodyIntegrityTest {
    @Test
    fun unavailableManifestDoesNotPublishReusableBodyMetadata() {
        val sanitized = SyncedArticleManifest(
            articleId = "article-1",
            sourceDeviceId = "watch",
            contentHash = "content-hash",
            updatedAt = 2L,
            independentChangedAt = 0L,
            favoriteChangedAt = 2L,
            watchLaterChangedAt = 0L,
            deletedAt = 0L,
            bodyHash = "stale-body-hash",
            bodyByteCount = 123L,
            chunkSize = ArticleSyncBody.CHUNK_SIZE_BYTES,
            chunkHashes = listOf("stale-chunk-hash"),
            metadataHash = "stale-metadata-hash",
            bodyAvailable = false
        ).withoutUnavailableBodyMetadata()

        assertFalse(sanitized.bodyAvailable)
        assertEquals("", sanitized.bodyHash)
        assertEquals(0L, sanitized.bodyByteCount)
        assertEquals(0, sanitized.chunkSize)
        assertEquals(emptyList<String>(), sanitized.chunkHashes)
        assertEquals("", sanitized.metadataHash)
    }

    @Test
    fun metadataOnlyRejectsMissingLocalArticle() {
        val payload = metadataOnlyPayload(article(contentText = "remote body"))

        assertThrows(IllegalArgumentException::class.java) {
            requireMetadataOnlyLocalBody(
                payload = payload,
                localArticle = null,
                localBodyAvailable = false,
                localHasStoredBody = false
            )
        }
    }

    @Test
    fun metadataOnlyRejectsUnavailableExternalBody() {
        val remote = article(contentText = "same body")
        val payload = metadataOnlyPayload(remote)

        assertThrows(IllegalArgumentException::class.java) {
            requireMetadataOnlyLocalBody(
                payload = payload,
                localArticle = remote.copy(sourceDeviceId = "watch"),
                localBodyAvailable = false,
                localHasStoredBody = true
            )
        }
    }

    @Test
    fun metadataOnlyRejectsExcerptWithoutStoredBody() {
        val remote = article(contentText = "summary used as body")
        val payload = metadataOnlyPayload(remote)

        assertThrows(IllegalArgumentException::class.java) {
            requireMetadataOnlyLocalBody(
                payload = payload,
                localArticle = remote.copy(sourceDeviceId = "watch"),
                localBodyAvailable = true,
                localHasStoredBody = false
            )
        }
    }

    @Test
    fun metadataOnlyRejectsDifferentActualLocalBody() {
        val payload = metadataOnlyPayload(article(contentText = "remote body"))

        assertThrows(IllegalArgumentException::class.java) {
            requireMetadataOnlyLocalBody(
                payload = payload,
                localArticle = article(sourceDeviceId = "watch", contentText = "local body"),
                localBodyAvailable = true,
                localHasStoredBody = true
            )
        }
    }

    @Test
    fun metadataOnlyUsesActualLocalBodyAndRemoteMetadataHash() {
        val remote = article(title = "new title", contentText = "same body")
        val local = remote.copy(sourceDeviceId = "watch", title = "old title")
        val payload = metadataOnlyPayload(remote)

        val verified = requireMetadataOnlyLocalBody(
            payload = payload,
            localArticle = local,
            localBodyAvailable = true,
            localHasStoredBody = true
        )

        val actualLocal = ArticleSyncBody.metadataFor(local)
        assertEquals(actualLocal.bodyHash, verified.bodyHash)
        assertEquals(actualLocal.bodyByteCount, verified.bodyByteCount)
        assertEquals(actualLocal.chunkSize, verified.chunkSize)
        assertEquals(actualLocal.chunkHashes, verified.chunkHashes)
        assertEquals(ArticleSyncBody.metadataHashFor(remote), verified.metadataHash)
    }

    @Test
    fun fullPayloadBoundaryRejectsBodyThatDoesNotMatchManifest() {
        val remote = article(contentText = "remote body")
        val payload = metadataOnlyPayload(remote).copy(metadataOnly = false)

        assertThrows(IllegalArgumentException::class.java) {
            verifiedPayloadBodyMetadata(
                articleWithActualBody = remote.copy(contentText = "rebuilt wrong body"),
                payload = payload
            )
        }
    }

    @Test
    fun tombstoneManifestUsesActualCanonicalBodyAndFinalMetadataHash() {
        val tombstone = article(contentText = "").copy(
            sourceDeviceId = "watch",
            updatedAt = 30L,
            favoriteSaved = false,
            favoriteChangedAt = 30L,
            deleted = true,
            deletedAt = 30L
        )

        val manifest = tombstone.toVerifiedManifest()
        val actual = ArticleSyncBody.metadataFor(tombstone)

        assertTrue(manifest.deleted)
        assertTrue(manifest.bodyAvailable)
        assertEquals(actual.bodyHash, manifest.bodyHash)
        assertEquals(actual.bodyByteCount, manifest.bodyByteCount)
        assertEquals(actual.chunkSize, manifest.chunkSize)
        assertEquals(actual.chunkHashes, manifest.chunkHashes)
        assertEquals(ArticleSyncBody.metadataHashFor(tombstone), manifest.metadataHash)
    }

    @Test
    fun metadataOnlyTombstoneUsesVerifiedLocalBodyAndFinalMetadataHash() {
        val local = article(sourceDeviceId = "watch", contentText = "local body")
        val remote = local.copy(
            sourceDeviceId = "phone",
            contentHtml = null,
            contentText = "",
            updatedAt = 30L,
            favoriteSaved = false,
            favoriteChangedAt = 30L,
            deleted = true,
            deletedAt = 30L
        )
        val metadata = ArticleSyncBody.metadataFor(local)
        val payload = SyncedChunkedArticle(
            article = remote,
            bodyHash = metadata.bodyHash,
            bodyByteCount = metadata.bodyByteCount,
            chunkSize = metadata.chunkSize,
            chunkHashes = metadata.chunkHashes,
            chunks = emptyList(),
            metadataOnly = true
        )

        val retained = requireMetadataOnlyLocalBody(
            payload = payload,
            localArticle = local,
            localBodyAvailable = true,
            localHasStoredBody = true
        )

        assertEquals(metadata.bodyHash, retained.bodyHash)
        assertEquals(metadata.bodyByteCount, retained.bodyByteCount)
        assertEquals(metadata.chunkHashes, retained.chunkHashes)
        assertEquals(ArticleSyncBody.metadataHashFor(remote), retained.metadataHash)
    }

    private fun metadataOnlyPayload(article: SyncedSavedArticle): SyncedChunkedArticle {
        val metadata = ArticleSyncBody.metadataFor(article)
        return SyncedChunkedArticle(
            article = article.copy(contentHtml = null, contentText = ""),
            bodyHash = metadata.bodyHash,
            bodyByteCount = metadata.bodyByteCount,
            chunkSize = metadata.chunkSize,
            chunkHashes = metadata.chunkHashes,
            chunks = emptyList(),
            metadataOnly = true
        )
    }

    private fun article(
        sourceDeviceId: String = "phone",
        title: String = "Title",
        contentText: String
    ): SyncedSavedArticle {
        return SyncedSavedArticle(
            articleId = "article-1",
            sourceDeviceId = sourceDeviceId,
            url = "https://example.com/article-1",
            title = title,
            siteName = "Example",
            excerpt = "Summary",
            contentHtml = null,
            contentText = contentText,
            imageUrl = null,
            contentHash = "content-hash",
            importedAt = 1L,
            updatedAt = 2L,
            favoriteSaved = true,
            favoriteChangedAt = 2L,
            favoriteSortOrder = 2L,
            watchLaterSaved = false,
            watchLaterChangedAt = 0L,
            watchLaterSortOrder = 0L,
            deleted = false,
            deletedAt = 0L
        )
    }
}
