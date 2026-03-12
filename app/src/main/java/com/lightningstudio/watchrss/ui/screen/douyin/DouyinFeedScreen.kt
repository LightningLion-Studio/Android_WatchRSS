package com.lightningstudio.watchrss.ui.screen.douyin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.ui.components.EmptyStateCard
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.ToastMessage
import com.lightningstudio.watchrss.ui.screen.bili.BiliFeedCard
import com.lightningstudio.watchrss.ui.screen.bili.formatBiliCount
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedUiState

@Composable
fun DouyinFeedScreen(
    uiState: DouyinFeedUiState,
    onRefresh: () -> Unit,
    onItemClick: (DouyinStreamItem, Int) -> Unit,
    onLoginClick: () -> Unit = {},
    onHeaderClick: () -> Unit = {}
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val itemSpacing = dimensionResource(R.dimen.hey_distance_8dp)
    val listState = rememberLazyListState()
    val isAtTop = remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PullRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorPadding = safePadding,
            isAtTop = { isAtTop.value }
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
                    DouyinFeedHeader(
                        isLoggedIn = uiState.isLoggedIn,
                        onLoginClick = onLoginClick,
                        onHeaderClick = onHeaderClick
                    )
                }
                if (uiState.items.isEmpty()) {
                    item {
                        val title = if (uiState.message.isNullOrBlank()) "暂无内容" else "加载失败"
                        val subtitle = uiState.message ?: "下拉刷新获取推荐内容"
                        EmptyStateCard(title = title, subtitle = subtitle)
                    }
                } else {
                    itemsIndexed(uiState.items) { index, item ->
                        val title = item.title?.ifBlank { "抖音视频" } ?: "抖音视频"
                        val summary = buildSummary(item)
                        BiliFeedCard(
                            title = title,
                            summary = summary,
                            coverUrl = item.coverUrl,
                            onClick = { onItemClick(item, index) }
                        )
                    }
                }
            }
        }

        if (!uiState.message.isNullOrBlank() && uiState.items.isNotEmpty()) {
            ToastMessage(text = uiState.message!!)
        }
    }
}

@Composable
private fun DouyinFeedHeader(
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit = {},
    onHeaderClick: () -> Unit = {}
) {
    val headerPadding = dimensionResource(R.dimen.hey_distance_6dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onHeaderClick)
            .padding(horizontal = headerPadding, vertical = headerPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "抖音精选",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.hey_distance_2dp)))
        Text(
            text = if (isLoggedIn) "已登录" else "未登录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!isLoggedIn) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.hey_distance_4dp)))
            Text(
                text = "登录",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1E88E5),
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { onLoginClick() }
            )
        }
    }
}

private fun buildSummary(item: DouyinStreamItem): String {
    val author = item.author?.takeIf { it.isNotBlank() } ?: "未知作者"
    val like = if (item.likeCount > 0) "赞 ${formatBiliCount(item.likeCount)}" else null
    return listOfNotNull(author, like).joinToString(" · ")
}
