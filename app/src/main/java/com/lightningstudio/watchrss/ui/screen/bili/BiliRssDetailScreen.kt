package com.lightningstudio.watchrss.ui.screen.bili

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.WatchBackgroundDeep
import com.lightningstudio.watchrss.ui.theme.WatchReadingBackgroundLight
import com.lightningstudio.watchrss.ui.theme.WatchReadingTextLight
import com.lightningstudio.watchrss.ui.theme.WatchTextPrimary
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import com.lightningstudio.watchrss.ui.viewmodel.BiliDetailUiState
import android.text.TextPaint
import kotlin.math.min

@Composable
fun BiliRssDetailScreen(
    uiState: BiliDetailUiState,
    readingThemeDark: Boolean,
    readingFontSizeSp: Int,
    onPlayClick: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val pagePadding = dimensionResource(R.dimen.detail_page_horizontal_padding)
    val blockSpacing = dimensionResource(R.dimen.detail_block_spacing)
    val listState = rememberLazyListState()
    val isScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    val backgroundColor = if (readingThemeDark) WatchBackgroundDeep else WatchReadingBackgroundLight
    val textColor = if (readingThemeDark) WatchTextPrimary else WatchReadingTextLight
    val bodySize = readingFontSizeSp.sp
    val titleSize = textSize(R.dimen.hey_m_title)
    val actionIconSize = 32.dp
    val actionIconPadding = dimensionResource(R.dimen.hey_distance_6dp)
    val activeColor = MaterialTheme.colorScheme.primary

    val detail = uiState.detail
    if (detail == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            val loadingText = if (uiState.isLoading) "加载中..." else {
                uiState.message?.trim().takeUnless { it.isNullOrBlank() } ?: "加载中..."
            }
            Text(
                text = loadingText,
                color = textColor,
                fontSize = titleSize,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }
    val display = remember(detail) {
        val title = detail.item.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: detail.item.bvid?.let { "BV号 $it" }
            ?: detail.item.aid?.let { "av$it" }
            ?: "哔哩哔哩视频"
        val owner = detail.item.owner?.name?.trim().takeUnless { it.isNullOrBlank() } ?: "未知作者"
        val desc = detail.desc?.trim().takeUnless { it.isNullOrBlank() } ?: "暂无简介"
        BiliRssDisplay(title = title, owner = owner, desc = desc)
    }
    val coverUrl = detail.item.cover
    val context = LocalContext.current
    val maxWidthPx = remember(context) {
        val paddingPx =
            context.resources.getDimensionPixelSize(R.dimen.detail_page_horizontal_padding)
        (context.resources.displayMetrics.widthPixels - paddingPx * 2).coerceAtLeast(1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = pagePadding)
        ) {
            item(key = "topSpacer") {
                Spacer(modifier = Modifier.height(safePadding))
            }
            item(key = "titleGap") {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.hey_distance_4dp)))
            }
            item(key = "title") {
                BiliRssDetailTitle(
                    title = display.title,
                    titlePadding = dimensionResource(R.dimen.detail_title_safe_padding),
                    textColor = textColor
                )
            }
            item(key = "contentGap") {
                Spacer(modifier = Modifier.height(blockSpacing))
            }
            item(key = "author") {
                Text(
                    text = "作者：${display.owner}",
                    color = textColor,
                    fontSize = bodySize,
                    style = TextStyle(textAlign = TextAlign.Start),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "desc") {
                Text(
                    text = "简介：${display.desc}",
                    color = textColor,
                    fontSize = bodySize,
                    style = TextStyle(textAlign = TextAlign.Start),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "video") {
                BiliRssVideoCard(
                    poster = coverUrl,
                    maxWidthPx = maxWidthPx,
                    topPadding = 0.dp,
                    isScrolling = isScrolling,
                    onClick = onPlayClick
                )
            }
            item(key = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(
                        iconRes = R.drawable.ic_action_favorite,
                        contentDescription = "收藏",
                        tint = if (uiState.isFavorited) activeColor else textColor,
                        size = actionIconSize,
                        padding = actionIconPadding,
                        enabled = !isScrolling,
                        onClick = onFavorite
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    CircleIconButton(
                        iconRes = R.drawable.ic_action_share,
                        contentDescription = "分享",
                        tint = textColor,
                        size = actionIconSize,
                        padding = actionIconPadding,
                        enabled = !isScrolling,
                        onClick = onShare
                    )
                }
            }
            if (!uiState.message.isNullOrBlank()) {
                item(key = "message") {
                    Text(
                        text = uiState.message.orEmpty(),
                        color = textColor.copy(alpha = 0.8f),
                        fontSize = bodySize,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item(key = "bottomSpacer") {
                Spacer(modifier = Modifier.height(blockSpacing))
            }
        }
    }
}

@Composable
private fun BiliRssVideoCard(
    poster: String?,
    maxWidthPx: Int,
    topPadding: Dp,
    isScrolling: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var cover by remember(poster, maxWidthPx) { mutableStateOf<Bitmap?>(null) }
    val ratio = cover?.let { it.width.toFloat() / it.height.toFloat() }
        ?: poster?.let { RssImageLoader.getCachedAspectRatio(it) }
    val coverHeight = dimensionResource(R.dimen.hey_card_large_height)
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_card_normal_bg_radius))

    LaunchedEffect(poster, maxWidthPx, isScrolling) {
        if (isScrolling) return@LaunchedEffect
        if (cover != null) return@LaunchedEffect
        if (poster.isNullOrBlank()) return@LaunchedEffect
        cover = RssImageLoader.loadBitmap(context, poster, maxWidthPx)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickableWithoutRipple(enabled = !isScrolling, onClick = onClick)
    ) {
        val bitmap = cover
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "视频封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (ratio != null && ratio > 0f) {
                            Modifier.aspectRatio(ratio)
                        } else {
                            Modifier.height(coverHeight)
                        }
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight)
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_play_circle),
            contentDescription = "播放",
            modifier = Modifier
                .align(Alignment.Center)
                .size(dimensionResource(R.dimen.hey_listitem_widget_size))
        )
    }
}

