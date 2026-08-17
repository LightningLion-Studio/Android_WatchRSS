package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.WatchLater
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Paint
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.RssUrlResolver
import com.lightningstudio.watchrss.ui.components.BlurFadeVisibility
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.SwipeActionButton
import com.lightningstudio.watchrss.ui.components.SwipeActionRow
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.rememberPullRefreshEnabled
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.util.formatWatchTitleForWidthLimits
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import com.lightningstudio.watchrss.debug.PerfTrace
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.max
import kotlin.math.roundToInt

private const val FEED_PREFETCH_BEFORE = 2
private const val FEED_PREFETCH_AFTER = 6
private const val FEED_PREFETCH_LIMIT = 8
private const val CHANNEL_TITLE_CLICK_HINT_SYMBOL = "ⓘ"

@Composable
fun FeedScreen(
    channel: RssChannel?,
    items: List<RssItem>,
    hasLoadedItems: Boolean = true,
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
        val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
        val imageItemSpacing = watchDimensionResource(R.dimen.hey_distance_8dp)
        val textItemSpacing = watchDimensionResource(R.dimen.hey_distance_8dp)
        val listState = rememberLazyListState()
        InstallDigitalCrownLazyListHandler(listState)
        val canRefresh = rememberPullRefreshEnabled(listState)
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
        val isScrolling by remember(listState) {
            derivedStateOf { listState.isScrollInProgress }
        }

        LaunchedEffect(channel?.id, items.size, useOriginalContent) {
            PerfTrace.log(
                "feed",
                "screen snapshot channelId=${channel?.id ?: -1L} items=${items.size} useOriginal=$useOriginalContent hasMore=$hasMore refreshing=$isRefreshing"
            )
        }

        LaunchedEffect(listState, channel?.id) {
            snapshotFlow {
                Triple(
                    listState.isScrollInProgress,
                    listState.firstVisibleItemIndex,
                    listState.layoutInfo.visibleItemsInfo.size
                )
            }
                .distinctUntilChanged()
                .collect { (scrolling, firstIndex, visibleCount) ->
                    PerfTrace.log(
                        "feed",
                        "scroll state channelId=${channel?.id ?: -1L} scrolling=$scrolling firstVisible=$firstIndex visibleCount=$visibleCount total=${listState.layoutInfo.totalItemsCount}"
                    )
                }
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
                    val startNanos = PerfTrace.now()
                    var requestedOriginalCount = 0
                    if (useOriginalContent && indices.isNotEmpty()) {
                        val ids = indices.mapNotNull { index -> items.getOrNull(index)?.id }
                        if (ids.isNotEmpty()) {
                            requestedOriginalCount = ids.distinct().size
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
                    PerfTrace.log(
                        "feed",
                        "visible settle channelId=${channel?.id ?: -1L} indices=${indices.joinToString(",")} prefetchRange=$start..$end prefetched=$prefetched requestedOriginal=$requestedOriginalCount durMs=${PerfTrace.elapsedMs(startNanos)}"
                    )
                }
        }

        LaunchedEffect(listState, useOriginalContent, channel?.id) {
            if (!useOriginalContent) return@LaunchedEffect
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { isScrolling ->
                    PerfTrace.log(
                        "feed",
                        "original updates scroll gate channelId=${channel?.id ?: -1L} paused=$isScrolling"
                    )
                    onOriginalContentScrollStateChangedState.value(isScrolling)
                }
        }

        DisposableEffect(useOriginalContent, channel?.id) {
            if (useOriginalContent) {
                PerfTrace.log(
                    "feed",
                    "original updates disposable init channelId=${channel?.id ?: -1L}"
                )
                onOriginalContentScrollStateChangedState.value(false)
            }
            onDispose {
                if (useOriginalContent) {
                    PerfTrace.log(
                        "feed",
                        "original updates disposable dispose channelId=${channel?.id ?: -1L}"
                    )
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
                        PerfTrace.log(
                            "feed",
                            "loadMore trigger channelId=${channel?.id ?: -1L} lastIndex=$lastIndex total=$total itemsSize=$itemsSize"
                        )
                        onLoadMore()
                    }
                }
        }

        WatchSurface {
            PullRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "RSS内容列表"
                        stateDescription = if (isRefreshing) "刷新中" else if (items.isEmpty()) "无内容" else "共 ${items.size} 条"
                    },
                indicatorPadding = safePadding,
                canRefresh = canRefresh
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
                    item(key = "empty") {
                        BlurFadeVisibility(
                            visible = items.isEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (hasLoadedItems) {
                                FeedEmptyState()
                            } else {
                                FeedEmptySkeleton(
                                    imageItemSpacing = imageItemSpacing,
                                    textItemSpacing = textItemSpacing
                                )
                            }
                        }
                    }
                    if (items.isNotEmpty()) {
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
}

@Composable
internal fun FeedHeader(
    title: String,
    isRefreshing: Boolean,
    enabled: Boolean,
    showInfoHint: Boolean = true,
    idleHint: String = "下拉刷新",
    onClick: () -> Unit
) {
    val verticalPadding = watchDimensionResource(R.dimen.hey_content_horizontal_distance)
    val hintSize = textSize(R.dimen.hey_caption)
    val titleSize = textSize(R.dimen.hey_m_title)
    val titleLineHeight = max(
        MaterialTheme.typography.titleMedium.lineHeight.value,
        titleSize.value * 1.24f
    ).sp
    val context = LocalContext.current
    val density = LocalDensity.current
    val titleSizePx = with(density) { watchDimensionResource(R.dimen.hey_m_title).toPx() }
    val firstLimitPx = with(density) {
        watchDimensionResource(R.dimen.detail_title_first_line_max_width).toPx()
    }
    val secondLimitPx = with(density) {
        watchDimensionResource(R.dimen.detail_title_second_line_max_width).toPx()
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
            .clickableWithRipple(
                enabled = enabled,
                onClick = onClick
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val formattedTitle = remember(title, showInfoHint, availableWidthPx, titleSizePx, typeface) {
                formatWatchTitleForWidthLimits(
                    title = if (showInfoHint) "$title $CHANNEL_TITLE_CLICK_HINT_SYMBOL" else title,
                    paint = paint,
                    availableWidthPx = availableWidthPx,
                    firstLimitPx = firstLimitPx,
                    secondLimitPx = secondLimitPx,
                    protectedSuffix = CHANNEL_TITLE_CLICK_HINT_SYMBOL.takeIf { showInfoHint },
                    minPrefixCharsBeforeSuffixOnLastLine = 2
                )
            }
            Text(
                text = if (showInfoHint) {
                    channelTitleWithStyledHint(formattedTitle, titleSize)
                } else {
                    androidx.compose.ui.text.AnnotatedString(formattedTitle)
                },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleSize,
                lineHeight = titleLineHeight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )
        }
        Text(
            text = if (isRefreshing) "正在刷新中..." else idleHint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = hintSize,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (isRefreshing) "正在刷新内容" else idleHint
                }
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
private fun FeedEmptySkeleton(
    imageItemSpacing: Dp,
    textItemSpacing: Dp
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FeedImageSkeletonCard()
        Spacer(modifier = Modifier.height(imageItemSpacing))
        FeedImageSkeletonCard(
            titleWidths = listOf(0.68f, 0.46f),
            summaryWidths = listOf(0.76f, 0.58f)
        )
        Spacer(modifier = Modifier.height(textItemSpacing))
        FeedTextSkeletonCard()
    }
}

@Composable
internal fun FeedEmptyState() {
    val topPadding = watchDimensionResource(R.dimen.hey_distance_20dp)
    val textSize = textSize(R.dimen.hey_s_title)
    Text(
        text = "暂无内容",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = textSize,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .semantics { contentDescription = "暂无内容" }
    )
}

@Composable
private fun FeedImageSkeletonCard(
    titleWidths: List<Float> = listOf(0.74f, 0.38f),
    summaryWidths: List<Float> = listOf(0.82f, 0.64f)
) {
    val shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_card_normal_bg_radius))
    val imageHeight = watchDimensionResource(R.dimen.feed_card_image_height)
    val padding = watchDimensionResource(R.dimen.hey_distance_8dp)
    val cardColor = MaterialTheme.colorScheme.surface
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val summaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val overlay = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .feedSkeletonPlaceholder(
                    baseColor = lineColor.copy(alpha = 0.9f),
                    highlightColor = Color.White.copy(alpha = 0.18f),
                    cornerRadius = watchDimensionResource(R.dimen.hey_card_normal_bg_radius)
                )
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
            FeedSkeletonParagraph(
                widths = titleWidths,
                lineHeight = 10.dp,
                spacing = 6.dp,
                color = lineColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeedSkeletonParagraph(
                widths = summaryWidths,
                lineHeight = 8.dp,
                spacing = 5.dp,
                color = summaryColor
            )
        }
    }
}

