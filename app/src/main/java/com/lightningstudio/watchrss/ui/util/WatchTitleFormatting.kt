package com.lightningstudio.watchrss.ui.util

import android.text.TextPaint
import kotlin.math.min

fun normalizeWatchTitleWhitespace(title: String): String {
    val collapsed = title
        .replace('\n', ' ')
        .replace('\u00A0', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
    if (collapsed.isEmpty()) {
        return title.trim()
    }
    if (!looksLikeCjkTitle(collapsed)) {
        return collapsed
    }

    val normalized = buildString(collapsed.length) {
        collapsed.forEachIndexed { index, ch ->
            if (ch != ' ') {
                append(ch)
                return@forEachIndexed
            }

            val previous = collapsed.previousNonSpaceChar(index - 1)
            val next = collapsed.nextNonSpaceChar(index + 1)
            val keepSpace = previous != null && next != null &&
                previous.isAsciiWordChar() && next.isAsciiWordChar()
            if (keepSpace) {
                append(' ')
            }
        }
    }

    return normalized.ifEmpty { collapsed.replace(" ", "") }
}

fun formatWatchTitleForWidthLimits(
    title: String,
    paint: TextPaint,
    availableWidthPx: Float,
    firstLimitPx: Float,
    secondLimitPx: Float,
    protectedSuffix: String? = null,
    minPrefixCharsBeforeSuffixOnLastLine: Int = 0
): String {
    return formatWatchTitleForWidthLimitsWithMeasurer(
        title = title,
        availableWidthPx = availableWidthPx,
        firstLimitPx = firstLimitPx,
        secondLimitPx = secondLimitPx,
        protectedSuffix = protectedSuffix,
        minPrefixCharsBeforeSuffixOnLastLine = minPrefixCharsBeforeSuffixOnLastLine,
        measureText = paint::measureText
    )
}

internal fun formatWatchTitleForWidthLimitsWithMeasurer(
    title: String,
    availableWidthPx: Float,
    firstLimitPx: Float,
    secondLimitPx: Float,
    protectedSuffix: String? = null,
    minPrefixCharsBeforeSuffixOnLastLine: Int = 0,
    measureText: (String) -> Float
): String {
    val normalized = normalizeWatchTitleWhitespace(title)
    if (normalized.isEmpty()) {
        return title
    }
    val firstLimit = min(firstLimitPx, availableWidthPx)
    val secondLimit = min(secondLimitPx, availableWidthPx)
    val lines = mutableListOf<String>()
    var start = 0
    var lineIndex = 0
    while (start < normalized.length) {
        val limit = if (lineIndex == 0) firstLimit else secondLimit
        val end = breakTextIndex(normalized, start, limit, measureText)
        if (end <= start) {
            lines.add(normalized.substring(start, start + 1))
            start += 1
        } else {
            lines.add(normalized.substring(start, end))
            start = end
        }
        lineIndex++
    }
    balanceSingleCharLines(lines, measureText, firstLimit, secondLimit)
    balanceProtectedSuffixLines(
        lines = lines,
        measureText = measureText,
        firstLimitPx = firstLimit,
        otherLimitPx = secondLimit,
        protectedSuffix = protectedSuffix,
        minPrefixCharsBeforeSuffixOnLastLine = minPrefixCharsBeforeSuffixOnLastLine,
        normalizedTitle = normalized
    )
    return lines.joinToString("\n")
}

private fun looksLikeCjkTitle(text: String): Boolean {
    val compact = text.filterNot(Char::isWhitespace)
    if (compact.isEmpty()) return false
    val cjkCount = compact.count { it.isCjkChar() }
    return cjkCount >= 4 && cjkCount * 2 >= compact.length
}

private fun breakTextIndex(
    text: String,
    start: Int,
    widthPx: Float,
    measureText: (String) -> Float
): Int {
    var low = start
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        val current = text.substring(start, mid)
        if (measureText(current) <= widthPx) {
            low = mid
        } else {
            high = mid - 1
        }
    }
    return low
}