@Composable
private fun CircleIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    size: Dp,
    padding: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableWithoutRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
        )
    }
}

@Composable
private fun textSize(id: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { dimensionResource(id).toSp() }
}

@Composable
private fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    return clickable(
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

private data class BiliRssDisplay(
    val title: String,
    val owner: String,
    val desc: String
)

@Composable
private fun BiliRssDetailTitle(
    title: String,
    titlePadding: Dp,
    textColor: Color
) {
    val hintSize = textSize(R.dimen.hey_m_title)
    val context = LocalContext.current
    val density = LocalDensity.current
    val titleSizePx = with(density) { dimensionResource(R.dimen.hey_m_title).toPx() }
    val firstLimitPx = with(density) {
        dimensionResource(R.dimen.detail_title_first_line_max_width).toPx()
    }
    val secondLimitPx = with(density) {
        dimensionResource(R.dimen.detail_title_second_line_max_width).toPx()
    }
    val typeface = remember(context) { ResourcesCompat.getFont(context, R.font.watch_sans) }
    val paint = remember(typeface, titleSizePx) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = titleSizePx
            this.typeface = typeface
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = titlePadding)
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val formattedTitle = remember(title, availableWidthPx, titleSizePx, typeface) {
            formatTitleForWidthLimits(
                title = title,
                paint = paint,
                availableWidthPx = availableWidthPx,
                firstLimitPx = firstLimitPx,
                secondLimitPx = secondLimitPx
            )
        }
        Text(
            text = formattedTitle,
            color = textColor,
            fontSize = hintSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatTitleForWidthLimits(
    title: String,
    paint: TextPaint,
    availableWidthPx: Float,
    firstLimitPx: Float,
    secondLimitPx: Float
): String {
    val normalized = title.trim().replace('\n', ' ')
    if (normalized.isEmpty()) {
        return title
    }
    val cappedFirst = min(firstLimitPx, availableWidthPx)
    val cappedSecond = min(secondLimitPx, availableWidthPx)
    val lines = mutableListOf<String>()
    var start = 0
    var lineIndex = 0
    while (start < normalized.length) {
        val limitPx = if (lineIndex == 0) cappedFirst else cappedSecond
        val end = breakTextIndex(normalized, start, limitPx, paint)
        if (end <= start) {
            lines.add(normalized.substring(start, start + 1))
            start += 1
        } else {
            lines.add(normalized.substring(start, end))
            start = end
        }
        lineIndex++
    }
    balanceSingleCharLines(lines, paint, cappedFirst, cappedSecond)
    return lines.joinToString("\n")
}

private fun breakTextIndex(
    text: String,
    start: Int,
    maxWidthPx: Float,
    paint: TextPaint
): Int {
    if (start >= text.length || maxWidthPx <= 0f) {
        return text.length
    }
    val count = paint.breakText(text, start, text.length, true, maxWidthPx, null)
    if (count <= 0) {
        return start
    }
    var end = start + count
    while (end < text.length && text[end] == ' ') {
        end++
    }
    return end
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
