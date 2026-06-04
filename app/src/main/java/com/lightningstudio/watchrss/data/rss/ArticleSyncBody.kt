package com.lightningstudio.watchrss.data.rss

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class ArticleBodyMetadata(
    val bodyHash: String,
    val bodyByteCount: Long,
    val chunkSize: Int,
    val chunkHashes: List<String>,
    val metadataHash: String
)

data class ArticleBodyPayload(
    val metadata: ArticleBodyMetadata,
    val chunks: List<SyncedArticleBodyChunk>
)

object ArticleSyncBody {
    const val CHUNK_SIZE_BYTES = 128 * 1024
    private const val BODY_ENCODING_VERSION = 2

    fun metadataFor(article: SyncedSavedArticle): ArticleBodyMetadata {
        val output = BodyChunkOutputStream()
        writeEncodedBody(article.contentHtml, article.contentText, output)
        return output.metadata(metadataHashFor(article))
    }

    fun metadataHashFor(article: SyncedSavedArticle): String {
        val json = JSONObject().apply {
            put("bodyEncodingVersion", BODY_ENCODING_VERSION)
            put("articleId", article.articleId)
            put("sourceDeviceId", article.sourceDeviceId)
            put("url", article.url)
            put("title", article.title)
            put("siteName", article.siteName)
            put("excerpt", article.excerpt)
            put("imageUrl", article.imageUrl)
            put("importedAt", article.importedAt)
            put("updatedAt", article.updatedAt)
            put("independentSaved", article.independentSaved)
            put("independentChangedAt", article.independentChangedAt)
            put("independentSortOrder", article.independentSortOrder)
            put("rssSourceUrl", article.rssSourceUrl)
            put("rssSourceTitle", article.rssSourceTitle)
            put("favoriteSaved", article.favoriteSaved)
            put("favoriteChangedAt", article.favoriteChangedAt)
            put("favoriteSortOrder", article.favoriteSortOrder)
            put("watchLaterSaved", article.watchLaterSaved)
            put("watchLaterChangedAt", article.watchLaterChangedAt)
            put("watchLaterSortOrder", article.watchLaterSortOrder)
            put("deleted", article.deleted)
            put("deletedAt", article.deletedAt)
        }
        return sha256(json.toString().toByteArray(Charsets.UTF_8))
    }

    fun chunksForRequest(
        article: SyncedSavedArticle,
        request: SyncedArticleBodyRequest,
        cachedMetadata: ArticleBodyMetadata? = article.cachedBodyMetadata
    ): List<SyncedArticleBodyChunk> =
        payloadForRequest(article, request, cachedMetadata).chunks

    fun payloadForRequest(
        article: SyncedSavedArticle,
        request: SyncedArticleBodyRequest,
        cachedMetadata: ArticleBodyMetadata? = article.cachedBodyMetadata
    ): ArticleBodyPayload {
        cachedMetadata
            ?.takeIf { request.bodyHash.isBlank() || request.bodyHash == it.bodyHash }
            ?.let { metadata ->
                runCatching {
                    ArticleBodyPayload(
                        metadata = metadata,
                        chunks = chunksForRequestWithCachedMetadata(article, request, metadata)
                    )
                }.getOrNull()
            }
            ?.let { return it }

        val output = BodyChunkOutputStream(request.chunkIndexes.toSet())
        writeEncodedBody(article.contentHtml, article.contentText, output)
        val metadata = output.metadata(metadataHashFor(article))
        return ArticleBodyPayload(
            metadata = metadata,
            chunks = output.chunks()
        )
    }

