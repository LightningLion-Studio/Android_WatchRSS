package com.lightningstudio.watchrss.ui.screen.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailBehaviorSupportTest {
    @Test
    fun importedTextRestoreTarget_mapsProgressToChunkAndOffsetAfterHeaderItems() {
        val target = importedTextRestoreTarget(
            progress = 0.5f,
            firstChunkItemIndex = 12,
            chunkCount = 50
        )
        assertEquals(36, target.itemIndex)
        assertEquals(0.5f, target.itemScrollOffsetProgress, 0.0001f)
    }

    @Test
    fun importedTextRestoreTarget_clampsToFirstAndLastChunk() {
        val first = importedTextRestoreTarget(-1f, firstChunkItemIndex = 8, chunkCount = 4)
        val last = importedTextRestoreTarget(2f, firstChunkItemIndex = 8, chunkCount = 4)
        assertEquals(8, first.itemIndex)
        assertEquals(0f, first.itemScrollOffsetProgress, 0f)
        assertEquals(11, last.itemIndex)
        assertEquals(0f, last.itemScrollOffsetProgress, 0f)
    }

    @Test
    fun calculateImportedTextReadingProgressFromPosition_usesChunkRelativePosition() {
        assertEquals(
            0.5f,
            calculateImportedTextReadingProgressFromPosition(
                firstVisibleChunkIndex = 4,
                firstVisibleItemScrollOffsetProgress = 0.5f,
                chunkCount = 10
            ),
            0.0001f
        )
    }

    @Test
    fun calculateImportedTextReadingProgressFromPosition_clampsOutsideChunkRange() {
        assertEquals(
            0f,
            calculateImportedTextReadingProgressFromPosition(
                firstVisibleChunkIndex = -3,
                firstVisibleItemScrollOffsetProgress = 0f,
                chunkCount = 10
            ),
            0f
        )
        assertEquals(
            1f,
            calculateImportedTextReadingProgressFromPosition(
                firstVisibleChunkIndex = 10,
                firstVisibleItemScrollOffsetProgress = 0f,
                chunkCount = 10
            ),
            0f
        )
    }
}
