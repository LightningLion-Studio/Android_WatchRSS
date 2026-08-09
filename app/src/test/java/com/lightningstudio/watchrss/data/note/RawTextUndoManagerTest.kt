package com.lightningstudio.watchrss.data.note

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawTextUndoManagerTest {
    @Test
    fun typingBurstIsOneUndoStep() {
        val history = RawTextUndoManager(value(""))
        history.record(value("你"), 100L)
        history.record(value("你好"), 200L)

        assertTrue(history.canUndo)
        assertEquals("", history.undo().text)
        assertTrue(history.canRedo)
        assertEquals("你好", history.redo().text)
    }

    @Test
    fun delayedEditCreatesAnotherUndoStep() {
        val history = RawTextUndoManager(value("原文"))
        history.record(value("原文一"), 100L)
        history.record(value("原文一二"), 1_000L)

        assertEquals("原文一", history.undo().text)
        assertEquals("原文", history.undo().text)
        assertFalse(history.canUndo)
    }

    private fun value(text: String) = TextFieldValue(text, TextRange(text.length))
}
