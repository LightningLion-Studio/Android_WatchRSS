package com.lightningstudio.watchrss

import android.content.Intent
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
import com.lightningstudio.watchrss.data.network.InternetAvailabilityStatus
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
import com.lightningstudio.watchrss.debug.DouyinPlaybackDebugController
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinImmersiveScreen
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinLoginScreen
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinRssFeedScreen
import com.lightningstudio.watchrss.ui.screen.douyin.rememberDouyinPlayerPoolSession
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.getWebViewUnavailableMessage
import com.lightningstudio.watchrss.ui.util.warnWebViewUnavailable
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedViewModel
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedUiState
import com.lightningstudio.watchrss.ui.viewmodel.DouyinViewModelFactory
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DouyinEntryActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val repository by lazy { container.douyinRepository }
    private val rssRepository by lazy { container.rssRepository }
    private val preloadManager by lazy { container.douyinPreloadManager }
    private val playbackTransport by lazy { container.douyinPlaybackTransport }
    private val playbackSourceCoordinator by lazy { container.douyinPlaybackSourceCoordinator }
    private val watchHistoryStore by lazy { container.douyinWatchHistoryStore }
    private val feedCacheStore by lazy { container.douyinFeedCacheStore }
    private val recentWindowStore by lazy { container.douyinRecentWindowStore }
    private val recentWindowCacheCoordinator by lazy { container.douyinRecentWindowCacheCoordinator }
    private val viewModel: DouyinFeedViewModel by viewModels {
        DouyinViewModelFactory(
            repository = repository,
            preloadManager = preloadManager,
            playbackTransport = playbackTransport,
            playbackSourceCoordinator = playbackSourceCoordinator,
            watchHistoryStore = watchHistoryStore,
            feedCacheStore = feedCacheStore,
            recentWindowStore = recentWindowStore,
            recentWindowCacheCoordinator = recentWindowCacheCoordinator,
            resumeToVideoFlowOnEntry = shouldResumeToVideoFlow(intent),
            resumeAwemeIdOnEntry = readResumeAwemeId(intent)
        )
    }
    private var disableSwipeBack = false
    private var handledLauncherOpenToken = AppLaunchSignal.currentToken()
    private var isNavigating by mutableStateOf(false)
    private var pendingAutoEnterFlow = false
    private var pendingResumeAwemeId: String? = null

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
            val state = viewModel.uiState.value
            if (state.isLoggedIn && state.showTitlePage) {
                viewModel.loadCachedFeedForAppLaunch()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        pendingAutoEnterFlow = shouldAutoEnterFlow(intent)
        pendingResumeAwemeId = readResumeAwemeId(intent)

        val initialWebViewError = getWebViewUnavailableMessage(this)
        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val warningMessage = remember { mutableStateOf<String?>(null) }
                    val uiState by viewModel.uiState.collectAsState()
                    val volumeGuardEnabled by container.settingsRepository.mediaVolumeGuardEnabled.collectAsState(
                        initial = DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
                    )
                    val volumeControlEnabled by container.settingsRepository.mediaVolumeControlEnabled.collectAsState(
                        initial = DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
                    )
                    val playbackStartVolumeLimitPercent by container.settingsRepository.mediaPlaybackStartVolumeLimitPercent.collectAsState(
                        initial = DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT
                    )
                    val internetAvailabilityStatus by container.internetAvailabilityMonitor.internetAvailability.collectAsState(
                        initial = InternetAvailabilityStatus.Checking
                    )
                    val douyinChannel by remember(rssRepository) {
                        rssRepository.observeChannels().map { channels ->
                            channels.firstOrNull { it.url == BuiltinChannelType.DOUYIN.url }
                        }
                    }.collectAsState(initial = null)
                    val originalContentEnabled = douyinChannel?.useOriginalContent ?: true
                    val continuePlaybackInBackground =
                        douyinChannel?.continuePlaybackInBackground ?: false
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
                    LaunchedEffect(uiState.isLoggedIn, uiState.items.size, uiState.showTitlePage) {
                        if (
                            pendingAutoEnterFlow &&
                            uiState.isLoggedIn &&
                            uiState.showTitlePage &&
                            uiState.items.isNotEmpty()
                        ) {
                            val resumeAwemeId = pendingResumeAwemeId
                            pendingAutoEnterFlow = false
                            viewModel.enterVideoFlow(resumeAwemeId)
                            pendingResumeAwemeId = null
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
                        val playerSession = rememberDouyinPlayerPoolSession(
                            headers = uiState.playHeaders,
                            enabled = originalContentEnabled
                        )
                        if (originalContentEnabled) {
                            if (playerSession == null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    WatchCircularProgressIndicator()
                                }
                            } else {
                                DouyinImmersiveScreen(
                                    playerSession = playerSession,
                                    uiState = uiState,
                                    digitalCrownVolumeEnabled = volumeControlEnabled,
                                    volumeGuardEnabled = volumeGuardEnabled,
                                    playbackStartVolumeLimitPercent = playbackStartVolumeLimitPercent,
                                    continuePlaybackInBackground = continuePlaybackInBackground,
                                    internetAvailabilityStatus = internetAvailabilityStatus,
                                    onRefresh = {
                                        if (uiState.showTitlePage) {
                                            viewModel.refreshTitlePageFeed()
                                        } else {
                                            viewModel.loadInitial()
                                        }
                                    },
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
                                    onDiscardPlaybackItem = viewModel::discardPlaybackItem,
                                    onMessageShown = viewModel::clearMessage,
                                    onHeaderClick = {
                                        isNavigating = true
                                        startActivity(DouyinChannelInfoActivity.createIntent(this@DouyinEntryActivity))
                                    }
                                )
                            }
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.action == ACTION_DEBUG_ADVANCE_TO_NEXT_VIDEO) {
            DouyinPlaybackDebugController.requestAdvanceToNextVideo(source = "intent")
            return
        }
        if (!shouldAutoEnterFlow(intent)) return
        pendingAutoEnterFlow = true
        pendingResumeAwemeId = readResumeAwemeId(intent)
        val state = viewModel.uiState.value
        if (state.isLoggedIn && state.showTitlePage && state.items.isNotEmpty()) {
            val resumeAwemeId = pendingResumeAwemeId
            pendingAutoEnterFlow = false
            viewModel.enterVideoFlow(resumeAwemeId)
            pendingResumeAwemeId = null
        }
    }

    override fun buildResumeIntent(): Intent {
        val state = viewModel.uiState.value
        return createIntent(
            context = this,
            resumeToVideoFlow = shouldResumeDouyinVideoFlow(state),
            resumeAwemeId = resolveResumeDouyinAwemeId(state)
        )
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

    private fun shouldAutoEnterFlow(intent: Intent? = this.intent): Boolean {
        return intent?.action == ACTION_DEBUG_OPEN_DOUYIN ||
            intent?.getBooleanExtra(EXTRA_DEBUG_AUTO_ENTER_FLOW, false) == true
    }

    private fun shouldResumeToVideoFlow(intent: Intent? = this.intent): Boolean {
        return intent?.getBooleanExtra(EXTRA_RESUME_TO_VIDEO_FLOW, false) == true
    }

    private fun readResumeAwemeId(intent: Intent? = this.intent): String? {
        return intent?.getStringExtra(EXTRA_RESUME_AWEME_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        const val EXTRA_DEBUG_AUTO_ENTER_FLOW = "watchrss.debug.auto_enter_douyin_flow"
        const val EXTRA_RESUME_TO_VIDEO_FLOW = "resume_to_video_flow"
        const val EXTRA_RESUME_AWEME_ID = "resume_aweme_id"
        const val ACTION_DEBUG_OPEN_DOUYIN = "com.lightningstudio.watchrss.debug.action.OPEN_DOUYIN"
        const val ACTION_DEBUG_ADVANCE_TO_NEXT_VIDEO =
            DouyinPlaybackDebugController.ACTION_ADVANCE_TO_NEXT_VIDEO

        fun createIntent(
            context: android.content.Context,
            resumeToVideoFlow: Boolean = false,
            resumeAwemeId: String? = null
        ): Intent {
            return Intent(context, DouyinEntryActivity::class.java).apply {
                putExtra(EXTRA_RESUME_TO_VIDEO_FLOW, resumeToVideoFlow)
                resumeAwemeId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { putExtra(EXTRA_RESUME_AWEME_ID, it) }
            }
        }
    }
}

internal fun shouldResumeDouyinVideoFlow(uiState: DouyinFeedUiState): Boolean {
    return uiState.isLoggedIn &&
        !uiState.showTitlePage &&
        uiState.currentPage > 0 &&
        uiState.items.isNotEmpty()
}

internal fun resolveResumeDouyinAwemeId(uiState: DouyinFeedUiState): String? {
    if (!shouldResumeDouyinVideoFlow(uiState)) return null
    return uiState.items
        .getOrNull(uiState.currentPage - 1)
        ?.awemeId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