@Composable
private fun FeedTextSkeletonCard() {
    val shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_card_normal_bg_radius))
    val padding = watchDimensionResource(R.dimen.hey_content_horizontal_distance)
    val cardColor = MaterialTheme.colorScheme.surface
    val titleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
    val summaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardColor)
            .padding(padding)
    ) {
        FeedSkeletonParagraph(
            widths = listOf(0.84f, 0.56f),
            lineHeight = 10.dp,
            spacing = 6.dp,
            color = titleColor
        )
        Spacer(modifier = Modifier.height(10.dp))
        FeedSkeletonParagraph(
            widths = listOf(0.96f, 0.88f, 0.62f),
            lineHeight = 8.dp,
            spacing = 5.dp,
            color = summaryColor
        )
    }
}

@Composable
private fun FeedSkeletonParagraph(
    widths: List<Float>,
    lineHeight: Dp,
    spacing: Dp,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        widths.forEachIndexed { index, width ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .height(lineHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .feedSkeletonPlaceholder(
                        baseColor = color,
                        highlightColor = Color.White.copy(alpha = 0.22f),
                        cornerRadius = 6.dp
                    )
            )
            if (index != widths.lastIndex) {
                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }
}

@Composable
private fun Modifier.feedSkeletonPlaceholder(
    baseColor: Color,
    highlightColor: Color,
    cornerRadius: Dp
): Modifier {
    val transition = rememberInfiniteTransition(label = "FeedSkeleton")
    val shimmerProgress by transition.animateFloat(
        initialValue = -1.1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FeedSkeletonShimmer"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FeedSkeletonPulse"
    )

    return this
        .graphicsLayer { alpha = pulseAlpha }
        .background(baseColor)
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val widthPx = size.width.coerceAtLeast(1f)
            val heightPx = size.height.coerceAtLeast(1f)
            val startX = widthPx * shimmerProgress
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    highlightColor,
                    Color.Transparent
                ),
                start = Offset(startX - widthPx * 0.8f, 0f),
                end = Offset(startX + widthPx * 0.25f, heightPx)
            )
            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    brush = brush,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx)
                )
            }
        }
}

