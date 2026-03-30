package com.lightningstudio.watchrss.ui.screen.douyin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.douyin.buildDouyinShareLink
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.ui.components.EmptyStateCard
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.rememberPullRefreshEnabled
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.screen.rss.FeedActions
import com.lightningstudio.watchrss.ui.screen.rss.FeedHeader
import com.lightningstudio.watchrss.ui.screen.rss.FeedItemEntry
import com.lightningstudio.watchrss.ui.screen.rss.FeedPillButton
import com.lightningstudio.watchrss.ui.screen.bili.formatBiliCount
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun DouyinRssFeedScreen(
    uiState: DouyinFeedUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (DouyinStreamItem, Int) -> Unit,
    onItemLongClick: (DouyinStreamItem) -> Unit,
    onFavoriteClick: (DouyinStreamItem) -> Unit,
    onWatchLaterClick: (DouyinStreamItem) -> Unit,
    onLoginClick: () -> Unit,
    onHeaderClick: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val itemSpacing = watchDimensionResource(R.dimen.hey_distance_8dp)
    val listState = rememberLazyListState()
    InstallDigitalCrownLazyListHandler(listState)
    val canRefresh = rememberPullRefreshEnabled(listState)
    val isScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    val isLoadingState = rememberUpdatedState(uiState.isLoading)
    val isLoadingMoreState = rememberUpdatedState(uiState.isLoadingMore)
    val hasMoreState = rememberUpdatedState(uiState.hasMore)
    var openSwipeId by remember { mutableStateOf<Long?>(null) }
    var draggingSwipeId by remember { mutableStateOf<Long?>(null) }
    val feedItems = remember(uiState.items) {
        uiState.items.map(::buildDouyinFeedItem)
    }

    fun closeOpenSwipe(): Boolean {
        val hasOpen = openSwipeId != null
        if (hasOpen) {
            openSwipeId = null
        }
        return hasOpen
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastIndex = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastIndex to info.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (_, total) -> total > 0 }
            .collect { (lastIndex, total) ->
                if (
                    lastIndex >= total - 3 &&
                    !isLoadingState.value &&
                    !isLoadingMoreState.value &&
                    hasMoreState.value
                ) {
                    onLoadMore()
                }
            }
    }

    PullRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        indicatorPadding = safePadding,
        canRefresh = canRefresh
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
                FeedHeader(
                    title = "抖音",
                    isRefreshing = uiState.isLoading,
                    enabled = !isScrolling,
                    onClick = {
                        if (!closeOpenSwipe()) {
                            onHeaderClick()
                        }
                    }
                )
            }

            if (!uiState.isLoggedIn) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "未登录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(itemSpacing))
                        FeedPillButton(text = "登录", onClick = onLoginClick)
                    }
                }
            } else if (uiState.items.isEmpty()) {
                item {
                    val title = if (uiState.message.isNullOrBlank()) "暂无内容" else "加载失败"
                    val subtitle = uiState.message ?: "下拉刷新获取推荐内容"
                    EmptyStateCard(title = title, subtitle = subtitle)
                }
            } else {
                itemsIndexed(feedItems, key = { _, item -> item.id }) { index, feedItem ->
                    val item = uiState.items[index]
                    FeedItemEntry(
                        item = feedItem,
                        thumbUrl = null,
                        maxImageWidthPx = 1,
                        isScrolling = isScrolling,
                        useOriginalContent = false,
                        openSwipeId = openSwipeId,
                        onOpenSwipe = { openSwipeId = it },
                        onCloseSwipe = { openSwipeId = null },
                        draggingSwipeId = draggingSwipeId,
                        onDragStart = { draggingSwipeId = it },
                        onDragEnd = { draggingSwipeId = null },
                        onClick = {
                            if (!closeOpenSwipe()) {
                                onItemClick(item, index)
                            }
                        },
                        onLongClick = {
                            if (!closeOpenSwipe()) {
                                onItemLongClick(item)
                            }
                        },
                        onFavoriteClick = { onFavoriteClick(item) },
                        onWatchLaterClick = { onWatchLaterClick(item) }
                    )
                }
                item {
                    FeedActions(
                        canLoadMore = uiState.hasMore && !uiState.isLoadingMore,
                        onLoadMore = onLoadMore
                    )
                }
            }
        }
    }
}

private fun buildSummary(item: DouyinStreamItem): String {
    val author = item.author?.takeIf { it.isNotBlank() } ?: "未知作者"
    val like = if (item.likeCount > 0) "赞 ${formatBiliCount(item.likeCount)}" else null
    return listOfNotNull(author, like).joinToString(" · ")
}

private fun buildDouyinFeedItem(item: DouyinStreamItem): RssItem {
    val summary = buildSummary(item)
    return RssItem(
        id = item.awemeId.hashCode().toLong(),
        channelId = 0L,
        title = item.title?.ifBlank { "抖音视频" } ?: "抖音视频",
        description = summary,
        content = null,
        originalContent = null,
        link = buildDouyinShareLink(item.awemeId),
        pubDate = null,
        imageUrl = null,
        audioUrl = null,
        videoUrl = item.playUrl,
        summary = summary,
        previewImageUrl = null,
        isRead = true,
        isLiked = false,
        readingProgress = 0f,
        fetchedAt = item.playUrlResolvedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
    )
}
