package com.lightningstudio.watchrss

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesActivityTest {
    @Test
    fun notePreview_doesNotRepeatTitle() {
        assertEquals(
            "第一段 第二段",
            notePreview("会议安排", "会议安排\n\n第一段\n第二段")
        )
    }

    @Test
    fun notePreview_keepsFirstLineWhenDifferentFromTitle() {
        assertEquals(
            "先买牛奶 再取快递",
            notePreview("待办", "先买牛奶\n再取快递")
        )
    }

    @Test
    fun notePreview_collapsesBlankLinesAndLimitsLength() {
        val preview = notePreview("标题", "\n\n" + "内容".repeat(100))

        assertEquals(120, preview.length)
    }

    @Test
    fun crownCursorMovement_collapsesSelectionAndMovesByCodePoint() {
        val selected = TextFieldValue("甲😀乙", selection = TextRange(1, 3))

        assertEquals(4, moveNoteCursor(selected, 1).selection.start)
        assertEquals(0, moveNoteCursor(selected, -1).selection.start)
    }

    @Test
    fun crownCursorMovement_stopsAtDocumentEdges() {
        val atStart = TextFieldValue("正文", selection = TextRange(0))
        val atEnd = TextFieldValue("正文", selection = TextRange(2))

        assertEquals(0, moveNoteCursor(atStart, -1).selection.start)
        assertEquals(2, moveNoteCursor(atEnd, 1).selection.start)
    }

    @Test
    fun initialCursor_roughlyTracksPreservedScrollPosition() {
        assertEquals(0, estimateNoteCursorForScroll(100, 0, 400))
        assertEquals(50, estimateNoteCursorForScroll(100, 200, 400))
        assertEquals(100, estimateNoteCursorForScroll(100, 400, 400))
    }
}
