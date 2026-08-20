package com.lightningstudio.watchrss.data.rss

import org.json.JSONObject
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
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
    private const val BODY_ENCODING_VERSION = 3
    private const val MAX_DECOMPRESSED_TEXT_BYTES = 32 * 1024 * 1024
    private const val JSON_WRITE_BUFFER_CHARS = 16 * 1024
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

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
            BufferedWriter(
                OutputStreamWriter(gzip, Charsets.UTF_8),
                JSON_WRITE_BUFFER_CHARS
            ).use { writer ->
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
        writer.write('"'.code)
        var runStart = 0
        value.forEachIndexed { index, char ->
            val escape = when (char) {
                '\\' -> "\\\\"
                '"' -> "\\\""
                '\b' -> "\\b"
                '\u000C' -> "\\f"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                else -> null
            }
            if (escape != null || char.code < 0x20) {
                if (runStart < index) {
                    writer.write(value, runStart, index - runStart)
                }
                if (escape != null) {
                    writer.write(escape)
                } else {
                    writer.write("\\u00")
                    writer.write(HEX_DIGITS[char.code ushr 4].code)
                    writer.write(HEX_DIGITS[char.code and 0x0f].code)
                }
                runStart = index + 1
            }
        }
        if (runStart < value.length) {
            writer.write(value, runStart, value.length - runStart)
        }
        writer.write('"'.code)
    }

    private fun decodeBody(bytes: ByteArray): Pair<String?, String> {
        val input = runCatching<InputStream> {
            GZIPInputStream(ByteArrayInputStream(bytes))
        }.getOrElse {
            ByteArrayInputStream(bytes)
        }
        return input.use { stream ->
            decodeBodyJson(
                InputStreamReader(
                    LimitedInputStream(stream, MAX_DECOMPRESSED_TEXT_BYTES),
                    Charsets.UTF_8
                )
            )
        }
    }

    private class LimitedInputStream(
        private val upstream: InputStream,
        private val maxBytes: Int
    ) : InputStream() {
        private var totalBytes = 0

        override fun read(): Int {
            val value = upstream.read()
            if (value >= 0) count(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = upstream.read(buffer, offset, length)
            if (read > 0) count(read)
            return read
        }

        private fun count(bytes: Int) {
            totalBytes += bytes
            if (totalBytes > maxBytes) {
                throw IllegalArgumentException("解压内容过大")
            }
        }
    }

    private fun decodeBodyJson(reader: Reader): Pair<String?, String> {
        val cursor = BodyJsonCursor(reader)
        var contentHtml: String? = null
        var contentText = ""
        cursor.expectObjectStart()
        var firstField = true
        while (true) {
            val marker = cursor.nextNonWhitespace()
            if (marker == -1 || marker.toChar() == '}') break
            if (!firstField) {
                require(marker.toChar() == ',') { "同步正文JSON格式错误" }
            } else {
                cursor.unread(marker)
            }
            val name = cursor.readName()
            cursor.expect(':')
            val value = cursor.readNullableString()
            when (name) {
                "contentHtml" -> contentHtml = value?.ifBlank { null }
                "contentText" -> contentText = value.orEmpty()
            }
            firstField = false
        }
        return contentHtml to contentText
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(bytes)
        }
        return out.toByteArray()
    }

    private class BodyJsonCursor(
        private val reader: Reader
    ) {
        private var buffered: Int = NO_BUFFER

        fun expectObjectStart() {
            expect('{')
        }

        fun expect(expected: Char) {
            val actual = nextNonWhitespace()
            require(actual == expected.code) { "同步正文JSON格式错误" }
        }

        fun readName(): String {
            val marker = nextNonWhitespace()
            require(marker == '"'.code) { "同步正文JSON字段格式错误" }
            return readStringBody()
        }

        fun readNullableString(): String? {
            return when (val marker = nextNonWhitespace()) {
                '"'.code -> readStringBody()
                'n'.code -> {
                    expectLiteral("ull")
                    null
                }
                else -> error("同步正文JSON值格式错误：$marker")
            }
        }

        fun nextNonWhitespace(): Int {
            while (true) {
                val char = read()
                if (char == -1 || !char.toChar().isWhitespace()) return char
            }
        }

        fun unread(char: Int) {
            buffered = char
        }

        private fun readStringBody(): String {
            val builder = StringBuilder()
            while (true) {
                val char = read()
                require(char != -1) { "同步正文JSON字符串未结束" }
                when (char.toChar()) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(readEscapedChar())
                    else -> builder.append(char.toChar())
                }
            }
        }

        private fun readEscapedChar(): Char {
            val escaped = read()
            require(escaped != -1) { "同步正文JSON转义未结束" }
            return when (escaped.toChar()) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> readUnicodeEscape()
                else -> error("同步正文JSON转义格式错误：${escaped.toChar()}")
            }
        }

        private fun readUnicodeEscape(): Char {
            var value = 0
            repeat(4) {
                val char = read()
                require(char != -1) { "同步正文JSON Unicode转义未结束" }
                value = (value shl 4) + char.toChar().digitToInt(16)
            }
            return value.toChar()
        }

        private fun expectLiteral(value: String) {
            value.forEach { expected ->
                require(read() == expected.code) { "同步正文JSON字面量格式错误" }
            }
        }

        private fun read(): Int {
            if (buffered != NO_BUFFER) {
                val char = buffered
                buffered = NO_BUFFER
                return char
            }
            return reader.read()
        }

        private companion object {
            const val NO_BUFFER = -2
        }
    }

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
        return digest.toHexString()
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
        private var chunkDigest = MessageDigest.getInstance("SHA-256")
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
                chunkDigest.update(b, offset, count)
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
            val expectedHash = metadata.chunkHashes.getOrNull(chunkIndex)
                ?: error("同步正文缓存缺少分块哈希：$chunkIndex")
            require(chunkDigest.digest().toHexString() == expectedHash) {
                "同步正文缓存分块校验失败：$chunkIndex"
            }
            val bytes = chunkBuffer?.toByteArray()
            if (bytes != null) {
                chunks += SyncedArticleBodyChunk(
                    index = chunkIndex,
                    hash = expectedHash,
                    bytes = bytes
                )
            }
            chunkIndex += 1
            chunkByteCount = 0
            chunkDigest = MessageDigest.getInstance("SHA-256")
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

    private fun ByteArray.toHexString(): String {
        val encoded = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            encoded[index * 2] = HEX_DIGITS[value ushr 4]
            encoded[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
        }
        return String(encoded)
    }
}
