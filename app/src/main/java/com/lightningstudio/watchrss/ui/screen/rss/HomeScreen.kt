package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.ui.components.EmptyStateCard
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.ToastMessage
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.theme.BrandOrange
import com.lightningstudio.watchrss.ui.util.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HomeScreen(
    channels: StateFlow<List<RssChannel>>,
    isRefreshing: StateFlow<Boolean>,
    message: StateFlow<String?>,
    onRefreshAll: () -> Unit,
    onMessageShown: () -> Unit,
    onAddRss: () -> Unit,
    onOpenSettings: () -> Unit,
    onChannelClick: (RssChannel) -> Unit
) {
    val channelList by channels.collectAsState()
    val refreshing by isRefreshing.collectAsState()
    val toastMessage by message.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(1500)
            onMessageShown()
        }
    }

    WatchSurface {
        PullRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefreshAll,
            modifier = Modifier.fillMaxSize(),
            indicatorPadding = 9.dp,
            isAtTop = {
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "腕上RSS",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text(text = "设置", color = BrandOrange)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (refreshing) "同步中..." else "下拉刷新全部频道",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (channelList.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "还没有 RSS 频道",
                                subtitle = "点击下方添加你的第一个订阅源"
                            )
                        }
                    } else {
                        items(channelList, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                onClick = { onChannelClick(channel) }
                            )
                        }
                    }

                    item {
                        AddRssCard(onAdd = onAddRss)
                    }
                }
            }
        }

        toastMessage?.let { messageText ->
            ToastMessage(text = messageText)
        }
    }
}

@Composable
private fun ChannelCard(
    channel: RssChannel,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = channel.description ?: channel.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "更新: ${formatTime(channel.lastFetchedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddRssCard(onAdd: () -> Unit) {
    Surface(
        onClick = onAdd,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "添加 RSS",
                style = MaterialTheme.typography.titleMedium,
                color = BrandOrange
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "手动输入或粘贴订阅地址",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
