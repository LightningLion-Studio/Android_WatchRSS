package com.lightningstudio.watchrss.data.note

import org.json.JSONArray
import org.json.JSONObject

data class NoteCloudState(val notes: List<WatchNoteEntity>, val folders: List<WatchNoteFolderEntity>)

/** v1 remains readable by old clients; folders are an additive, optional field. */
object NoteCloudStateCodec {
    fun encode(notes: List<WatchNoteEntity>, folders: List<WatchNoteFolderEntity> = emptyList()): ByteArray = JSONObject().apply {
        put("format", "watchrss-notes"); put("version", 1)
        put("notes", JSONArray().apply { notes.forEach { note -> put(JSONObject().apply {
            put("id", note.noteId); put("folder", note.folderId ?: JSONObject.NULL); put("title", note.title)
            put("markdown", note.markdown); put("hash", watchNoteHash(note.markdown)); put("base", note.baseMarkdown)
            put("updatedAt", note.updatedAt); put("modifiedBy", note.modifiedBy); put("pinned", note.pinned)
            put("deleted", note.deleted); put("deletedAt", note.deletedAt); put("createdAt", note.createdAt)
        }) } })
        put("folders", JSONArray().apply { folders.forEach { folder -> put(JSONObject().apply {
            put("id", folder.folderId); put("name", folder.name); put("sortOrder", folder.sortOrder)
            put("updatedAt", folder.updatedAt); put("modifiedBy", folder.modifiedBy)
            put("deleted", folder.deleted); put("deletedAt", folder.deletedAt)
        }) } })
    }.toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): List<WatchNoteEntity> = decodeState(bytes).notes

    fun decodeState(bytes: ByteArray): NoteCloudState {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("format") == "watchrss-notes" && root.optInt("version") == 1) { "云端笔记格式不受支持" }
        val array = root.getJSONArray("notes")
        val notes = List(array.length()) { index ->
            val json = array.getJSONObject(index)
            val markdown = json.getString("markdown")
            val hash = watchNoteHash(markdown)
            require(json.getString("hash") == hash) { "云端备忘录正文校验失败" }
            val base = json.getString("base")
            WatchNoteEntity(json.getString("id"), if (!json.has("folder") || json.isNull("folder")) null else json.getString("folder"),
                json.optString("title"), markdown, watchPlainText(markdown), hash, watchNoteHash(base), base,
                json.optBoolean("pinned"), json.getLong("createdAt"), json.getLong("updatedAt"), json.getString("modifiedBy"), json.optBoolean("deleted"), json.optLong("deletedAt"))
        }
        val folders = root.optJSONArray("folders")?.let { items -> List(items.length()) { index ->
            val json = items.getJSONObject(index)
            WatchNoteFolderEntity(json.getString("id"), json.getString("name"), json.getLong("sortOrder"), json.getLong("updatedAt"),
                json.getString("modifiedBy"), json.optBoolean("deleted"), json.optLong("deletedAt"))
        } }.orEmpty()
        require(notes.all { it.noteId.isNotBlank() } && notes.map { it.noteId }.distinct().size == notes.size) { "云端备忘录标识为空或重复" }
        require(folders.all { it.folderId.isNotBlank() } && folders.map { it.folderId }.distinct().size == folders.size) { "云端备忘录目录标识为空或重复" }
        return NoteCloudState(notes, folders)
    }
}

internal fun mergeCloudFolder(local: WatchNoteFolderEntity?, remote: WatchNoteFolderEntity): WatchNoteFolderEntity {
    if (local == null) return remote
    if (local.deleted != remote.deleted) return if (local.deleted) local else remote
    return if (compareValuesBy(remote, local, { it.updatedAt }, { it.modifiedBy }, { it.name }, { it.sortOrder }) > 0) remote else local
}
