package com.lightningstudio.watchrss.ui.screen.douyin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryEntry
import com.lightningstudio.watchrss.ui.components.WatchButton
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.screen.bili.formatBiliCount
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchActionButtonWidthFor
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.formatTime

@Composable
fun DouyinHistoryScreen(
    items: List<DouyinWatchHistoryEntry>,
    onItemClick: (DouyinWatchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit
) {
    val safePadding = WatchDimens.watch_safe_padding
    val itemSpacing = watchDimensionResource(R.dimen.hey_distance_6dp)
    val listState = rememberLazyListState()

    InstallDigitalCrownLazyListHandler(listState)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
        state = listState,
        contentPadding = PaddingValues(
            start = safePadding,
            top = safePadding,
            end = safePadding,
            bottom = safePadding + 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        item {
            HistoryHeader()
        }
        if (items.isNotEmpty()) {
            item {
                ClearHistoryButton(onClick = onClearHistory)
            }
            items(items, key = { it.awemeId }) { entry ->
                DouyinHistoryItemRow(
                    entry = entry,
                    onClick = { onItemClick(entry) }
                )
            }
        } else {
            item {
                EmptyHistory()
            }
        }
    }
}

@Composable
private fun HistoryHeader() {
    val padding = watchDimensionResource(R.dimen.hey_distance_4dp)
    val titleSize = textSize(R.dimen.settings_title_text_size)
    val hintSize = textSize(R.dimen.hey_s_desription)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "播放历史",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "保存在本地，记录最近播放内容",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = hintSize,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ClearHistoryButton(onClick: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val buttonWidth = watchActionButtonWidthFor(maxWidth)
        WatchButton(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(WatchDimens.hey_button_default_radius),
            modifier = Modifier
                .width(buttonWidth)
                .height(WatchDimens.watch_action_button_height)
        ) {
            Text(
                text = "清空历史",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = textSize(R.dimen.hey_s_desription),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DouyinHistoryItemRow(
    entry: DouyinWatchHistoryEntry,
    onClick: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(WatchDimens.hey_card_normal_bg_radius)
    val paddingStart = WatchDimens.hey_content_horizontal_distance_6_0
    val paddingEnd = watchDimensionResource(R.dimen.hey_listitem_padding_right)
    val verticalPadding = watchDimensionResource(R.dimen.hey_multiple_default_summary_alone_padding_vertical)
    val titleSize = textSize(R.dimen.hey_s_title)
    val summarySize = textSize(R.dimen.hey_m_desription)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(
                start = paddingStart,
                end = paddingEnd,
                top = verticalPadding,
                bottom = verticalPadding
            )
    ) {
        Text(
            text = entry.title?.ifBlank { "抖音视频" } ?: "抖音视频",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = titleSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = remember(entry) { buildHistorySummary(entry) },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = summarySize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyHistory() {
    Text(
        text = "暂无播放历史",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = textSize(R.dimen.hey_m_desription),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(watchDimensionResource(R.dimen.hey_content_horizontal_distance))
    )
}

@Composable
private fun textSize(id: Int): androidx.compose.ui.unit.TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        watchDimensionResource(id).toSp()
    }
}

private fun buildHistorySummary(entry: DouyinWatchHistoryEntry): String {
    val author = entry.author?.takeIf { it.isNotBlank() } ?: "未知作者"
    val watchedAt = formatTime(entry.watchedAt)
    val like = if (entry.likeCount > 0) "赞 ${formatBiliCount(entry.likeCount)}" else null
    return listOfNotNull("$author · $watchedAt", like).joinToString("\n")
}