@Composable
internal fun FeedActions(
    canLoadMore: Boolean,
    onLoadMore: () -> Unit
) {
    val padding = watchDimensionResource(R.dimen.hey_distance_4dp)

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
internal fun FeedPillButton(
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
internal fun FeedItemEntry(
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
    onWatchLaterClick: () -> Unit,
    swipeActionsEnabled: Boolean = true,
    semanticItemLabel: String = "文章"
) {
    val actionPadding = watchDimensionResource(R.dimen.hey_distance_4dp)
    val actionWidth = watchDimensionResource(R.dimen.watch_swipe_action_button_width)
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
                    semanticItemLabel = semanticItemLabel,
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
                    semanticItemLabel = semanticItemLabel,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            }
        }
    }

    if (isScrolling || !swipeActionsEnabled) {
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
                        icon = Icons.Filled.Star,
                        semanticLabel = "收藏按钮"
                    )
                    SwipeActionButton(
                        text = "稍后再看",
                        width = actionWidth,
                        onClick = {
                            onCloseSwipe()
                            onWatchLaterClick()
                        },
                        icon = Icons.Outlined.WatchLater,
                        semanticLabel = "稍后再看按钮"
                    )
                }
                cardContent(offsetModifier)
            }
        }
    }
}

@Composable
private fun FeedCardTitle(
    title: String,
    isRead: Boolean,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val unreadIndicatorId = "feed_unread_indicator"
    val text = remember(title, isRead) {
        buildAnnotatedString {
            if (!isRead) {
                appendInlineContent(unreadIndicatorId, "[unread]")
                append(' ')
            }
            append(title)
        }
    }
    val inlineContent = if (!isRead) {
        mapOf(
            unreadIndicatorId to InlineTextContent(
                placeholder = Placeholder(
                    width = 0.5.em,
                    height = 0.5.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        )
    } else {
        emptyMap()
    }

    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inlineContent,
        modifier = modifier
            .semantics {
                contentDescription = when {
                    isRead -> title
                    else -> "未读：$title"
                }
            }
    )
}

@Composable
private fun FeedTextCard(
    item: RssItem,
    pressState: PressScaleState,
    enabled: Boolean,
    useOriginalContent: Boolean,
    modifier: Modifier,
    semanticItemLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val background = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_card_normal_bg_radius))
    val padding = watchDimensionResource(R.dimen.hey_content_horizontal_distance)
    val titleSize = textSize(R.dimen.feed_card_title_text_size)
    val summarySize = textSize(R.dimen.feed_card_summary_text_size)
    val summaryLineHeight = summarySize * 1.1f
    val summaryTop = watchDimensionResource(R.dimen.hey_distance_2dp)
    val summary = remember(item.id, item.summary, item.originalContent, useOriginalContent) {
        val baseSummary = item.summary ?: "暂无摘要"
        if (useOriginalContent && item.originalContent.isNullOrBlank()) {
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
            .clickableWithRipple(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = pressState.interactionSource
            )
            .padding(padding)
            .semantics {
                contentDescription = "$semanticItemLabel：${item.title}${if (item.isRead) "" else "，未读"}"
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FeedCardTitle(
                title = item.title,
                isRead = item.isRead,
                fontSize = titleSize
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
    semanticItemLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val background = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_card_normal_bg_radius))
    val imageHeight = watchDimensionResource(R.dimen.feed_card_image_height)
    val padding = watchDimensionResource(R.dimen.hey_distance_8dp)
    val titleSize = textSize(R.dimen.feed_card_title_text_size)
    val summarySize = textSize(R.dimen.feed_card_summary_text_size)
    val summaryLineHeight = summarySize * 1.1f
    val summaryTop = watchDimensionResource(R.dimen.hey_distance_2dp)
    val summary = remember(item.id, item.summary, item.originalContent, useOriginalContent) {
        val baseSummary = item.summary ?: "暂无摘要"
        if (useOriginalContent && item.originalContent.isNullOrBlank()) {
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
            .clickableWithRipple(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = pressState.interactionSource
            )
            .semantics {
                contentDescription = "$semanticItemLabel：${item.title}${if (item.isRead) "" else "，未读"}"
            }
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
            FeedCardTitle(
                title = item.title,
                isRead = item.isRead,
                fontSize = titleSize
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
        if (isScrolling && bitmapState.value == null) {
            PerfTrace.log(
                "feed",
                "thumbnail defer key=${thumbKey(url)} reason=scroll_no_cache"
            )
            return@LaunchedEffect
        }
        val startNanos = PerfTrace.now()
        val loaded = RssImageLoader.loadBitmap(context, url, maxWidthPx)
        if (loaded != null) {
            if (isScrolling && bitmapState.value == null) {
                pendingState.value = loaded
            } else {
                bitmapState.value = loaded
            }
            PerfTrace.log(
                "feed",
                "thumbnail ready key=${thumbKey(url)} scrolling=$isScrolling applied=${!isScrolling || bitmapState.value === loaded} size=${loaded.width}x${loaded.height} durMs=${PerfTrace.elapsedMs(startNanos)}"
            )
        } else {
            PerfTrace.log(
                "feed",
                "thumbnail miss key=${thumbKey(url)} scrolling=$isScrolling durMs=${PerfTrace.elapsedMs(startNanos)}"
            )
        }
    }

    LaunchedEffect(isScrolling) {
        if (!isScrolling) {
            pendingState.value?.let { bitmap ->
                bitmapState.value = bitmap
                pendingState.value = null
                PerfTrace.log(
                    "feed",
                    "thumbnail promote key=${thumbKey(url)} size=${bitmap.width}x${bitmap.height}"
                )
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
    return with(density) { watchDimensionResource(id).toSp() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.clickableWithRipple(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource =
        androidx.compose.foundation.interaction.MutableInteractionSource()
): Modifier {
    return if (onLongClick != null) {
        combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
    }
}

private fun resolveThumbUrl(item: RssItem): String? {
    val candidate = item.imageUrl?.takeIf { it.isNotBlank() }
    return RssUrlResolver.resolveMediaUrl(candidate, item.link)
}

private fun thumbKey(url: String): String = Integer.toHexString(url.hashCode())
