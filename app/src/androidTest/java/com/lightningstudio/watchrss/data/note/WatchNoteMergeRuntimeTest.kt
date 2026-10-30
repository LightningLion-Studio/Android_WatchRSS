package com.lightningstudio.watchrss.data.note

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.data.account.workspace.AccountDataContext
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchNoteMergeRuntimeTest {
    @Test fun concurrentTextIsRetainedAsOneStableEditableConflictCopy() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountDataContext(target, "watch_merge_${UUID.randomUUID()}", false)
        val database = Room.inMemoryDatabaseBuilder(context, WatchRssDatabase::class.java).build()
        try {
            val repository = WatchNoteRepository(database.watchNoteDao())
            val original = repository.saveRawMarkdown(null, "base", "watch", now = 100)
            repository.saveRawMarkdown(original.noteId, "local", "watch", now = 200)
            val remote = original.copy(markdown = "remote", contentHash = watchNoteHash("remote"), plainText = "remote", updatedAt = 300, modifiedBy = "phone")
            assertEquals(1, repository.merge(listOf(remote), "watch"))
            assertEquals("local", repository.all().single { it.noteId == original.noteId }.markdown)
            val copy = repository.all().single { it.noteId != original.noteId }
            assertTrue(copy.title.startsWith("冲突副本"))
            assertEquals("remote", copy.markdown)
            assertEquals(0, repository.merge(listOf(remote), "watch"))
            repository.saveRawMarkdown(copy.noteId, "user edited conflict copy", "watch", now = 400)
            assertEquals(0, repository.merge(listOf(remote), "watch"))
            assertEquals("user edited conflict copy", repository.all().single { it.noteId == copy.noteId }.markdown)
        } finally { database.close(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively() }
    }

    @Test fun metadataPersistsAndOldLiveDataCannotUndoDeletion() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountDataContext(target, "watch_merge_metadata_${UUID.randomUUID()}", false)
        val database = Room.inMemoryDatabaseBuilder(context, WatchRssDatabase::class.java).build()
        try {
            val repository = WatchNoteRepository(database.watchNoteDao())
            val original = repository.saveRawMarkdown(null, "body", "watch", now = 100)
            val updated = original.copy(title = "renamed", folderId = "folder", pinned = true, updatedAt = 200, modifiedBy = "phone")
            assertEquals(1, repository.merge(listOf(updated), "watch"))
            assertEquals(updated, repository.all().single())
            val versions = coroutineScope {
                List(32) { index -> async(Dispatchers.IO) {
                    repository.setPinned(original.noteId, index % 2 == 0, "watch", now = 150).updatedAt
                } }.awaitAll()
            }
            assertEquals(32, versions.distinct().size)
            assertEquals(232L, versions.maxOrNull()!!)
            repository.delete(original.noteId, "watch", now = 300)
            assertEquals(0, repository.merge(listOf(original), "watch"))
            assertTrue(repository.all().single().deleted)
            repository.merge(listOf(original.copy(updatedAt = 400)), "watch")
            assertTrue(repository.all().single().deleted)
        } finally { database.close(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively() }
    }
}
