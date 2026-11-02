package com.lightningstudio.watchrss

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal data class NoteImageSpan(val start: Int, val end: Int)

private val imageReference = Regex("""!\[(?:\\.|[^\]])*\]\((?:[^)\s]+)(?:\s+["'][^"']*["'])?\)|(?is:<img\b[^>]*>(?:\s*</img>)?)""")

internal fun noteImageSpans(text: String): List<NoteImageSpan> {
    val codeRanges = mutableListOf<IntRange>()
    var fence: String? = null
    var fenceStart = 0
    var offset = 0
    text.splitToSequence('\n').forEach { line ->
        val marker = line.trimStart().take(3)
        if (marker == "```" || marker == "~~~") {
            if (fence == null) { fence = marker; fenceStart = offset }
            else if (fence == marker) { codeRanges += fenceStart until offset + line.length; fence = null }
        } else if (fence == null) {
            Regex("`+[^`]*`+").findAll(line).forEach { codeRanges += (offset + it.range.first)..(offset + it.range.last) }
        }
        offset += line.length + 1
    }
    if (fence != null) codeRanges += fenceStart until text.length
    return imageReference.findAll(text).filter { match ->
        codeRanges.none { match.range.first in it }
    }.map { NoteImageSpan(it.range.first, it.range.last + 1) }.toList()
}

internal class NoteImageVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val spans = noteImageSpans(text.text)
        if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val sourceToDisplay = IntArray(text.length + 1)
        val displayToSource = mutableListOf(0)
        val display = StringBuilder()
        var source = 0
        fun appendPlain(end: Int) {
            while (source < end) {
                sourceToDisplay[source] = display.length
                display.append(text[source++])
                displayToSource += source
            }
        }
        spans.forEachIndexed { index, span ->
            appendPlain(span.start)
            val label = "〔图片 ${index + 1}〕"
            val displayStart = display.length
            for (position in span.start until span.end) {
                sourceToDisplay[position] = displayStart +
                    ((position - span.start).toLong() * label.length / (span.end - span.start)).toInt()
            }
            label.forEachIndexed { labelIndex, char ->
                display.append(char)
                displayToSource += if (labelIndex + 1 < (label.length + 1) / 2) span.start else span.end
            }
            source = span.end
        }
        appendPlain(text.length)
        sourceToDisplay[text.length] = display.length
        return TransformedText(AnnotatedString(display.toString()), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = sourceToDisplay[offset.coerceIn(0, text.length)]
            override fun transformedToOriginal(offset: Int): Int = displayToSource[offset.coerceIn(0, display.length)]
        })
    }
}

/** IMEs delete raw characters; expand edits touching an image to its complete reference. */
internal fun normalizeNoteImageEdit(current: TextFieldValue, incoming: TextFieldValue): TextFieldValue {
    val spans = noteImageSpans(current.text)
    if (spans.isEmpty()) return incoming
    fun snap(offset: Int): Int {
        val span = spans.firstOrNull { offset > it.start && offset < it.end } ?: return offset
        return if (offset - span.start < span.end - offset) span.start else span.end
    }
    if (incoming.text == current.text) {
        val selection = TextRange(snap(incoming.selection.start), snap(incoming.selection.end))
        return if (selection == incoming.selection) incoming else incoming.copy(selection = selection, composition = null)
    }
    var prefix = 0
    while (prefix < current.text.length && prefix < incoming.text.length && current.text[prefix] == incoming.text[prefix]) prefix++
    var suffix = 0
    while (suffix < current.text.length - prefix && suffix < incoming.text.length - prefix &&
        current.text[current.text.lastIndex - suffix] == incoming.text[incoming.text.lastIndex - suffix]) suffix++
    val removedEnd = current.text.length - suffix
    val inserted = incoming.text.substring(prefix, incoming.text.length - suffix)
    if (prefix == removedEnd) {
        val target = snap(prefix)
        if (target == prefix) return incoming
        return TextFieldValue(current.text.substring(0, target) + inserted + current.text.substring(target), TextRange(target + inserted.length))
    }
    val touched = spans.filter { it.start < removedEnd && it.end > prefix }
    if (touched.isEmpty()) return incoming
    val start = minOf(prefix, touched.first().start)
    val end = maxOf(removedEnd, touched.last().end)
    if (start == prefix && end == removedEnd) return incoming
    return TextFieldValue(current.text.substring(0, start) + inserted + current.text.substring(end), TextRange(start + inserted.length))
}
