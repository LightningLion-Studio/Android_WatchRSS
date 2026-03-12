package com.lightningstudio.watchrss.debug

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import com.lightningstudio.watchrss.BaseWatchActivity
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.ui.screen.rss.FeedScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PerfLargeListActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }
        setupSystemBars()
        PerformanceMonitor.setScenario(this, "perf_large_list")

        setContent {
            WatchRSSTheme {
                var openSwipeKey by remember { mutableStateOf<Long?>(null) }
                var draggingSwipeKey by remember { mutableStateOf<Long?>(null) }
                val items by produceState(initialValue = emptyList()) {
                    value = withContext(Dispatchers.Default) {
                        PerfDataFactory.buildLargeList()
                    }
                }
                val channel = remember {
                    RssChannel(
                        id = 0L,
                        url = "debug://perf/large_list",
                        title = "性能测试：超大列表",
                        description = null,
                        imageUrl = null,
                        lastFetchedAt = null,
                        sortOrder = 0L,
                        isPinned = false,
                        useOriginalContent = false,
                        unreadCount = 0
                    )
                }

                FeedScreen(
                    channel = channel,
                    items = items,
                    isRefreshing = false,
                    hasMore = false,
                    openSwipeId = openSwipeKey,
                    onOpenSwipe = { openSwipeKey = it },
                    onCloseSwipe = { openSwipeKey = null },
                    draggingSwipeId = draggingSwipeKey,
                    onDragStart = { draggingSwipeKey = it },
                    onDragEnd = { draggingSwipeKey = null },
                    onHeaderClick = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onItemClick = {},
                    onItemLongClick = {},
                    onFavoriteClick = {},
                    onWatchLaterClick = {},
                    onBack = { finish() },
                    densityScale = 3f
                )
            }
        }
    }
}
