package com.lightningstudio.watchrss.ui.util

import com.lightningstudio.watchrss.data.rss.RssPreviewFormatter

private const val DEFAULT_MAX_CHARS = 60

fun formatRssSummary(raw: String?, maxChars: Int = DEFAULT_MAX_CHARS): String? {
    return RssPreviewFormatter.formatSummary(raw, maxChars)
}
