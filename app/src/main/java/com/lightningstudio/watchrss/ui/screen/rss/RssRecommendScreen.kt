package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.rss.RssRecommendChannel
import com.lightningstudio.watchrss.data.rss.RssRecommendGroup
import com.lightningstudio.watchrss.ui.components.WatchSurface

@Composable
fun RssRecommendScreen(
    groups: List<RssRecommendGroup>,
    onGroupClick: (RssRecommendGroup) -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val spacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp

    WatchSurface {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(safePadding),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            item {
                RecommendHeader(title = "RSS推荐", hint = "点击媒体查看频道")
            }
            items(groups) { group ->
                RecommendGroupCard(group = group, onClick = { onGroupClick(group) })
            }
        }
    }
}

@Composable
fun RssRecommendGroupScreen(
    group: RssRecommendGroup,
    onAddChannel: (RssRecommendChannel) -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val spacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_6dp
    val startIndent = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance

    WatchSurface {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(safePadding),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            item {
                RecommendHeader(title = "RSS推荐", hint = group.name)
            }
            items(group.channels) { channel ->
                RecommendChannelCard(
                    channel = channel,
                    modifier = Modifier.padding(start = startIndent),
                    onAddClick = { onAddChannel(channel) }
                )
            }
        }
    }
}

@Composable
private fun RecommendHeader(title: String, hint: String) {
    val padding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecommendGroupCard(group: RssRecommendGroup, onClick: () -> Unit) {
    val cardColor = MaterialTheme.colorScheme.surface
    val cardRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_card_normal_bg_radius
    val horizontalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_12dp
    val verticalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val summarySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_2dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardRadius))
            .background(cardColor)
            .clickable(onClick = onClick)
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = verticalPadding,
                bottom = verticalPadding
            )
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!group.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(summarySpacing))
            Text(
                text = group.description.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendChannelCard(
    channel: RssRecommendChannel,
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit
) {
    val cardColor = MaterialTheme.colorScheme.surface
    val cardRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_card_normal_bg_radius
    val horizontalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_12dp
    val endPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val verticalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val iconSize = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_20dp
    val iconSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val summarySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_2dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardRadius))
            .background(cardColor)
            .padding(
                start = horizontalPadding,
                end = endPadding,
                top = verticalPadding,
                bottom = verticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(summarySpacing))
            Text(
                text = channel.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(iconSpacing))
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
