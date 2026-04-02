package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.res.ResourcesCompat
import android.graphics.Paint
import android.text.TextPaint
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.ActionButtonTextStyle
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.rememberWatchTitleLineLimitsPx
import com.lightningstudio.watchrss.ui.theme.watchActionButtonWidthFor
import com.lightningstudio.watchrss.ui.util.normalizeWatchTitleWhitespace
import kotlin.math.min

@Composable
fun ChannelDetailScreen(
    channel: RssChannel?,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onMarkRead: () -> Unit,
    onShare: () -> Unit
) {
    val safePadding = WatchDimens.watch_safe_padding
    val titleSpacing = WatchDimens.hey_distance_6dp
    val infoSpacing = WatchDimens.hey_distance_4dp
    val buttonSpacing = WatchDimens.hey_distance_4dp
    val buttonHeight = WatchDimens.watch_action_button_height
    val titleSize = textSize(R.dimen.hey_s_title)
    val context = LocalContext.current
    val density = LocalDensity.current
    val titleSizePx = with(density) { watchDimensionResource(R.dimen.hey_s_title).toPx() }
    val typeface = remember(context) { ResourcesCompat.getFont(context, R.font.watch_sans) }
    val paint = remember(typeface, titleSizePx) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = titleSizePx
            this.typeface = typeface
        }
    }
    val scrollState = rememberScrollState()

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
                .semantics { contentDescription = "频道详情页面" },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidthPx = with(density) { maxWidth.toPx() }
                val (firstLimitPx, secondLimitPx) = remember(availableWidthPx, density) {
                    rememberWatchTitleLineLimitsPx(availableWidthPx, density)
                }
                val formattedTitle = remember(channel?.title, availableWidthPx, titleSizePx, typeface) {
                    formatTitleForWidthLimits(
                        title = channel?.title ?: "加载中...",
                        paint = paint,
                        availableWidthPx = availableWidthPx,
                        firstLimitPx = firstLimitPx,
                        secondLimitPx = secondLimitPx
                    )
                }
                Text(
                    text = formattedTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = titleSize,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )
            }

            if (channel != null) {
                Spacer(modifier = Modifier.height(titleSpacing))
                Text(
                    text = channel.description ?: "暂无简介",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(infoSpacing))
                Text(
                    text = channel.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(infoSpacing))
                Text(
                    text = "更新: ${com.lightningstudio.watchrss.ui.util.formatTime(channel.lastFetchedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(infoSpacing))
                Text(
                    text = "未读 ${channel.unreadCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "未读数量 ${channel.unreadCount} 条" }
                        .testTag("unread_count"),
                    textAlign = TextAlign.Center
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val buttonWidth = watchActionButtonWidthFor(maxWidth)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(buttonSpacing))

                    ActionButton(
                        label = "设置",
                        enabled = channel != null,
                        width = buttonWidth,
                        height = buttonHeight,
                        onClick = onOpenSettings,
                        semanticLabel = "频道设置按钮"
                    )

                    Spacer(modifier = Modifier.height(buttonSpacing))

                    ActionButton(
                        label = "搜索",
                        enabled = channel != null,
                        width = buttonWidth,
                        height = buttonHeight,
                        onClick = onSearch,
                        semanticLabel = "频道内搜索按钮"
                    )

                    Spacer(modifier = Modifier.height(buttonSpacing))

                    ActionButton(
                        label = "分享",
                        enabled = channel != null,
                        width = buttonWidth,
                        height = buttonHeight,
                        onClick = onShare,
                        semanticLabel = "分享频道按钮"
                    )

                    Spacer(modifier = Modifier.height(buttonSpacing))

                    ActionButton(
                        label = "标记已读",
                        enabled = channel?.unreadCount?.let { it > 0 } ?: false,
                        width = buttonWidth,
                        height = buttonHeight,
                        onClick = onMarkRead,
                        semanticLabel = "标记全部已读按钮"
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    semanticLabel: String = label
) {
    val pillColor = watchColorResource(R.color.watch_pill_background)
    val pillRadius = WatchDimens.hey_button_default_radius
    val horizontalPadding = WatchDimens.hey_distance_6dp
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.6f)

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(pillRadius))
            .background(pillColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding)
            .semantics {
                contentDescription = semanticLabel
                stateDescription = if (enabled) "可用" else "禁用"
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = ActionButtonTextStyle,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun textSize(id: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { watchDimensionResource(id).toSp() }
}

private fun formatTitleForWidthLimits(
    title: String,
    paint: TextPaint,
    availableWidthPx: Float,
    firstLimitPx: Float,
    secondLimitPx: Float
): String {
    val normalized = normalizeWatchTitleWhitespace(title)
    if (normalized.isEmpty()) {
        return title
    }
    val firstLimit = min(firstLimitPx, availableWidthPx)
    val secondLimit = min(secondLimitPx, availableWidthPx)
    val lines = mutableListOf<String>()
    var start = 0
    var lineIndex = 0
    while (start < normalized.length) {
        val limit = if (lineIndex == 0) firstLimit else secondLimit
        val end = breakTextIndex(normalized, start, limit, paint)
        if (end <= start) {
            lines.add(normalized.substring(start, start + 1))
            start += 1
        } else {
            lines.add(normalized.substring(start, end))
            start = end
        }
        lineIndex++
    }
    balanceSingleCharLines(lines, paint, firstLimit, secondLimit)
    return lines.joinToString("\n")
}

private fun breakTextIndex(text: String, start: Int, widthPx: Float, paint: TextPaint): Int {
    var low = start
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        val current = text.substring(start, mid)
        if (paint.measureText(current) <= widthPx) {
            low = mid
        } else {
            high = mid - 1
        }
    }
    return low
}

private fun balanceSingleCharLines(
    lines: MutableList<String>,
    paint: TextPaint,
    firstLimitPx: Float,
    otherLimitPx: Float
) {
    var index = 1
    while (index < lines.size) {
        val current = lines[index]
        if (current.length == 1) {
            val prevIndex = index - 1
            val prev = lines[prevIndex]
            val prevLimit = if (prevIndex == 0) firstLimitPx else otherLimitPx
            val mergedPrev = prev + current
            if (paint.measureText(mergedPrev) <= prevLimit) {
                lines[prevIndex] = mergedPrev
                lines.removeAt(index)
                continue
            }
            if (prev.length > 1) {
                val shiftedPrev = prev.dropLast(1)
                val shiftedCurrent = prev.takeLast(1) + current
                val currentLimit = if (index == 0) firstLimitPx else otherLimitPx
                if (paint.measureText(shiftedCurrent) <= currentLimit) {
                    lines[prevIndex] = shiftedPrev
                    lines[index] = shiftedCurrent
                    if (prevIndex > 0) {
                        index--
                        continue
                    }
                }
            }
            if (index + 1 < lines.size) {
                val next = lines[index + 1]
                if (next.isNotEmpty()) {
                    val mergedCurrent = current + next.first()
                    val currentLimit = if (index == 0) firstLimitPx else otherLimitPx
                    if (paint.measureText(mergedCurrent) <= currentLimit) {
                        lines[index] = mergedCurrent
                        val remaining = next.substring(1)
                        if (remaining.isEmpty()) {
                            lines.removeAt(index + 1)
                            continue
                        } else {
                            lines[index + 1] = remaining
                        }
                    }
                }
            }
        }
        index++
    }
}
