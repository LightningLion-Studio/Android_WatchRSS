package com.lightningstudio.watchrss.ui.screen.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.novel.novelResumeIndex
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NovelContentsScreen(
    channel: RssChannel,
    items: List<RssItem>,
    loaded: Boolean,
    lastChapterUrl: String?,
    onChapterClick: (RssItem) -> Unit,
    onChapterLongClick: (RssItem) -> Unit,
    onHeaderClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val anchorIndex = remember(channel.url, items, lastChapterUrl) {
        novelResumeIndex(channel.url, items, lastChapterUrl)
    }
    var positionedChapter by remember(channel.url) { mutableStateOf<String?>(null) }
    val inset = with(LocalDensity.current) { (LocalConfiguration.current.screenHeightDp.dp / 3).roundToPx() }
    LaunchedEffect(channel.url, lastChapterUrl, anchorIndex) {
        if (lastChapterUrl != null && anchorIndex != null && positionedChapter != lastChapterUrl) {
            listState.scrollToItem(anchorIndex + 1, -inset)
            positionedChapter = lastChapterUrl
        }
    }
    InstallDigitalCrownLazyListHandler(listState)
    WatchSurface {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("novel-contents-list"),
            state = listState,
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().clickable(onClick = onHeaderClick).padding(vertical = 12.dp)) {
                    Text(channel.title, style = MaterialTheme.typography.titleLarge)
                    Text(if (!loaded) "正在加载目录…" else if (items.isEmpty()) "暂无章节" else "共 ${items.size} 章",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val isAnchor = index == anchorIndex
                val marker = if (item.link == lastChapterUrl) "上次阅读" else "上次位置附近"
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .then(if (isAnchor) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                        .combinedClickable(onClick = { onChapterClick(item) }, onLongClick = { onChapterLongClick(item) })
                        .padding(12.dp)
                        .testTag("novel-chapter-${item.id}")
                        .semantics {
                            contentDescription = "章节：${item.title}${if (item.isRead) "" else "，未读"}"
                        }
                ) {
                    if (isAnchor) Text(marker, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!item.isRead) Text("未读", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
