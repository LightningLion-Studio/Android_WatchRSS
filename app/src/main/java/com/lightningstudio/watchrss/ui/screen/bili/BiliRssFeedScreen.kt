package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.ui.components.EmptyStateCard
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.SwipeActionButton
import com.lightningstudio.watchrss.ui.components.SwipeActionRow
import com.lightningstudio.watchrss.ui.viewmodel.BiliFeedUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val CHANNEL_TITLE_CLICK_HINT_SYMBOL = "ⓘ"

@Composable
fun BiliRssFeedScreen(
    uiState: BiliFeedUiState,
    onLoginClick: () -> Unit,
    onRefresh: () -> Unit,
    onHeaderClick: () -> Unit,
    onLoadMore: () -> Unit,
    onFavoriteClick: (BiliItem) -> Unit,
    onWatchLaterClick: (BiliItem) -> Unit,
    onItemClick: (BiliItem) -> Unit
) {
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
        val safePadding = dimensionResource(R.dimen.watch_safe_padding)
        val itemSpacing = dimensionResource(R.dimen.hey_distance_8dp)
        val listState = rememberLazyListState()
        val isScrolling by remember(listState) {
            derivedStateOf { listState.isScrollInProgress }
        }
        var openSwipeId by remember { mutableStateOf<Long?>(null) }
        var draggingSwipeId by remember { mutableStateOf<Long?>(null) }
        val isRefreshingState = rememberUpdatedState(uiState.isRefreshing)
        val isLoadingMoreState = rememberUpdatedState(uiState.isLoadingMore)
        val isLoggedInState = rememberUpdatedState(uiState.isLoggedIn)
        val canLoadMoreState = rememberUpdatedState(uiState.canLoadMore)
        val hasItemsState = rememberUpdatedState(uiState.items.isNotEmpty())

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
                    if (shouldLoadMore &&
                        isLoggedInState.value &&
                        !isRefreshingState.value &&
                        !isLoadingMoreState.value &&
                        canLoadMoreState.value &&
                        hasItemsState.value
                    ) {
                        onLoadMore()
                    }
                }
        }

        PullRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorPadding = safePadding,
            isAtTop = {
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = safePadding),
                state = listState,
                contentPadding = PaddingValues(
                    top = safePadding,
                    bottom = safePadding + itemSpacing
                ),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                item {
                    BiliRssFeedHeader(
                        isLoggedIn = uiState.isLoggedIn,
                        onLoginClick = onLoginClick,
                        onHeaderClick = onHeaderClick
                    )
                }
                if (!uiState.isLoggedIn) {
                    item {
                        EmptyStateCard(
                            title = "需要登录",
                            subtitle = "登录后获取推荐内容"
                        )
                    }
                } else if (uiState.items.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "暂无内容",
                            subtitle = "稍后再试或刷新"
                        )
                    }
                } else {
                    items(uiState.items, key = { it.bvid ?: it.aid ?: it.title.orEmpty() }) { item ->
                        val summary = remember(item) { buildRssSummary(item) }
                        val itemId = remember(item) { itemSwipeId(item) }
                        BiliRssFeedItemEntry(
                            title = item.title.orEmpty(),
                            summary = summary,
                            itemId = itemId,
                            isScrolling = isScrolling,
                            openSwipeId = openSwipeId,
                            onOpenSwipe = { openSwipeId = it },
                            onCloseSwipe = { openSwipeId = null },
                            draggingSwipeId = draggingSwipeId,
                            onDragStart = { draggingSwipeId = it },
                            onDragEnd = { draggingSwipeId = null },
                            onClick = {
                                openSwipeId = null
                                onItemClick(item)
                            },
                            onLongClick = {
                                openSwipeId = if (openSwipeId == itemId) null else itemId
                            },
                            onFavoriteClick = { onFavoriteClick(item) },
                            onWatchLaterClick = { onWatchLaterClick(item) }
                        )
                    }
                }
                item {
                    if (uiState.isLoadingMore) {
                        Text(
                            text = "加载中...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BiliRssFeedItemEntry(
    title: String,
    summary: String,
    itemId: Long,
    isScrolling: Boolean,
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

    SwipeActionRow(
        itemId = itemId,
        enabled = !isScrolling,
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
                .height(IntrinsicSize.Min)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(horizontal = actionPadding),
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

            BiliRssTextCard(
                title = title,
                summary = summary,
                enabled = !isScrolling,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(offsetModifier),
                onClick = onClick,
                onLongClick = onLongClick
            )
        }
    }
}

@Composable
private fun BiliRssTextCard(
    title: String,
    summary: String,
    enabled: Boolean,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickableWithoutRipple(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(padding)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
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
    }
}

@Composable
private fun BiliRssFeedHeader(
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onHeaderClick: () -> Unit
) {
    val titleSize = textSize(R.dimen.hey_m_title)
    val captionSize = textSize(R.dimen.hey_caption)
    val spacing = dimensionResource(R.dimen.hey_distance_6dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickableWithoutRipple(onClick = onHeaderClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Text(
                text = channelTitleWithStyledHint("哔哩哔哩", titleSize),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleSize,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (isLoggedIn) "已登录" else "未登录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = captionSize,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!isLoggedIn) {
            Spacer(modifier = Modifier.height(spacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                BiliPillButton(text = "登录", onClick = onLoginClick)
            }
        }
    }
}

private fun channelTitleWithStyledHint(
    title: String,
    titleSize: TextUnit
): androidx.compose.ui.text.AnnotatedString {
    val hintSize = (titleSize.value - 2f).coerceAtLeast(8f).sp
    return buildAnnotatedString {
        append("$title ")
        withStyle(
            SpanStyle(
                color = Color(0xFFBDBDBD),
                fontWeight = FontWeight.Bold,
                fontSize = hintSize
            )
        ) {
            append(CHANNEL_TITLE_CLICK_HINT_SYMBOL)
        }
    }
}

@Composable
private fun textSize(id: Int): TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        dimensionResource(id).toSp()
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier {
    return combinedClickable(
        enabled = enabled,
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

private fun buildRssSummary(item: BiliItem): String {
    val owner = item.owner?.name?.trim()?.takeUnless { it.isNullOrBlank() }?.let { "作者：$it" }
    val views = item.stat?.view?.let { "播放 ${formatBiliCount(it)}" }
    val parts = listOfNotNull(owner, views)
    return if (parts.isNotEmpty()) parts.joinToString(" · ") else "哔哩哔哩推荐"
}

private fun itemSwipeId(item: BiliItem): Long {
    return item.aid
        ?: item.bvid?.hashCode()?.toLong()
        ?: item.title?.hashCode()?.toLong()
        ?: 0L
}
