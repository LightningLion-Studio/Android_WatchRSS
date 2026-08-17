package com.lightningstudio.watchrss.data.note

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNoteRepositoryTest {
    @Test
    fun saveRawMarkdown_preservesSourceExactlyAndKeepsOriginalMergeBase() = runTest {
        val original = note(
            markdown = "# 标题\n\n**初始正文**",
            contentHash = "original-hash",
            baseContentHash = "remote-base-hash",
            baseMarkdown = "remote base"
        )
        val dao = FakeWatchNoteDao(original)
        val repository = WatchNoteRepository(dao)

        val saved = repository.saveRawMarkdown(
            noteId = original.noteId,
            rawMarkdown = "# 标题\r\n\r\n- 修改后\r\n![图](assets/a.jpg)",
            deviceId = "watch-device",
            now = 200L
        )

        assertEquals("# 标题\n\n- 修改后\n![图](assets/a.jpg)", saved.markdown)
        assertEquals("标题\n修改后\n[图片：图]", saved.plainText)
        assertEquals("remote-base-hash", saved.baseContentHash)
        assertEquals("remote base", saved.baseMarkdown)
        assertEquals("watch-device", saved.modifiedBy)
        assertEquals(200L, saved.updatedAt)
    }

    @Test
    fun saveRawMarkdown_createsWatchNoteAndDerivesTitle() = runTest {
        val dao = FakeWatchNoteDao()
        val saved = WatchNoteRepository(dao).saveRawMarkdown(
            noteId = null,
            rawMarkdown = "## 手表新建\n正文",
            deviceId = "watch",
            now = 100L
        )

        assertTrue(saved.noteId.isNotBlank())
        assertEquals("手表新建", saved.title)
        assertEquals(saved.contentHash, saved.baseContentHash)
        assertEquals(saved.markdown, saved.baseMarkdown)
        assertFalse(saved.deleted)
    }

    @Test
    fun saveRawMarkdown_persistsExplicitTitleSeparatelyFromBody() = runTest {
        val dao = FakeWatchNoteDao()
        val repository = WatchNoteRepository(dao)

        val created = repository.saveRawMarkdown(
            noteId = null,
            rawMarkdown = "",
            rawTitle = "独立标题",
            deviceId = "watch",
            now = 100L
        )
        val renamed = repository.saveRawMarkdown(
            noteId = created.noteId,
            rawMarkdown = created.markdown,
            rawTitle = "修改后的标题",
            deviceId = "watch",
            now = 200L
        )

        assertEquals("独立标题", created.title)
        assertEquals("", created.markdown)
        assertEquals("修改后的标题", renamed.title)
        assertEquals(created.contentHash, renamed.contentHash)
        assertEquals(200L, renamed.updatedAt)
    }

    @Test
    fun saveRawMarkdown_allowsClearingAnExistingNote() = runTest {
        val original = note(
            markdown = "正文",
            contentHash = "original-hash",
            baseContentHash = "remote-base-hash",
            baseMarkdown = "remote base"
        )
        val repository = WatchNoteRepository(FakeWatchNoteDao(original))

        val saved = repository.saveRawMarkdown(original.noteId, "", "watch", now = 300L)

        assertEquals("", saved.markdown)
        assertEquals("", saved.plainText)
        assertEquals("remote-base-hash", saved.baseContentHash)
    }

    @Test
    fun plainTextProjection_handlesPhoneRichHtmlWithoutChangingRawEditorSource() {
        val html = "<p style=\"text-align: justify\"><strong>加粗</strong><br><span style=\"color: red\">彩色</span><img src=\"assets/a.jpg\" alt=\"插图\"></p>"

        assertEquals("加粗\n彩色[图片：插图]", watchPlainText(html))
        assertEquals("加粗", watchNoteTitle(html))
    }

    @Test
    fun managementUpdates_preserveContentAndCreateSyncTombstone() = runTest {
        val original = note(
            markdown = "正文",
            contentHash = "original-hash",
            baseContentHash = "remote-base-hash",
            baseMarkdown = "remote base"
        )
        val dao = FakeWatchNoteDao(original)
        val repository = WatchNoteRepository(dao)

        val moved = repository.move(original.noteId, "folder-1", "watch", now = 100L)
        val pinned = repository.setPinned(original.noteId, true, "watch", now = 200L)
        val deleted = repository.delete(original.noteId, "watch", now = 300L)

        assertEquals("folder-1", moved.folderId)
        assertEquals(original.markdown, moved.markdown)
        assertEquals(original.baseMarkdown, moved.baseMarkdown)
        assertTrue(pinned.pinned)
        assertTrue(deleted.deleted)
        assertEquals(300L, deleted.deletedAt)
        assertEquals("watch", deleted.modifiedBy)
    }

    private fun note(
        markdown: String,
        contentHash: String,
        baseContentHash: String,
        baseMarkdown: String
    ) = WatchNoteEntity(
        noteId = "note-1",
        folderId = null,
        title = "标题",
        markdown = markdown,
        plainText = watchPlainText(markdown),
        contentHash = contentHash,
        baseContentHash = baseContentHash,
        baseMarkdown = baseMarkdown,
        pinned = false,
        createdAt = 1L,
        updatedAt = 2L,
        modifiedBy = "phone"
    )
}

private class FakeWatchNoteDao(vararg initial: WatchNoteEntity) : WatchNoteDao {
    private val notes = LinkedHashMap(initial.associateBy { it.noteId })
    private val flow = MutableStateFlow(notes.values.toList())

    override fun observeFolders(): Flow<List<WatchNoteFolderEntity>> = MutableStateFlow(emptyList())
    override fun observeNotes(): Flow<List<WatchNoteEntity>> = flow
    override fun observeNote(noteId: String): Flow<WatchNoteEntity?> =
        flow.map { current -> current.firstOrNull { it.noteId == noteId && !it.deleted } }
    override suspend fun get(noteId: String): WatchNoteEntity? = notes[noteId]
    override suspend fun all(): List<WatchNoteEntity> = notes.values.toList()
    override suspend fun upsert(notes: List<WatchNoteEntity>) {
        notes.forEach { this.notes[it.noteId] = it }
        flow.value = this.notes.values.toList()
    }
    override suspend fun upsertFolders(folders: List<WatchNoteFolderEntity>) = Unit
}
