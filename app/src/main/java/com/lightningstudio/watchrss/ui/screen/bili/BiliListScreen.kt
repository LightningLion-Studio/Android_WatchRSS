package com.lightningstudio.watchrss.ui.screen.bili

import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import com.lightningstudio.watchrss.ui.components.WatchTextButton
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.viewmodel.BiliListType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.style.TextAlign
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.EmptyStateCard
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.rememberPullRefreshEnabled
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.viewmodel.BiliListItem
import com.lightningstudio.watchrss.ui.viewmodel.BiliListUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun BiliListScreen(
    uiState: BiliListUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (BiliListItem) -> Unit,
    onSelectFavoriteFolder: (Long) -> Unit = {}
) {
    var selectingFolder by remember { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()
    stateHolder.SaveableStateProvider(uiState.selectedFavoriteFolderId?.toString() ?: uiState.type.id) {
        BiliListContent(uiState, onRefresh, onLoadMore, onItemClick, onOpenFolders = { selectingFolder = true })
    }
    if (selectingFolder) {
        Dialog(onDismissRequest = { selectingFolder = false }) {
            val scroll = rememberScrollState()
            InstallDigitalCrownScrollHandler(scroll)
            Column(Modifier.background(MaterialTheme.colorScheme.surface).verticalScroll(scroll).padding(20.dp)) {
                Text(
                    "选择收藏夹",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                uiState.favoriteFolders.forEach { folder ->
                    WatchTextButton(onClick = {
                        selectingFolder = false
                        onSelectFavoriteFolder(folder.id)
                    }) {
                        Text(if (folder.id == uiState.selectedFavoriteFolderId) "${folder.title}（当前）" else folder.title)
                    }
                }
                if (uiState.favoriteFolders.isEmpty()) Text("暂无收藏夹，请下拉刷新重试")
                WatchTextButton(onClick = { selectingFolder = false }) { Text("返回") }
            }
        }
    }
}

@Composable
private fun BiliListContent(
    uiState: BiliListUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (BiliListItem) -> Unit,
    onOpenFolders: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val itemSpacing = watchDimensionResource(R.dimen.hey_distance_8dp)
    val spacing = watchDimensionResource(R.dimen.hey_distance_6dp)
    val listState = rememberLazyListState()
    val visibleItemKeys by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapTo(hashSetOf()) { it.key }
        }
    }
    InstallDigitalCrownLazyListHandler(listState)
    val canRefresh = rememberPullRefreshEnabled(listState)

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

    WatchSurface {
        PullRefreshBox(
            isRefreshing = uiState.isRefreshing,
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
                    bottom = safePadding + itemSpacing + 40.dp
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
                            text = uiState.favoriteFolders.firstOrNull { it.id == uiState.selectedFavoriteFolderId }?.title ?: uiState.type.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = textSize(R.dimen.hey_m_title),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(
                                    enabled = uiState.type == BiliListType.FAVORITE,
                                    onClickLabel = "切换收藏夹",
                                    role = Role.Button,
                                    onClick = onOpenFolders
                                )
                        )
                        Text(
                            text = if (uiState.type == BiliListType.FAVORITE) "点击标题切换收藏夹 · 下拉刷新" else "下拉刷新",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = textSize(R.dimen.hey_caption),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                uiState.favoriteError?.let { error ->
                    item {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        WatchTextButton(onClick = onRefresh) { Text("重试加载收藏夹") }
                    }
                }
                if (uiState.items.isEmpty() && !uiState.isRefreshing && uiState.favoriteError == null) {
                    item {
                        EmptyStateCard(
                            title = "暂无内容",
                            subtitle = "稍后再试"
                        )
                    }
                } else {
                    items(uiState.items, key = { it.bvid ?: it.aid ?: it.title }) { item ->
                        val summary = remember(item) { buildListSummary(item) }
                        val itemKey = item.bvid ?: item.aid ?: item.title
                        BiliFeedCard(
                            title = item.title,
                            summary = summary,
                            coverUrl = item.cover,
                            isVisible = itemKey in visibleItemKeys,
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
}

@Composable
private fun textSize(id: Int): androidx.compose.ui.unit.TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        watchDimensionResource(id).toSp()
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
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, remain)
}
