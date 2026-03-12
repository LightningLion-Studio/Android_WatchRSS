package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.EmptyStateCard
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.SwipeActionButton
import com.lightningstudio.watchrss.ui.components.SwipeActionRow
import com.lightningstudio.watchrss.ui.viewmodel.BiliFeedUiState
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.animation.core.animateDpAsState

private const val CHANNEL_TITLE_CLICK_HINT_SYMBOL = "ⓘ"

@Composable
fun BiliFeedScreen(
    uiState: BiliFeedUiState,
    onLoginClick: () -> Unit,
    onRefresh: () -> Unit,
    onHeaderClick: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    onFavoriteClick: (BiliItem) -> Unit,
    onWatchLaterClick: (BiliItem) -> Unit,
    onItemClick: (BiliItem) -> Unit,
    onSearchClick: () -> Unit = {}
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val itemSpacing = dimensionResource(R.dimen.hey_distance_8dp)
    val listState = rememberLazyListState()
    val isScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    var menuOpen by remember { mutableStateOf(false) }
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        val maxWidth = this.maxWidth
        val menuWidth = (maxWidth * 0.68f).coerceAtMost(180.dp)
        val edgeWidth = 44.dp
        val dragThresholdPx = with(LocalDensity.current) { 20.dp.toPx() }
        val menuOffset by animateDpAsState(
            targetValue = if (menuOpen) 0.dp else menuWidth,
            label = "biliMenuOffset"
        )

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
                    BiliFeedHeader(
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
                        val summary = remember(item) { buildFeedSummary(item) }
                        val itemId = remember(item) { itemSwipeId(item) }
                        BiliFeedItemEntry(
                            item = item,
                            itemId = itemId,
                            summary = summary,
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

        if (menuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
                    .clickableWithoutRipple { menuOpen = false }
            )
        } else {
            var dragDistance by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(edgeWidth)
                    .align(Alignment.CenterEnd)
                    .pointerInput(menuOpen) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragDistance += dragAmount
                            },
                            onDragEnd = {
                                if (dragDistance <= -dragThresholdPx) {
                                    menuOpen = true
                                }
                            }
                        )
                    }
            )
        }

        BiliSideMenu(
            isLoggedIn = uiState.isLoggedIn,
            modifier = Modifier
                .fillMaxHeight()
                .width(menuWidth)
                .align(Alignment.CenterEnd)
                .offset(x = menuOffset)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(menuOpen) {
                    if (!menuOpen) return@pointerInput
                    var dragDistance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragDistance = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragDistance += dragAmount
                        },
                        onDragEnd = {
                            if (dragDistance >= dragThresholdPx) {
                                menuOpen = false
                            }
                        }
                    )
                },
            onLoginClick = {
                menuOpen = false
                onLoginClick()
            },
            onHeaderClick = {
                menuOpen = false
                onHeaderClick()
            },
            onSearchClick = {
                menuOpen = false
                onSearchClick()
            },
            onOpenWatchLater = {
                menuOpen = false
                onOpenWatchLater()
            },
            onOpenHistory = {
                menuOpen = false
                onOpenHistory()
            },
            onOpenFavorites = {
                menuOpen = false
                onOpenFavorites()
            }
        )
    }
}

@Composable
private fun BiliFeedItemEntry(
    item: BiliItem,
    itemId: Long,
    summary: String,
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

            BiliFeedCard(
                title = item.title.orEmpty(),
                summary = summary,
                coverUrl = item.cover,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(offsetModifier)
            )
        }
    }
}

@Composable
private fun BiliFeedHeader(
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
private fun textSize(id: Int): androidx.compose.ui.unit.TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        dimensionResource(id).toSp()
    }
}

@Composable
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier {
    return clickable(
        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

private fun buildFeedSummary(item: BiliItem): String {
    val owner = item.owner?.name
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

@Composable
private fun BiliSideMenu(
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit,
    onHeaderClick: () -> Unit,
    onSearchClick: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit
) {
    val spacing = dimensionResource(R.dimen.hey_distance_6dp)
    val padding = dimensionResource(R.dimen.hey_distance_6dp)

    Column(
        modifier = modifier.padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Text(
            text = "快速入口",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = textSize(R.dimen.hey_s_title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BiliSideMenuItem(
            label = "频道信息",
            enabled = true,
            onClick = onHeaderClick
        )
        if (!isLoggedIn) {
            BiliSideMenuItem(
                label = "登录",
                enabled = true,
                onClick = onLoginClick
            )
        }
        BiliSideMenuItem(
            label = "搜索",
            enabled = isLoggedIn,
            onClick = onSearchClick
        )
        BiliSideMenuItem(
            label = "稍后再看",
            enabled = isLoggedIn,
            onClick = onOpenWatchLater
        )
        BiliSideMenuItem(
            label = "历史记录",
            enabled = isLoggedIn,
            onClick = onOpenHistory
        )
        BiliSideMenuItem(
            label = "收藏夹",
            enabled = isLoggedIn,
            onClick = onOpenFavorites
        )
    }
}

@Composable
private fun BiliSideMenuItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val radius = dimensionResource(R.dimen.hey_card_normal_bg_radius)
    val height = dimensionResource(R.dimen.hey_button_height)
    val padding = dimensionResource(R.dimen.hey_distance_8dp)
    val textSize = textSize(R.dimen.feed_card_action_text_size)
    val background = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = textSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
