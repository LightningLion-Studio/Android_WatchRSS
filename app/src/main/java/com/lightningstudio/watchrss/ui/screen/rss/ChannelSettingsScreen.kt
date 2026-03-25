package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSwitch
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource

@Composable
fun ChannelSettingsScreen(
    showOriginalContent: Boolean,
    originalContentEnabled: Boolean,
    onToggleOriginalContent: () -> Unit,
    deleteEnabled: Boolean,
    onDelete: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val entrySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val valueSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp
    val valueIndent = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val spacerHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_20dp
    val scrollState = rememberScrollState()

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
        ) {
            SettingsHeader()

            if (showOriginalContent) {
                Spacer(modifier = Modifier.height(sectionSpacing))
                SettingsPillRow(label = "原文阅读模式", endPaddingMultiplier = 1.5f) {
                    WatchSwitch(
                        checked = originalContentEnabled,
                        onCheckedChange = { onToggleOriginalContent() }
                    )
                }
                Text(
                    text = "刷新时抓取原文正文与图片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                )
            }

            Spacer(modifier = Modifier.height(entrySpacing))

            SettingsDangerRow(
                label = "删除频道",
                icon = Icons.Outlined.Delete,
                enabled = deleteEnabled,
                onClick = onDelete
            )

            Spacer(modifier = Modifier.height(spacerHeight))
        }
    }
}

@Composable
private fun SettingsHeader() {
    val padding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "频道设置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsPillRow(
    label: String,
    endPaddingMultiplier: Float = 1f,
    content: @Composable RowScope.() -> Unit
) {
    val pillColor = watchColorResource(R.color.watch_pill_background)
    val pillRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_button_default_radius
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val startPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val endPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp * endPaddingMultiplier
    val verticalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(pillHeight)
            .clip(RoundedCornerShape(pillRadius))
            .background(pillColor)
            .padding(
                start = startPadding,
                end = endPadding,
                top = verticalPadding,
                bottom = verticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        content()
    }
}

@Composable
private fun SettingsDangerRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val pillColor = watchColorResource(R.color.watch_pill_background)
    val pillRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_button_default_radius
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val startPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val endPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val verticalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val background = pillColor
    val textColor = watchColorResource(R.color.danger_red).copy(alpha = if (enabled) 1f else 0.6f)
    val iconSize = watchDimensionResource(R.dimen.hey_listitem_lefticon_height_width)
    val iconSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(pillHeight)
            .clip(RoundedCornerShape(pillRadius))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                start = startPadding,
                end = endPadding,
                top = verticalPadding,
                bottom = verticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(iconSpacing))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
