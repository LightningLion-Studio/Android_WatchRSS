package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStore
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManager
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStore
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinImmersiveScreen
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinLoginScreen
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinRssFeedScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedViewModel
import com.lightningstudio.watchrss.ui.viewmodel.DouyinViewModelFactory
import kotlinx.coroutines.flow.map

class DouyinEntryActivity : BaseWatchActivity() {
    private val repository by lazy { (application as WatchRssApplication).container.douyinRepository }
    private val rssRepository by lazy { (application as WatchRssApplication).container.rssRepository }
    private val preloadManager by lazy { DouyinPreloadManager(this) }
    private val watchHistoryStore by lazy { DouyinWatchHistoryStore(this) }
    private val feedCacheStore by lazy { DouyinFeedCacheStore(this) }
    private val viewModel: DouyinFeedViewModel by viewModels {
        DouyinViewModelFactory(repository, preloadManager, watchHistoryStore, feedCacheStore)
    }
    private var disableSwipeBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val uiState by viewModel.uiState.collectAsState()
                    val originalContentEnabled by remember(rssRepository) {
                        rssRepository.observeChannels().map { channels ->
                            channels.firstOrNull { it.url == BuiltinChannelType.DOUYIN.url }?.useOriginalContent
                                ?: true
                        }
                    }.collectAsState(initial = true)

                    if (!uiState.isLoggedIn) {
                        SideEffect { disableSwipeBack = true }
                        DouyinLoginScreen(
                            onLoginComplete = viewModel::applyCookie,
                            onBack = { }
                        )
                    } else {
                        SideEffect { disableSwipeBack = false }
                        if (originalContentEnabled) {
                            DouyinImmersiveScreen(
                                uiState = uiState,
                                onPageSettled = viewModel::onPageSettled,
                                onEnterFlow = viewModel::enterVideoFlow,
                                onMessageShown = viewModel::clearMessage,
                                onHeaderClick = {
                                    startActivity(DouyinChannelInfoActivity.createIntent(this@DouyinEntryActivity))
                                }
                            )
                        } else {
                            DouyinRssFeedScreen(
                                uiState = uiState,
                                onRefresh = viewModel::loadInitial,
                                onLoadMore = viewModel::loadMoreForList,
                                onItemClick = { item, _ ->
                                    startActivity(
                                        DouyinDetailActivity.createIntent(
                                            context = this@DouyinEntryActivity,
                                            awemeId = item.awemeId,
                                            title = item.title,
                                            author = item.author,
                                            summary = "点赞 ${item.likeCount}",
                                            playUrl = item.playUrl,
                                            coverUrl = item.coverUrl
                                        )
                                    )
                                },
                                onLoginClick = {
                                    startActivity(DouyinLoginActivity.createIntent(this@DouyinEntryActivity))
                                },
                                onHeaderClick = {
                                    startActivity(DouyinChannelInfoActivity.createIntent(this@DouyinEntryActivity))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun isSwipeBackEnabled(): Boolean = !disableSwipeBack
}