private fun balanceSingleCharLines(
    lines: MutableList<String>,
    measureText: (String) -> Float,
    firstLimitPx: Float,
    otherLimitPx: Float
) {
    var index = 1
    while (index < lines.size) {
        val current = lines[index]
        if (current.length == 1) {
            val prevIndex = index - 1
            val prev = lines[prevIndex]
            val prevLimit = if (prevIndex == 0) firstLimitPx else otherLimitPx
            val mergedPrev = prev + current
            if (measureText(mergedPrev) <= prevLimit) {
                lines[prevIndex] = mergedPrev
                lines.removeAt(index)
                continue
            }
            if (prev.length > 1) {
                val shiftedPrev = prev.dropLast(1)
                val shiftedCurrent = prev.takeLast(1) + current
                val currentLimit = if (index == 0) firstLimitPx else otherLimitPx
                if (measureText(shiftedCurrent) <= currentLimit) {
                    lines[prevIndex] = shiftedPrev
                    lines[index] = shiftedCurrent
                    if (prevIndex > 0) {
                        index--
                        continue
                    }
                }
            }
            if (index + 1 < lines.size) {
                val next = lines[index + 1]
                if (next.isNotEmpty()) {
                    val mergedCurrent = current + next.first()
                    val currentLimit = if (index == 0) firstLimitPx else otherLimitPx
                    if (measureText(mergedCurrent) <= currentLimit) {
                        lines[index] = mergedCurrent
                        val remaining = next.substring(1)
                        if (remaining.isEmpty()) {
                            lines.removeAt(index + 1)
                            continue
                        } else {
                            lines[index + 1] = remaining
                        }
                    }
                }
            }
        }
        index++
    }
}

private fun balanceProtectedSuffixLines(
    lines: MutableList<String>,
    measureText: (String) -> Float,
    firstLimitPx: Float,
    otherLimitPx: Float,
    protectedSuffix: String?,
    minPrefixCharsBeforeSuffixOnLastLine: Int,
    normalizedTitle: String
) {
    if (protectedSuffix.isNullOrEmpty()) return
    if (minPrefixCharsBeforeSuffixOnLastLine <= 1) return
    if (lines.size < 2) return
    if (!looksLikeCjkTitle(normalizedTitle.removeSuffix(protectedSuffix))) return

    val lastIndex = lines.lastIndex
    if (!lines[lastIndex].endsWith(protectedSuffix)) return

    while (countVisibleCharsBeforeSuffix(lines[lastIndex], protectedSuffix) <
        minPrefixCharsBeforeSuffixOnLastLine
    ) {
        val prevIndex = lastIndex - 1
        val prev = lines[prevIndex]
        if (prev.length <= 1) return

        val shiftedCurrent = prev.takeLast(1) + lines[lastIndex]
        val currentLimit = if (lastIndex == 0) firstLimitPx else otherLimitPx
        if (measureText(shiftedCurrent) > currentLimit) return

        lines[prevIndex] = prev.dropLast(1)
        lines[lastIndex] = shiftedCurrent
    }
}

private fun countVisibleCharsBeforeSuffix(text: String, suffix: String): Int {
    return text
        .removeSuffix(suffix)
        .trimEnd()
        .count { !it.isWhitespace() }
}

private fun String.previousNonSpaceChar(index: Int): Char? {
    var cursor = index
    while (cursor >= 0) {
        val ch = this[cursor]
        if (!ch.isWhitespace()) return ch
        cursor--
    }
    return null
}

private fun String.nextNonSpaceChar(index: Int): Char? {
    var cursor = index
    while (cursor < length) {
        val ch = this[cursor]
        if (!ch.isWhitespace()) return ch
        cursor++
    }
    return null
}

private fun Char.isAsciiWordChar(): Boolean {
    return code < 128 && (isLetterOrDigit() || this == '_' || this == '-' || this == '/')
}

private fun Char.isCjkChar(): Boolean {
    return when (Character.UnicodeScript.of(code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL -> true
        else -> false
    }
}
