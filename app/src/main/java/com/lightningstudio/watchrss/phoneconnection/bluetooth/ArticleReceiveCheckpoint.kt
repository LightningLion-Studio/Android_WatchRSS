package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.rss.ArticleSyncBody
import com.lightningstudio.watchrss.data.rss.SyncedArticleBodyChunk
import com.lightningstudio.watchrss.data.rss.SyncedArticleBodyRequest
import com.lightningstudio.watchrss.data.rss.SyncedChunkedArticle
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Only verified chunks belong to a checkpoint. A frozen manifest binds metadata and content. */
internal class ArticleReceiveCheckpoint(private val root: File, private val owner: String, private val peer: String,
    manifest: List<ArticleSyncManifestEntry>) {
    private val entries = manifest.associateBy { it.articleId }
    init { require(entries.size == manifest.size) { "同步清单包含重复文章" } }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun directory(entry: ArticleSyncManifestEntry): File = File(root, digest((owner + "|" + peer).toByteArray()) + "/" +
        digest(listOf(entry.articleId, entry.bodyHash, entry.bodyByteCount, entry.chunkSize,
            entry.chunkHashes.joinToString(","), entry.metadataHash, entry.updatedAt).joinToString("|").toByteArray()))
    private fun chunk(entry: ArticleSyncManifestEntry, index: Int): File = File(directory(entry), "$index.bin")
    private fun verified(entry: ArticleSyncManifestEntry, index: Int): ByteArray? {
        require(index in entry.chunkHashes.indices)
        val file = chunk(entry, index)
        if (!file.isFile) return null
        val expectedSize = minOf(entry.chunkSize.toLong(), entry.bodyByteCount - index.toLong() * entry.chunkSize)
        if (expectedSize !in 0..ArticleSyncBody.CHUNK_SIZE_BYTES.toLong() || file.length() != expectedSize) return null
        return file.readBytes().takeIf { digest(it) == entry.chunkHashes[index] }
    }
    fun remaining(request: SyncedArticleBodyRequest): SyncedArticleBodyRequest {
        val entry = entries[request.articleId] ?: error("同步文章不在清单中")
        require(entry.bodyHash == request.bodyHash) { "同步清单发生变化" }
        if (request.metadataOnly) return request
        return request.copy(chunkIndexes = request.chunkIndexes.filter { verified(entry, it) == null })
    }
    fun stage(payload: SyncedChunkedArticle): SyncedChunkedArticle {
        val entry = entries[payload.article.articleId] ?: error("同步文章不在清单中")
        require(entry.contentHash == payload.article.contentHash && entry.updatedAt == payload.article.updatedAt &&
            entry.deleted == payload.article.deleted && entry.deletedAt == payload.article.deletedAt) { "同步清单发生变化，请重试" }
        require(entry.metadataHash.isBlank() || entry.metadataHash == ArticleSyncBody.metadataHashFor(payload.article)) { "同步清单元数据发生变化，请重试" }
        if (payload.metadataOnly || payload.article.deleted) return payload
        require(entry.bodyHash == payload.bodyHash && entry.bodyByteCount == payload.bodyByteCount &&
            entry.chunkSize == payload.chunkSize && entry.chunkHashes == payload.chunkHashes) { "同步清单正文发生变化，请重试" }
        require(payload.chunkSize in 1..ArticleSyncBody.CHUNK_SIZE_BYTES && payload.bodyByteCount in 0..Int.MAX_VALUE.toLong())
        require(payload.chunkHashes.size.toLong() == (payload.bodyByteCount + payload.chunkSize - 1) / payload.chunkSize)
        require(payload.chunks.map { it.index }.distinct().size == payload.chunks.size) { "同步正文包含重复分块" }
        for (incoming in payload.chunks) {
            require(incoming.index in entry.chunkHashes.indices && incoming.hash == entry.chunkHashes[incoming.index] &&
                incoming.bytes.size.toLong() == minOf(entry.chunkSize.toLong(), entry.bodyByteCount - incoming.index.toLong() * entry.chunkSize) &&
                digest(incoming.bytes) == incoming.hash) { "同步正文分块校验失败" }
            val file = chunk(entry, incoming.index)
            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            val temporary = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(temporary).use { it.write(incoming.bytes); it.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        return payload.copy(chunks = entry.chunkHashes.indices.mapNotNull { index ->
            verified(entry, index)?.let { SyncedArticleBodyChunk(index, entry.chunkHashes[index], it) }
        })
    }
    /** Called only after the repository's verified rebuild and durable merge return. */
    fun applied(articleId: String) { entries[articleId]?.let { directory(it).deleteRecursively() } }
}
