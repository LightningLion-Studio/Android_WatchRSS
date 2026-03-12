package com.lightningstudio.watchrss.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.lightningstudio.watchrss.BeianActivity
import com.lightningstudio.watchrss.BiliEntryActivity
import com.lightningstudio.watchrss.ChannelDetailActivity
import com.lightningstudio.watchrss.DouyinEntryActivity
import com.lightningstudio.watchrss.ItemActionsActivity
import com.lightningstudio.watchrss.OobeActivity
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.debug.PerfEntryActivity
import com.lightningstudio.watchrss.ui.screen.rss.AddRssScreen
import com.lightningstudio.watchrss.ui.screen.rss.DetailScreen
import com.lightningstudio.watchrss.ui.screen.rss.FeedScreen
import com.lightningstudio.watchrss.ui.screen.rss.HomeScreen
import com.lightningstudio.watchrss.ui.screen.rss.SettingsScreen
import com.lightningstudio.watchrss.ui.viewmodel.AddRssViewModel
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.DetailViewModel
import com.lightningstudio.watchrss.ui.viewmodel.FeedViewModel
import com.lightningstudio.watchrss.ui.viewmodel.HomeViewModel
import com.lightningstudio.watchrss.ui.viewmodel.SettingsViewModel

object Routes {
    const val HOME = "home"
    const val ADD_RSS = "add_rss"
    const val FEED = "feed/{channelId}"
    const val DETAIL = "detail/{itemId}"
    const val SETTINGS = "settings"

