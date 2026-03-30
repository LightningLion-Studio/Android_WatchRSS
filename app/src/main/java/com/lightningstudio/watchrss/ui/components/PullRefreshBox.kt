package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val PullRefreshThreshold = 48.dp

@Composable
fun rememberPullRefreshEnabled(listState: LazyListState): Boolean {
    val canRefresh by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }
    return canRefresh
}

@Composable
fun rememberPullRefreshEnabled(scrollState: ScrollState): Boolean {
    val canRefresh by remember(scrollState) {
        derivedStateOf { scrollState.value == 0 }
    }
    return canRefresh
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun rememberWatchPullRefreshState(
    isRefreshing: Boolean,
    onRefresh: () -> Unit
): PullRefreshState {
    return rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh,
        refreshThreshold = PullRefreshThreshold
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WatchPullRefreshIndicator(
    isRefreshing: Boolean,
    state: PullRefreshState,
    modifier: Modifier = Modifier,
    indicatorPadding: Dp = 0.dp
) {
    PullRefreshIndicator(
        refreshing = isRefreshing,
        state = state,
        modifier = modifier.padding(top = indicatorPadding),
        backgroundColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        scale = true
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorPadding: Dp = 0.dp,
    canRefresh: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val refreshState = rememberWatchPullRefreshState(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    )
    Box(modifier = modifier.pullRefresh(refreshState, enabled = canRefresh)) {
        content()
        WatchPullRefreshIndicator(
            isRefreshing = isRefreshing,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            indicatorPadding = indicatorPadding
        )
    }
}
