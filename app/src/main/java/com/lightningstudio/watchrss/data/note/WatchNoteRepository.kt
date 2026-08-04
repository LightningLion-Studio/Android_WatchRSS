package com.lightningstudio.watchrss.data.note

import java.security.MessageDigest

class WatchNoteRepository(private val dao: WatchNoteDao) {
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
}

fun watchPlainText(markdown: String): String = markdown
    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "[图片：$1]")
    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("(?m)^#{1,6}\\s+"), "").replace("**", "").replace("__", "").replace("~~", "").replace("`", "")
    .trim()

fun watchNoteHash(markdown: String): String = MessageDigest.getInstance("SHA-256").digest(markdown.toByteArray()).joinToString("") { "%02x".format(it) }
