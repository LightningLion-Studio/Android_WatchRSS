package com.lightningstudio.watchrss.data.note

import org.junit.Assert.*
import org.junit.Test

class NoteMergePolicyTest {
    private fun note(body: String, base: String = "base", time: Long = 10, source: String = "local") =
        WatchNoteEntity("note", null, "title", body, watchPlainText(body), watchNoteHash(body), watchNoteHash(base), base, false, 1, time, source)

    @Test fun metadataOnlyChangesAreNotLost() {
        val local = note("same")
        val remote = local.copy(title = "new title", folderId = "folder", pinned = true, updatedAt = 20, modifiedBy = "remote")
        assertEquals(remote, mergeNoteVersions(local, remote, "receiver"))
    }

    @Test fun staleSnapshotCannotResurrectNewerDeletion() {
        val local = note("edited", time = 30).copy(deleted = true, deletedAt = 30)
        val old = note("base", base = "base", time = 10, source = "remote")
        assertEquals(local, mergeNoteVersions(local, old, "receiver"))
    }

    @Test fun independentEditsMergeAndReturnToSenderAsAProvenDescendant() {
        val base = "one\ntwo\nthree"
        val local = note("ONE\ntwo\nthree", base, time = 20)
        val remote = note("one\ntwo\nTHREE", base, time = 30, source = "remote")
        val merged = requireNotNull(mergeNoteVersions(local, remote, "receiver"))
        assertEquals("ONE\ntwo\nTHREE", merged.markdown)
        assertEquals(remote.markdown, merged.baseMarkdown)
        assertEquals(31L, merged.updatedAt)
        assertEquals(merged, mergeNoteVersions(remote, merged, "remote"))
        assertEquals(merged, mergeNoteVersions(merged, remote, "receiver"))
    }

    @Test fun concurrentInsertionsAtTheSamePositionAreAConflict() {
        val base = "one\ntwo"
        assertNull(mergeNoteVersions(note("one\nLOCAL\ntwo", base), note("one\nREMOTE\ntwo", base, 20, "remote"), "receiver"))
    }

    @Test fun incompatibleBasesMustNotBePassedToDiff3() {
        assertNull(mergeNoteVersions(note("local", "base A"), note("remote", "base B", 20, "remote"), "receiver"))
    }

    @Test fun ancestorBodyCannotRollbackLocalEditButItsNewerMetadataIsPreserved() {
        val local = note("local edit", "base", time = 20)
        val metadataChange = note("base", "base", time = 30, source = "remote").copy(pinned = true)
        val merged = requireNotNull(mergeNoteVersions(local, metadataChange, "receiver"))
        assertEquals("local edit", merged.markdown)
        assertTrue(merged.pinned)
    }

    @Test fun concurrentDeletionAndBodyEditRetainAConflictInsteadOfDiscardingText() {
        val local = note("local edit", "base", time = 20)
        val deletion = note("base", "base", time = 30, source = "remote").copy(deleted = true, deletedAt = 30)
        assertNull(mergeNoteVersions(local, deletion, "receiver"))
    }
    @Test fun newerUnchangedLiveSnapshotStillCannotImplicitlyUndelete() {
        val deleted = note("body", "base", 20).copy(deleted = true, deletedAt = 20)
        assertEquals(deleted, mergeNoteVersions(deleted, note("body", "base", 30, "remote"), "receiver"))
    }

    @Test fun editAgainstTombstoneIsAConflictAndMetadataTiesConverge() {
        val deleted = note("base", "base", 20).copy(deleted = true, deletedAt = 20)
        assertNull(mergeNoteVersions(deleted, note("remote edit", "base", 30, "remote"), "receiver"))
        val a = note("same").copy(title = "A")
        val b = a.copy(title = "B")
        assertEquals(mergeNoteVersions(a, b, "receiver"), mergeNoteVersions(b, a, "receiver"))
    }

}
