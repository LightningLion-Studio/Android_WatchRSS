package com.lightningstudio.watchrss.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BiliFormatUtils {

    fun formatCount(count: Long?): String {
        if (count == null || count < 0) return "--"
        return when {
            count < 10000 -> count.toString()
            count < 100000000 -> String.format(Locale.getDefault(), "%.1f万", count / 10000.0)
            else -> String.format(Locale.getDefault(), "%.1f亿", count / 100000000.0)
        }
    }

    fun formatCommentTime(timestamp: Long?): String {
        if (timestamp == null) return ""
        val now = System.currentTimeMillis() / 1000
        val diff = now - timestamp

        return when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${diff / 60}分钟前"
            diff < 86400 -> "${diff / 3600}小时前"
            diff < 2592000 -> "${diff / 86400}天前"
            else -> {
                val date = Date(timestamp * 1000)
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
            }
        }
    }

    fun parseVipColor(color: String?): Color {
        if (color.isNullOrBlank()) return Color.Unspecified
        return try {
            val colorInt = android.graphics.Color.parseColor(color)
            Color(colorInt)
        } catch (e: Exception) {
            Color.Unspecified
        }
    }

    fun isVideoId(keyword: String): Boolean {
        val trimmed = keyword.trim()
        return trimmed.matches(Regex("^(av|AV)\\d+$")) ||
                trimmed.matches(Regex("^(bv|BV)[a-zA-Z0-9]+$"))
    }

    fun parseVideoId(keyword: String): Pair<Long?, String?> {
        val trimmed = keyword.trim()
        return when {
            trimmed.matches(Regex("^(av|AV)(\\d+)$", RegexOption.IGNORE_CASE)) -> {
                val aid = trimmed.substring(2).toLongOrNull()
                Pair(aid, null)
            }
            trimmed.matches(Regex("^(bv|BV)([a-zA-Z0-9]+)$", RegexOption.IGNORE_CASE)) -> {
                val bvid = trimmed.substring(0, 2).uppercase() + trimmed.substring(2)
                Pair(null, bvid)
            }
            else -> Pair(null, null)
        }
    }

    fun formatDuration(duration: String?): String {
        if (duration.isNullOrBlank()) return "--"
        return duration
    }

    fun formatDuration(seconds: Int?): String {
        if (seconds == null || seconds < 0) return "--"
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }

    fun parseKeywordHighlight(text: String?, highlightColor: Color): AnnotatedString {
        if (text.isNullOrBlank()) return AnnotatedString("")

        return buildAnnotatedString {
            var currentIndex = 0
            val keywordPattern = Regex("""<em class="keyword">(.*?)</em>""")

            keywordPattern.findAll(text).forEach { matchResult ->
                // Add text before the keyword
                if (matchResult.range.first > currentIndex) {
                    append(text.substring(currentIndex, matchResult.range.first))
                }

                // Add highlighted keyword
                val keyword = matchResult.groupValues[1]
                withStyle(style = SpanStyle(color = highlightColor)) {
                    append(keyword)
                }

                currentIndex = matchResult.range.last + 1
            }

            // Add remaining text after last keyword
            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }

    fun stripKeywordHighlight(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val keywordPattern = Regex("""<em class="keyword">(.*?)</em>""")
        return keywordPattern.replace(text) { matchResult ->
            matchResult.groupValues[1]
        }
    }
}
