package com.lightningstudio.watchrss.data.note

import org.jsoup.Jsoup

/** Markdown/HTML references are authoritative; asset indexes can be missing after a remote restore. */
private val watchMarkdownImageRegex = Regex(
    """!\[((?:\\.|[^\]])*)\]\(\s*(?:<([^>]+)>|([^\s)]+))(?:\s+"[^"]*")?\s*\)"""
)

internal fun watchMarkdownImageMatches(markdown: String): List<MatchResult> {
    val codeRanges = noteMarkdownCodeRanges(markdown)
    return watchMarkdownImageRegex.findAll(markdown)
        .filterNot { match -> codeRanges.any { match.range.first in it } }
        .toList()
}

internal fun noteAssetKeys(markdown: String): Set<String> {
    val codeRanges = noteMarkdownCodeRanges(markdown)
    val markupWithoutCode = StringBuilder(markdown).apply {
        codeRanges.forEach { range ->
            for (index in range) setCharAt(index, ' ')
        }
    }
    val sources = buildList {
        watchMarkdownImageMatches(markdown)
            .forEach { add(it.groupValues[2].ifEmpty { it.groupValues[3] }) }
        Jsoup.parse(markupWithoutCode.toString()).select("img[src]").forEach { add(it.attr("src")) }
    }
    return sources.filter { it.startsWith("assets/") }.mapTo(linkedSetOf()) {
        noteAssetStorageKey(it.removePrefix("assets/"))
    }
}

private fun noteMarkdownCodeRanges(markdown: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var fence: String? = null
    var fenceStart = 0
    var offset = 0
    markdown.splitToSequence('\n').forEach { line ->
        val marker = line.trimStart().take(3)
        if (marker == "\u0060\u0060\u0060" || marker == "~~~") {
            if (fence == null) {
                fence = marker
                fenceStart = offset
            } else if (fence == marker) {
                if (fenceStart < offset + line.length) {
                    ranges += fenceStart until (offset + line.length)
                }
                fence = null
            }
        } else if (fence == null) {
            Regex("\\x60+[^\\x60]*\\x60+").findAll(line).forEach {
                ranges += (offset + it.range.first)..(offset + it.range.last)
            }
        }
        offset += line.length + 1
    }
    if (fence != null && fenceStart < markdown.length) {
        ranges += fenceStart until markdown.length
    }
    return ranges
}

internal fun noteAssetStorageKey(key: String): String {
    require(key.isNotBlank() && key != "." && key != ".." && '/' !in key && '\\' !in key && '\u0000' !in key && key.toByteArray().size <= 240) {
        "备忘录图片路径无效"
    }
    return key
}