    fun rebuildBody(
        localArticle: SyncedSavedArticle?,
        payload: SyncedChunkedArticle,
        localBodyHash: String
    ): Pair<String?, String> {
        if (localArticle != null && localBodyHash == payload.bodyHash) {
            return localArticle.contentHtml to localArticle.contentText
        }
        val localChunks = localArticle
            ?.let { encodeBody(it.contentHtml, it.contentText) }
            ?.let(::chunkBytes)
            .orEmpty()
        val sentByIndex = payload.chunks.associateBy { it.index }
        val rebuilt = payload.chunkHashes.mapIndexed { index, expectedHash ->
            val sent = sentByIndex[index]
            when {
                sent != null -> {
                    require(sent.hash == expectedHash && sha256(sent.bytes) == expectedHash) {
                        "同步正文分块校验失败：${payload.article.articleId}#$index"
                    }
                    sent.bytes
                }
                index in localChunks.indices && sha256(localChunks[index]) == expectedHash -> localChunks[index]
                else -> error("同步正文缺少分块：${payload.article.articleId}#$index")
            }
        }
        val totalBytes = rebuilt.sumOf { it.size }
        val out = ByteArrayOutputStream(totalBytes)
        rebuilt.forEach(out::write)
        val bodyBytes = out.toByteArray()
        require(sha256(bodyBytes) == payload.bodyHash) {
            "同步正文整体校验失败：${payload.article.articleId}"
        }
        return decodeBody(bodyBytes)
    }

