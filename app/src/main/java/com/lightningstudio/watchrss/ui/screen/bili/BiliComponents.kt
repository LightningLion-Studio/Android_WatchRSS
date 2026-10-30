package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import com.lightningstudio.watchrss.ui.util.ValidatedNetworkRetryBudget
import com.lightningstudio.watchrss.ui.util.rememberValidatedNetworkRevision
import java.util.Locale
import androidx.compose.ui.layout.ContentScale

@Composable
fun BiliPillButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val radius = watchDimensionResource(R.dimen.hey_button_default_radius)
    val height = watchDimensionResource(R.dimen.hey_button_height)
    val horizontalPadding = watchDimensionResource(R.dimen.hey_button_mergin_horizontal)
    val verticalPadding = watchDimensionResource(R.dimen.hey_button_padding_vertical)
    val textSize = textSize(R.dimen.hey_s_title)
    val background = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .height(height.coerceAtLeast(48.dp))
            .clip(RoundedCornerShape(radius))
            .background(background)
            .clickable(
                enabled = enabled,
                onClickLabel = text,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = textSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BiliVideoCard(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    durationSeconds: Int?,
    onClick: () -> Unit
) {
    val cardRadius = watchDimensionResource(R.dimen.hey_card_normal_bg_radius)
    val coverHeight = watchDimensionResource(R.dimen.hey_card_large_height)
    val textPadding = watchDimensionResource(R.dimen.hey_distance_6dp)
    val titleSize = textSize(R.dimen.hey_s_title)
    val subtitleSize = textSize(R.dimen.hey_caption)
    val badgePadding = watchDimensionResource(R.dimen.hey_distance_4dp)
    val badgeTextSize = textSize(R.dimen.feed_card_action_text_size)
    val cardColor = MaterialTheme.colorScheme.surface
    val durationText = remember(durationSeconds) { formatDuration(durationSeconds) }
    val context = LocalContext.current
    val maxWidthPx = remember(context) { context.resources.displayMetrics.widthPixels.coerceAtLeast(1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardRadius))
            .background(cardColor)
            .clickable(
                role = Role.Button,
                onClickLabel = "打开视频：${title.ifBlank { "未命名视频" }}",
                onClick = onClick
            )
            .padding(textPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .clip(RoundedCornerShape(cardRadius))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            val networkRevision = rememberValidatedNetworkRevision(context)
            var retry by remember(coverUrl) { mutableIntStateOf(0) }
            var loadFailed by remember(coverUrl) { mutableStateOf(false) }
            val networkRetryBudget = remember(coverUrl) { ValidatedNetworkRetryBudget(networkRevision) }
            val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, coverUrl, maxWidthPx, networkRevision, retry) {
                value = if (coverUrl.isNullOrBlank() || !networkRetryBudget.shouldAttempt(networkRevision)) null else {
                    RssImageLoader.loadBitmap(
                        context = context,
                        url = coverUrl,
                        maxWidthPx = maxWidthPx,
                        retryAfterInFlightFailure = networkRevision > 0 || retry > 0
                    )
                }
                loadFailed = !coverUrl.isNullOrBlank() && value == null
            }
            val safeBitmap = bitmap
            if (safeBitmap != null) {
                Image(
                    bitmap = safeBitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth(),
                    alignment = Alignment.Center
                )
            } else if (loadFailed) {
                Text(
                    text = "封面加载失败 · 点击重试",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = subtitleSize,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clickable(role = Role.Button, onClickLabel = "重试封面加载") { retry++ }
                        .padding(badgePadding)
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(watchDimensionResource(R.dimen.hey_listitem_widget_size))
            )
            if (durationText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(badgePadding)
                        .clip(RoundedCornerShape(badgePadding))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                        .padding(horizontal = badgePadding, vertical = badgePadding / 2)
                ) {
                    Text(
                        text = durationText,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = badgeTextSize
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(textPadding))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = titleSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(badgePadding))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = subtitleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BiliFeedCard(
    title: String,
    summary: String,
    coverUrl: String?,
    isVisible: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null
) {
    val background = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_card_normal_bg_radius))
    val imageHeight = watchDimensionResource(R.dimen.feed_card_image_height)
    val padding = watchDimensionResource(R.dimen.hey_distance_8dp)
    val titleSize = textSize(R.dimen.feed_card_title_text_size)
    val summarySize = textSize(R.dimen.feed_card_summary_text_size)
    val summaryTop = watchDimensionResource(R.dimen.hey_distance_2dp)
    val overlay = Brush.verticalGradient(
        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
    )
    val context = LocalContext.current
    val maxWidthPx = remember(context) { context.resources.displayMetrics.widthPixels }
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickableWithRipple(
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = resolvedInteractionSource
            )
    ) {
        if (!coverUrl.isNullOrBlank()) {
            com.lightningstudio.watchrss.ui.screen.rss.RssThumbnail(
                url = coverUrl,
                maxWidthPx = maxWidthPx,
                isScrolling = false,
                isVisible = isVisible,
                modifier = Modifier.fillMaxWidth().height(imageHeight)
            )
        } else {
            Box(Modifier.fillMaxWidth().height(imageHeight).background(background))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .background(overlay)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(padding)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = summarySize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = summaryTop)
            )
        }
    }
}

@Stable
data class PressScaleState(
    val scale: Float,
    val interactionSource: MutableInteractionSource
)

@Composable
fun rememberPressScaleState(enabled: Boolean): PressScaleState {
    val interactionSource = remember { MutableInteractionSource() }
    if (!enabled) {
        return PressScaleState(1f, interactionSource)
    }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 240 else 360),
        label = "biliPressScale"
    )
    return PressScaleState(scale, interactionSource)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.clickableWithRipple(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier {
    return clickableWithRipple(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = remember { MutableInteractionSource() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.clickableWithRipple(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource
): Modifier {
    return if (onLongClick == null) {
        clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClickLabel = "打开视频",
            role = Role.Button,
            onClick = onClick
        )
    } else {
        combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClickLabel = "打开视频",
            onLongClickLabel = "视频操作",
            role = Role.Button,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}

@Composable
fun BiliStatsRow(
    viewCount: Long?,
    likeCount: Long?,
    danmakuCount: Long?
) {
    val captionSize = textSize(R.dimen.hey_caption)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val parts = listOfNotNull(
        viewCount?.let { "播放 ${formatBiliCount(it)}" },
        likeCount?.let { "赞 ${formatBiliCount(it)}" },
        danmakuCount?.let { "弹幕 ${formatBiliCount(it)}" }
    )
    if (parts.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(watchDimensionResource(R.dimen.hey_distance_4dp))) {
        parts.forEach { text ->
            Text(text = text, color = color, fontSize = captionSize)
        }
    }
}

@Composable
private fun textSize(id: Int): TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        watchDimensionResource(id).toSp()
    }
}

private fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val minutes = seconds / 60
    val remain = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, remain)
}

fun formatBiliCount(value: Long): String {
    return when {
        value >= 100_000_000L -> String.format(Locale.getDefault(), "%.1f亿", value / 100_000_000.0)
        value >= 10_000L -> String.format(Locale.getDefault(), "%.1f万", value / 10_000.0)
        else -> value.toString()
    }
}
