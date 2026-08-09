package com.lightningstudio.watchrss.data.note

import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.jsoup.parser.Parser

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

    suspend fun saveRawMarkdown(
        noteId: String?,
        rawMarkdown: String,
        deviceId: String,
        now: Long = System.currentTimeMillis()
    ): WatchNoteEntity {
        val markdown = rawMarkdown.replace("\r\n", "\n").replace('\r', '\n')
        val old = noteId?.let { dao.get(it) ?: error("笔记不存在") }
        require(old != null || markdown.isNotBlank()) { "新建备忘录不能为空" }
        val hash = watchNoteHash(markdown)
        return WatchNoteEntity(
            noteId = old?.noteId ?: UUID.randomUUID().toString(),
            folderId = old?.folderId,
            title = old?.title?.takeIf(String::isNotBlank) ?: watchNoteTitle(markdown),
            markdown = markdown,
            plainText = watchPlainText(markdown),
            contentHash = hash,
            baseContentHash = old?.baseContentHash ?: hash,
            baseMarkdown = old?.baseMarkdown ?: markdown,
            pinned = old?.pinned ?: false,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            modifiedBy = deviceId,
            deleted = false,
            deletedAt = 0L
        ).also { dao.upsert(listOf(it)) }
    }
}

fun watchPlainText(markdown: String): String {
    val withoutHtml = markdown
        .replace(Regex("(?is)<img\\b[^>]*alt=[\"']([^\"']*)[\"'][^>]*>"), "[图片：$1]")
        .replace(Regex("(?is)<img\\b[^>]*>"), "[图片]")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)<li(?:\\s[^>]*)?>"), "- ")
        .replace(Regex("(?i)</(?:p|div|h[1-6]|li|ul|ol|blockquote|table|tr)>"), "\n")
        .replace(Regex("(?i)</?(?:p|div|span|b|strong|i|em|u|s|strike|del|ol|ul|li|br|h[1-6]|blockquote|code|a|img|mark|table|thead|tbody|tr|th|td)(?:\\s[^>]*)?>"), "")
        .let { Parser.unescapeEntities(it, false) }
    return withoutHtml
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "[图片：$1]")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace(Regex("(?m)^>\\s?"), "")
        .replace(Regex("(?m)^\\s*([-*+] |\\d+\\. )"), "")
        .replace("**", "")
        .replace("__", "")
        .replace("~~", "")
        .replace("`", "")
        .lineSequence()
        .map(String::trimEnd)
        .filterNot(String::isBlank)
        .joinToString("\n")
        .trim()
}

fun watchNoteTitle(markdown: String): String = watchPlainText(markdown)
    .lineSequence()
    .firstOrNull()
    .orEmpty()
    .ifBlank { "未命名笔记" }
    .take(80)

fun watchNoteHash(markdown: String): String = MessageDigest.getInstance("SHA-256").digest(markdown.toByteArray()).joinToString("") { "%02x".format(it) }
