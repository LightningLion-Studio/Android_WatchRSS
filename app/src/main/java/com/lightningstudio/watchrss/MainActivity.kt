package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.debug.PerformanceMonitor
import com.lightningstudio.watchrss.ui.screen.common.ReadAloudBubbleDock
import com.lightningstudio.watchrss.ui.screen.common.ReadAloudFloatingBubbleOverlay
import com.lightningstudio.watchrss.ui.screen.home.HomeComposeScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.HomeViewModel
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStore
import com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseWatchActivity() {
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
    private var keepSplashOnScreen = true
    private var initialStartupCompleted = false
    private var startupMaintenanceScheduled = false

    override fun onSwipeBackAttempt(dx: Float, dy: Float): Boolean {
        val hasOpen = openSwipeKey != null
        if (hasOpen) {
            openSwipeKey = null
        }
        return hasOpen
    }

    override fun isSwipeBackEnabled(): Boolean = false

    override fun shouldAnimateSwipeBackGesture(): Boolean = false

    override fun shouldResetViewStateImmediatelyOnTouchEnd(): Boolean = false

    override fun onResume() {
        super.onResume()
        closeOpenSwipe()
        if (initialStartupCompleted) {
            window.decorView.post {
                viewModel.refreshPlatformLoginState()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isLauncherEntry(intent)) {
            AppLaunchSignal.markLauncherOpen()
            scheduleLauncherEntryTasks(intent)
            return
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)
        PerformanceMonitor.setScenario(this, "home_cold_start")
        onBackPressedDispatcher.addCallback(this, closeOpenSwipeBackCallback)
        setupSystemBars()
        renderHomeContent()

        lifecycleScope.launch {
            val shouldShowOobe = withContext(Dispatchers.IO) {
                val settingsRepository = (application as WatchRssApplication).container.settingsRepository
                settingsRepository.oobeSeenVersion.first() < CURRENT_OOBE_VERSION
            }
            if (shouldShowOobe) {
                keepSplashOnScreen = false
                startActivity(OobeActivity.createIntent(this@MainActivity))
                finish()
                return@launch
            }
            initialStartupCompleted = true
            schedulePlatformLoginStateRefresh()
            scheduleStartupMaintenance()
            if (isLauncherEntry(intent)) {
                AppLaunchSignal.markLauncherOpen()
                scheduleLauncherEntryTasks(intent)
            }
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
                val readAloudState by (application as WatchRssApplication)
                    .container
                    .readAloudController
                    .uiState
                    .collectAsState()

                LaunchedEffect(hasLoadedChannels) {
                    if (hasLoadedChannels) {
                        keepSplashOnScreen = false
                    }
                }

                LaunchedEffect(message) {
                    if (message != null) {
                        com.lightningstudio.watchrss.ui.util.showAppToast(context, message, android.widget.Toast.LENGTH_SHORT)
                        viewModel.clearMessage()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    HomeComposeScreen(
                        channels = channels,
                        hasLoadedChannels = hasLoadedChannels,
                        platformLoginState = platformLoginState,
                        isRefreshing = isRefreshing,
                        onRefreshAll = viewModel::refreshAll,
                        openSwipeId = openSwipeKey,
                        onOpenSwipe = { openSwipeKey = it },
                        onCloseSwipe = { openSwipeKey = null },
                        draggingSwipeId = draggingSwipeKey,
                        onDragStart = { draggingSwipeKey = it },
                        onDragEnd = { draggingSwipeKey = null },
                        onProfileClick = {
                            if (!allowNavigation()) return@HomeComposeScreen
                            startActivity(Intent(this@MainActivity, ProfileActivity::class.java))
                        },
                        onRecommendClick = {
                            if (closeOpenSwipe()) return@HomeComposeScreen
                            if (!allowNavigation()) return@HomeComposeScreen
                            startActivity(Intent(this@MainActivity, RssRecommendActivity::class.java))
                        },
                        onChannelClick = { channel ->
                            if (closeOpenSwipe()) return@HomeComposeScreen
                            if (!allowNavigation()) return@HomeComposeScreen
                            openChannel(channel)
                        },
                        onChannelLongClick = { channel ->
                            showChannelActions(channel, quick = false)
                        },
                        onSwipeBack = {
                            onBackPressedDispatcher.onBackPressed()
                        },
                        onAddRssClick = {
                            if (!allowNavigation()) return@HomeComposeScreen
                            startActivity(Intent(this@MainActivity, AddRssActivity::class.java))
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
                            if (!allowNavigation()) return@HomeComposeScreen
                            startActivity(BeianActivity.createIntent(this@MainActivity))
                        }
                    )
                    ReadAloudFloatingBubbleOverlay(
                        state = readAloudState,
                        defaultDock = ReadAloudBubbleDock.RIGHT,
                        onClick = {
                            if (!allowNavigation()) return@ReadAloudFloatingBubbleOverlay
                            startActivity(ReadAloudPlaybackActivity.createIntent(this@MainActivity))
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

    private suspend fun maybeResumeLastContent(sourceIntent: Intent?) {
        if (!isLauncherEntry(sourceIntent)) return
        val resumeIntent = withContext(Dispatchers.IO) {
            AppResumeStateStore.load(this@MainActivity)
        } ?: return
        val component = resumeIntent.component ?: return
        if (component.className == MainActivity::class.java.name) return
        startActivity(
            resumeIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    private fun isLauncherEntry(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_MAIN) return false
        return intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
    }

    private suspend fun prewarmDouyinFeed() = withContext(Dispatchers.IO) {
        val container = (application as WatchRssApplication).container
        if (!container.douyinRepository.isLoggedIn()) return@withContext
        val result = container.douyinRepository.fetchFeedPage(
            cursor = null,
            count = DOUYIN_APP_OPEN_REFRESH_COUNT
        )
        val items = result.data?.items.orEmpty()
            .mapNotNull(::toDouyinStreamItem)
        if (items.isNotEmpty()) {
            DouyinFeedCacheStore(this@MainActivity).save(items)
        }
    }

    private fun schedulePlatformLoginStateRefresh() {
        window.decorView.post {
            viewModel.refreshPlatformLoginState()
        }
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

    private fun scheduleLauncherEntryTasks(sourceIntent: Intent?) {
        if (!initialStartupCompleted) return
        lifecycleScope.launch {
            maybeResumeLastContent(sourceIntent)
        }
        lifecycleScope.launch {
            delay(STARTUP_DOUYIN_PREWARM_DELAY_MS)
            prewarmDouyinFeed()
        }
    }

    private fun showChannelActions(channel: RssChannel, quick: Boolean) {
        val intent = Intent(this, ChannelActionsActivity::class.java)
        intent.putExtra(ChannelActionsActivity.EXTRA_CHANNEL_ID, channel.id)
        intent.putExtra(ChannelActionsActivity.EXTRA_QUICK, quick)
        startActivity(intent)
    }

    private fun openChannel(channel: RssChannel) {
        when (BuiltinChannelType.fromUrl(channel.url)) {
            BuiltinChannelType.BILI -> startActivity(Intent(this, BiliEntryActivity::class.java))
            BuiltinChannelType.DOUYIN -> startActivity(Intent(this, DouyinEntryActivity::class.java))
            null -> {
                val intent = Intent(this, FeedActivity::class.java)
                intent.putExtra(FeedActivity.EXTRA_CHANNEL_ID, channel.id)
                startActivity(intent)
            }
        }
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
            sourceOrigin = DouyinSourceOrigin.NETWORK_FEED
        )
    }

    companion object {
        private const val STARTUP_DOUYIN_PREWARM_DELAY_MS = 2_000L
        private const val STARTUP_CACHE_MAINTENANCE_DELAY_MS = 5_000L
        private const val DOUYIN_APP_OPEN_REFRESH_COUNT = 16
    }
}
