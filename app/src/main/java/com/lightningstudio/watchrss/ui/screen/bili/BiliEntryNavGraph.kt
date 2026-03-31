package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lightningstudio.watchrss.BiliChannelInfoActivity
import com.lightningstudio.watchrss.BiliDetailActivity
import com.lightningstudio.watchrss.BiliVideoActionsActivity
import com.lightningstudio.watchrss.BiliLoginActivity
import com.lightningstudio.watchrss.BiliListActivity
import com.lightningstudio.watchrss.BiliSearchActivity
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.ui.viewmodel.BiliFeedViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.BaseWatchActivity
import com.lightningstudio.watchrss.ui.viewmodel.BiliListType
import kotlinx.coroutines.flow.map

object BiliRoutes {
    const val FEED = "bili_feed"
    const val SEARCH = "bili_search"
    const val SEARCH_RESULT = "bili_search_result/{keyword}"
    const val COMMENT = "bili_comment/{oid}/{uploaderMid}"
    const val REPLY_DETAIL = "bili_reply_detail/{oid}/{root}/{uploaderMid}"

    fun searchResult(keyword: String) = "bili_search_result/$keyword"
    fun comment(oid: Long, uploaderMid: Long) = "bili_comment/$oid/$uploaderMid"
    fun replyDetail(oid: Long, root: Long, uploaderMid: Long) = "bili_reply_detail/$oid/$root/$uploaderMid"
}

@Composable
fun BiliEntryNavGraph(repository: BiliRepositoryContract, rssRepository: RssRepository) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val factory = remember(repository, rssRepository) { BiliViewModelFactory(repository, rssRepository) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val originalContentEnabled by remember(rssRepository) {
        rssRepository.observeChannels().map { channels ->
            channels.firstOrNull { it.url == BuiltinChannelType.BILI.url }?.useOriginalContent ?: true
        }
    }.collectAsState(initial = true)

    LaunchedEffect(currentBackStackEntry?.destination?.route) {
        (context as? BaseWatchActivity)?.resetNavigationThrottle()
    }

    NavHost(
        navController = navController,
        startDestination = BiliRoutes.FEED
    ) {
        composable(BiliRoutes.FEED) {
            val viewModel: BiliFeedViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsState()
            val lifecycleOwner = LocalLifecycleOwner.current

            LaunchedEffect(Unit) {
                viewModel.refreshLoginState()
            }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshLoginState()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(uiState.message) {
                val message = uiState.message
                if (!message.isNullOrBlank()) {
                    com.lightningstudio.watchrss.ui.util.showAppToast(context, message, android.widget.Toast.LENGTH_SHORT)
                    viewModel.clearMessage()
                }
            }

            if (originalContentEnabled) {
                BiliFeedScreen(
                    uiState = uiState,
                    onLoginClick = { context.startActivity(BiliLoginActivity.createIntent(context)) },
                    onRefresh = viewModel::refresh,
                    onHeaderClick = { context.startActivity(BiliChannelInfoActivity.createIntent(context)) },
                    onLoadMore = viewModel::loadMore,
                    onOpenWatchLater = {
                        context.startActivity(BiliListActivity.createIntent(context, BiliListType.WATCH_LATER))
                    },
                    onOpenHistory = {
                        context.startActivity(BiliListActivity.createIntent(context, BiliListType.HISTORY))
                    },
                    onOpenFavorites = {
                        context.startActivity(BiliListActivity.createIntent(context, BiliListType.FAVORITE))
                    },
                    onFavoriteClick = viewModel::favorite,
                    onWatchLaterClick = viewModel::watchLater,
                    onItemClick = { item ->
                        context.startActivity(
                            BiliDetailActivity.createIntent(context, item.aid, item.bvid, item.cid)
                        )
                    },
                    onItemLongClick = { item ->
                        val hostActivity = context as? BaseWatchActivity
                        if (hostActivity != null && !hostActivity.tryAllowNavigation()) {
                            return@BiliFeedScreen
                        }
                        context.startActivity(
                            BiliVideoActionsActivity.createIntent(
                                context = context,
                                aid = item.aid,
                                bvid = item.bvid,
                                cid = item.cid,
                                title = item.title,
                                owner = item.owner?.name,
                                coverUrl = item.cover
                            )
                        )
                    },
                    onSearchClick = {
                        val hostActivity = context as? BaseWatchActivity
                        if (hostActivity != null && !hostActivity.tryAllowNavigation()) {
                            return@BiliFeedScreen
                        }
                        context.startActivity(BiliSearchActivity.createIntent(context))
                    }
                )
            } else {
                BiliRssFeedScreen(
                    uiState = uiState,
                    onLoginClick = { context.startActivity(BiliLoginActivity.createIntent(context)) },
                    onRefresh = viewModel::refresh,
                    onHeaderClick = { context.startActivity(BiliChannelInfoActivity.createIntent(context)) },
                    onLoadMore = viewModel::loadMore,
                    onFavoriteClick = viewModel::favorite,
                    onWatchLaterClick = viewModel::watchLater,
                    onItemClick = { item ->
                        context.startActivity(
                            BiliDetailActivity.createIntent(
                                context = context,
                                aid = item.aid,
                                bvid = item.bvid,
                                cid = item.cid,
                                rssMode = true
                            )
                        )
                    },
                    onItemLongClick = { item ->
                        val hostActivity = context as? BaseWatchActivity
                        if (hostActivity != null && !hostActivity.tryAllowNavigation()) {
                            return@BiliRssFeedScreen
                        }
                        context.startActivity(
                            BiliVideoActionsActivity.createIntent(
                                context = context,
                                aid = item.aid,
                                bvid = item.bvid,
                                cid = item.cid,
                                title = item.title,
                                owner = item.owner?.name,
                                coverUrl = item.cover
                            )
                        )
                    }
                )
            }
        }

        composable(BiliRoutes.COMMENT) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("oid")?.toLongOrNull() ?: 0L
            val uploaderMid = backStackEntry.arguments?.getString("uploaderMid")?.toLongOrNull() ?: 0L
            BiliCommentScreen(
                oid = oid,
                uploaderMid = uploaderMid,
                factory = factory,
                onNavigateBack = { navController.popBackStack() },
                onReplyClick = { commentOid, root ->
                    val hostActivity = context as? BaseWatchActivity
                    if (hostActivity != null && !hostActivity.tryAllowNavigation()) {
                        return@BiliCommentScreen
                    }
                    navController.navigate(BiliRoutes.replyDetail(commentOid, root, uploaderMid)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(BiliRoutes.REPLY_DETAIL) { backStackEntry ->
            val oid = backStackEntry.arguments?.getString("oid")?.toLongOrNull() ?: 0L
            val root = backStackEntry.arguments?.getString("root")?.toLongOrNull() ?: 0L
            val uploaderMid = backStackEntry.arguments?.getString("uploaderMid")?.toLongOrNull() ?: 0L
            BiliReplyDetailScreen(
                oid = oid,
                root = root,
                uploaderMid = uploaderMid,
                factory = factory,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
