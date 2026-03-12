package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.lightningstudio.watchrss.ui.components.BiliCommentCard
import com.lightningstudio.watchrss.ui.components.LoadingIndicator
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.viewmodel.BiliReplyViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回复详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        PullRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    replies.refresh()
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Root comment
                rootComment?.let { root ->
                    item {
                        Column {
                            BiliCommentCard(
                                comment = root,
                                uploaderMid = uploaderMid,
                                onReplyClick = {}
                            )
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "所有回复",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                // Replies
                when {
                    replies.loadState.refresh is LoadState.Loading -> {
                        item {
                            LoadingIndicator(replies.loadState.refresh)
                        }
                    }
                    replies.loadState.refresh is LoadState.Error -> {
                        item {
                            LoadingIndicator(replies.loadState.refresh)
                        }
                    }
                    else -> {
                        items(replies.itemCount) { index ->
                            val reply = replies[index]
                            reply?.let {
                                BiliCommentCard(
                                    comment = it,
                                    uploaderMid = uploaderMid,
                                    onReplyClick = {}
                                )
                            }
                        }

                        item {
                            LoadingIndicator(replies.loadState.append)
                        }
                    }
                }
            }
        }
    }
}
