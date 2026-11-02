package com.lightningstudio.watchrss.ui.reader

import kotlin.math.roundToLong
import kotlin.math.min
import kotlin.math.sqrt

/** Offsets refer to the original text, never to a font-dependent page number. */
internal data class ReaderTextAnchor(val segment: Int, val offset: Int)
internal data class ReaderPageLine(
    val anchor: ReaderTextAnchor,
    val end: Int,
    val left: Float,
    val top: Float,
    val width: Float
)
internal data class ReaderDiscretePage(val lines: List<ReaderPageLine>, val mediaSegment: Int? = null) {
    val anchor: ReaderTextAnchor get() = lines.firstOrNull()?.anchor ?: ReaderTextAnchor(requireNotNull(mediaSegment), 0)
}

/** Use the narrowest chord across a complete line, with a margin inside the circle. */
internal fun readerRoundLineWidth(width: Float, height: Float, top: Float, lineHeight: Float): Float {
    val radius = min(width, height) / 2f
    val dy = maxOf(kotlin.math.abs(top - height / 2f), kotlin.math.abs(top + lineHeight - height / 2f))
    return (2f * sqrt((radius * radius - dy * dy).coerceAtLeast(0f)) - 32f).coerceAtLeast(0f)
}

internal fun paginateReaderText(
    texts: List<String>,
    width: Float,
    height: Float,
    lineHeight: Float,
    mediaSegments: Set<Int> = emptySet(),
    measureLineEnd: (String, Int, Int) -> Int
): List<ReaderDiscretePage> {
    if (width <= 0f || height <= 0f || lineHeight <= 0f) return emptyList()
    val top = height * 0.17f
    val bottom = height * 0.68f
    if (top + lineHeight > bottom) return emptyList()
    val pages = mutableListOf<ReaderDiscretePage>()
    var lines = mutableListOf<ReaderPageLine>()
    var y = top
    texts.forEachIndexed { segment, text ->
        if (segment in mediaSegments) {
            if (lines.isNotEmpty()) pages += ReaderDiscretePage(lines)
            pages += ReaderDiscretePage(emptyList(), segment)
            lines = mutableListOf()
            y = top
            return@forEachIndexed
        }
        var offset = 0
        while (offset < text.length) {
            if (y + lineHeight > bottom) {
                if (lines.isNotEmpty()) pages += ReaderDiscretePage(lines)
                lines = mutableListOf()
                y = top
            }
            val available = readerRoundLineWidth(width, height, y, lineHeight)
            if (available < 1f) return emptyList()
            val end = measureLineEnd(text, offset, available.toInt()).coerceIn(offset + 1, text.length)
            lines += ReaderPageLine(ReaderTextAnchor(segment, offset), end, (width - available) / 2f, y, available)
            offset = end
            y += lineHeight
        }
    }
    if (lines.isNotEmpty()) pages += ReaderDiscretePage(lines)
    return pages
}

internal fun readerPageForAnchor(pages: List<ReaderDiscretePage>, anchor: ReaderTextAnchor): Int =
    pages.indexOfLast { page ->
        page.anchor.segment < anchor.segment ||
            (page.anchor.segment == anchor.segment && page.anchor.offset <= anchor.offset)
    }.coerceAtLeast(0)

internal fun readerAnchorFromProgress(texts: List<String>, progress: Float): ReaderTextAnchor {
    val sizes = texts.map { it.toByteArray(Charsets.UTF_8).size }
    var target = (sizes.sumOf { it.toLong() }.toDouble() * progress.coerceIn(0f, 1f)).roundToLong()
    texts.forEachIndexed { index, text ->
        if (target == 0L || target < sizes[index] || index == texts.lastIndex) {
            var bytes = 0L
            var offset = 0
            while (offset < text.length) {
                val count = Character.charCount(text.codePointAt(offset))
                val next = bytes + text.substring(offset, offset + count).toByteArray(Charsets.UTF_8).size
                if (next > target) break
                bytes = next
                offset += count
            }
            return ReaderTextAnchor(index, offset)
        }
        target -= sizes[index]
    }
    return ReaderTextAnchor(0, 0)
}

internal fun readerAnchorProgress(texts: List<String>, anchor: ReaderTextAnchor): Float {
    val total = texts.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
    if (total <= 0L) return 0f
    val before = texts.take(anchor.segment).sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
    val current = texts.getOrNull(anchor.segment).orEmpty()
    val bytes = current.take(anchor.offset.coerceIn(0, current.length)).toByteArray(Charsets.UTF_8).size
    return ((before + bytes).toDouble() / total).toFloat().coerceIn(0f, 1f)
}
