package com.lightningstudio.watchrss.phoneconnection.bluetooth

import com.lightningstudio.watchrss.data.note.WatchNoteEntity
import com.lightningstudio.watchrss.data.note.watchPlainText
import org.json.JSONArray
import org.json.JSONObject

object WatchNoteSyncPayload {
    const val ACTION = "syncNotes"
    const val VERSION = 1
    fun response(deviceId: String, notes: List<WatchNoteEntity>, applied: Int) = JSONObject().apply {
        put("success", true); put("action", ACTION); put("version", VERSION); put("deviceId", deviceId); put("applied", applied)
        put("notes", JSONArray().apply { notes.forEach { note -> put(note.toJson()) } })
    }
    fun parse(request: JSONObject): List<WatchNoteEntity> = request.optJSONArray("notes")?.let { array -> List(array.length()) { index -> array.getJSONObject(index).toNote() } }.orEmpty()
    private fun WatchNoteEntity.toJson() = JSONObject().apply { put("noteId", noteId); put("folderId", folderId ?: JSONObject.NULL); put("title", title); put("markdown", markdown); put("contentHash", contentHash); put("baseContentHash", baseContentHash); put("baseMarkdown", baseMarkdown); put("pinned", pinned); put("createdAt", createdAt); put("updatedAt", updatedAt); put("modifiedBy", modifiedBy); put("deleted", deleted); put("deletedAt", deletedAt) }
    private fun JSONObject.toNote(): WatchNoteEntity { val markdown = optString("markdown"); return WatchNoteEntity(getString("noteId"), if (isNull("folderId")) null else optString("folderId"), optString("title"), markdown, watchPlainText(markdown), optString("contentHash"), optString("baseContentHash"), optString("baseMarkdown"), optBoolean("pinned"), optLong("createdAt"), optLong("updatedAt"), optString("modifiedBy"), optBoolean("deleted"), optLong("deletedAt")) }
}
