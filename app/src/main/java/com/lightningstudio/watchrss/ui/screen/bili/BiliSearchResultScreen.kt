package com.lightningstudio.watchrss.ui.screen.bili

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.sdk.bili.BiliSearchedVideo
import com.lightningstudio.watchrss.ui.components.LoadingIndicator
import com.lightningstudio.watchrss.ui.components.SearchInputBar
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.utils.BiliFormatUtils
import com.lightningstudio.watchrss.ui.viewmodel.BiliSearchResultViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

@Composable
fun BiliSearchResultScreen(
    keyword: String,
    factory: BiliViewModelFactory,
    onNavigateBack: () -> Unit,
    onVideoClick: (Long?, String?) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BiliSearchResultViewModel = viewModel(factory = factory)
) {
    var searchText by remember(keyword) { mutableStateOf(keyword) }
    val searchResults = viewModel.searchResultFlow.collectAsLazyPagingItems()
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val titleSize = textSize(R.dimen.hey_s_title)
    val listSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_6dp

    BackHandler(onBack = onNavigateBack)

    WatchSurface {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = safePadding,
                top = safePadding,
                end = safePadding,
                bottom = safePadding + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(listSpacing)
        ) {
            item {
                Text(
                    text = "搜索",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = titleSize,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SearchInputBar(
                    keyword = searchText,
                    onKeywordChange = { searchText = it },
                    onSearch = {
                        if (searchText.isNotBlank()) {
                            onSearch(searchText)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when {
                searchText.isBlank() -> {
                    item { EmptyHint(text = "输入关键词开始搜索") }
                }
                searchResults.loadState.refresh is LoadState.Loading -> {
                    item { LoadingIndicator(searchResults.loadState.refresh) }
                }
                searchResults.loadState.refresh is LoadState.Error -> {
                    item { LoadingIndicator(searchResults.loadState.refresh) }
                }
                searchResults.itemCount == 0 -> {
                    item { EmptyHint(text = "没有找到相关结果") }
                }
                else -> {
                    items(searchResults.itemCount) { index ->
                        val item = searchResults[index] ?: return@items
                        BiliFeedCard(
                            title = BiliFormatUtils.stripKeywordHighlight(item.data.title),
                            summary = buildVideoSummary(item.data),
                            coverUrl = item.data.pic,
                            onClick = { onVideoClick(item.data.aid, item.data.bvid) }
                        )
                    }

                    item {
                        LoadingIndicator(searchResults.loadState.append)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun buildVideoSummary(video: BiliSearchedVideo): String {
    val parts = mutableListOf<String>()
    val author = BiliFormatUtils.stripKeywordHighlight(video.author)
    if (author.isNotBlank()) {
        parts.add(author)
    }
    val play = video.play?.let { "播放 ${BiliFormatUtils.formatCount(it)}" }
    if (!play.isNullOrBlank()) {
        parts.add(play)
    }
    val danmaku = video.danmaku?.let { "弹幕 ${BiliFormatUtils.formatCount(it)}" }
    if (!danmaku.isNullOrBlank()) {
        parts.add(danmaku)
    }
    return if (parts.isNotEmpty()) parts.joinToString(" · ") else "哔哩哔哩视频"
}

@Composable
private fun textSize(id: Int): TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        dimensionResource(id).toSp()
    }
}
