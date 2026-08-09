package com.lightningstudio.watchrss.data.note

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Session-local raw-text history. Nearby edits that touch the same range are grouped, matching
 * established code-editor behavior instead of treating every IME update or character as a step.
 */
class RawTextUndoManager(
    initialValue: TextFieldValue,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val groupDelayMillis: Long = DEFAULT_GROUP_DELAY_MILLIS
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
        require(groupDelayMillis >= 0L) { "groupDelayMillis must not be negative" }
    }

    var value: TextFieldValue = initialValue
        private set

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var lastEdit: Edit? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoSize: Int get() = undoStack.size
    val redoSize: Int get() = redoStack.size

    fun record(next: TextFieldValue, nowMillis: Long): TextFieldValue {
        if (next.text == value.text) {
            if (next.selection != value.selection) lastEdit = null
            value = next
            return value
        }

        val edit = Edit.between(value, next, nowMillis)
        val groupWithPrevious = lastEdit?.canGroupWith(edit, groupDelayMillis) == true
        if (!groupWithPrevious) {
            undoStack.addLast(value.copy(composition = null))
            trimToCapacity(undoStack)
        }
        redoStack.clear()
        value = next
        lastEdit = edit
        return value
    }

    fun undo(): TextFieldValue {
        val previous = undoStack.removeLastOrNull() ?: return value
        redoStack.addLast(value.copy(composition = null))
        trimToCapacity(redoStack)
        value = previous.copy(selection = previous.selection.clamp(previous.text.length), composition = null)
        lastEdit = null
        return value
    }

    fun redo(): TextFieldValue {
        val next = redoStack.removeLastOrNull() ?: return value
        undoStack.addLast(value.copy(composition = null))
        trimToCapacity(undoStack)
        value = next.copy(selection = next.selection.clamp(next.text.length), composition = null)
        lastEdit = null
        return value
    }

    private fun trimToCapacity(stack: ArrayDeque<TextFieldValue>) {
        while (stack.size > capacity) stack.removeFirst()
    }

    private data class Edit(
        val kind: Kind,
        val beforeStart: Int,
        val beforeEnd: Int,
        val afterStart: Int,
        val afterEnd: Int,
        val happenedAtMillis: Long
    ) {
        fun canGroupWith(next: Edit, delayMillis: Long): Boolean {
            if (kind != next.kind || next.happenedAtMillis - happenedAtMillis > delayMillis) return false
            return rangesTouch(afterStart, afterEnd, next.beforeStart, next.beforeEnd)
        }

        companion object {
            fun between(before: TextFieldValue, after: TextFieldValue, nowMillis: Long): Edit {
                val prefix = commonPrefixLength(before.text, after.text)
                val suffix = commonSuffixLength(before.text, after.text, prefix)
                val beforeEnd = before.text.length - suffix
                val afterEnd = after.text.length - suffix
                val removed = beforeEnd - prefix
                val inserted = afterEnd - prefix
                val kind = when {
                    removed == 0 && inserted > 0 -> Kind.INSERT
                    removed > 0 && inserted == 0 -> Kind.DELETE
                    else -> Kind.REPLACE
                }
                return Edit(kind, prefix, beforeEnd, prefix, afterEnd, nowMillis)
            }
        }
    }

    private enum class Kind { INSERT, DELETE, REPLACE }

    companion object {
        const val DEFAULT_CAPACITY = 200
        const val DEFAULT_GROUP_DELAY_MILLIS = 500L
    }
}

private fun rangesTouch(firstStart: Int, firstEnd: Int, secondStart: Int, secondEnd: Int): Boolean =
    secondStart <= firstEnd && secondEnd >= firstStart ||
        secondEnd == firstStart || secondStart == firstEnd

private fun commonPrefixLength(first: String, second: String): Int {
    val limit = minOf(first.length, second.length)
    var index = 0
    while (index < limit && first[index] == second[index]) index++
    return index
}

private fun commonSuffixLength(first: String, second: String, prefixLength: Int): Int {
    val limit = minOf(first.length, second.length) - prefixLength
    var count = 0
    while (count < limit && first[first.lastIndex - count] == second[second.lastIndex - count]) count++
    return count
}

private fun TextRange.clamp(textLength: Int): TextRange = TextRange(
    start = start.coerceIn(0, textLength),
    end = end.coerceIn(0, textLength)
)
