package com.lightningstudio.watchrss.data.note

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NoteCloudStateCodecTest {
    private val markdown = "<p>正文<img src=\"assets/cloud.jpg\"></p>"
    private fun note() = WatchNoteEntity("cloud-note", "cloud-folder", "云笔记", markdown, watchPlainText(markdown), watchNoteHash(markdown), watchNoteHash(markdown), markdown, true, 10, 20, "watch")
    private fun folders() = listOf(WatchNoteFolderEntity("cloud-folder", "工作", 7, 20, "watch"), WatchNoteFolderEntity("deleted-folder", "旧目录", 8, 30, "watch", true, 30))

    @Test fun rawContentFoldersAndFolderTombstonesRoundTrip() {
        val decoded = NoteCloudStateCodec.decodeState(NoteCloudStateCodec.encode(listOf(note()), folders()))
        assertEquals(listOf(note()), decoded.notes)
        assertEquals(folders(), decoded.folders)
    }

    @Test fun legacyPayloadWithoutFoldersStillPreservesNoteFolderIdentity() {
        val json = JSONObject(NoteCloudStateCodec.encode(listOf(note())).toString(Charsets.UTF_8)).apply { remove("folders") }
        val decoded = NoteCloudStateCodec.decodeState(json.toString().toByteArray())
        assertTrue(decoded.folders.isEmpty())
        assertEquals("cloud-folder", decoded.notes.single().folderId)
        json.getJSONArray("notes").getJSONObject(0).remove("folder")
        assertNull(NoteCloudStateCodec.decode(json.toString().toByteArray()).single().folderId)
    }

    @Test fun duplicateIdentitiesAndCorruptBodyHashesFailBeforeMerge() {
        val json = JSONObject(NoteCloudStateCodec.encode(listOf(note()), folders()).toString(Charsets.UTF_8))
        json.getJSONArray("notes").put(json.getJSONArray("notes").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { NoteCloudStateCodec.decodeState(json.toString().toByteArray()) }
        val corrupt = JSONObject(NoteCloudStateCodec.encode(listOf(note())).toString(Charsets.UTF_8))
        corrupt.getJSONArray("notes").getJSONObject(0).put("hash", "incorrect")
        assertThrows(IllegalArgumentException::class.java) { NoteCloudStateCodec.decodeState(corrupt.toString().toByteArray()) }
    }

    @Test fun folderVersionsConvergeAndOrdinaryDataCannotUndoDeletion() {
        val active = folders().first()
        val deleted = active.copy(deleted = true, deletedAt = 30, updatedAt = 30)
        assertEquals(deleted, mergeCloudFolder(deleted, active.copy(updatedAt = 40)))
        assertEquals(deleted, mergeCloudFolder(active.copy(updatedAt = 40), deleted))
        val other = active.copy(name = "另一目录")
        assertEquals(mergeCloudFolder(active, other), mergeCloudFolder(other, active))
    }

    @Test fun producesCompatibilityFixturesWithTheRealEncoderAndArchiveWriter() {
        val directory = File("build/test-fixtures").apply { mkdirs() }
        File(directory, "watch-notes-state.json").writeBytes(NoteCloudStateCodec.encode(listOf(note()), folders()))
        File(directory, "watch-notes-assets.zip").writeBytes(MarkdownNoteArchive.write(listOf(
            MarkdownArchiveEntry("notes/cloud-note.md", "fixture".toByteArray()),
            MarkdownArchiveEntry("notes/assets/cloud.jpg", "cloud fixture image".toByteArray())
        )))
    }
}
