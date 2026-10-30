package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.lightningstudio.watchrss.ui.components.BiliCommentCard
import com.lightningstudio.watchrss.ui.components.LoadingIndicator
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.WatchIconButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.rememberPullRefreshEnabled
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.viewmodel.BiliReplyViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliReplyDetailScreen(
    oid: Long,
    root: Long,
    uploaderMid: Long,
    factory: BiliViewModelFactory,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BiliReplyViewModel = viewModel(factory = factory)
) {
    val replies = viewModel.replyFlow.collectAsLazyPagingItems()
    val rootComment by viewModel.rootComment.collectAsState()
    val safePadding = WatchDimens.watch_safe_padding
    val listState = rememberLazyListState()
    val isRefreshing = replies.loadState.refresh is LoadState.Loading
    InstallDigitalCrownLazyListHandler(listState)
    val canRefresh = rememberPullRefreshEnabled(listState)

    WatchSurface {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "回复详情",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        WatchIconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            modifier = modifier,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) { paddingValues ->
            PullRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { replies.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = safePadding),
                canRefresh = canRefresh
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Root comment
                    rootComment?.let { root ->
                        item {
                            Column {
                                BiliCommentCard(
                                    comment = root,
                                    uploaderMid = uploaderMid,
                                    onReplyClick = null
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    text = "所有回复",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .padding(horizontal = safePadding)
                                        .semantics { heading() }
                                )
                            }
                        }
                    }

                    // Replies
                    when {
                        replies.itemCount == 0 && replies.loadState.refresh is LoadState.Loading -> {
                            item {
                                LoadingIndicator(replies.loadState.refresh)
                            }
                        }
                        replies.itemCount == 0 && replies.loadState.refresh is LoadState.Error -> {
                            item {
                                LoadingIndicator(
                                    loadState = replies.loadState.refresh,
                                    onRetry = replies::retry
                                )
                            }
                        }
                        replies.itemCount == 0 -> {
                            item(key = "emptyReplies") {
                                Text(
                                    text = "暂无回复",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            if (replies.loadState.refresh is LoadState.Error) {
                                item(key = "refreshError") {
                                    LoadingIndicator(
                                        loadState = replies.loadState.refresh,
                                        onRetry = replies::retry
                                    )
                                }
                            }
                            items(replies.itemCount) { index ->
                                val reply = replies[index]
                                reply?.let {
                                    BiliCommentCard(
                                        comment = it,
                                        uploaderMid = uploaderMid,
                                        onReplyClick = null
                                    )
                                }
                            }

                            item {
                                LoadingIndicator(
                                    loadState = replies.loadState.append,
                                    onRetry = replies::retry
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
