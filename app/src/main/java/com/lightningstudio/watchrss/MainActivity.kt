package com.lightningstudio.watchrss

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStore
import com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import com.lightningstudio.watchrss.ui.util.formatTime
import com.lightningstudio.watchrss.ui.util.showAppToast
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.HomeViewModel
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseWatchActivity() {
    private val viewModel: HomeViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    private var initialStartupCompleted = false
    private var startupMaintenanceScheduled = false
    private var homeCards by mutableStateOf<List<HomeCardPresentation>?>(null)
    private var homeScrollY = 0
    private var homeScrollView: ScrollView? = null
    private var homeContentLayout: LinearLayout? = null
    private var pendingNavigationChannelId by mutableStateOf<Long?>(null)
    private var profileButtonNavigationInProgress by mutableStateOf(false)
    private var addButtonNavigationInProgress by mutableStateOf(false)
    private var refreshHomeCardsJob: Job? = null
    private var pendingHomeCoveredLabel: String? = null
    private var pendingHomeCoveredAction: (() -> Unit)? = null
    private var pendingHomeAppearedLabel: String? = null
    private var pendingHomeAppearedAction: (() -> Unit)? = null

    override fun shouldUseDebugWatchMask(): Boolean = false

    override fun shouldAttachPerformanceMonitor(): Boolean = false

    override fun shouldLogActivityLifecycle(): Boolean = false

    override fun shouldResetRootViewStateOnResume(): Boolean = true

    override fun shouldScheduleRootViewResetAfterTouchEnd(): Boolean = false

    override fun isSwipeBackEnabled(): Boolean = false

    override fun shouldResetViewStateImmediatelyOnTouchEnd(): Boolean = false

    override fun onResume() {
        super.onResume()
        restoreHomeInteractionState(recreateScrollView = false)
        AppLogger.d(DEBUG_TAG, "MainActivity.onResume")
        val appearedLabel = pendingHomeAppearedLabel
        val appearedAction = pendingHomeAppearedAction
        pendingHomeAppearedLabel = null
        pendingHomeAppearedAction = null
        if (appearedLabel != null) {
            AppLogger.d(DEBUG_TAG, "home appeared after child label=$appearedLabel")
            appearedAction?.invoke()
        }
        refreshHomeCards()
    }

    override fun onPause() {
        resetHomeInteractionState()
        AppLogger.d(DEBUG_TAG, "MainActivity.onPause")
        super.onPause()
    }

    override fun onStop() {
        AppLogger.d(DEBUG_TAG, "MainActivity.onStop")
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        AppLogger.d(DEBUG_TAG, "MainActivity.onRestart")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            restoreHomeInteractionState(recreateScrollView = true)
            return
        }
        val coveredLabel = pendingHomeCoveredLabel ?: return
        val coveredAction = pendingHomeCoveredAction
        pendingHomeCoveredLabel = null
        pendingHomeCoveredAction = null
        AppLogger.d(DEBUG_TAG, "home covered by child label=$coveredLabel")
        if (coveredAction != null) {
            coveredAction.invoke()
        } else {
            clearHomeLoadingIndicators()
        }
    }

    override fun onDestroy() {
        AppLogger.d(DEBUG_TAG, "MainActivity.onDestroy")
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isLauncherEntry(intent)) {
            AppLaunchSignal.markLauncherOpen()
            scheduleLauncherEntryTasks(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        setupHomeContent()
        observeMessages()

        lifecycleScope.launch {
            val shouldShowOobe = withContext(Dispatchers.IO) {
                val settingsRepository = (application as WatchRssApplication).container.settingsRepository
                settingsRepository.oobeSeenVersion.first() < CURRENT_OOBE_VERSION
            }
            if (shouldShowOobe) {
                startActivity(OobeActivity.createIntent(this@MainActivity))
                finish()
                return@launch
            }
            initialStartupCompleted = true
            scheduleStartupMaintenance()
            if (isLauncherEntry(intent)) {
                AppLaunchSignal.markLauncherOpen()
                scheduleLauncherEntryTasks(intent)
            }
        }
    }

    private fun setupHomeContent() {
        setContent {
            HomeScreen()
        }
    }

    @Composable
    private fun HomeScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val cards = homeCards
            if (cards == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = Color.White
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        FrameLayout(context)
                    },
                    update = { container ->
                        renderHomeCards(container = container, cards = cards)
                    }
                )
            }
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.message.collect { message ->
                    if (message == null) return@collect
                    showAppToast(this@MainActivity, message, Toast.LENGTH_SHORT)
                    viewModel.clearMessage()
                }
            }
        }
    }

    private fun refreshHomeCards() {
        refreshHomeCardsJob?.cancel()
        refreshHomeCardsJob = lifecycleScope.launch {
            val shouldShowInitialLoading = homeCards == null
            if (shouldShowInitialLoading) {
                homeCards = null
            }
            val channels = viewModel.channels.filterNotNull().first()
            val cards = withContext(Dispatchers.IO) {
                val container = (application as WatchRssApplication).container
                val biliLoggedIn = container.biliRepository.isLoggedIn()
                val douyinLoggedIn = container.douyinRepository.isLoggedIn()
                buildHomeCards(channels, biliLoggedIn, douyinLoggedIn)
            }
            homeCards = cards
        }
    }

    private fun clearHomeLoadingIndicators() {
        pendingNavigationChannelId = null
        profileButtonNavigationInProgress = false
        addButtonNavigationInProgress = false
    }

    private fun resetHomeInteractionState() {
        val root = window.decorView
        dispatchHomeCancelEvent()
        root.cancelPendingInputEvents()
        clearHomeViewState(root)
        homeScrollView?.apply {
            cancelPendingInputEvents()
            requestDisallowInterceptTouchEvent(false)
            clearFocus()
        }
    }

    private fun restoreHomeInteractionState(recreateScrollView: Boolean) {
        resetHomeInteractionState()
        if (recreateScrollView) {
            recreateHomeScrollView()
        }
    }

    private fun dispatchHomeCancelEvent() {
        val target = homeScrollView ?: window.decorView
        val now = SystemClock.uptimeMillis()
        val cancelEvent = MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_CANCEL,
            0f,
            0f,
            0
        )
        target.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun clearHomeViewState(view: View) {
        view.cancelPendingInputEvents()
        view.isPressed = false
        view.isSelected = false
        view.isActivated = false
        view.jumpDrawablesToCurrentState()
        view.clearFocus()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                view.getChildAt(index)?.let(::clearHomeViewState)
            }
        }
    }

    private fun recreateHomeScrollView() {
        val cards = homeCards ?: return
        val container = homeScrollView?.parent as? FrameLayout ?: return
        homeScrollY = homeScrollView?.scrollY ?: homeScrollY
        homeScrollView = null
        homeContentLayout = null
        container.removeAllViews()
        renderHomeCards(container = container, cards = cards)
    }

    private fun launchHomeCoveringActivity(
        intent: Intent,
        label: String,
        onCovered: (() -> Unit)? = null,
        onAppeared: (() -> Unit)? = null
    ) {
        pendingHomeCoveredLabel = label
        pendingHomeCoveredAction = onCovered
        pendingHomeAppearedLabel = label
        pendingHomeAppearedAction = onAppeared
        AppLogger.d(DEBUG_TAG, "launch home child label=$label intent=${intent.component}")
        startActivity(intent)
    }

    private fun buildHomeCards(
        channels: List<RssChannel>,
        biliLoggedIn: Boolean,
        douyinLoggedIn: Boolean
    ): List<HomeCardPresentation> {
        AppLogger.d(
            DEBUG_TAG,
            "buildHomeCards channels=${channels.size} biliLoggedIn=$biliLoggedIn douyinLoggedIn=$douyinLoggedIn"
        )
        if (channels.isEmpty()) {
            AppLogger.d(DEBUG_TAG, "buildHomeCards empty channels, use empty-state card")
            return listOf(
                HomeCardPresentation(
                    channel = null,
                    title = "还没有 RSS 频道",
                    subtitle = "当前没有可显示的订阅源",
                    supporting = ""
                )
            )
        }

        return channels.map { channel ->
            HomeCardPresentation(
                channel = channel,
                title = channel.title.ifBlank { channel.url },
                subtitle = resolveChannelSubtitle(
                    channel = channel,
                    biliLoggedIn = biliLoggedIn,
                    douyinLoggedIn = douyinLoggedIn
                ),
                supporting = resolveChannelSupportingText(
                    channel = channel,
                    biliLoggedIn = biliLoggedIn,
                    douyinLoggedIn = douyinLoggedIn
                )
            )
        }
    }

    private fun resolveChannelSubtitle(
        channel: RssChannel,
        biliLoggedIn: Boolean,
        douyinLoggedIn: Boolean
    ): String {
        val builtinType = BuiltinChannelType.fromUrl(channel.url)
        val subtitle = if (
            (builtinType == BuiltinChannelType.BILI && biliLoggedIn) ||
            (builtinType == BuiltinChannelType.DOUYIN && douyinLoggedIn)
        ) {
            "进入以获取推荐内容"
        } else {
            channel.description?.takeIf { it.isNotBlank() } ?: channel.url
        }
        AppLogger.d(
            DEBUG_TAG,
            "resolveChannelSubtitle channelId=${channel.id} builtinType=$builtinType title=${channel.title.ifBlank { channel.url }} subtitle=$subtitle"
        )
        return subtitle
    }

    private fun resolveChannelSupportingText(
        channel: RssChannel,
        biliLoggedIn: Boolean,
        douyinLoggedIn: Boolean
    ): String {
        val builtinType = BuiltinChannelType.fromUrl(channel.url)
        val unreadText = if (builtinType == null && channel.unreadCount > 0) {
            "未读 ${channel.unreadCount} · "
        } else {
            ""
        }
        val updateText = if (
            (builtinType == BuiltinChannelType.BILI && biliLoggedIn) ||
            (builtinType == BuiltinChannelType.DOUYIN && douyinLoggedIn)
        ) {
            "更新: 实时"
        } else {
            "更新: ${formatTime(channel.lastFetchedAt)}"
        }
        return unreadText + updateText
    }

    private fun renderHomeCards(
        container: FrameLayout,
        cards: List<HomeCardPresentation>
    ) {
        val scrollView = ensureHomeScrollView(container)
        val previousScrollY = scrollView.scrollY
        homeScrollY = previousScrollY
        val contentLayout = requireNotNull(homeContentLayout)
        contentLayout.removeAllViews()

        contentLayout.addView(
            createProfileButton(
                isLoading = profileButtonNavigationInProgress,
                onClick = {
                    if (allowNavigation()) {
                        pendingNavigationChannelId = null
                        profileButtonNavigationInProgress = true
                        addButtonNavigationInProgress = false
                        launchHomeCoveringActivity(
                            intent = Intent(this@MainActivity, ProfileActivity::class.java),
                            label = "profile"
                        )
                    }
                }
            )
        )

        cards.forEach { card ->
            val clickHandler = card.channel?.let { channel ->
                {
                    if (allowNavigation()) {
                        pendingNavigationChannelId = channel.id
                        profileButtonNavigationInProgress = false
                        addButtonNavigationInProgress = false
                        launchHomeCoveringActivity(
                            intent = createChannelIntent(channel),
                            label = "channel-${channel.id}"
                        )
                    }
                }
            }
            contentLayout.addView(
                createChannelCard(
                    title = card.title,
                    subtitle = card.subtitle,
                    supporting = card.supporting,
                    isLoading = card.channel?.id == pendingNavigationChannelId,
                    onClick = clickHandler
                )
            )
        }

        contentLayout.addView(
            createAddChannelButton(
                isLoading = addButtonNavigationInProgress,
                onClick = {
                    if (allowNavigation()) {
                        pendingNavigationChannelId = null
                        profileButtonNavigationInProgress = false
                        addButtonNavigationInProgress = true
                        launchHomeCoveringActivity(
                            intent = Intent(this@MainActivity, AddRssActivity::class.java),
                            label = "add-rss"
                        )
                    }
                }
            )
        )

        scrollView.post {
            scrollView.scrollTo(0, homeScrollY)
        }
    }

    private fun ensureHomeScrollView(container: FrameLayout): ScrollView {
        container.setBackgroundColor(HOME_BACKGROUND_COLOR)
        val existingScrollView = homeScrollView?.takeIf { it.parent === container }
        if (existingScrollView != null && homeContentLayout?.parent === existingScrollView) {
            return existingScrollView
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(HOME_BACKGROUND_COLOR)
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                homeScrollY = scrollY
            }
        }
        val contentLayout = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scrollView.addView(contentLayout)
        container.removeAllViews()
        container.addView(scrollView)
        homeScrollView = scrollView
        homeContentLayout = contentLayout
        return scrollView
    }

    private fun createChannelCard(
        title: String,
        subtitle: String,
        supporting: String,
        isLoading: Boolean,
        onClick: (() -> Unit)? = null
    ): View {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(72)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(CHANNEL_CARD_COLOR)
            }

            addView(
                FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                    addView(
                        TextView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            )
                            text = buildString {
                                append(title)
                                append('\n')
                                append(subtitle)
                                if (supporting.isNotBlank()) {
                                    append('\n')
                                    append(supporting)
                                }
                            }
                            setTextColor(CHANNEL_TEXT_COLOR)
                            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                            includeFontPadding = false
                            maxLines = 3
                            ellipsize = TextUtils.TruncateAt.END
                            visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
                        }
                    )

                    if (isLoading) {
                        // 手表性能较弱，拉起 Activity 往往会有延迟，这里在原位显示 loading 反馈。
                        addView(
                            ProgressBar(context).apply {
                                isIndeterminate = true
                                indeterminateTintList = ColorStateList.valueOf(CHANNEL_TEXT_COLOR)
                                layoutParams = FrameLayout.LayoutParams(
                                    dp(24),
                                    dp(24),
                                    Gravity.CENTER
                                )
                            }
                        )
                    }
                }
            )

            if (onClick != null && !isLoading) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }
    }

    private fun createAddChannelButton(
        isLoading: Boolean,
        onClick: (() -> Unit)? = null
    ): View {
        return createCircularActionButton(
            isLoading = isLoading,
            contentDescription = "添加 RSS",
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            },
            onClick = onClick
        )
    }

    private fun createProfileButton(
        isLoading: Boolean,
        onClick: (() -> Unit)? = null
    ): View {
        return createCircularActionButton(
            isLoading = isLoading,
            contentDescription = "我的",
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            },
            bottomMarginDp = 8,
            onClick = onClick
        )
    }

    private fun createCircularActionButton(
        isLoading: Boolean,
        contentDescription: String,
        icon: @Composable () -> Unit,
        bottomMarginDp: Int = 0,
        onClick: (() -> Unit)? = null
    ): View {
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(bottomMarginDp)
            }
            this.contentDescription = contentDescription

            addView(
                FrameLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        dp(52),
                        dp(52),
                        Gravity.CENTER
                    )
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(CHANNEL_ADD_BUTTON_COLOR)
                    }

                    addView(
                        ComposeView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER
                            )
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
                            setContent {
                                icon()
                            }
                        }
                    )

                    if (isLoading) {
                        // 手表性能较弱，拉起 Activity 往往会有延迟，这里在原位显示 loading 反馈。
                        addView(
                            ProgressBar(context).apply {
                                isIndeterminate = true
                                indeterminateTintList = ColorStateList.valueOf(CHANNEL_TEXT_COLOR)
                                layoutParams = FrameLayout.LayoutParams(
                                    dp(24),
                                    dp(24),
                                    Gravity.CENTER
                                )
                            }
                        )
                    }
                }
            )

            if (onClick != null && !isLoading) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
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

    private fun scheduleStartupMaintenance() {
        if (startupMaintenanceScheduled) return
        startupMaintenanceScheduled = true
        lifecycleScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(STARTUP_CACHE_MAINTENANCE_DELAY_MS)
            (application as WatchRssApplication).container.managedCacheService
                .scheduleMaintenance(CacheTrimReason.APP_START)
        }
    }

    private fun scheduleLauncherEntryTasks(sourceIntent: Intent?) {
        if (!initialStartupCompleted) return
        lifecycleScope.launch {
            kotlinx.coroutines.delay(STARTUP_RESUME_LAST_CONTENT_DELAY_MS)
            maybeResumeLastContent(sourceIntent)
        }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(STARTUP_DOUYIN_PREWARM_DELAY_MS)
            prewarmDouyinFeed()
        }
    }

    private fun createChannelIntent(channel: RssChannel): Intent {
        return when (BuiltinChannelType.fromUrl(channel.url)) {
            BuiltinChannelType.BILI -> Intent(this, BiliEntryActivity::class.java)
            BuiltinChannelType.DOUYIN -> Intent(this, DouyinEntryActivity::class.java)
            null -> Intent(this, FeedActivity::class.java).apply {
                putExtra(FeedActivity.EXTRA_CHANNEL_ID, channel.id)
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

    private data class HomeCardPresentation(
        val channel: RssChannel?,
        val title: String,
        val subtitle: String,
        val supporting: String
    )

    companion object {
        private const val DEBUG_TAG = "MainActivityDebug"
        private const val STARTUP_RESUME_LAST_CONTENT_DELAY_MS = 8_000L
        private const val STARTUP_DOUYIN_PREWARM_DELAY_MS = 12_000L
        private const val STARTUP_CACHE_MAINTENANCE_DELAY_MS = 15_000L
        private const val DOUYIN_APP_OPEN_REFRESH_COUNT = 16
        private const val HOME_BACKGROUND_COLOR = 0xFF000000.toInt()
        private const val CHANNEL_CARD_COLOR = 0xFF1A1A1A.toInt()
        private const val CHANNEL_ADD_BUTTON_COLOR = 0xFF2A2A2A.toInt()
        private const val CHANNEL_TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val CHANNEL_SUBTITLE_COLOR = 0xFFB5B5B5.toInt()
    }
}
