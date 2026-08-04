package com.lightningstudio.watchrss.data.note

import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow

class WatchNoteRepository(private val dao: WatchNoteDao) {
    fun observe(): Flow<List<WatchNoteEntity>> = dao.observeNotes()
    suspend fun all(): List<WatchNoteEntity> = dao.all()
    suspend fun merge(notes: List<WatchNoteEntity>): Int {
        var changed = 0
        notes.forEach { remote ->
            val local = dao.get(remote.noteId)
            if (local == null || remote.updatedAt >= local.updatedAt || local.contentHash == local.baseContentHash) {
                dao.upsert(listOf(remote)); changed++
            }
        }
        return changed
    }

    suspend fun savePlainText(
        noteId: String,
        text: String,
        deviceId: String,
        now: Long = System.currentTimeMillis()
    ): WatchNoteEntity {
        val old = dao.get(noteId) ?: error("笔记不存在")
        val markdown = replaceWatchTextKeepingImages(old.markdown, text)
        return old.copy(
            markdown = markdown,
            plainText = watchPlainText(markdown),
            contentHash = watchNoteHash(markdown),
            baseContentHash = old.contentHash,
            baseMarkdown = old.markdown,
            updatedAt = now,
            modifiedBy = deviceId,
            deleted = false,
            deletedAt = 0L
        ).also { dao.upsert(listOf(it)) }
    }
}

fun watchPlainText(markdown: String): String = markdown
    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "[图片：$1]")
    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("(?m)^#{1,6}\\s+"), "").replace("**", "").replace("__", "").replace("~~", "").replace("`", "")
    .trim()

fun watchNoteHash(markdown: String): String = MessageDigest.getInstance("SHA-256").digest(markdown.toByteArray()).joinToString("") { "%02x".format(it) }

private fun replaceWatchTextKeepingImages(markdown: String, text: String): String {
    val images = Regex("!\\[([^]]*)]\\([^)]*\\)").findAll(markdown).associate { match ->
        "[图片：${match.groupValues[1]}]" to match.value
    }
    return Regex("\\[图片：[^]]*]").replace(text.trim()) { match -> images[match.value] ?: match.value }
}
