package com.lightningstudio.watchrss.ui.screen.douyin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.ui.components.EmptyStateCard
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.ToastMessage
import com.lightningstudio.watchrss.ui.screen.bili.BiliPillButton
import com.lightningstudio.watchrss.ui.screen.bili.formatBiliCount
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val CHANNEL_TITLE_CLICK_HINT_SYMBOL = "ⓘ"

@Composable
fun DouyinRssFeedScreen(
    uiState: DouyinFeedUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (DouyinStreamItem, Int) -> Unit,
    onLoginClick: () -> Unit,
    onHeaderClick: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val itemSpacing = dimensionResource(R.dimen.hey_distance_8dp)
    val listState = rememberLazyListState()
    val isAtTop = rememberTopState(listState)
    val isLoadingState = rememberUpdatedState(uiState.isLoading)
    val isLoadingMoreState = rememberUpdatedState(uiState.isLoadingMore)
    val hasMoreState = rememberUpdatedState(uiState.hasMore)

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
                DouyinRssFeedHeader(
                    isLoggedIn = uiState.isLoggedIn,
                    onLoginClick = onLoginClick,
                    onHeaderClick = onHeaderClick
                )
            }

            if (!uiState.isLoggedIn) {
                item {
                    EmptyStateCard(title = "需要登录", subtitle = "登录后查看推荐内容")
                }
            } else if (uiState.items.isEmpty()) {
                item {
                    val title = if (uiState.message.isNullOrBlank()) "暂无内容" else "加载失败"
                    val subtitle = uiState.message ?: "下拉刷新获取推荐内容"
                    EmptyStateCard(title = title, subtitle = subtitle)
                }
            } else {
                itemsIndexed(uiState.items) { index, item ->
                    val title = item.title?.ifBlank { "抖音视频" } ?: "抖音视频"
                    DouyinRssTextCard(
                        title = title,
                        summary = buildSummary(item),
                        onClick = { onItemClick(item, index) },
                        enabled = true
                    )
                }
                item {
                    if (uiState.isLoadingMore) {
                        Text(
                            text = "加载中...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (!uiState.message.isNullOrBlank() && uiState.items.isNotEmpty()) {
        ToastMessage(text = uiState.message)
    }
}

@Composable
private fun DouyinRssFeedHeader(
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
                text = channelTitleWithStyledHint("抖音", titleSize),
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                BiliPillButton(text = "登录", onClick = onLoginClick)
            }
        }
    }
}

@Composable
private fun DouyinRssTextCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val background = MaterialTheme.colorScheme.surface
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        dimensionResource(R.dimen.hey_card_normal_bg_radius)
    )
    val padding = dimensionResource(R.dimen.hey_content_horizontal_distance)
    val titleSize = textSize(R.dimen.feed_card_title_text_size)
    val summarySize = textSize(R.dimen.feed_card_summary_text_size)
    val summaryLineHeight = summarySize * 1.1f
    val summaryTop = dimensionResource(R.dimen.hey_distance_2dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickableWithoutRipple(enabled = enabled, onClick = onClick)
            .padding(padding)
    ) {
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

@Composable
private fun rememberTopState(
    listState: androidx.compose.foundation.lazy.LazyListState
): State<Boolean> {
    return remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
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
    return LocalDensity.current.run {
        dimensionResource(id).toSp()
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    return combinedClickable(
        enabled = enabled,
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

private fun buildSummary(item: DouyinStreamItem): String {
    val author = item.author?.takeIf { it.isNotBlank() } ?: "未知作者"
    val like = if (item.likeCount > 0) "赞 ${formatBiliCount(item.likeCount)}" else null
    return listOfNotNull(author, like).joinToString(" · ")
}
