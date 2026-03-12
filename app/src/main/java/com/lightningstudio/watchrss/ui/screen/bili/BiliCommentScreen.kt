package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.lightningstudio.watchrss.ui.components.EmptyCommentState
import com.lightningstudio.watchrss.ui.components.LoadingIndicator
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.viewmodel.BiliCommentViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliCommentScreen(
    oid: Long,
    uploaderMid: Long,
    factory: BiliViewModelFactory,
    onNavigateBack: () -> Unit,
    onReplyClick: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BiliCommentViewModel = viewModel(factory = factory)
) {
    val comments = viewModel.commentFlow.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("评论") },
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
                    comments.refresh()
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                comments.loadState.refresh is LoadState.Loading -> {
                    LoadingIndicator(comments.loadState.refresh)
                }
                comments.loadState.refresh is LoadState.Error -> {
                    LoadingIndicator(comments.loadState.refresh)
                }
                comments.itemCount == 0 -> {
                    EmptyCommentState()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(comments.itemCount) { index ->
                            val comment = comments[index]
                            comment?.let {
                                BiliCommentCard(
                                    comment = it,
                                    uploaderMid = viewModel.uploaderMid,
                                    onReplyClick = {
                                        it.rpid?.let { rpid ->
                                            onReplyClick(oid, rpid)
                                        }
                                    }
                                )
                            }
                        }

                        item {
                            LoadingIndicator(comments.loadState.append)
                        }
                    }
                }
            }
        }
    }
}
