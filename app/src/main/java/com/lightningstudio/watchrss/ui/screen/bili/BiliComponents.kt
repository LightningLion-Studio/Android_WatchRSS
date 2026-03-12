package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import androidx.compose.ui.layout.ContentScale

@Composable
fun BiliPillButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val radius = dimensionResource(R.dimen.hey_button_default_radius)
    val height = dimensionResource(R.dimen.hey_button_height)
    val horizontalPadding = dimensionResource(R.dimen.hey_button_mergin_horizontal)
    val verticalPadding = dimensionResource(R.dimen.hey_button_padding_vertical)
    val textSize = textSize(R.dimen.hey_s_title)
    val background = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
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
    val cardRadius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val coverHeight = dimensionResource(R.dimen.hey_card_large_height)
    val textPadding = dimensionResource(R.dimen.hey_distance_6dp)
    val titleSize = textSize(R.dimen.hey_s_title)
    val subtitleSize = textSize(R.dimen.hey_caption)
    val badgePadding = dimensionResource(R.dimen.hey_distance_4dp)
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
            .clickable(onClick = onClick)
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
            val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, coverUrl, maxWidthPx) {
                value = if (coverUrl.isNullOrBlank()) null else {
                    RssImageLoader.loadBitmap(context = context, url = coverUrl, maxWidthPx = maxWidthPx)
                }
            }
            val safeBitmap = bitmap
            if (safeBitmap != null) {
                Image(
                    bitmap = safeBitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth(),
                    alignment = Alignment.Center
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_play_circle),
                contentDescription = "播放",
                modifier = Modifier.size(dimensionResource(R.dimen.hey_listitem_widget_size))
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
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val background = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_card_normal_bg_radius))
    val imageHeight = dimensionResource(R.dimen.feed_card_image_height)
    val padding = dimensionResource(R.dimen.hey_distance_8dp)
    val titleSize = textSize(R.dimen.feed_card_title_text_size)
    val summarySize = textSize(R.dimen.feed_card_summary_text_size)
    val summaryTop = dimensionResource(R.dimen.hey_distance_2dp)
    val overlay = Brush.verticalGradient(
        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
    )
    val context = LocalContext.current
    val maxWidthPx = remember(context) { context.resources.displayMetrics.widthPixels }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickableWithoutRipple(onClick, onLongClick)
    ) {
        val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, coverUrl, maxWidthPx) {
            value = if (coverUrl.isNullOrBlank()) null else {
                RssImageLoader.loadBitmap(context, coverUrl, maxWidthPx)
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                .background(MaterialTheme.colorScheme.surface)
            )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = summarySize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = summaryTop)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.clickableWithoutRipple(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return if (onLongClick == null) {
        clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        combinedClickable(
            interactionSource = interactionSource,
            indication = null,
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
    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.hey_distance_4dp))) {
        parts.forEach { text ->
            Text(text = text, color = color, fontSize = captionSize)
        }
    }
}

@Composable
private fun textSize(id: Int): TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        dimensionResource(id).toSp()
    }
}

private fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val minutes = seconds / 60
    val remain = seconds % 60
    return String.format("%02d:%02d", minutes, remain)
}

fun formatBiliCount(value: Long): String {
    return when {
        value >= 100_000_000L -> String.format("%.1f亿", value / 100_000_000.0)
        value >= 10_000L -> String.format("%.1f万", value / 10_000.0)
        else -> value.toString()
    }
}
