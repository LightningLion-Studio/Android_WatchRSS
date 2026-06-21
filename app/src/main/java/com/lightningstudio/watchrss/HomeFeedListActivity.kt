package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewCache
import com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.douyin.DOUYIN_ACTIVE_PRELOAD_WINDOW_UNWATCHED
import com.lightningstudio.watchrss.data.douyin.DOUYIN_PLAYBACK_PREFETCH_COUNT
import com.lightningstudio.watchrss.data.douyin.DOUYIN_RECENT_WINDOW_SIZE
import com.lightningstudio.watchrss.data.douyin.dropDouyinItemsBeforeAwemeId
import com.lightningstudio.watchrss.data.douyin.mergeDouyinBootstrapItems
import com.lightningstudio.watchrss.data.douyin.prioritizeDouyinPreloadItems
import com.lightningstudio.watchrss.data.douyin.refreshExpiredDouyinBootstrapPlayUrls
import com.lightningstudio.watchrss.data.douyin.resolveDouyinResumeAnchorAwemeId
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.debug.PerformanceMonitor
import com.lightningstudio.watchrss.debug.StartupDurationTracker
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import com.lightningstudio.watchrss.ui.screen.home.HomeComposeScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.HomeViewModel
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFeedListActivity : BaseWatchActivity() {
    private val viewModel: HomeViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    private val closeOpenSwipeBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeOpenSwipe()
        }
    }
    private val openSwipeKeyState = mutableStateOf<Long?>(null)
    private var openSwipeKey: Long?
        get() = openSwipeKeyState.value
        set(value) {
            openSwipeKeyState.value = value
            closeOpenSwipeBackCallback.isEnabled = value != null
        }
    private var draggingSwipeKey by mutableStateOf<Long?>(null)
    private var initialStartupCompleted = false
    private var startupMaintenanceScheduled = false
    private var launcherWarmupScheduled = false
    private var douyinWarmupJob: Job? = null
    private var douyinCacheWarmupJob: Job? = null
    private var initialHomeLoginRefreshScheduled = false
    private var homePinnedPreviewRestoreScheduled = false
    private val refreshPlatformLoginStateRunnable = Runnable {
        if (!isDestroyed) {
            viewModel.refreshPlatformLoginState()
        }
    }

    override fun onSwipeBackAttempt(dx: Float, dy: Float): Boolean {
        val hasOpen = openSwipeKey != null
        if (hasOpen) {
            openSwipeKey = null
        }
        return hasOpen
    }

    override fun isSwipeBackEnabled(): Boolean = true

    override fun shouldAnimateSwipeBackGesture(): Boolean = false

    override fun shouldResetViewStateImmediatelyOnTouchEnd(): Boolean = false

    override fun shouldScheduleDelayedViewStateResetOnTouchEnd(): Boolean = false

    override fun buildResumeIntent(): Intent = createIntent(this)

    override fun onStop() {
        super.onStop()
        douyinWarmupJob?.cancel()
        douyinWarmupJob = null
        douyinCacheWarmupJob?.cancel()
        douyinCacheWarmupJob = null
    }

    override fun onResume() {
        super.onResume()
        closeOpenSwipe()
        if (initialStartupCompleted) {
            schedulePlatformLoginStateRefresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PerformanceMonitor.setScenario(this, "home_cold_start")
        onBackPressedDispatcher.addCallback(this, closeOpenSwipeBackCallback)
        setupSystemBars()
        renderHomeContent()
        initialStartupCompleted = true
        restorePinnedDouyinPreviewsOnHome()

        if (intent.getBooleanExtra(EXTRA_LAUNCHER_ENTRY, false)) {
            scheduleStartupMaintenance()
            scheduleLauncherWarmup()
        }
    }

    private fun renderHomeContent() {
        setContent {
            WatchRSSTheme {
                val context = LocalContext.current
                val channels by viewModel.channels.collectAsState()
                val hasLoadedChannels by viewModel.hasLoadedChannels.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val message by viewModel.message.collectAsState()
                val platformLoginState by viewModel.platformLoginState.collectAsState()
                val readAloudController = (application as WatchRssApplication)
                    .container
                    .readAloudController
                val readAloudState by readAloudController.uiState.collectAsState()

                androidx.compose.runtime.LaunchedEffect(hasLoadedChannels) {
                    if (hasLoadedChannels) {
                        scheduleInitialHomeLoginStateRefresh()
                    }
                }

                androidx.compose.runtime.LaunchedEffect(message) {
                    if (message != null) {
                        com.lightningstudio.watchrss.ui.util.showAppToast(
                            context,
                            message,
                            Toast.LENGTH_SHORT
                        )
                        viewModel.clearMessage()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    HomeComposeScreen(
                        channels = channels,
                        hasLoadedChannels = hasLoadedChannels,
                        platformLoginState = platformLoginState,
                        readAloudState = readAloudState,
                        readAloudAudioSpectrum = readAloudController.audioSpectrumFrames,
                        enableChannelSwipeActions = false,
                        isRefreshing = isRefreshing,
                        debugAutoScrollPerf = intent.getBooleanExtra(
                            EXTRA_DEBUG_AUTOSCROLL_PERF,
                            false
                        ),
                        onMinimalContentReady = {
                            StartupDurationTracker.markStartupReady(destination = "home")
                        },
                        onRefreshAll = viewModel::refreshAll,
                        openSwipeId = openSwipeKey,
                        onOpenSwipe = { openSwipeKey = it },
                        onCloseSwipe = { openSwipeKey = null },
                        draggingSwipeId = draggingSwipeKey,
                        onDragStart = { draggingSwipeKey = it },
                        onDragEnd = { draggingSwipeKey = null },
                        onProfileClick = {
                            startNavigatingActivity(
                                intent = Intent(this@HomeFeedListActivity, ProfileActivity::class.java)
                            )
                        },
                        onRecommendClick = {
                            if (closeOpenSwipe()) return@HomeComposeScreen
                            startNavigatingActivity(
                                intent = Intent(this@HomeFeedListActivity, RssRecommendActivity::class.java)
                            )
                        },
                        onReadAloudClick = {
                            startNavigatingActivity(
                                ReadAloudPlaybackActivity.createIntent(this@HomeFeedListActivity)
                            )
                        },
                        onChannelClick = { channel ->
                            if (closeOpenSwipe()) return@HomeComposeScreen
                            openChannel(channel)
                        },
                        onChannelLongClick = { channel ->
                            showChannelActions(channel, quick = false)
                        },
                        onSwipeBack = {
                            onBackPressedDispatcher.onBackPressed()
                        },
                        onAddRssClick = {
                            startNavigatingActivity(
                                intent = Intent(this@HomeFeedListActivity, AddRssActivity::class.java)
                            )
                        },
                        onMoveTopClick = { channel ->
                            closeOpenSwipe()
                            viewModel.moveToTop(channel)
                        },
                        onMarkReadClick = { channel ->
                            closeOpenSwipe()
                            viewModel.markChannelRead(channel)
                        },
                        onBeianClick = {
                            startNavigatingActivity(BeianActivity.createIntent(this@HomeFeedListActivity))
                        }
                    )
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

    private fun usesSystemBackGesture(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    private fun ensureDouyinWarmup(
        cacheFirst: Boolean,
        logReason: String
    ): Job {
        if (cacheFirst) {
            return ensureDouyinCacheWarmup(logReason)
        }
        douyinWarmupJob?.takeIf { it.isActive }?.let { return it }
        return launchDouyinWarmup(cacheFirst = false, logReason = logReason)
    }

    private fun ensureDouyinCacheWarmup(logReason: String): Job {
        douyinCacheWarmupJob?.takeIf { it.isActive }?.let { return it }
        return lifecycleScope.launch(Dispatchers.IO) {
            val container = (application as WatchRssApplication).container
            if (!container.douyinRepository.isLoggedIn()) return@launch

            val feedCacheStore = container.douyinFeedCacheStore
            val cachedSnapshot = feedCacheStore.readSnapshot(limit = DOUYIN_APP_OPEN_REFRESH_COUNT)
            val cachedItems = cachedSnapshot?.items.orEmpty()
            if (cachedItems.isEmpty()) {
                launchDouyinWarmup(cacheFirst = false, logReason = logReason)
                return@launch
            }

            val refreshResult = refreshExpiredDouyinBootstrapPlayUrls(
                items = cachedItems,
                repository = container.douyinRepository
            )
            if (refreshResult.refreshedAwemeIds.isNotEmpty()) {
                AppLogger.d(
                    "HomeFeedList",
                    "refresh cached douyin playUrls ids=${refreshResult.refreshedAwemeIds.joinToString(",")}"
                )
                feedCacheStore.save(
                    items = refreshResult.items,
                    nextCursor = cachedSnapshot?.nextCursor,
                    hasMore = cachedSnapshot?.hasMore ?: false
                )
            }
            primeDouyinPlaybackWindow(
                items = refreshResult.items,
                logReason = "${logReason}_cached"
            )
        }.also { job ->
            douyinCacheWarmupJob = job
            job.invokeOnCompletion {
                if (douyinCacheWarmupJob === job) {
                    douyinCacheWarmupJob = null
                }
            }
        }
    }

    private fun launchDouyinWarmup(
        cacheFirst: Boolean,
        logReason: String
    ): Job {
        return lifecycleScope.launch(Dispatchers.IO) {
            val container = (application as WatchRssApplication).container
            if (!container.douyinRepository.isLoggedIn()) return@launch

            val feedCacheStore = container.douyinFeedCacheStore
            val cachedSnapshot = if (cacheFirst) {
                feedCacheStore.readSnapshot(limit = DOUYIN_APP_OPEN_REFRESH_COUNT)
            } else {
                null
            }
            val cachedItems = cachedSnapshot?.items.orEmpty()
            val items = if (cachedItems.isNotEmpty()) {
                val refreshResult = refreshExpiredDouyinBootstrapPlayUrls(
                    items = cachedItems,
                    repository = container.douyinRepository
                )
                if (refreshResult.refreshedAwemeIds.isNotEmpty()) {
                    AppLogger.d(
                        "HomeFeedList",
                        "refresh cached douyin playUrls ids=${refreshResult.refreshedAwemeIds.joinToString(",")}"
                    )
                    feedCacheStore.save(
                        items = refreshResult.items,
                        nextCursor = cachedSnapshot?.nextCursor,
                        hasMore = cachedSnapshot?.hasMore ?: false
                    )
                }
                refreshResult.items
            } else {
                val result = container.douyinRepository.fetchFeedPage(
                    cursor = null,
                    count = DOUYIN_APP_OPEN_REFRESH_COUNT
                )
                result.data?.items.orEmpty()
                    .mapNotNull(::toDouyinStreamItem)
                    .also { fetchedItems ->
                        if (fetchedItems.isNotEmpty()) {
                            feedCacheStore.save(
                                items = fetchedItems,
                                nextCursor = result.data?.nextCursor,
                                hasMore = result.data?.hasMore ?: false
                            )
                        }
                    }
            }
            if (items.isEmpty()) return@launch
            primeDouyinPlaybackWindow(
                items = items,
                logReason = if (cachedItems.isNotEmpty()) "${logReason}_cached" else "${logReason}_network"
            )
        }.also { job ->
            douyinWarmupJob = job
            job.invokeOnCompletion {
                if (douyinWarmupJob === job) {
                    douyinWarmupJob = null
                }
            }
        }
    }

    private suspend fun primeDouyinPlaybackWindow(
        items: List<DouyinStreamItem>,
        logReason: String
    ) {
        if (items.isEmpty()) return
        val container = (application as WatchRssApplication).container
        val watchHistoryStore = container.douyinWatchHistoryStore
        val recentWindowSnapshot = container.douyinRecentWindowStore.readSnapshot(
            limit = DOUYIN_RECENT_WINDOW_SIZE
        )
        val mergedItems = mergeDouyinBootstrapItems(
            feedItems = items,
            recentItems = recentWindowSnapshot.items,
            limit = DOUYIN_APP_OPEN_REFRESH_COUNT + DOUYIN_RECENT_WINDOW_SIZE
        )
        val latestWatchedAwemeId = watchHistoryStore.readHistory()
            .firstOrNull()
            ?.awemeId
            ?.takeIf { it.isNotBlank() }
        val anchorAwemeId = when {
            !latestWatchedAwemeId.isNullOrBlank() && mergedItems.any { it.awemeId == latestWatchedAwemeId } -> {
                resolveDouyinResumeAnchorAwemeId(mergedItems, latestWatchedAwemeId)
            }
            !recentWindowSnapshot.anchorAwemeId.isNullOrBlank() -> {
                recentWindowSnapshot.anchorAwemeId
            }
            else -> {
                resolveDouyinResumeAnchorAwemeId(mergedItems, latestWatchedAwemeId)
            }
        }
        val forwardItems = dropDouyinItemsBeforeAwemeId(
            items = mergedItems,
            anchorAwemeId = anchorAwemeId
        )
        val prioritizedItems = prioritizeDouyinPreloadItems(
            items = forwardItems,
            anchorAwemeId = anchorAwemeId
        )
        val pinnedSnapshotItems = DouyinPlaybackPreviewCache.restorePinnedItems()
        val startupPrimeItems = buildList {
            val seenAwemeIds = linkedSetOf<String>()
            if (anchorAwemeId.isNullOrBlank()) {
                pinnedSnapshotItems.forEach { item ->
                    if (seenAwemeIds.add(item.awemeId)) {
                        add(item)
                    }
                }
            }
            prioritizedItems
                .take(1 + DOUYIN_PLAYBACK_PREFETCH_COUNT)
                .forEach { item ->
                    if (seenAwemeIds.add(item.awemeId)) {
                        add(item)
                    }
                }
        }
        val headers = container.douyinRepository.buildPlayHeaders()
        AppLogger.d(
            "HomeFeedList",
            "prime douyin window reason=$logReason ids=${
                startupPrimeItems.take(DOUYIN_ACTIVE_PRELOAD_WINDOW_UNWATCHED).joinToString(",") { it.awemeId }
            } pinned=${
                pinnedSnapshotItems.joinToString(",") { it.awemeId }
            }"
        )
        container.douyinPlaybackTransport.primeStartupWindow(
            items = startupPrimeItems,
            headers = headers,
            reason = logReason
        )
    }

    private fun restorePinnedDouyinPreviewsOnHome() {
        if (homePinnedPreviewRestoreScheduled) return
        homePinnedPreviewRestoreScheduled = true
        lifecycleScope.launch(Dispatchers.IO) {
            delay(HOME_PINNED_PREVIEW_RESTORE_DELAY_MS)
            val restoredItems = DouyinPlaybackPreviewCache.restorePinnedItems()
            if (restoredItems.isNotEmpty()) {
                AppLogger.d(
                    "HomeFeedList",
                    "home restore douyin pinned previews ids=${restoredItems.joinToString(",") { it.awemeId }}"
                )
            }
        }
    }

    private fun schedulePlatformLoginStateRefresh() {
        val decorView = window.decorView
        decorView.removeCallbacks(refreshPlatformLoginStateRunnable)
        decorView.post(refreshPlatformLoginStateRunnable)
    }

    private fun scheduleInitialHomeLoginStateRefresh() {
        if (initialHomeLoginRefreshScheduled) return
        initialHomeLoginRefreshScheduled = true
        val decorView = window.decorView
        decorView.removeCallbacks(refreshPlatformLoginStateRunnable)
        decorView.postDelayed(
            refreshPlatformLoginStateRunnable,
            INITIAL_HOME_LOGIN_STATE_REFRESH_DELAY_MS
        )
    }

    private fun scheduleStartupMaintenance() {
        if (startupMaintenanceScheduled) return
        startupMaintenanceScheduled = true
        lifecycleScope.launch(Dispatchers.IO) {
            delay(STARTUP_CACHE_MAINTENANCE_DELAY_MS)
            (application as WatchRssApplication).container.managedCacheService
                .scheduleMaintenance(CacheTrimReason.APP_START)
        }
    }

    private fun scheduleLauncherWarmup() {
        if (launcherWarmupScheduled) return
        launcherWarmupScheduled = true
        ensureDouyinWarmup(
            cacheFirst = false,
            logReason = "startup_prewarm"
        )
    }

    private fun showChannelActions(channel: RssChannel, quick: Boolean) {
        val intent = Intent(this, ChannelActionsActivity::class.java)
        intent.putExtra(ChannelActionsActivity.EXTRA_CHANNEL_ID, channel.id)
        intent.putExtra(ChannelActionsActivity.EXTRA_QUICK, quick)
        startNavigatingActivity(intent = intent)
    }

    private fun openChannel(channel: RssChannel) {
        when (BuiltinChannelType.fromUrl(channel.url)) {
            BuiltinChannelType.BILI -> startNavigatingActivity(
                intent = Intent(this, BiliEntryActivity::class.java)
            )
            BuiltinChannelType.DOUYIN -> {
                ensureDouyinWarmup(
                    cacheFirst = true,
                    logReason = "channel_open"
                )
                startNavigatingActivity(
                    intent = Intent(this, DouyinEntryActivity::class.java)
                )
            }
            null -> {
                val intent = Intent(this, FeedActivity::class.java)
                intent.putExtra(FeedActivity.EXTRA_CHANNEL_ID, channel.id)
                startNavigatingActivity(intent = intent)
            }
        }
    }

    private fun startNavigatingActivity(intent: Intent) {
        if (!usesSystemBackGesture()) {
            closeOpenSwipe()
        }
        startActivity(intent)
    }

    private fun toDouyinStreamItem(video: DouyinVideo): DouyinStreamItem? {
        val awemeId = video.awemeId?.trim().orEmpty()
        val playUrl = video.playUrl?.trim().orEmpty()
        if (awemeId.isEmpty() || playUrl.isEmpty()) return null
        return DouyinStreamItem(
            awemeId = awemeId,
            playUrl = playUrl,
            coverUrl = video.coverUrl?.takeIf { it.isNotBlank() },
            title = video.desc?.takeIf { it.isNotBlank() },
            author = video.authorName?.takeIf { it.isNotBlank() },
            likeCount = video.likeCount,
            playUrlResolvedAtMs = System.currentTimeMillis(),
            sourceOrigin = DouyinSourceOrigin.NETWORK_FEED,
            durationMs = video.duration.toLong().coerceAtLeast(0L),
            variants = video.variants
        )
    }

    companion object {
        private const val EXTRA_LAUNCHER_ENTRY = "extra_launcher_entry"
        private const val INITIAL_HOME_LOGIN_STATE_REFRESH_DELAY_MS = 8_000L
        private const val HOME_PINNED_PREVIEW_RESTORE_DELAY_MS = 10_000L
        private const val STARTUP_CACHE_MAINTENANCE_DELAY_MS = 5_000L
        private const val DOUYIN_APP_OPEN_REFRESH_COUNT = 16
        const val EXTRA_DEBUG_AUTOSCROLL_PERF = "debug_home_autoscroll_perf"

        fun createIntent(context: Context, launcherEntry: Boolean = false): Intent {
            return Intent(context, HomeFeedListActivity::class.java).apply {
                putExtra(EXTRA_LAUNCHER_ENTRY, launcherEntry)
            }
        }
    }
}
