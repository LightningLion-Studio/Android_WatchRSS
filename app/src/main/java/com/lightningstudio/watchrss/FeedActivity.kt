package com.lightningstudio.watchrss

import android.content.Intent
import androidx.compose.runtime.remember
import com.lightningstudio.watchrss.data.novel.NovelReadingHistory
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.ui.screen.novel.NovelContentsScreen
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lightningstudio.watchrss.debug.PerfTrace
import com.lightningstudio.watchrss.debug.PerformanceMonitor
import com.lightningstudio.watchrss.ui.screen.rss.FeedScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.FeedViewModel

class FeedActivity : BaseWatchActivity() {
    private val viewModel: FeedViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    private var openSwipeKey by mutableStateOf<Long?>(null)
    private var draggingSwipeKey by mutableStateOf<Long?>(null)

    override fun onSwipeBackAttempt(dx: Float, dy: Float): Boolean {
        val hasOpen = openSwipeKey != null
        if (hasOpen) {
            openSwipeKey = null
        }
        return hasOpen
    }

    override fun onResume() {
        super.onResume()
        closeOpenSwipe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        val channelId = intent.getLongExtra(EXTRA_CHANNEL_ID, 0L)
        PerformanceMonitor.setScenario(this, "feed_channel_$channelId")
        PerfTrace.log("feed", "activity create channelId=$channelId")
        setContent {
            WatchRSSTheme {
                val context = LocalContext.current
                val channel by viewModel.channel.collectAsState()
                val hasLoadedItems by viewModel.hasLoadedItems.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val hasMore by viewModel.hasMore.collectAsState()
                val message by viewModel.message.collectAsState()

                LaunchedEffect(message) {
                    if (message != null) {
                        com.lightningstudio.watchrss.ui.util.showAppToast(context, message, android.widget.Toast.LENGTH_SHORT)
                        viewModel.clearMessage()
                    }
                }

                val openingNovelItemId by viewModel.openingNovelItemId.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    val novelChannel = channel?.takeIf { ImportedContentIds.isNovelChapterSourceUrl(it.url) }
                    if (novelChannel != null) {
                        val catalog by viewModel.novelCatalog.collectAsState()
                        val history = remember { NovelReadingHistory(this@FeedActivity) }
                        val lastChapter by remember(novelChannel.url) { history.observe(novelChannel.url) }.collectAsState(initial = null)
                        NovelContentsScreen(
                            channel = novelChannel,
                            items = catalog.items,
                            loaded = catalog.loaded,
                            lastChapterUrl = lastChapter,
                            onChapterClick = { item ->
                                viewModel.openItem(item) {
                                    if (allowNavigation()) {
                                        startActivity(Intent(this@FeedActivity, DetailActivity::class.java)
                                            .putExtra(DetailActivity.EXTRA_ITEM_ID, item.id))
                                    }
                                }
                            },
                            onChapterLongClick = { item -> if (allowNavigation()) showItemActions(item) },
                            onHeaderClick = { if (allowNavigation()) openChannelDetail() }
                        )
                    } else {
                        val items by viewModel.items.collectAsState()
                        FeedScreen(
                            channel = channel,
                            items = items,
                            hasLoadedItems = hasLoadedItems,
                            isRefreshing = isRefreshing,
                            hasMore = hasMore,
                            openSwipeId = openSwipeKey,
                            onOpenSwipe = { openSwipeKey = it },
                            onCloseSwipe = { openSwipeKey = null },
                            draggingSwipeId = draggingSwipeKey,
                            onDragStart = { draggingSwipeKey = it },
                            onDragEnd = { draggingSwipeKey = null },
                            onHeaderClick = {
                                if (closeOpenSwipe()) return@FeedScreen
                                if (!allowNavigation()) return@FeedScreen
                                openChannelDetail()
                            },
                            onRefresh = { viewModel.refresh() },
                            onLoadMore = { viewModel.loadMore() },
                            onItemClick = { item ->
                                if (closeOpenSwipe()) return@FeedScreen
                                viewModel.openItem(item) {
                                    // Reserve navigation only after the asynchronous save; an earlier
                                    // reservation expires before the shared navigation throttle allows it.
                                    if (allowNavigation()) {
                                        val intent = Intent(this@FeedActivity, DetailActivity::class.java)
                                        intent.putExtra(DetailActivity.EXTRA_ITEM_ID, item.id)
                                        startActivity(intent)
                                    }
                                }
                            },
                            onItemLongClick = { item ->
                                if (!allowNavigation()) return@FeedScreen
                                showItemActions(item)
                            },
                            onFavoriteClick = { item ->
                                closeOpenSwipe()
                                viewModel.toggleFavorite(item.id)
                            },
                            onWatchLaterClick = { item ->
                                closeOpenSwipe()
                                viewModel.toggleWatchLater(item.id)
                            },
                            onBack = { finish() },
                            onOriginalContentScrollStateChanged = viewModel::setOriginalContentUpdatesPaused,
                            onRequestOriginalContents = viewModel::requestOriginalContents
                        )
                    }
                    if (openingNovelItemId != null) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable {},
                            contentAlignment = Alignment.Center
                        ) {
                            Text("正在保存阅读状态…", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    private fun closeOpenSwipe(): Boolean {
        val hasOpen = openSwipeKey != null
        if (hasOpen) {
            openSwipeKey = null
        }
        return hasOpen
    }

    private fun showItemActions(item: com.lightningstudio.watchrss.data.rss.RssItem) {
        val intent = Intent(this, ItemActionsActivity::class.java)
        intent.putExtra(ItemActionsActivity.EXTRA_ITEM_ID, item.id)
        intent.putExtra(ItemActionsActivity.EXTRA_ITEM_TITLE, item.title)
        startActivity(intent)
    }

    private fun openChannelDetail() {
        val channelId = intent.getLongExtra(EXTRA_CHANNEL_ID, 0L)
        if (channelId <= 0L) return
        val intent = Intent(this, ChannelDetailActivity::class.java)
        intent.putExtra(ChannelDetailActivity.EXTRA_CHANNEL_ID, channelId)
        startActivity(intent)
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channelId"
    }
}
