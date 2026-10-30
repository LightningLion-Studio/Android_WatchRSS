package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import com.lightningstudio.watchrss.data.account.workspace.libraryContext
import com.lightningstudio.watchrss.data.note.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal data class WatchCloudNotesPayload(val state: ByteArray, val archive: ByteArray?)

/** The same authenticated JSON + portable Markdown ZIP objects used by the phone. */
internal class WatchCloudNotesAdapter(context: Context, private val repository: WatchNoteRepository, private val deviceId: String) {
    private val files = libraryContext(context).filesDir

    suspend fun export(): WatchCloudNotesPayload {
        val snapshot = repository.cloudState()
        val entries = linkedMapOf<String, MarkdownArchiveEntry>()
        var total = 0L
        snapshot.notes.filterNot { it.deleted }.forEach { note ->
            val markdown = buildString {
                appendLine("---"); appendLine("watchrss-note: 1"); appendLine("id: ${note.noteId}")
                note.folderId?.let { appendLine("folder: $it") }
                appendLine("created-at: ${note.createdAt}"); appendLine("updated-at: ${note.updatedAt}"); appendLine("---")
                append(note.markdown); if (!note.markdown.endsWith('\n')) appendLine()
            }.toByteArray(Charsets.UTF_8)
            entries["notes/${note.noteId}.md"] = MarkdownArchiveEntry("notes/${note.noteId}.md", markdown)
            total += markdown.size
            noteAssetKeys(note.markdown).forEach { key ->
                val path = "notes/assets/$key"
                if (path !in entries) {
                    val file = File(files, "notes/assets/$key")
                    require(file.isFile) { "备忘录图片缺失：$key" }
                    total += file.length()
                    require(total <= 128L * 1024 * 1024) { "备忘录图片归档过大" }
                    entries[path] = MarkdownArchiveEntry(path, file.readBytes())
                }
            }
        }
        return WatchCloudNotesPayload(NoteCloudStateCodec.encode(snapshot.notes, snapshot.folders),
            entries.takeIf { it.isNotEmpty() }?.values?.toList()?.let(MarkdownNoteArchive::write))
    }

    suspend fun restore(stateBytes: ByteArray?, archive: ByteArray?): Int {
        val state = stateBytes?.let(NoteCloudStateCodec::decodeState) ?: return 0
        val entries = archive?.let(MarkdownNoteArchive::read).orEmpty()
        val incoming = entries.filter { it.path.startsWith("notes/assets/") }.associate {
            noteAssetStorageKey(it.path.removePrefix("notes/assets/")) to it.bytes
        }
        val needed = state.notes.filterNot { it.deleted }.flatMap { noteAssetKeys(it.markdown) }.toSet()
        needed.forEach { key ->
            require(key in incoming || File(files, "notes/assets/$key").isFile) { "云快照缺少备忘录图片：$key" }
        }
        // The entire ZIP has already passed cloud chunk authentication. Install files before publishing notes.
        needed.forEach { key -> incoming[key]?.let { bytes ->
            val target = File(files, "notes/assets/$key")
            if (!target.isFile || WatchCloudCodec.sha256(target.readBytes()) != WatchCloudCodec.sha256(bytes)) {
                target.parentFile?.mkdirs()
                val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.cloud")
                try {
                    temporary.writeBytes(bytes)
                    require(WatchCloudCodec.sha256(temporary.readBytes()) == WatchCloudCodec.sha256(bytes)) { "备忘录图片落盘校验失败" }
                    try {
                        Files.move(
                            temporary.toPath(),
                            target.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    } catch (_: java.io.IOException) {
                        Files.move(
                            temporary.toPath(),
                            target.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                } finally { temporary.delete() }
            }
        } }
        return repository.applyCloudState(state, deviceId)
    }
}
