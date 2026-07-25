package com.lightningstudio.watchrss.data.tts

import org.jsoup.Jsoup

internal object ReadAloudTextSegmenter {
    const val MAX_SEGMENT_CHARS = 900

    fun buildArticleText(title: String, rawContent: String?): String {
        val parsed = rawContent
            ?.let { Jsoup.parse(it).text() }
            ?.let(::normalizePlainText)
            .orEmpty()
        val safeTitle = normalizePlainText(title)
        return buildString {
            if (safeTitle.isNotBlank()) {
                append(safeTitle)
                append("。")
            }
            if (parsed.isNotBlank() && !parsed.equals(safeTitle, ignoreCase = false)) {
                append(parsed)
            }
        }.trim()
    }

    fun normalizePlainText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun segment(text: String, maxSegmentChars: Int = MAX_SEGMENT_CHARS): List<String> {
        require(maxSegmentChars > 0) { "maxSegmentChars must be positive" }
        val normalized = normalizePlainText(text)
        if (normalized.isBlank()) return emptyList()

        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        val parts = normalized.split(Regex("(?<=[。！？!?；;：:\\.])"))
        for (part in parts) {
            val segment = part.trim()
            if (segment.isBlank()) continue
            if (segment.length > maxSegmentChars) {
                flushCurrent(current, chunks)
                var start = 0
                while (start < segment.length) {
                    val end = (start + maxSegmentChars).coerceAtMost(segment.length)
                    chunks += segment.substring(start, end)
                    start = end
                }
                continue
            }
            val separatorLength = if (current.isNotEmpty()) 1 else 0
            val appendedLength = current.length + separatorLength + segment.length
            if (appendedLength > maxSegmentChars && current.isNotEmpty()) {
                flushCurrent(current, chunks)
            }
            if (current.isNotEmpty()) {
                current.append(' ')
            }
            current.append(segment)
        }
        flushCurrent(current, chunks)
        return chunks
    }

    private fun flushCurrent(current: StringBuilder, chunks: MutableList<String>) {
        if (current.isNotEmpty()) {
            chunks += current.toString()
            current.clear()
        }
    }
}
