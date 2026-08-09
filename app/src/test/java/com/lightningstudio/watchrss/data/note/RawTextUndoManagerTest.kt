package com.lightningstudio.watchrss.data.note

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawTextUndoManagerTest {
    @Test
    fun nearbyTouchingTypingIsOneUndoStep() {
        val history = RawTextUndoManager(value(""))

        history.record(value("a"), 0L)
        history.record(value("ab"), 200L)
        history.record(value("abc"), 400L)

        assertEquals(1, history.undoSize)
        assertEquals("", history.undo().text)
        assertEquals("abc", history.redo().text)
    }

    @Test
    fun pauseAndCursorMoveCreateNewUndoGroups() {
        val history = RawTextUndoManager(value(""))
        history.record(value("a"), 0L)
        history.record(value("ab"), 600L)
        history.record(TextFieldValue("ab", TextRange(0)), 650L)
        history.record(TextFieldValue("Xab", TextRange(1)), 700L)

        assertEquals(3, history.undoSize)
        assertEquals("ab", history.undo().text)
        assertEquals("a", history.undo().text)
        assertEquals("", history.undo().text)
    }

    @Test
    fun newEditAfterUndoClearsRedo() {
        val history = RawTextUndoManager(value(""))
        history.record(value("a"), 0L)
        history.record(value("ab"), 600L)

        history.undo()
        assertTrue(history.canRedo)
        history.record(value("ac"), 1_200L)

        assertFalse(history.canRedo)
        assertEquals("ac", history.value.text)
    }

    @Test
    fun historyIsCappedAtTwoHundredSteps() {
        val history = RawTextUndoManager(value(""))
        repeat(205) { index ->
            history.record(value("x".repeat(index + 1)), index * 600L)
        }

        assertEquals(200, history.undoSize)
        repeat(200) { history.undo() }
        assertEquals("xxxxx", history.value.text)
        assertFalse(history.canUndo)
    }

    private fun value(text: String) = TextFieldValue(text, TextRange(text.length))
}
