package com.lightningstudio.watchrss.data.note

import androidx.compose.ui.text.input.TextFieldValue

/** Small, allocation-bounded undo stack for the watch's plain-text note editor. */
class RawTextUndoManager(
    initialValue: TextFieldValue,
    private val maxEntries: Int = 50,
    private val mergeWindowMillis: Long = 700L
) {
    private val entries = mutableListOf(initialValue)
    private var position = 0
    private var lastRecordAt = Long.MIN_VALUE
    private var currentEntryStartsAt = Long.MIN_VALUE

    val canUndo: Boolean get() = position > 0
    val canRedo: Boolean get() = position < entries.lastIndex

    fun record(value: TextFieldValue, now: Long): TextFieldValue {
        if (value == entries[position]) return value
        if (canRedo) entries.subList(position + 1, entries.size).clear()

        val continuesTypingBurst = position > 0 &&
            now - lastRecordAt in 0..mergeWindowMillis &&
            now - currentEntryStartsAt <= MAX_MERGED_EDIT_MILLIS
        if (continuesTypingBurst) {
            entries[position] = value
        } else {
            entries.add(value)
            position++
            currentEntryStartsAt = now
            if (entries.size > maxEntries) {
                entries.removeAt(0)
                position--
            }
        }
        lastRecordAt = now
        return value
    }

    fun undo(): TextFieldValue {
        if (canUndo) position--
        lastRecordAt = Long.MIN_VALUE
        return entries[position]
    }

    fun redo(): TextFieldValue {
        if (canRedo) position++
        lastRecordAt = Long.MIN_VALUE
        return entries[position]
    }

    private companion object {
        const val MAX_MERGED_EDIT_MILLIS = 4_000L
    }
}
