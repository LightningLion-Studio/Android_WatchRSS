package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
        setContent {
            WatchRSSTheme {
                val context = LocalContext.current
                val channel by viewModel.channel.collectAsState()
                val items by viewModel.items.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val hasMore by viewModel.hasMore.collectAsState()
                val message by viewModel.message.collectAsState()

                LaunchedEffect(message) {
                    if (message != null) {
                        com.lightningstudio.watchrss.ui.util.showAppToast(context, message, android.widget.Toast.LENGTH_SHORT)
                        viewModel.clearMessage()
                    }
                }

                FeedScreen(
                    channel = channel,
                    items = items,
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
                        if (!allowNavigation()) return@FeedScreen
                        val intent = Intent(this, DetailActivity::class.java)
                        intent.putExtra(DetailActivity.EXTRA_ITEM_ID, item.id)
                        startActivity(intent)
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
