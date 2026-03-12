package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Paint
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.RssUrlResolver
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.SwipeActionButton
import com.lightningstudio.watchrss.ui.components.SwipeActionRow
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.min
import kotlin.math.roundToInt

private const val FEED_PREFETCH_BEFORE = 2
private const val FEED_PREFETCH_AFTER = 6
private const val FEED_PREFETCH_LIMIT = 8
private const val CHANNEL_TITLE_CLICK_HINT_SYMBOL = "ⓘ"

@Composable
fun FeedScreen(
    channel: RssChannel?,
    items: List<RssItem>,
    isRefreshing: Boolean,
    hasMore: Boolean,
    openSwipeId: Long?,
    onOpenSwipe: (Long) -> Unit,
    onCloseSwipe: () -> Unit,
    draggingSwipeId: Long?,
    onDragStart: (Long) -> Unit,
    onDragEnd: () -> Unit,
    onHeaderClick: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (RssItem) -> Unit,
    onItemLongClick: (RssItem) -> Unit,
    onFavoriteClick: (RssItem) -> Unit,
    onWatchLaterClick: (RssItem) -> Unit,
    onBack: () -> Unit,
    onOriginalContentScrollStateChanged: (Boolean) -> Unit = {},
    onRequestOriginalContents: (List<Long>) -> Unit = {},
    densityScale: Float = 2f
) {
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(densityScale, baseDensity.fontScale)) {
        val safePadding = dimensionResource(R.dimen.watch_safe_padding)
        val imageItemSpacing = dimensionResource(R.dimen.hey_distance_8dp)
        val textItemSpacing = dimensionResource(R.dimen.hey_distance_8dp)
        val listState = rememberLazyListState()
        val context = LocalContext.current
        val canLoadMoreState = rememberUpdatedState(hasMore)
        val isRefreshingState = rememberUpdatedState(isRefreshing)
        val hasItemsState = rememberUpdatedState(items.isNotEmpty())
        val itemsSizeState = rememberUpdatedState(items.size)
        val useOriginalContent = channel?.useOriginalContent == true
        val onOriginalContentScrollStateChangedState =
            rememberUpdatedState(onOriginalContentScrollStateChanged)
        val onRequestOriginalContentsState = rememberUpdatedState(onRequestOriginalContents)
        var lastLoadMoreSize by remember(channel?.id) { mutableStateOf(-1) }
        val maxImageWidthPx = remember(context) {
            val safePaddingPx = context.resources.getDimensionPixelSize(R.dimen.watch_safe_padding)
            (context.resources.displayMetrics.widthPixels - safePaddingPx * 2).coerceAtLeast(1)
        }
        val prefetchedUrls = remember(channel?.id) { mutableSetOf<String>() }
        val isAtTop by remember(listState) {
            derivedStateOf {
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            }
        }
        val isScrolling by remember(listState) {
            derivedStateOf { listState.isScrollInProgress }
        }

        LaunchedEffect(listState, items, maxImageWidthPx, channel?.id, useOriginalContent) {
            if (items.isEmpty()) return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                .map { info ->
                    val indices = info.mapNotNull { itemInfo ->
                        val index = itemInfo.index - 1
                        if (index in items.indices) index else null
                    }
                    val first = indices.minOrNull() ?: 0
                    val last = indices.maxOrNull() ?: -1
                    Triple(indices, first, last)
                }
                .distinctUntilChanged()
                .collectLatest { (indices, first, last) ->
                    if (listState.isScrollInProgress) return@collectLatest
                    if (last < 0) return@collectLatest
                    if (useOriginalContent && indices.isNotEmpty()) {
                        val ids = indices.mapNotNull { index -> items.getOrNull(index)?.id }
                        if (ids.isNotEmpty()) {
                            onRequestOriginalContentsState.value(ids.distinct())
                        }
                    }
                    val start = (first - FEED_PREFETCH_BEFORE).coerceAtLeast(0)
                    val end = (last + FEED_PREFETCH_AFTER).coerceAtMost(items.lastIndex)
                    var prefetched = 0
                    for (index in start..end) {
                        if (prefetched >= FEED_PREFETCH_LIMIT) break
                        val url = resolveThumbUrl(items[index]) ?: continue
                        if (!prefetchedUrls.add(url)) continue
                        RssImageLoader.preloadAndCacheRatio(context, url, maxImageWidthPx)
                        prefetched++
                    }
                }
        }

        LaunchedEffect(listState, useOriginalContent, channel?.id) {
            if (!useOriginalContent) return@LaunchedEffect
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { isScrolling ->
                    onOriginalContentScrollStateChangedState.value(isScrolling)
                }
        }

        DisposableEffect(useOriginalContent, channel?.id) {
            if (useOriginalContent) {
                onOriginalContentScrollStateChangedState.value(false)
            }
            onDispose {
                if (useOriginalContent) {
                    onOriginalContentScrollStateChangedState.value(false)
                }
            }
        }

        LaunchedEffect(listState) {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val lastIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastIndex to layoutInfo.totalItemsCount
            }
                .distinctUntilChanged()
                .filter { (_, total) -> total > 0 }
                .collect { (lastIndex, total) ->
                    val shouldLoadMore = lastIndex >= total - 3
                    val itemsSize = itemsSizeState.value
                    if (shouldLoadMore &&
                        canLoadMoreState.value &&
                        !isRefreshingState.value &&
                        hasItemsState.value &&
                        lastLoadMoreSize != itemsSize
                    ) {
                        lastLoadMoreSize = itemsSize
                        onLoadMore()
                    }
                }
        }

        PullRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            indicatorPadding = safePadding,
            isAtTop = { isAtTop }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = safePadding),
                state = listState,
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = imageItemSpacing
                )
            ) {
                item(key = "header") {
                    Box(modifier = Modifier.padding(bottom = imageItemSpacing)) {
                        FeedHeader(
                            title = channel?.title ?: "RSS",
                            isRefreshing = isRefreshing,
                            enabled = !isScrolling,
                            onClick = onHeaderClick
                        )
                    }
                }
                if (items.isEmpty()) {
                    item(key = "empty") {
                        FeedEmpty(
                            onBack = onBack
                        )
                    }
                } else {
                    items(
                        items,
                        key = { it.id },
                        contentType = {
                            if (!it.imageUrl.isNullOrBlank() || !it.previewImageUrl.isNullOrBlank()) {
                                "image"
                            } else {
                                "text"
                            }
                        }
                    ) { item ->
                        val thumbUrl = resolveThumbUrl(item)
                        val itemSpacing = if (thumbUrl.isNullOrBlank()) {
                            textItemSpacing
                        } else {
                            imageItemSpacing
                        }
                        Box(modifier = Modifier.padding(bottom = itemSpacing)) {
                            FeedItemEntry(
                                item = item,
                                thumbUrl = thumbUrl,
                                maxImageWidthPx = maxImageWidthPx,
                                isScrolling = isScrolling,
                                useOriginalContent = channel?.useOriginalContent == true,
                                openSwipeId = openSwipeId,
                                onOpenSwipe = onOpenSwipe,
                                onCloseSwipe = onCloseSwipe,
                                draggingSwipeId = draggingSwipeId,
                                onDragStart = onDragStart,
                                onDragEnd = onDragEnd,
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) },
                                onFavoriteClick = { onFavoriteClick(item) },
                                onWatchLaterClick = { onWatchLaterClick(item) }
                            )
                        }
                    }
                    item(key = "actions") {
                        Box(modifier = Modifier.padding(top = imageItemSpacing)) {
                            FeedActions(
                                canLoadMore = hasMore,
                                onLoadMore = onLoadMore
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedHeader(
    title: String,
    isRefreshing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val verticalPadding = dimensionResource(R.dimen.hey_content_horizontal_distance)
    val hintSize = textSize(R.dimen.hey_caption)
    val titleSize = textSize(R.dimen.hey_m_title)
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
            .clickableWithoutRipple(
                enabled = enabled,
                onClick = onClick
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val formattedTitle = remember(title, availableWidthPx, titleSizePx, typeface) {
                formatTitleForWidthLimits(
                    title = "$title $CHANNEL_TITLE_CLICK_HINT_SYMBOL",
                    paint = paint,
                    availableWidthPx = availableWidthPx,
                    firstLimitPx = firstLimitPx,
                    secondLimitPx = secondLimitPx
                )
            }
            Text(
                text = channelTitleWithStyledHint(formattedTitle, titleSize),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleSize,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = if (isRefreshing) "正在刷新中..." else "下拉刷新",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = hintSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun channelTitleWithStyledHint(
    text: String,
    titleSize: TextUnit
): androidx.compose.ui.text.AnnotatedString {
    val symbolIndex = text.lastIndexOf(CHANNEL_TITLE_CLICK_HINT_SYMBOL)
    if (symbolIndex < 0) return androidx.compose.ui.text.AnnotatedString(text)
    val hintSize = (titleSize.value - 2f).coerceAtLeast(8f).sp

    return buildAnnotatedString {
        append(text.substring(0, symbolIndex))
        withStyle(
            SpanStyle(
                color = Color(0xFFBDBDBD),
                fontWeight = FontWeight.Bold,
                fontSize = hintSize
            )
        ) {
            append(CHANNEL_TITLE_CLICK_HINT_SYMBOL)
        }
        if (symbolIndex + CHANNEL_TITLE_CLICK_HINT_SYMBOL.length < text.length) {
            append(text.substring(symbolIndex + CHANNEL_TITLE_CLICK_HINT_SYMBOL.length))
        }
    }
}

@Composable
private fun FeedEmpty(
    onBack: () -> Unit
) {
    val padding = dimensionResource(R.dimen.hey_content_horizontal_distance)
    val buttonSpacing = dimensionResource(R.dimen.hey_distance_6dp)
    val hintSize = textSize(R.dimen.hey_caption)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "暂无内容",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = hintSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(buttonSpacing))
        FeedPillButton(text = "返回", onClick = onBack)
    }
}

@Composable
private fun FeedActions(
    canLoadMore: Boolean,
    onLoadMore: () -> Unit
) {
    val padding = dimensionResource(R.dimen.hey_distance_4dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeedPillButton(
            text = if (canLoadMore) "加载更多" else "没有更多",
            enabled = canLoadMore,
            onClick = onLoadMore
        )
    }
}

@Composable
private fun FeedPillButton(
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
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = textSize,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeedItemEntry(
    item: RssItem,
    thumbUrl: String?,
    maxImageWidthPx: Int,
    isScrolling: Boolean,
    useOriginalContent: Boolean,
    openSwipeId: Long?,
    onOpenSwipe: (Long) -> Unit,
    onCloseSwipe: () -> Unit,
    draggingSwipeId: Long?,
    onDragStart: (Long) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchLaterClick: () -> Unit
) {
    val actionPadding = dimensionResource(R.dimen.hey_distance_4dp)
    val actionWidth = dimensionResource(R.dimen.watch_swipe_action_button_width)
    val fallbackActionsWidthPx = with(LocalDensity.current) {
        (actionWidth * 2 + actionPadding * 3).toPx()
    }
    val revealGapPx = with(LocalDensity.current) { (actionPadding * 2).toPx() }
    var actionsWidthPx by remember { mutableStateOf(fallbackActionsWidthPx) }
    var cardHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val cardHeightModifier = if (cardHeightPx > 0) {
        val height = with(density) { cardHeightPx.toDp() }
        Modifier.height(height)
    } else {
        Modifier
    }
    val pressState = rememberPressScaleState(enabled = !isScrolling)
    val pressScale = pressState.scale
    val cardScaleModifier = if (pressScale != 1f) {
        Modifier.graphicsLayer(
            scaleX = pressScale,
            scaleY = pressScale
        )
    } else {
        Modifier
    }
    val backgroundScaleModifier = if (pressScale != 1f) {
        Modifier.graphicsLayer(
            scaleX = pressScale,
            scaleY = 1f
        )
    } else {
        Modifier
    }

    val cardContent: @Composable (Modifier) -> Unit = { offsetModifier ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(offsetModifier)
                .onSizeChanged { size ->
                    if (cardHeightPx == 0 || !isScrolling) {
                        cardHeightPx = size.height
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(backgroundScaleModifier)
                    .background(Color.Black)
            )
            if (thumbUrl.isNullOrBlank()) {
                FeedTextCard(
                    item = item,
                    pressState = pressState,
                    enabled = !isScrolling,
                    useOriginalContent = useOriginalContent,
                    modifier = cardScaleModifier,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            } else {
                FeedImageCard(
                    item = item,
                    thumbUrl = thumbUrl,
                    maxImageWidthPx = maxImageWidthPx,
                    pressState = pressState,
                    enabled = !isScrolling,
                    isScrolling = isScrolling,
                    useOriginalContent = useOriginalContent,
                    modifier = cardScaleModifier,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            }
        }
    }

    if (isScrolling) {
        Box(modifier = Modifier.fillMaxWidth()) {
            cardContent(Modifier)
        }
    } else {
        SwipeActionRow(
            itemId = item.id,
            enabled = true,
            openSwipeId = openSwipeId,
            onOpenSwipe = onOpenSwipe,
            onCloseSwipe = onCloseSwipe,
            draggingSwipeId = draggingSwipeId,
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            actionsWidthPx = actionsWidthPx,
            revealGapPx = revealGapPx
        ) { offsetModifier ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .then(cardHeightModifier)
                        .padding(horizontal = actionPadding)
                        .onSizeChanged { size ->
                            actionsWidthPx = size.width.toFloat()
                        },
                    horizontalArrangement = Arrangement.spacedBy(actionPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwipeActionButton(
                        text = "收藏",
                        width = actionWidth,
                        onClick = {
                            onCloseSwipe()
                            onFavoriteClick()
                        },
                        iconRes = R.drawable.ic_action_favorite
                    )
                    SwipeActionButton(
                        text = "稍后再看",
                        width = actionWidth,
                        onClick = {
                            onCloseSwipe()
                            onWatchLaterClick()
                        },
                        iconRes = R.drawable.ic_action_watch_later
                    )
                }
                cardContent(offsetModifier)
            }
        }
    }
}

@Composable
private fun FeedTextCard(
    item: RssItem,
    pressState: PressScaleState,
    enabled: Boolean,
    useOriginalContent: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val background = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_card_normal_bg_radius))
    val padding = dimensionResource(R.dimen.hey_content_horizontal_distance)
    val titleSize = textSize(R.dimen.feed_card_title_text_size)
    val summarySize = textSize(R.dimen.feed_card_summary_text_size)
    val summaryLineHeight = summarySize * 1.1f
    val summaryTop = dimensionResource(R.dimen.hey_distance_2dp)
    val unreadSize = dimensionResource(R.dimen.hey_distance_8dp)
    val unreadMargin = dimensionResource(R.dimen.hey_distance_6dp)
    val summary = remember(item.id, item.summary, item.content, useOriginalContent) {
        val baseSummary = item.summary ?: "暂无摘要"
        if (useOriginalContent && item.content.isNullOrBlank()) {
            "$baseSummary\n原文加载中..."
        } else {
            baseSummary
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickableWithoutRipple(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = pressState.interactionSource
            )
            .padding(padding)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = summarySize,
                lineHeight = summaryLineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = summaryTop)
            )
        }
        if (!item.isRead) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = unreadMargin, end = unreadMargin)
                    .size(unreadSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun FeedImageCard(
    item: RssItem,
    thumbUrl: String,
    maxImageWidthPx: Int,
    pressState: PressScaleState,
    enabled: Boolean,
    isScrolling: Boolean,
    useOriginalContent: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val background = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_card_normal_bg_radius))
    val imageHeight = dimensionResource(R.dimen.feed_card_image_height)
    val padding = dimensionResource(R.dimen.hey_distance_8dp)
    val titleSize = textSize(R.dimen.feed_card_title_text_size)
    val summarySize = textSize(R.dimen.feed_card_summary_text_size)
    val summaryLineHeight = summarySize * 1.1f
    val summaryTop = dimensionResource(R.dimen.hey_distance_2dp)
    val unreadSize = dimensionResource(R.dimen.hey_distance_8dp)
    val unreadMargin = dimensionResource(R.dimen.hey_distance_6dp)
    val summary = remember(item.id, item.summary, item.content, useOriginalContent) {
        val baseSummary = item.summary ?: "暂无摘要"
        if (useOriginalContent && item.content.isNullOrBlank()) {
            "$baseSummary\n原文加载中..."
        } else {
            baseSummary
        }
    }
    val overlay = Brush.verticalGradient(
        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickableWithoutRipple(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = pressState.interactionSource
            )
    ) {
        RssThumbnail(
            url = thumbUrl,
            maxWidthPx = maxImageWidthPx,
            isScrolling = isScrolling,
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
        )
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
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = summarySize,
                lineHeight = summaryLineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = summaryTop)
            )
        }
        if (!item.isRead) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = unreadMargin, end = unreadMargin)
                    .size(unreadSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun RssThumbnail(
    url: String,
    maxWidthPx: Int,
    isScrolling: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cached = remember(url) { RssImageLoader.getCachedBitmap(url) }
    val bitmapState = remember(url, maxWidthPx) { mutableStateOf(cached) }
    val pendingState = remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(url, maxWidthPx, isScrolling) {
        if (isScrolling && bitmapState.value == null) return@LaunchedEffect
        val loaded = RssImageLoader.loadBitmap(context, url, maxWidthPx)
        if (loaded != null) {
            if (isScrolling && bitmapState.value == null) {
                pendingState.value = loaded
            } else {
                bitmapState.value = loaded
            }
        }
    }

    LaunchedEffect(isScrolling) {
        if (!isScrolling) {
            pendingState.value?.let { bitmap ->
                bitmapState.value = bitmap
                pendingState.value = null
            }
        }
    }

    val bitmap = bitmapState.value
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    if (imageBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = imageBitmap,
            contentDescription = "缩略图",
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surface)
        )
    }
}

@Stable
private data class PressScaleState(
    val scale: Float,
    val interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource
)

@Composable
private fun rememberPressScaleState(enabled: Boolean): PressScaleState {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    if (!enabled) {
        return PressScaleState(1f, interactionSource)
    }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 240 else 360),
        label = "pressScale"
    )
    return PressScaleState(scale, interactionSource)
}

@Composable
private fun textSize(id: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { dimensionResource(id).toSp() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource =
        androidx.compose.foundation.interaction.MutableInteractionSource()
): Modifier {
    return if (onLongClick != null) {
        combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
    }
}

private fun resolveThumbUrl(item: RssItem): String? {
    val candidate = item.imageUrl?.takeIf { it.isNotBlank() }
    return RssUrlResolver.resolveMediaUrl(candidate, item.link)
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
