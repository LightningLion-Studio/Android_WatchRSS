package com.lightningstudio.watchrss

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.lightningstudio.watchrss.data.note.RawTextUndoManager
import org.junit.Assert.*
import org.junit.Test

class NoteImageEditingTest {
    private val reference = "![说明](notes/assets/stable-image-id-0123456789.png)"

    @Test
    fun imageReferencesRenderAsCompactLabelsWithoutChangingTheDocument() {
        val raw = "正文$reference\n<img src=\"notes/second.png\" alt=\"第二张\">结尾"
        val transformed = NoteImageVisualTransformation().filter(AnnotatedString(raw))
        assertEquals("正文〔图片 1〕\n〔图片 2〕结尾", transformed.text.text)
        assertEquals(raw.length, transformed.offsetMapping.transformedToOriginal(transformed.text.length))
        assertEquals(2, noteImageSpans(raw).size)
    }

    @Test
    fun allOffsetsStayMonotonicAndInsideBounds() {
        val raw = "甲$reference${reference}乙"
        val transformed = NoteImageVisualTransformation().filter(AnnotatedString(raw))
        val mapping = transformed.offsetMapping
        val toDisplay = (0..raw.length).map(mapping::originalToTransformed)
        val toSource = (0..transformed.text.length).map(mapping::transformedToOriginal)
        assertTrue(toDisplay.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(toSource.zipWithNext().all { (a, b) -> a <= b })
        assertTrue(toDisplay.all { it in 0..transformed.text.length })
        assertTrue(toSource.all { it in 0..raw.length })
        assertEquals(0, toDisplay.first())
        assertEquals(0, toSource.first())
        assertEquals(transformed.text.length, toDisplay.last())
        assertEquals(raw.length, toSource.last())
    }

    @Test
    fun literalImageSyntaxInsideCodeRemainsEditable() {
        val raw = "`$reference`\n```markdown\n$reference\n```\n~~~\n$reference\n~~~"
        assertTrue(noteImageSpans(raw).isEmpty())
        assertEquals(raw, NoteImageVisualTransformation().filter(AnnotatedString(raw)).text.text)
    }

    @Test
    fun backspaceDeletesWholeImageAndUndoRestoresExactReference() {
        val raw = "甲${reference}乙"
        val end = 1 + reference.length
        val current = TextFieldValue(raw, TextRange(end))
        val partialDeletion = TextFieldValue(raw.removeRange(end - 1, end), TextRange(end - 1))
        val normalized = normalizeNoteImageEdit(current, partialDeletion)
        assertEquals(TextFieldValue("甲乙", TextRange(1)), normalized)
        val history = RawTextUndoManager(current)
        history.record(normalized, 1)
        assertEquals(current, history.undo())
        assertEquals(normalized, history.redo())
    }

    @Test
    fun textEditingAndCompositionOutsideImagesRemainUnchanged() {
        val raw = "甲${reference}乙"
        val current = TextFieldValue(raw, TextRange(1))
        val composing = TextFieldValue("甲新${reference}乙", TextRange(2), TextRange(1, 2))
        assertEquals(composing, normalizeNoteImageEdit(current, composing))
        val ordinarySelection = current.copy(selection = TextRange(raw.length))
        assertEquals(ordinarySelection, normalizeNoteImageEdit(current, ordinarySelection))
    }

    @Test
    fun crownSkipsImageAsOneObjectAndInsertionCannotCorruptItsReference() {
        val raw = "甲${reference}乙"
        val current = TextFieldValue(raw, TextRange(1))
        assertEquals(1 + reference.length, moveNoteCursor(current, 1, protectImages = true).selection.end)
        assertEquals(1, moveNoteCursor(current.copy(selection = TextRange(1 + reference.length)), -1, protectImages = true).selection.end)
        val insertedInside = TextFieldValue(raw.substring(0, 3) + "新" + raw.substring(3), TextRange(4))
        val safe = normalizeNoteImageEdit(current, insertedInside)
        assertEquals("甲新${reference}乙", safe.text)
        assertEquals(2, safe.selection.end)
    }

    @Test
    fun replacingAcrossMultipleImagesDoesNotLeaveBrokenFragments() {
        val raw = "甲${reference}中${reference}乙"
        val current = TextFieldValue(raw)
        val end = raw.lastIndexOf(".png")
        val incoming = TextFieldValue(raw.substring(0, 3) + "替换" + raw.substring(end), TextRange(5))
        assertEquals(TextFieldValue("甲替换乙", TextRange(3)), normalizeNoteImageEdit(current, incoming))
    }
}
