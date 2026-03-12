package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.EmptyStateCard
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.viewmodel.BiliListItem
import com.lightningstudio.watchrss.ui.viewmodel.BiliListUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun BiliListScreen(
    uiState: BiliListUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (BiliListItem) -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val itemSpacing = dimensionResource(R.dimen.hey_distance_8dp)
    val spacing = dimensionResource(R.dimen.hey_distance_6dp)
    val listState = rememberLazyListState()

    val canLoadMoreState = rememberUpdatedState(uiState.canLoadMore)
    val isLoadingMoreState = rememberUpdatedState(uiState.isLoadingMore)
    val isRefreshingState = rememberUpdatedState(uiState.isRefreshing)
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
                    canLoadMoreState.value &&
                    !isLoadingMoreState.value &&
                    !isRefreshingState.value &&
                    hasItemsState.value
                ) {
                    onLoadMore()
                }
            }
    }

    PullRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.type.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = textSize(R.dimen.hey_m_title),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "下拉刷新",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = textSize(R.dimen.hey_caption),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (uiState.items.isEmpty() && !uiState.isRefreshing) {
                item {
                    EmptyStateCard(
                        title = "暂无内容",
                        subtitle = "稍后再试"
                    )
                }
            } else {
                items(uiState.items, key = { it.bvid ?: it.aid ?: it.title }) { item ->
                    val summary = remember(item) { buildListSummary(item) }
                    BiliFeedCard(
                        title = item.title,
                        summary = summary,
                        coverUrl = item.cover,
                        onClick = { onItemClick(item) }
                    )
                }
            }
            item {
                if (uiState.isLoadingMore) {
                    Text(
                        text = "加载中...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun textSize(id: Int): androidx.compose.ui.unit.TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        dimensionResource(id).toSp()
    }
}

private fun buildListSummary(item: BiliListItem): String {
    val owner = item.subtitle?.takeIf { it.isNotBlank() }
    val duration = formatDuration(item.durationSeconds)
    val parts = listOfNotNull(owner, duration)
    return if (parts.isNotEmpty()) {
        parts.joinToString(" · ")
    } else {
        "哔哩哔哩"
    }
}

private fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val minutes = seconds / 60
    val remain = seconds % 60
    return String.format("%02d:%02d", minutes, remain)
}
