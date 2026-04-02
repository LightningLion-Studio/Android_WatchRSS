package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.douyin.buildDouyinExternalSavedItem
import com.lightningstudio.watchrss.data.douyin.containsDouyinSavedItem
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStore
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManager
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStore
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinImmersiveScreen
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinLoginScreen
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinRssFeedScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.getWebViewUnavailableMessage
import com.lightningstudio.watchrss.ui.util.warnWebViewUnavailable
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedViewModel
import com.lightningstudio.watchrss.ui.viewmodel.DouyinViewModelFactory
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DouyinEntryActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val repository by lazy { container.douyinRepository }
    private val rssRepository by lazy { container.rssRepository }
    private val preloadManager by lazy { DouyinPreloadManager(this, container.managedCacheService) }
    private val watchHistoryStore by lazy { DouyinWatchHistoryStore(this) }
    private val feedCacheStore by lazy { DouyinFeedCacheStore(this) }
    private val viewModel: DouyinFeedViewModel by viewModels {
        DouyinViewModelFactory(repository, preloadManager, watchHistoryStore, feedCacheStore)
    }
    private var disableSwipeBack = false
    private var handledLauncherOpenToken = AppLaunchSignal.currentToken()
    private var isNavigating by mutableStateOf(false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            isNavigating = false
        }
    }

    override fun onResume() {
        super.onResume()
        val currentToken = AppLaunchSignal.currentToken()
        if (currentToken != handledLauncherOpenToken) {
            handledLauncherOpenToken = currentToken
            if (viewModel.uiState.value.isLoggedIn) {
                viewModel.loadCachedFeedForAppLaunch()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val initialWebViewError = getWebViewUnavailableMessage(this)
        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val warningMessage = remember { mutableStateOf<String?>(null) }
                    val uiState by viewModel.uiState.collectAsState()
                    val originalContentEnabled by remember(rssRepository) {
                        rssRepository.observeChannels().map { channels ->
                            channels.firstOrNull { it.url == BuiltinChannelType.DOUYIN.url }?.useOriginalContent
                                ?: true
                        }
                    }.collectAsState(initial = true)
                    val favoriteItems by remember(rssRepository) {
                        rssRepository.observeSavedItems(SaveType.FAVORITE)
                    }.collectAsState(initial = emptyList())
                    val watchLaterItems by remember(rssRepository) {
                        rssRepository.observeSavedItems(SaveType.WATCH_LATER)
                    }.collectAsState(initial = emptyList())

                    LaunchedEffect(uiState.isLoggedIn) {
                        if (!uiState.isLoggedIn && initialWebViewError != null && warningMessage.value == null) {
                            warningMessage.value = initialWebViewError
                        }
                    }
                    LaunchedEffect(warningMessage.value) {
                        val message = warningMessage.value ?: return@LaunchedEffect
                        warnWebViewUnavailable(this@DouyinEntryActivity, message)
                        warningMessage.value = null
                    }
                    LaunchedEffect(originalContentEnabled, uiState.message) {
                        if (!originalContentEnabled) {
                            val message = uiState.message
                            if (!message.isNullOrBlank()) {
                                com.lightningstudio.watchrss.ui.util.showAppToast(
                                    this@DouyinEntryActivity,
                                    message,
                                    android.widget.Toast.LENGTH_SHORT
                                )
                                viewModel.clearMessage()
                            }
                        }
                    }

                    if (!uiState.isLoggedIn) {
                        SideEffect { disableSwipeBack = true }
                        DouyinLoginScreen(
                            initialErrorMessage = initialWebViewError,
                            onWebViewInitFailed = { warningMessage.value = it },
                            onLoginComplete = viewModel::applyCookie,
                            onBack = { }
                        )
                    } else {
                        SideEffect { disableSwipeBack = false }
                        if (originalContentEnabled) {
                            DouyinImmersiveScreen(
                                uiState = uiState,
                                onRefresh = viewModel::loadInitial,
                                onPageSettled = viewModel::onPageSettled,
                                onEnterFlow = viewModel::enterVideoFlow,
                                onItemLongPress = { item ->
                                    isNavigating = true
                                    startActivity(
                                        DouyinVideoActionsActivity.createIntent(
                                            context = this@DouyinEntryActivity,
                                            awemeId = item.awemeId,
                                            title = item.title,
                                            author = item.author,
                                            playUrl = item.playUrl,
                                            coverUrl = item.coverUrl,
                                            likeCount = item.likeCount
                                        )
                                    )
                                },
                                onRequestPlaybackRefresh = viewModel::refreshPlaybackSource,
                                onMessageShown = viewModel::clearMessage,
                                onHeaderClick = {
                                    isNavigating = true
                                    startActivity(DouyinChannelInfoActivity.createIntent(this@DouyinEntryActivity))
                                }
                            )
                        } else {
                            DouyinRssFeedScreen(
                                uiState = uiState,
                                onRefresh = viewModel::loadInitial,
                                onLoadMore = viewModel::loadMoreForList,
                                onItemClick = { item, _ ->
                                    if (allowNavigation()) {
                                        isNavigating = true
                                        watchHistoryStore.markWatched(item)
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
                                    }
                                },
                                onItemLongClick = { item ->
                                    if (allowNavigation()) {
                                        openDouyinItemActions(item)
                                    }
                                },
                                onFavoriteClick = { item ->
                                    val isFavorite = containsDouyinSavedItem(favoriteItems, item)
                                    lifecycleScope.launch {
                                        toggleDouyinSaved(
                                            item = item,
                                            saveType = SaveType.FAVORITE,
                                            currentlySaved = isFavorite,
                                            successMessage = if (isFavorite) {
                                                "已取消收藏"
                                            } else {
                                                "已收藏"
                                            }
                                        )
                                    }
                                },
                                onWatchLaterClick = { item ->
                                    val isWatchLater = containsDouyinSavedItem(watchLaterItems, item)
                                    lifecycleScope.launch {
                                        toggleDouyinSaved(
                                            item = item,
                                            saveType = SaveType.WATCH_LATER,
                                            currentlySaved = isWatchLater,
                                            successMessage = if (isWatchLater) {
                                                "已从稍后再看移除"
                                            } else {
                                                "已加入稍后再看"
                                            }
                                        )
                                    }
                                },
                                onLoginClick = {
                                    isNavigating = true
                                    DouyinLoginActivity.open(this@DouyinEntryActivity)
                                },
                                onHeaderClick = {
                                    isNavigating = true
                                    startActivity(DouyinChannelInfoActivity.createIntent(this@DouyinEntryActivity))
                                }
                            )
                        }
                    }

                    if (isNavigating) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            WatchCircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    override fun isSwipeBackEnabled(): Boolean = !disableSwipeBack

    private fun openDouyinItemActions(item: DouyinStreamItem) {
        isNavigating = true
        startActivity(
            DouyinVideoActionsActivity.createIntent(
                context = this,
                awemeId = item.awemeId,
                title = item.title,
                author = item.author,
                playUrl = item.playUrl,
                coverUrl = item.coverUrl,
                likeCount = item.likeCount
            )
        )
    }

    private suspend fun toggleDouyinSaved(
        item: DouyinStreamItem,
        saveType: SaveType,
        currentlySaved: Boolean,
        successMessage: String
    ) {
        val external = buildDouyinExternalSavedItem(item)
        if (external == null) {
            com.lightningstudio.watchrss.ui.util.showAppToast(
                this,
                "当前内容暂不支持保存",
                android.widget.Toast.LENGTH_SHORT
            )
            return
        }
        val result = rssRepository.syncExternalSavedItem(
            item = external,
            saveType = saveType,
            saved = !currentlySaved
        )
        val message = if (result.isSuccess) {
            successMessage
        } else {
            result.exceptionOrNull()?.message ?: "操作失败"
        }
        com.lightningstudio.watchrss.ui.util.showAppToast(
            this,
            message,
            android.widget.Toast.LENGTH_SHORT
        )
    }
}