    fun feed(channelId: Long) = "feed/$channelId"
    fun detail(itemId: Long) = "detail/$itemId"
}

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val container = (context.applicationContext as WatchRssApplication).container
    val factory = remember(container) { AppViewModelFactory(container) }
    val settingsRepository = remember(container) { container.settingsRepository }

    // 导航节流：防止快速重复点击导致重复导航
    var lastNavigationTime by remember { mutableLongStateOf(0L) }
    val allowNavigation: () -> Boolean = remember {
        {
            val now = SystemClock.elapsedRealtime()
            if (now - lastNavigationTime < NAVIGATION_THROTTLE_MS) {
                false
            } else {
                lastNavigationTime = now
                true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                channels = viewModel.channels,
                isRefreshing = viewModel.isRefreshing,
                message = viewModel.message,
                onRefreshAll = viewModel::refreshAll,
                onMessageShown = viewModel::clearMessage,
                onAddRss = {
                    if (!allowNavigation()) return@HomeScreen
                    navController.navigate(Routes.ADD_RSS)
                },
                onOpenSettings = {
                    if (!allowNavigation()) return@HomeScreen
                    navController.navigate(Routes.SETTINGS)
                },
                onChannelClick = { channel ->
                    if (!allowNavigation()) return@HomeScreen
                    when (BuiltinChannelType.fromUrl(channel.url)) {
                        BuiltinChannelType.BILI -> {
                            context.startActivity(android.content.Intent(context, BiliEntryActivity::class.java))
                        }
                        BuiltinChannelType.DOUYIN -> {
                            context.startActivity(android.content.Intent(context, DouyinEntryActivity::class.java))
                        }
                        null -> navController.navigate(Routes.feed(channel.id))
                    }
                }
            )
        }
        composable(Routes.ADD_RSS) {
            val viewModel: AddRssViewModel = viewModel(factory = factory)
            val phoneConnectionEnabled by settingsRepository.phoneConnectionEnabled.collectAsState(initial = true)
            AddRssScreen(
                uiState = viewModel.uiState,
                showRemoteInputButton = phoneConnectionEnabled,
                onUrlChange = viewModel::updateUrl,
                onSubmit = viewModel::submit,
                onConfirm = viewModel::confirmAdd,
                onBack = { navController.popBackStack() },
                onBackToInput = viewModel::backToInput,
                onOpenExisting = { channel ->
                    if (!allowNavigation()) return@AddRssScreen
                    when (BuiltinChannelType.fromUrl(channel.url)) {
                        BuiltinChannelType.BILI -> {
                            context.startActivity(android.content.Intent(context, BiliEntryActivity::class.java))
                        }
                        BuiltinChannelType.DOUYIN -> {
                            context.startActivity(android.content.Intent(context, DouyinEntryActivity::class.java))
                        }
                        null -> navController.navigate(Routes.feed(channel.id))
                    }
                },
                onChannelAdded = { url, channelId ->
                    if (!allowNavigation()) return@AddRssScreen
                    val builtin = BuiltinChannelType.fromUrl(url)
                        ?: BuiltinChannelType.fromHost(runCatching { Uri.parse(url).host }.getOrNull())
                    when (builtin) {
                        BuiltinChannelType.BILI -> {
                            context.startActivity(android.content.Intent(context, BiliEntryActivity::class.java))
                        }
                        BuiltinChannelType.DOUYIN -> {
                            context.startActivity(android.content.Intent(context, DouyinEntryActivity::class.java))
                        }
                        null -> {
                            navController.navigate(Routes.feed(channelId)) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    }
                },
                onConsumed = viewModel::consumeCreatedChannel,
                onClearError = viewModel::clearError,
                onRemoteInput = { /* TODO: Implement remote input */ }
            )
        }
        composable(
            route = Routes.FEED,
            arguments = listOf(navArgument("channelId") { type = NavType.LongType })
        ) { backStackEntry ->
            val viewModel: FeedViewModel = viewModel(backStackEntry, factory = factory)
            val channel by viewModel.channel.collectAsState()
            val items by viewModel.items.collectAsState()
            val isRefreshing by viewModel.isRefreshing.collectAsState()
            val hasMore by viewModel.hasMore.collectAsState()
            val message by viewModel.message.collectAsState()
            var openSwipeId by remember { mutableStateOf<Long?>(null) }
            var draggingSwipeId by remember { mutableStateOf<Long?>(null) }

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
                openSwipeId = openSwipeId,
                onOpenSwipe = { openSwipeId = it },
                onCloseSwipe = { openSwipeId = null },
                draggingSwipeId = draggingSwipeId,
                onDragStart = { draggingSwipeId = it },
                onDragEnd = { draggingSwipeId = null },
                onHeaderClick = {
                    val channelId = channel?.id ?: return@FeedScreen
                    val intent = Intent(context, ChannelDetailActivity::class.java)
                    intent.putExtra(ChannelDetailActivity.EXTRA_CHANNEL_ID, channelId)
                    context.startActivity(intent)
                },
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onItemClick = { item ->
                    if (!allowNavigation()) return@FeedScreen
                    navController.navigate(Routes.detail(item.id))
                },
                onItemLongClick = { item ->
                    val intent = Intent(context, ItemActionsActivity::class.java)
                    intent.putExtra(ItemActionsActivity.EXTRA_ITEM_ID, item.id)
                    intent.putExtra(ItemActionsActivity.EXTRA_ITEM_TITLE, item.title)
                    context.startActivity(intent)
                },
                onFavoriteClick = { item -> viewModel.toggleFavorite(item.id) },
                onWatchLaterClick = { item -> viewModel.toggleWatchLater(item.id) },
                onBack = { navController.popBackStack() },
                onOriginalContentScrollStateChanged = viewModel::setOriginalContentUpdatesPaused,
                onRequestOriginalContents = viewModel::requestOriginalContents
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val viewModel: DetailViewModel = viewModel(backStackEntry, factory = factory)
            DetailScreen(
                viewModel = viewModel,
                onBack = { _, _, _ -> navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                cacheLimitMb = viewModel.cacheLimitMb,
                cacheUsageMb = viewModel.cacheUsageMb,
                readingThemeDark = viewModel.readingThemeDark,
                shareUseSystem = viewModel.shareUseSystem,
                readingFontSizeSp = viewModel.readingFontSizeSp,
                phoneConnectionEnabled = viewModel.phoneConnectionEnabled,
                showPerformanceTools = false,
                onSelectCacheLimit = viewModel::updateCacheLimitMb,
                onToggleReadingTheme = viewModel::toggleReadingTheme,
                onToggleShareMode = viewModel::toggleShareUseSystem,
                onSelectFontSize = viewModel::updateReadingFontSizeSp,
                onTogglePhoneConnection = viewModel::togglePhoneConnection,
                onOpenOobe = {
                    context.startActivity(OobeActivity.createIntent(context, returnHomeOnFinish = false))
                },
                onOpenPerfLargeList = {
                    context.startActivity(
                        PerfEntryActivity.createIntent(
                            context,
                            PerfEntryActivity.TARGET_LARGE_LIST
                        )
                    )
                },
                onOpenPerfLargeArticle = {
                    context.startActivity(
                        PerfEntryActivity.createIntent(
                            context,
                            PerfEntryActivity.TARGET_LARGE_ARTICLE
                        )
                    )
                },
                onBeianClick = {
                    context.startActivity(BeianActivity.createIntent(context))
                }
            )
        }
    }
}

private const val NAVIGATION_THROTTLE_MS = 600L
