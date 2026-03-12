package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.ui.components.SearchInputBar
import com.lightningstudio.watchrss.ui.components.WatchSurface

@Composable
fun RssSearchScreen(
    keyword: String,
    results: List<RssItem>,
    onKeywordChange: (String) -> Unit,
    onItemClick: (RssItem) -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val titleSize = textSize(R.dimen.hey_s_title)
    val listSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_6dp

    WatchSurface {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = safePadding,
                top = safePadding,
                end = safePadding,
                bottom = safePadding + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(listSpacing)
        ) {
            item {
                Text(
                    text = "搜索",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = titleSize,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SearchInputBar(
                    keyword = keyword,
                    onKeywordChange = onKeywordChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when {
                keyword.isBlank() -> {
                    item { EmptyHint(text = "输入关键词开始搜索") }
                }
                results.isEmpty() -> {
                    item { EmptyHint(text = "没有找到相关结果") }
                }
                else -> {
                    items(
                        items = results,
                        key = { it.id }
                    ) { item ->
                        SearchResultCard(
                            item = item,
                            keyword = keyword,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: RssItem,
    keyword: String,
    onClick: () -> Unit
) {
    val background = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_card_normal_bg_radius))
    val padding = dimensionResource(R.dimen.hey_content_horizontal_distance)
    val titleSize = textSize(R.dimen.feed_card_title_text_size)
    val summarySize = textSize(R.dimen.feed_card_summary_text_size)
    val summaryLineHeight = summarySize * 1.1f
    val summaryTop = dimensionResource(R.dimen.hey_distance_2dp)
    val highlightColor = MaterialTheme.colorScheme.primary
    val snippet = remember(item.id, item.summary, item.description, item.content, keyword) {
        buildSearchSnippet(item, keyword)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(padding)
    ) {
        Text(
            text = buildHighlightedText(item.title, keyword, highlightColor),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = titleSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildHighlightedText(snippet, keyword, highlightColor),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = summarySize,
            lineHeight = summaryLineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = summaryTop)
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun buildSearchSnippet(item: RssItem, keyword: String): String {
    val normalizedKeyword = keyword.trim()
    val candidates = listOf(item.description, item.content, item.summary)
    if (normalizedKeyword.isNotEmpty()) {
        candidates.forEach { value ->
            if (!value.isNullOrBlank()) {
                val cleaned = normalizeSearchText(value)
                if (cleaned.contains(normalizedKeyword, ignoreCase = true)) {
                    return extractSnippetAroundMatch(cleaned, normalizedKeyword)
                }
            }
        }
    }
    val fallback = candidates.firstOrNull { !it.isNullOrBlank() }?.let { normalizeSearchText(it) } ?: "暂无摘要"
    return sanitizeSnippet(fallback)
}

private fun sanitizeSnippet(text: String): String {
    val compact = text.trim().replace(Regex("\\s+"), " ")
    return if (compact.length > MAX_SNIPPET_LENGTH) {
        compact.take(MAX_SNIPPET_LENGTH) + "..."
    } else {
        compact
    }
}

private fun extractSnippetAroundMatch(text: String, keyword: String): String {
    val compact = text.trim().replace(Regex("\\s+"), " ")
    if (compact.isEmpty()) return compact
    val lower = compact.lowercase()
    val query = keyword.lowercase()
    val index = lower.indexOf(query)
    if (index < 0) return sanitizeSnippet(compact)
    val prefixContext = ((MAX_SNIPPET_LENGTH / 4).coerceAtLeast(12)).coerceAtMost(index)
    var start = (index - prefixContext).coerceAtLeast(0)
    var end = (start + MAX_SNIPPET_LENGTH).coerceAtMost(compact.length)
    if (end - start < MAX_SNIPPET_LENGTH && start > 0) {
        start = (end - MAX_SNIPPET_LENGTH).coerceAtLeast(0)
    }
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < compact.length) "..." else ""
    return prefix + compact.substring(start, end) + suffix
}

private fun normalizeSearchText(text: String): String {
    val withoutTags = text.replace(Regex("<[^>]*>"), " ")
    return withoutTags
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

private fun buildHighlightedText(text: String, keyword: String, highlightColor: Color): AnnotatedString {
    val query = keyword.trim()
    if (query.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val builder = AnnotatedString.Builder()
    var index = lowerText.indexOf(lowerQuery)
    var start = 0
    while (index >= 0) {
        if (index > start) {
            builder.append(text.substring(start, index))
        }
        builder.pushStyle(
            SpanStyle(
                background = highlightColor,
                color = Color.Black
            )
        )
        builder.append(text.substring(index, index + query.length))
        builder.pop()
        start = index + query.length
        index = lowerText.indexOf(lowerQuery, start)
    }
    if (start < text.length) {
        builder.append(text.substring(start))
    }
    return builder.toAnnotatedString()
}

@Composable
private fun textSize(id: Int): TextUnit {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return with(density) { dimensionResource(id).toSp() }
}

private const val MAX_SNIPPET_LENGTH = 140
