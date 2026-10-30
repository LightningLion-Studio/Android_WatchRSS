package com.lightningstudio.watchrss.data.note

import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import org.jsoup.parser.Parser

class WatchNoteRepository(private val dao: WatchNoteDao) {
    private val mutationMutex = Mutex()
    fun observe(): Flow<List<WatchNoteEntity>> = dao.observeNotes()
    fun observeFolders(): Flow<List<WatchNoteFolderEntity>> = dao.observeFolders()
    fun observe(noteId: String): Flow<WatchNoteEntity?> = dao.observeNote(noteId)
    suspend fun all(): List<WatchNoteEntity> = dao.all()
    suspend fun retainedConflictCopyCount(): Int = mutationMutex.withLock {
        dao.all().count { !it.deleted && it.noteId.startsWith("conflict-") }
    }
    suspend fun merge(notes: List<WatchNoteEntity>, deviceId: String = "watch-merge"): Int = mutationMutex.withLock { mergeNotesUnlocked(notes, deviceId) }

    private suspend fun mergeNotesUnlocked(notes: List<WatchNoteEntity>, deviceId: String): Int {
        var changed = 0
        notes.forEach { remote ->
            val local = dao.get(remote.noteId)
            val merged = mergeNoteVersions(local, remote, deviceId)
            if (merged != null) {
                if (merged != local) { dao.upsert(listOf(merged)); changed++ }
            } else {
                // Stable identity makes retries idempotent and preserves edits to an existing conflict copy.
                val identity = listOf(remote.noteId, remote.markdown, remote.deleted.toString(), remote.updatedAt.toString(), remote.modifiedBy).joinToString("\u0000")
                val conflictId = "conflict-" + UUID.nameUUIDFromBytes(identity.toByteArray())
                if (dao.get(conflictId) == null) {
                    val label = if (remote.deleted) "远端删除冲突" else "冲突副本"
                    val copy = remote.copy(noteId = conflictId, title = "$label · ${remote.title}",
                        contentHash = watchNoteHash(remote.markdown), plainText = watchPlainText(remote.markdown),
                        baseMarkdown = remote.markdown, baseContentHash = watchNoteHash(remote.markdown), deleted = false, deletedAt = 0L)
                    dao.upsert(listOf(copy)); changed++
                }
            }
        }
        return changed
    }

    suspend fun cloudState(): NoteCloudState = mutationMutex.withLock { NoteCloudState(dao.all(), dao.allFolders()) }

    suspend fun applyCloudState(state: NoteCloudState, deviceId: String): Int = mutationMutex.withLock {
        val local = dao.allFolders().associateBy { it.folderId }
        val updates = state.folders.map { mergeCloudFolder(local[it.folderId], it) }.filter { it != local[it.folderId] }
        if (updates.isNotEmpty()) dao.upsertFolders(updates)
        mergeNotesUnlocked(state.notes, deviceId) + updates.size
    }

    suspend fun saveRawMarkdown(
        noteId: String?,
        rawMarkdown: String,
        deviceId: String,
        rawTitle: String? = null,
        now: Long = System.currentTimeMillis()
    ): WatchNoteEntity = mutationMutex.withLock {
        val markdown = rawMarkdown.replace("\r\n", "\n").replace('\r', '\n')
        val old = noteId?.let { dao.get(it) ?: error("笔记不存在") }
        require(old != null || markdown.isNotBlank() || !rawTitle.isNullOrBlank()) {
            "新建备忘录不能为空"
        }
        val hash = watchNoteHash(markdown)
        val version = nextNoteTimestamp(old?.updatedAt, now)
        WatchNoteEntity(
            noteId = old?.noteId ?: UUID.randomUUID().toString(),
            folderId = old?.folderId,
            title = rawTitle
                ?: old?.title?.takeIf(String::isNotBlank)
                ?: watchNoteTitle(markdown),
            markdown = markdown,
            plainText = watchPlainText(markdown),
            contentHash = hash,
            baseContentHash = old?.baseContentHash ?: hash,
            baseMarkdown = old?.baseMarkdown ?: markdown,
            pinned = old?.pinned ?: false,
            createdAt = old?.createdAt ?: now,
            updatedAt = version,
            modifiedBy = deviceId,
            deleted = false,
            deletedAt = 0L
        ).also { dao.upsert(listOf(it)) }
    }

    suspend fun move(
        noteId: String,
        folderId: String?,
        deviceId: String,
        now: Long = System.currentTimeMillis()
    ): WatchNoteEntity = updateMetadata(noteId, deviceId, now) { it.copy(folderId = folderId) }

    suspend fun setPinned(
        noteId: String,
        pinned: Boolean,
        deviceId: String,
        now: Long = System.currentTimeMillis()
    ): WatchNoteEntity = updateMetadata(noteId, deviceId, now) { it.copy(pinned = pinned) }

    suspend fun delete(
        noteId: String,
        deviceId: String,
        now: Long = System.currentTimeMillis()
    ): WatchNoteEntity = updateMetadata(noteId, deviceId, now) {
        it.copy(deleted = true, deletedAt = now)
    }

    private suspend fun updateMetadata(
        noteId: String,
        deviceId: String,
        now: Long,
        transform: (WatchNoteEntity) -> WatchNoteEntity
    ): WatchNoteEntity = mutationMutex.withLock {
        val local = dao.get(noteId) ?: error("笔记不存在")
        val version = nextNoteTimestamp(local.updatedAt, now)
        val transformed = transform(local)
        transformed.copy(updatedAt = version, modifiedBy = deviceId,
            deletedAt = if (transformed.deleted) maxOf(transformed.deletedAt, version) else transformed.deletedAt).also {
            dao.upsert(listOf(it))
        }
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
    val markdownImagesAsLabels = buildString {
        var cursor = 0
        watchMarkdownImageMatches(withoutHtml).forEach { match ->
            append(withoutHtml, cursor, match.range.first)
            val label = match.groupValues[1]
                .replace("\\\\", "\u0000")
                .replace("\\[", "[")
                .replace("\\]", "]")
                .replace("\u0000", "\\")
            append("[图片：").append(label).append(']')
            cursor = match.range.last + 1
        }
        append(withoutHtml, cursor, withoutHtml.length)
    }
    return markdownImagesAsLabels
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
