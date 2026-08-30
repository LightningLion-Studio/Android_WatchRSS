package com.lightningstudio.watchrss.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class ArticleSyncBodyCodecTest {
    @Test
    fun canonicalBodyEncoding_roundTripsWithoutHashDrift() {
        val bodies = listOf(
            null to "纯文本正文",
            "" to "",
            "<article><p>normal</p></article>" to "normal",
            "<article lang=\"zh\">你好 😀</article>" to "多语言 Ελληνικά العربية 😀\n第二行"
        )

        bodies.forEachIndexed { index, (contentHtml, contentText) ->
            val article = article("round-trip-$index", contentHtml, contentText)
            val sourceMetadata = ArticleSyncBody.metadataFor(article)
            val response = ArticleSyncBody.payloadForRequest(
                article,
                SyncedArticleBodyRequest(article.articleId, bodyHash = "", chunkIndexes = emptyList())
            )
            val rebuilt = ArticleSyncBody.rebuildBody(
                localArticle = null,
                payload = response.toChunkedArticle(article),
                localBodyHash = ""
            )
            val rebuiltArticle = article.copy(contentHtml = rebuilt.first, contentText = rebuilt.second)
            val rebuiltMetadata = ArticleSyncBody.metadataFor(rebuiltArticle)
            val rebuiltResponse = ArticleSyncBody.payloadForRequest(
                rebuiltArticle,
                SyncedArticleBodyRequest(article.articleId, bodyHash = "", chunkIndexes = emptyList())
            )

            assertEquals(contentHtml, rebuilt.first)
            assertEquals(contentText, rebuilt.second)
            assertEquals(sourceMetadata.bodyHash, rebuiltMetadata.bodyHash)
            assertEquals(sourceMetadata.bodyByteCount, rebuiltMetadata.bodyByteCount)
            assertEquals(sourceMetadata.chunkHashes, rebuiltMetadata.chunkHashes)
            assertEquals(response.chunks.size, rebuiltResponse.chunks.size)
            response.chunks.zip(rebuiltResponse.chunks).forEach { (source, reencoded) ->
                assertTrue(source.bytes.contentEquals(reencoded.bytes))
            }
        }
    }

    @Test
    fun decoder_rejectsTruncatedAndTrailingJsonDocuments() {
        listOf(
            "{\"contentText\":\"unfinished\"",
            "{\"contentText\":\"ok\"} trailing",
            "{\"contentText\":\"first\"}{\"contentText\":\"second\"}"
        ).forEach { json ->
            assertDecodeRejected(json)
        }
    }

    @Test
    fun decoder_rejectsWrongContentFieldTypes() {
        listOf(
            "{\"contentHtml\":7,\"contentText\":\"ok\"}",
            "{\"contentHtml\":{},\"contentText\":\"ok\"}",
            "{\"contentHtml\":null,\"contentText\":false}",
            "{\"contentText\":[]}"
        ).forEach { json ->
            assertDecodeRejected(json)
        }
    }

    @Test
    fun decoder_skipsUnknownValidJsonValuesForForwardCompatibility() {
        val payload = payloadForJson(
            """{"future":{"nested":[1,true,null,{"value":"ok"}]},"contentHtml":"","contentText":"正文"}"""
        )

        val rebuilt = ArticleSyncBody.rebuildBody(null, payload, localBodyHash = "")

        assertEquals("", rebuilt.first)
        assertEquals("正文", rebuilt.second)
    }

    private fun assertDecodeRejected(json: String) {
        try {
            ArticleSyncBody.rebuildBody(null, payloadForJson(json), localBodyHash = "")
            fail("Expected body JSON to be rejected: $json")
        } catch (expected: RuntimeException) {
            assertTrue(expected.message.orEmpty().contains("同步正文JSON"))
        }
    }

    private fun payloadForJson(json: String): SyncedChunkedArticle {
        val bytes = gzip(json.toByteArray(Charsets.UTF_8))
        val hash = sha256(bytes)
        return SyncedChunkedArticle(
            article = article("malformed", null, "placeholder"),
            bodyHash = hash,
            bodyByteCount = bytes.size.toLong(),
            chunkSize = ArticleSyncBody.CHUNK_SIZE_BYTES,
            chunkHashes = listOf(hash),
            chunks = listOf(SyncedArticleBodyChunk(index = 0, hash = hash, bytes = bytes))
        )
    }

    private fun ArticleBodyPayload.toChunkedArticle(article: SyncedSavedArticle) = SyncedChunkedArticle(
        article = article,
        bodyHash = metadata.bodyHash,
        bodyByteCount = metadata.bodyByteCount,
        chunkSize = metadata.chunkSize,
        chunkHashes = metadata.chunkHashes,
        chunks = chunks
    )

    private fun article(
        articleId: String,
        contentHtml: String?,
        contentText: String
    ) = SyncedSavedArticle(
        articleId = articleId,
        sourceDeviceId = "watch",
        url = "https://example.com/$articleId",
        title = articleId,
        siteName = "example.com",
        excerpt = "",
        contentHtml = contentHtml,
        contentText = contentText,
        imageUrl = null,
        contentHash = "content-$articleId",
        importedAt = 1L,
        updatedAt = 2L,
        favoriteSaved = false,
        favoriteChangedAt = 0L,
        favoriteSortOrder = 0L,
        watchLaterSaved = false,
        watchLaterChangedAt = 0L,
        watchLaterSortOrder = 0L,
        deleted = false,
        deletedAt = 0L
    )

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
