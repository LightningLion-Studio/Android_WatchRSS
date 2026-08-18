package com.lightningstudio.watchrss.ui.screen.rss

import com.lightningstudio.watchrss.ui.util.ContentBlock
import com.lightningstudio.watchrss.ui.util.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailBehaviorSupportTest {
    @Test
    fun contentReadAloudAnchor_startsAtPressedCharacterInPressedBlock() {
        val blocks = listOf(
            ContentBlock.Text("第一段不会被朗读。", TextStyle.BODY),
            ContentBlock.Text("第二段开头。这里是长按的位置。后面继续。", TextStyle.BODY)
        )
        val pressedOffset = blocks[1].let { block ->
            (block as ContentBlock.Text).text.indexOf("长按")
        }

        val anchor = contentReadAloudStartAnchorAtCharOffset(
            contentBlocks = blocks,
            blockIndex = 1,
            pressedCharOffset = pressedOffset
        )

        assertEquals(1, anchor?.contentBlockIndex)
        assertEquals(pressedOffset, anchor?.contentCharOffset)
        assertEquals("长按的位置。后面继续。", anchor?.textSnippet)
    }

    @Test
    fun readAloudPressedCharOffset_skipsWhitespaceAtTouchPoint() {
        assertEquals(5, readAloudPressedCharOffset("正文   从这里开始", 2))
    }

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
    fun importedTextByteRestoreTarget_mapsProgressToByteOffset() {
        val target = importedTextByteRestoreTarget(
            progress = 0.5f,
            firstChunkItemIndex = 12,
            byteLength = 100_000L,
            chunkCount = 10,
            chunkBytes = 10_000
        )

        assertEquals(17, target.itemIndex)
        assertEquals(5, target.chunkIndex)
        assertEquals(0, target.byteOffsetInChunk)
    }

    @Test
    fun importedTextByteRestoreTarget_clampsToAvailableBytes() {
        val first = importedTextByteRestoreTarget(
            progress = -1f,
            firstChunkItemIndex = 8,
            byteLength = 35_001L,
            chunkCount = 4,
            chunkBytes = 10_000
        )
        val last = importedTextByteRestoreTarget(
            progress = 2f,
            firstChunkItemIndex = 8,
            byteLength = 35_001L,
            chunkCount = 4,
            chunkBytes = 10_000
        )

        assertEquals(8, first.itemIndex)
        assertEquals(0, first.chunkIndex)
        assertEquals(0, first.byteOffsetInChunk)
        assertEquals(11, last.itemIndex)
        assertEquals(3, last.chunkIndex)
        assertEquals(5_000, last.byteOffsetInChunk)
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
