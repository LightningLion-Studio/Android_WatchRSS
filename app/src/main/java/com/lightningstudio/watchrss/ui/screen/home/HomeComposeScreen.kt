package com.lightningstudio.watchrss.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.ui.testing.HomeTestTags
import com.lightningstudio.watchrss.ui.util.formatTime

@Composable
fun HomeComposeScreen(
    channels: List<RssChannel>?,
    onChannelClick: (RssChannel) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(HomeTestTags.ROOT)
    ) {
        if (channels == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp
                )
            }
            return@Box
        }
        val loadedChannels = channels

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag(HomeTestTags.CHANNEL_LIST),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (loadedChannels.isEmpty()) {
                HomeTextCard(
                    title = "还没有 RSS 频道",
                    subtitle = "当前没有可显示的订阅源",
                    testTag = HomeTestTags.EMPTY_ENTRY
                )
            } else {
                loadedChannels.forEach { channel ->
                    val supporting = buildString {
                        append("${channel.unreadCount} 条未读")
                        channel.lastFetchedAt?.let {
                            append(" · 更新 ${formatTime(it)}")
                        }
                    }
                    HomeTextCard(
                        title = channel.title,
                        subtitle = channel.description?.takeIf { it.isNotBlank() } ?: channel.url,
                        supporting = supporting,
                        onClick = { onChannelClick(channel) },
                        testTag = HomeTestTags.channelCard(channel.id),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTextCard(
    title: String,
    subtitle: String,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    testTag: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