    fun encodeChunkData(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    fun decodeChunkData(value: String): ByteArray =
        Base64.getDecoder().decode(value)

    private fun encodeBody(contentHtml: String?, contentText: String): ByteArray {
        val rawBody = JSONObject().apply {
            put("contentHtml", contentHtml)
            put("contentText", contentText)
        }.toString().toByteArray(Charsets.UTF_8)
        return gzip(rawBody)
    }

    private fun writeEncodedBody(contentHtml: String?, contentText: String, output: OutputStream) {
        GZIPOutputStream(output).use { gzip ->
            OutputStreamWriter(gzip, Charsets.UTF_8).use { writer ->
                writer.append('{')
                var needsComma = false
                if (contentHtml != null) {
                    writeJsonStringField(writer, "contentHtml", contentHtml)
                    needsComma = true
                }
                if (needsComma) writer.append(',')
                writeJsonStringField(writer, "contentText", contentText)
                writer.append('}')
            }
        }
    }

    private fun chunksForRequestWithCachedMetadata(
        article: SyncedSavedArticle,
        request: SyncedArticleBodyRequest,
        metadata: ArticleBodyMetadata
    ): List<SyncedArticleBodyChunk> {
        val output = CachedBodyChunkOutputStream(
            metadata = metadata,
            captureIndexes = request.chunkIndexes.toSet()
        )
        writeEncodedBody(article.contentHtml, article.contentText, output)
        return output.chunks()
    }

    private fun writeJsonStringField(writer: Writer, name: String, value: String) {
        writeJsonString(writer, name)
        writer.append(':')
        writeJsonString(writer, value)
    }

    private fun writeJsonString(writer: Writer, value: String) {
        writer.append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> writer.append("\\\\")
                '"' -> writer.append("\\\"")
                '\b' -> writer.append("\\b")
                '\u000C' -> writer.append("\\f")
                '\n' -> writer.append("\\n")
                '\r' -> writer.append("\\r")
                '\t' -> writer.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        writer.append("\\u")
                        writer.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        writer.append(char)
                    }
                }
            }
        }
        writer.append('"')
    }

    private fun decodeBody(bytes: ByteArray): Pair<String?, String> {
        val rawBody = runCatching { gunzip(bytes) }.getOrElse { bytes }
        val json = JSONObject(rawBody.toString(Charsets.UTF_8))
        return json.optString("contentHtml").ifBlank { null } to json.optString("contentText")
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(bytes)
        }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private fun chunkBytes(bytes: ByteArray): List<ByteArray> {
        if (bytes.isEmpty()) return listOf(ByteArray(0))
        val chunks = ArrayList<ByteArray>((bytes.size + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES)
        var start = 0
        while (start < bytes.size) {
            val end = (start + CHUNK_SIZE_BYTES).coerceAtMost(bytes.size)
            chunks += bytes.copyOfRange(start, end)
            start = end
        }
        return chunks
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private class BodyChunkOutputStream(
        private val captureIndexes: Set<Int> = emptySet()
    ) : OutputStream() {
        private val bodyDigest = MessageDigest.getInstance("SHA-256")
        private var chunkDigest = MessageDigest.getInstance("SHA-256")
        private val chunkBuffer = ByteArrayOutputStream(CHUNK_SIZE_BYTES)
        private val chunkHashes = mutableListOf<String>()
        private val chunks = mutableListOf<SyncedArticleBodyChunk>()
        private var chunkIndex = 0
        private var chunkByteCount = 0
        private var bodyByteCount = 0L
        private var bodyHash: String? = null

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val count = minOf(remaining, CHUNK_SIZE_BYTES - chunkByteCount)
                bodyDigest.update(b, offset, count)
                chunkDigest.update(b, offset, count)
                chunkBuffer.write(b, offset, count)
                chunkByteCount += count
                bodyByteCount += count
                offset += count
                remaining -= count
                if (chunkByteCount == CHUNK_SIZE_BYTES) {
                    finishChunk()
                }
            }
        }

        fun metadata(metadataHash: String): ArticleBodyMetadata {
            finish()
            return ArticleBodyMetadata(
                bodyHash = bodyHash.orEmpty(),
                bodyByteCount = bodyByteCount,
                chunkSize = CHUNK_SIZE_BYTES,
                chunkHashes = chunkHashes.toList(),
                metadataHash = metadataHash
            )
        }

        fun chunks(): List<SyncedArticleBodyChunk> {
            finish()
            return chunks.toList()
        }

        private fun finish() {
            if (bodyHash != null) return
            if (chunkByteCount > 0 || bodyByteCount == 0L) {
                finishChunk()
            }
            bodyHash = bodyDigest.digest().toHexString()
        }

        private fun finishChunk() {
            val hash = chunkDigest.digest().toHexString()
            chunkHashes += hash
            if (chunkIndex in captureIndexes) {
                chunks += SyncedArticleBodyChunk(
                    index = chunkIndex,
                    hash = hash,
                    bytes = chunkBuffer.toByteArray()
                )
            }
            chunkIndex += 1
            chunkByteCount = 0
            chunkBuffer.reset()
            chunkDigest = MessageDigest.getInstance("SHA-256")
        }
    }

    private class CachedBodyChunkOutputStream(
        private val metadata: ArticleBodyMetadata,
        private val captureIndexes: Set<Int>
    ) : OutputStream() {
        private var chunkBuffer: ByteArrayOutputStream? = newChunkBuffer(0)
        private val chunks = mutableListOf<SyncedArticleBodyChunk>()
        private var chunkIndex = 0
        private var chunkByteCount = 0
        private var bodyByteCount = 0L
        private var finished = false

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val count = minOf(remaining, CHUNK_SIZE_BYTES - chunkByteCount)
                chunkBuffer?.write(b, offset, count)
                chunkByteCount += count
                bodyByteCount += count
                offset += count
                remaining -= count
                if (chunkByteCount == CHUNK_SIZE_BYTES) {
                    finishChunk()
                }
            }
        }

        fun chunks(): List<SyncedArticleBodyChunk> {
            finish()
            return chunks.toList()
        }

        private fun finish() {
            if (finished) return
            if (chunkByteCount > 0) {
                finishChunk()
            }
            require(bodyByteCount == metadata.bodyByteCount) {
                "同步正文缓存大小不匹配：expected=${metadata.bodyByteCount} actual=$bodyByteCount"
            }
            require(chunkIndex == metadata.chunkHashes.size) {
                "同步正文缓存分块数不匹配：expected=${metadata.chunkHashes.size} actual=$chunkIndex"
            }
            finished = true
        }

        private fun finishChunk() {
            val bytes = chunkBuffer?.toByteArray()
            if (bytes != null) {
                val hash = metadata.chunkHashes.getOrNull(chunkIndex)
                    ?: error("同步正文缓存缺少分块哈希：$chunkIndex")
                chunks += SyncedArticleBodyChunk(
                    index = chunkIndex,
                    hash = hash,
                    bytes = bytes
                )
            }
            chunkIndex += 1
            chunkByteCount = 0
            chunkBuffer = newChunkBuffer(chunkIndex)
        }

        private fun newChunkBuffer(index: Int): ByteArrayOutputStream? {
            return if (index in captureIndexes) {
                ByteArrayOutputStream(CHUNK_SIZE_BYTES)
            } else {
                null
            }
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}
