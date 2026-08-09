package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.settings.MAX_READER_AUTO_SCROLL_LINES_PER_SECOND
import com.lightningstudio.watchrss.data.settings.MIN_READER_AUTO_SCROLL_LINES_PER_SECOND
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.WatchSwitch
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import java.util.Locale

@Composable
fun AutoScrollControlScreen(
    autoStartEnabled: Boolean,
    linesPerSecond: Float,
    readerSessionActive: Boolean,
    isPlaying: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onAutoStartEnabledChange: (Boolean) -> Unit,
    onLinesPerSecondChange: (Float) -> Unit,
    onReturnToArticle: (() -> Unit)? = null
) {
    WatchSurface(pureBlack = true) {
        if (readerSessionActive) {
            AutoScrollReaderControlContent(
                autoStartEnabled = autoStartEnabled,
                linesPerSecond = linesPerSecond,
                isPlaying = isPlaying,
                onPlayingChange = onPlayingChange,
                onAutoStartEnabledChange = onAutoStartEnabledChange,
                onLinesPerSecondChange = onLinesPerSecondChange,
                onReturnToArticle = onReturnToArticle ?: {}
            )
        } else {
            AutoScrollSettingsContent(
                autoStartEnabled = autoStartEnabled,
                linesPerSecond = linesPerSecond,
                onAutoStartEnabledChange = onAutoStartEnabledChange,
                onLinesPerSecondChange = onLinesPerSecondChange
            )
        }
    }
}

@Composable
private fun AutoScrollReaderControlContent(
    autoStartEnabled: Boolean,
    linesPerSecond: Float,
    isPlaying: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onAutoStartEnabledChange: (Boolean) -> Unit,
    onLinesPerSecondChange: (Float) -> Unit,
    onReturnToArticle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "自动滚动控制",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.SwipeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 9.dp)
            ) {
                Text(
                    text = "当前文章",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (isPlaying) "正在自动滚动" else "自动滚动已暂停",
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AutoScrollRoundIconButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停自动滚动" else "启动自动滚动",
                size = 46.dp,
                selected = true,
                onClick = { onPlayingChange(!isPlaying) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "自动开始",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            WatchSwitch(
                checked = autoStartEnabled,
                onCheckedChange = onAutoStartEnabledChange
            )
        }

        AutoScrollSpeedControls(
            linesPerSecond = linesPerSecond,
            onLinesPerSecondChange = onLinesPerSecondChange,
            showUnit = true
        )

        Spacer(modifier = Modifier.height(5.dp))

        AutoScrollTextAction(
            text = "返回文章",
            onClick = onReturnToArticle
        )
    }
}

@Composable
private fun AutoScrollSettingsContent(
    autoStartEnabled: Boolean,
    linesPerSecond: Float,
    onAutoStartEnabledChange: (Boolean) -> Unit,
    onLinesPerSecondChange: (Float) -> Unit
) {
    val scrollState = rememberScrollState()
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val entrySpacing = WatchDimens.hey_distance_8dp
    val valueSpacing = WatchDimens.hey_distance_4dp
    val valueIndent = WatchDimens.hey_distance_10dp

    InstallDigitalCrownScrollHandler(scrollState)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(safePadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SettingsHeader(title = "自动滚动控制")

        Spacer(modifier = Modifier.height(WatchDimens.hey_content_horizontal_distance))

        WatchSettingsPillRow(
            label = "打开文章自动开始",
            endPaddingMultiplier = 1.5f
        ) {
            WatchSwitch(
                checked = autoStartEnabled,
                onCheckedChange = onAutoStartEnabledChange
            )
        }
        Text(
            text = if (autoStartEnabled) {
                "已开启，进入文章并恢复阅读位置后自动滚动"
            } else {
                "已关闭，仍可在阅读器中手动启动"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = valueIndent, top = valueSpacing)
        )

        Spacer(modifier = Modifier.height(entrySpacing))

        WatchSettingsPillRow(label = "速度") {
            AutoScrollSpeedControls(
                linesPerSecond = linesPerSecond,
                onLinesPerSecondChange = onLinesPerSecondChange
            )
        }
        Text(
            text = "每秒 ${formatAutoScrollSpeed(linesPerSecond)} 行，范围 " +
                "${formatAutoScrollSpeed(MIN_READER_AUTO_SCROLL_LINES_PER_SECOND)}–" +
                "${formatAutoScrollSpeed(MAX_READER_AUTO_SCROLL_LINES_PER_SECOND)} 行",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = valueIndent, top = valueSpacing)
        )

        Spacer(modifier = Modifier.height(entrySpacing))

        Text(
            text = "你也可以在阅读器中双击进入自动滚动控制",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = valueIndent)
        )

        Spacer(modifier = Modifier.height(WatchDimens.hey_multiple_item_height))
    }
}

@Composable
private fun AutoScrollSpeedControls(
    linesPerSecond: Float,
    onLinesPerSecondChange: (Float) -> Unit,
    showUnit: Boolean = false
) {
    Row(
        modifier = Modifier.height(36.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoScrollRoundIconButton(
            icon = Icons.Filled.Remove,
            contentDescription = "降低自动滚动速度",
            size = 32.dp,
            enabled = linesPerSecond > MIN_READER_AUTO_SCROLL_LINES_PER_SECOND,
            onClick = {
                onLinesPerSecondChange(
                    (linesPerSecond - AUTO_SCROLL_SPEED_STEP)
                        .coerceAtLeast(MIN_READER_AUTO_SCROLL_LINES_PER_SECOND)
                )
            }
        )
        Text(
            text = if (showUnit) {
                "${formatAutoScrollSpeed(linesPerSecond)} 行/秒"
            } else {
                formatAutoScrollSpeed(linesPerSecond)
            },
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(
                if (showUnit) AUTO_SCROLL_SPEED_WITH_UNIT_WIDTH else AUTO_SCROLL_SPEED_VALUE_WIDTH
            )
        )
        AutoScrollRoundIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "提高自动滚动速度",
            size = 32.dp,
            enabled = linesPerSecond < MAX_READER_AUTO_SCROLL_LINES_PER_SECOND,
            onClick = {
                onLinesPerSecondChange(
                    (linesPerSecond + AUTO_SCROLL_SPEED_STEP)
                        .coerceAtMost(MAX_READER_AUTO_SCROLL_LINES_PER_SECOND)
                )
            }
        )
    }
}

@Composable
private fun AutoScrollRoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: Dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(0xFF242424)
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color.White
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground.copy(alpha = if (enabled) 1f else 0.45f),
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
private fun AutoScrollTextAction(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = text
                role = Role.Button
                stateDescription = "可点击"
            }
            .padding(top = 8.dp)
    )
}

internal fun formatAutoScrollSpeed(linesPerSecond: Float): String =
    String.format(Locale.US, "%.1f", linesPerSecond)

private const val AUTO_SCROLL_SPEED_STEP = 0.5f
private val AUTO_SCROLL_SPEED_VALUE_WIDTH = 54.dp
private val AUTO_SCROLL_SPEED_WITH_UNIT_WIDTH = 96.dp
