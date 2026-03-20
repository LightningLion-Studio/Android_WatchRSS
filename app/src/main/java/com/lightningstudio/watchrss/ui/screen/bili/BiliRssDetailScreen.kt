package com.lightningstudio.watchrss.ui.screen.bili

import android.graphics.Bitmap
import android.graphics.Paint
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.input.InstallRotaryLazyListHandler
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.WatchReadingBackgroundLight
import com.lightningstudio.watchrss.ui.theme.WatchReadingTextLight
import com.lightningstudio.watchrss.ui.theme.WatchTextPrimary
import com.lightningstudio.watchrss.ui.theme.rememberWatchTitleLineLimitsPx
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import com.lightningstudio.watchrss.ui.util.formatWatchTitleForWidthLimits
import com.lightningstudio.watchrss.ui.util.normalizeWatchTitleWhitespace
import com.lightningstudio.watchrss.ui.viewmodel.BiliDetailUiState
import kotlin.math.max

@Composable
fun BiliRssDetailScreen(
    uiState: BiliDetailUiState,
    readingThemeDark: Boolean,
    readingFontSizeSp: Int,
    onPlayClick: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit
) {
    val safePadding = WatchDimens.watch_safe_padding
    val pagePadding = WatchDimens.detail_page_horizontal_padding
    val blockSpacing = WatchDimens.detail_block_spacing
    val listState = rememberLazyListState()
    InstallRotaryLazyListHandler(listState)
    val isScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    val backgroundColor = if (readingThemeDark) Color.Black else WatchReadingBackgroundLight
    val textColor = if (readingThemeDark) WatchTextPrimary else WatchReadingTextLight
    val bodySize = readingFontSizeSp.sp
    val titleSize = textSize(R.dimen.hey_m_title)
    val actionTopSpacing = 15.dp
    val actionIconSize = 32.dp
    val actionIconPadding = watchDimensionResource(R.dimen.hey_distance_6dp)
    val activeColor = MaterialTheme.colorScheme.primary
    val actionContainerColor = if (readingThemeDark) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.White.copy(alpha = 0.96f)
    }
    val actionBorderColor = if (readingThemeDark) {
        Color.Transparent
    } else {
        Color(0xFFD9CFC3)
    }
    val activeActionContainerColor = if (readingThemeDark) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color(0xFFFFF0E6)
    }
    val activeActionBorderColor = if (readingThemeDark) {
        Color.Transparent
    } else {
        activeColor.copy(alpha = 0.6f)
    }
    val mediaCardContainerColor = if (readingThemeDark) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.White
    }
    val mediaCardBorderColor = if (readingThemeDark) {
        Color.Transparent
    } else {
        Color.Transparent
    }

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
    val density = LocalDensity.current
    val maxWidthPx = remember(context, density, pagePadding) {
        val paddingPx = with(density) { pagePadding.roundToPx() }
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
                Spacer(modifier = Modifier.height(watchDimensionResource(R.dimen.hey_distance_4dp)))
            }
            item(key = "title") {
                BiliRssDetailTitle(
                    title = display.title,
                    titlePadding = WatchDimens.detail_title_safe_padding,
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
                    containerColor = mediaCardContainerColor,
                    borderColor = mediaCardBorderColor,
                    topPadding = 0.dp,
                    isScrolling = isScrolling,
                    onClick = onPlayClick
                )
            }
            item(key = "actionSpacing") {
                Spacer(modifier = Modifier.height(actionTopSpacing))
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
                        containerColor = if (uiState.isFavorited) activeActionContainerColor else actionContainerColor,
                        borderColor = if (uiState.isFavorited) activeActionBorderColor else actionBorderColor,
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
                        containerColor = actionContainerColor,
                        borderColor = actionBorderColor,
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
    containerColor: Color,
    borderColor: Color,
    topPadding: Dp,
    isScrolling: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var cover by remember(poster, maxWidthPx) { mutableStateOf<Bitmap?>(null) }
    val ratio = cover?.let { it.width.toFloat() / it.height.toFloat() }
        ?: poster?.let { RssImageLoader.getCachedAspectRatio(it) }
    val coverHeight = watchDimensionResource(R.dimen.hey_card_large_height)
    val useDecoratedCard = borderColor.alpha > 0f
    val cardShape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_card_normal_bg_radius))
    val cardModifier = if (useDecoratedCard) {
        Modifier
            .clip(cardShape)
            .border(1.dp, borderColor, cardShape)
    } else {
        Modifier
    }

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
            .then(cardModifier)
            .background(containerColor)
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
                .size(watchDimensionResource(R.dimen.hey_listitem_widget_size))
        )
    }
}

@Composable
private fun CircleIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    containerColor: Color,
    borderColor: Color,
    size: Dp,
    padding: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (isPressed && enabled) {
        tint.copy(
            alpha = if (containerColor.red + containerColor.green + containerColor.blue > 1.8f) {
                0.08f
            } else {
                0.14f
            }
        ).compositeOver(containerColor)
    } else {
        containerColor
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (borderColor.alpha > 0f) Modifier.border(1.dp, borderColor, CircleShape) else Modifier
            )
            .clickableWithoutRipple(
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick
            ),
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
    return with(density) { watchDimensionResource(id).toSp() }
}

@Composable
private fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
): Modifier {
    return clickable(
        enabled = enabled,
        interactionSource = interactionSource,
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
    val titleStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = hintSize,
        lineHeight = max(
            MaterialTheme.typography.titleMedium.lineHeight.value,
            hintSize.value * 1.24f
        ).sp
    )
    val context = LocalContext.current
    val density = LocalDensity.current
    val titleSizePx = with(density) { watchDimensionResource(R.dimen.hey_m_title).toPx() }
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
        val (firstLimitPx, secondLimitPx) = remember(availableWidthPx, density) {
            rememberWatchTitleLineLimitsPx(availableWidthPx, density)
        }
        val formattedTitle = remember(title, availableWidthPx, paint) {
            formatWatchTitleForWidthLimits(
                title = title,
                paint = paint,
                availableWidthPx = availableWidthPx,
                firstLimitPx = firstLimitPx,
                secondLimitPx = secondLimitPx
            )
        }
        Text(
            text = formattedTitle,
            style = titleStyle,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
