package com.lightningstudio.watchrss.ui.screen.home

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.view.Choreographer
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.rememberPullRefreshEnabled
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.tts.ReadAloudUiState
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.screen.rss.buildPlaybackStatusText
import com.lightningstudio.watchrss.ui.testing.HomeTestTags
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.formatTime
import com.lightningstudio.watchrss.ui.viewmodel.HomePlatformLoginState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@Composable
fun HomeComposeScreen(
    channels: List<RssChannel>,
    hasLoadedChannels: Boolean = true,
    platformLoginState: HomePlatformLoginState = HomePlatformLoginState(),
    readAloudState: ReadAloudUiState = ReadAloudUiState(),
    readAloudAudioSpectrum: SharedFlow<FloatArray>? = null,
    enableChannelSwipeActions: Boolean = false,
    isRefreshing: Boolean,
    debugAutoScrollPerf: Boolean = false,
    onMinimalContentReady: () -> Unit = {},
    onRefreshAll: () -> Unit,
    openSwipeId: Long?,
    onOpenSwipe: (Long) -> Unit,
    onCloseSwipe: () -> Unit,
    draggingSwipeId: Long?,
    onDragStart: (Long) -> Unit,
    onDragEnd: () -> Unit,
    onProfileClick: () -> Unit,
    onRecommendClick: () -> Unit,
    onReadAloudClick: () -> Unit = {},
    onChannelClick: (RssChannel) -> Unit,
    onChannelLongClick: (RssChannel) -> Unit,
    onSwipeBack: () -> Unit,
    onAddRssClick: () -> Unit,
    onMoveTopClick: (RssChannel) -> Unit,
    onMarkReadClick: (RssChannel) -> Unit,
    onBeianClick: () -> Unit
) {
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
        val showReadAloudEntry = readAloudState.visible && readAloudState.currentItemId != null
        val entries = remember(channels, hasLoadedChannels, showReadAloudEntry) {
            buildHomeEntries(channels, hasLoadedChannels, showReadAloudEntry)
        }
        val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
        val itemSpacing = watchDimensionResource(R.dimen.hey_distance_6dp)
        val listState = rememberLazyListState()
        InstallDigitalCrownLazyListHandler(listState)
        val canRefresh = rememberPullRefreshEnabled(listState)
        val view = LocalView.current
        val firstLayoutReported = remember { AtomicBoolean(false) }
        val onMinimalContentReadyState = rememberUpdatedState(onMinimalContentReady)
        LaunchedEffect(debugAutoScrollPerf, entries.size) {
            if (!debugAutoScrollPerf || entries.size < DEBUG_AUTOSCROLL_MIN_ENTRIES) return@LaunchedEffect
            listState.scrollToItem(0)
            delay(DEBUG_AUTOSCROLL_START_DELAY_MS)
            repeat(DEBUG_AUTOSCROLL_SEGMENT_COUNT) {
                listState.animateScrollBy(
                    value = DEBUG_AUTOSCROLL_DISTANCE_PX,
                    animationSpec = tween(
                        durationMillis = DEBUG_AUTOSCROLL_DURATION_MS,
                        easing = LinearEasing
                    )
                )
                delay(DEBUG_AUTOSCROLL_TURNAROUND_DELAY_MS)
            }
            repeat(DEBUG_AUTOSCROLL_SEGMENT_COUNT) {
                listState.animateScrollBy(
                    value = -DEBUG_AUTOSCROLL_DISTANCE_PX,
                    animationSpec = tween(
                        durationMillis = DEBUG_AUTOSCROLL_DURATION_MS,
                        easing = LinearEasing
                    )
                )
                delay(DEBUG_AUTOSCROLL_TURNAROUND_DELAY_MS)
            }
        }
        PullRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefreshAll,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(HomeTestTags.ROOT),
            indicatorPadding = safePadding,
            canRefresh = canRefresh
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = safePadding)
                        .onGloballyPositioned {
                            if (firstLayoutReported.compareAndSet(false, true)) {
                                view.post {
                                    onMinimalContentReadyState.value()
                                }
                            }
                        }
                        .testTag(HomeTestTags.CHANNEL_LIST),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = safePadding,
                        bottom = safePadding + itemSpacing
                    ),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    items(
                        entries,
                        key = { it.key },
                        contentType = { it::class }
                    ) { entry ->
                        when (entry) {
                            HomeEntry.Profile -> {
                                HomeProfileEntry(
                                    onProfileClick = onProfileClick,
                                    interactionsEnabled = true
                                )
                            }
                            HomeEntry.Empty -> {
                                HomeDefaultItem(
                                    title = "还没有 RSS 频道",
                                    summary = "点击下方添加你的第一个订阅源",
                                    backgroundColor = MaterialTheme.colorScheme.surface,
                                    showIndicator = false,
                                    testTag = HomeTestTags.EMPTY_ENTRY
                                )
                            }
                            HomeEntry.Recommend -> {
                                HomeDefaultItem(
                                    title = "RSS推荐",
                                    summary = "一键加入官方支持频道",
                                    backgroundColor = MaterialTheme.colorScheme.surface,
                                    showIndicator = false,
                                    testTag = HomeTestTags.RECOMMEND_ENTRY,
                                    onClick = onRecommendClick
                                )
                            }
                            HomeEntry.ReadAloud -> {
                                HomeReadAloudEntry(
                                    state = readAloudState,
                                    audioSpectrum = readAloudAudioSpectrum,
                                    onClick = onReadAloudClick
                                )
                            }
                            HomeEntry.AddRss -> {
                                HomeAddEntry(
                                    onAddRssClick = onAddRssClick,
                                    interactionsEnabled = true
                                )
                            }
                            HomeEntry.Beian -> {
                                HomeBeianEntry(
                                    onBeianClick = onBeianClick,
                                    interactionsEnabled = true
                                )
                            }
                            is HomeEntry.Channel -> {
                                HomeChannelEntry(
                                    channel = entry.channel,
                                    platformLoginState = platformLoginState,
                                    openSwipeId = openSwipeId,
                                    onOpenSwipe = onOpenSwipe,
                                    onCloseSwipe = onCloseSwipe,
                                    draggingSwipeId = draggingSwipeId,
                                    onDragStart = onDragStart,
                                    onDragEnd = onDragEnd,
                                    onChannelClick = { onChannelClick(entry.channel) },
                                    onChannelLongClick = { onChannelLongClick(entry.channel) },
                                    onSwipeBack = onSwipeBack,
                                    swipeInteractionsEnabled = enableChannelSwipeActions,
                                    interactionsEnabled = true,
                                    onMoveTopClick = { onMoveTopClick(entry.channel) },
                                    onMarkReadClick = { onMarkReadClick(entry.channel) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class HomeEntry(val key: String) {
    data object Profile : HomeEntry("profile")
    data object ReadAloud : HomeEntry("read_aloud")
    data class Channel(val channel: RssChannel) : HomeEntry("channel_${channel.id}")
    data object Empty : HomeEntry("empty")
    data object Recommend : HomeEntry("recommend")
    data object AddRss : HomeEntry("add_rss")
    data object Beian : HomeEntry("beian")
}

private fun buildHomeEntries(
    channels: List<RssChannel>,
    hasLoadedChannels: Boolean,
    showReadAloudEntry: Boolean
): List<HomeEntry> {
    val entries = mutableListOf<HomeEntry>()
    entries.add(HomeEntry.Profile)
    if (showReadAloudEntry) {
        entries.add(HomeEntry.ReadAloud)
    }
    if (!hasLoadedChannels) {
        // Wait for the first Room emission to avoid flashing an empty-state card on cold start.
    } else if (channels.isEmpty()) {
        entries.add(HomeEntry.Empty)
    } else {
        entries.addAll(channels.map { HomeEntry.Channel(it) })
    }
    entries.add(HomeEntry.Recommend)
    entries.add(HomeEntry.AddRss)
    entries.add(HomeEntry.Beian)
    return entries
}

@Composable
private fun HomeProfileEntry(
    onProfileClick: () -> Unit,
    interactionsEnabled: Boolean
) {
    val avatarSize = watchDimensionResource(R.dimen.hey_listitem_big_lefticon_height_width)
    val padding = watchDimensionResource(R.dimen.hey_distance_4dp)
    val hintSize = textSize(R.dimen.hey_caption)
    val avatarTextSize = textSize(R.dimen.hey_m_desription)
    val strokeWidth = watchDimensionResource(R.dimen.hey_dotStrokeWidth)
    val accentColor = MaterialTheme.colorScheme.primary
    val cardColor = MaterialTheme.colorScheme.surface
    val shape = CircleShape

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .graphicsLayer {
                    this.shape = shape
                    clip = true
                }
                .border(strokeWidth, accentColor, shape)
                .background(cardColor, shape)
                .clickableWithRipple(
                    enabled = interactionsEnabled,
                    onClick = onProfileClick
                )
                .testTag(HomeTestTags.PROFILE_ENTRY)
                .semantics { contentDescription = "个人中心" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "我",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = avatarTextSize
            )
        }
        Spacer(modifier = Modifier.height(padding))
        Text(
            text = "点击进入我的",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = hintSize
        )
    }
}

@Composable
private fun HomeReadAloudEntry(
    state: ReadAloudUiState,
    audioSpectrum: SharedFlow<FloatArray>?,
    onClick: () -> Unit
) {
    val status = buildPlaybackStatusText(state)
    val title = if (state.isPlaying) "正在朗读" else "朗读已暂停"
    val itemTitle = state.currentTitle.ifBlank { "本地朗读" }
    val source = state.currentChannelTitle.takeIf { it.isNotBlank() }
    val borderColor = if (state.isPlaying) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    }
    val summary = if (source == null) {
        "$itemTitle\n$status"
    } else {
        "$itemTitle\n$source · $status"
    }
    HomeDefaultItem(
        title = title,
        summary = summary,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        borderColor = borderColor,
        borderWidth = 1.dp,
        titleFontSize = 16.sp,
        showIndicator = false,
        trailingContent = {
            HomeReadAloudFourierVisualizer(
                isPlaying = state.isPlaying,
                audioSpectrum = audioSpectrum,
                fallbackSpectrum = state.audioSpectrum
            )
        },
        onClick = onClick
    )
}

@Composable
private fun HomeReadAloudFourierVisualizer(
    isPlaying: Boolean,
    audioSpectrum: SharedFlow<FloatArray>?,
    fallbackSpectrum: List<Float>,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val spectrumViewHolder = remember { HomeReadAloudSpectrumViewHolder() }

    LaunchedEffect(audioSpectrum, fallbackSpectrum) {
        val spectrumView = spectrumViewHolder.awaitView()
        if (audioSpectrum == null) {
            spectrumView.setTargets(fallbackSpectrum)
        } else {
            audioSpectrum.collect { spectrum ->
                spectrumView.setTargets(spectrum)
            }
        }
    }

    AndroidView(
        factory = { context ->
            HomeReadAloudSpectrumView(context).also { view ->
                spectrumViewHolder.view = view
            }
        },
        update = { view ->
            view.setSpectrumColor(accent.toArgb())
            view.setPlaying(isPlaying)
            if (audioSpectrum == null) {
                view.setTargets(fallbackSpectrum)
            }
        },
        modifier = modifier
            .width(38.dp)
            .height(34.dp)
    )
}

private class HomeReadAloudSpectrumViewHolder {
    var view: HomeReadAloudSpectrumView? = null

    suspend fun awaitView(): HomeReadAloudSpectrumView {
        while (true) {
            view?.let { return it }
            delay(READ_ALOUD_HOME_SPECTRUM_VIEW_AWAIT_DELAY_MS)
        }
    }
}

private class HomeReadAloudSpectrumView(context: Context) : View(context), Choreographer.FrameCallback {
    private val currentLevels = FloatArray(READ_ALOUD_HOME_SPECTRUM_BARS)
    private val targetLevels = FloatArray(READ_ALOUD_HOME_SPECTRUM_BARS)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRect = RectF()
    private var isPlaying = false
    private var isFrameCallbackPosted = false
    private var lastFrameTimeNanos = 0L

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setPlaying(value: Boolean) {
        if (isPlaying == value) return
        isPlaying = value
        lastFrameTimeNanos = 0L
        ensureFrameCallback()
    }

    fun setSpectrumColor(color: Int) {
        if (paint.color == color) return
        paint.color = color
        invalidate()
    }

    fun setTargets(source: List<Float>) {
        val changed = updateTargetLevels { index ->
            source.getOrNull(index) ?: 0f
        }
        if (changed && !isFrameCallbackPosted) {
            lastFrameTimeNanos = 0L
        }
        ensureFrameCallback()
    }

    fun setTargets(source: FloatArray) {
        val changed = updateTargetLevels { index ->
            source.getOrNull(index) ?: 0f
        }
        if (changed && !isFrameCallbackPosted) {
            lastFrameTimeNanos = 0L
        }
        ensureFrameCallback()
    }

    override fun doFrame(frameTimeNanos: Long) {
        isFrameCallbackPosted = false
        val elapsedSeconds = frameElapsedSeconds(frameTimeNanos)
        if (applyLevelFrame(elapsedSeconds)) {
            invalidate()
        }
        if (hasAnimationWork()) {
            ensureFrameCallback()
        } else {
            lastFrameTimeNanos = 0L
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val barCount = READ_ALOUD_HOME_SPECTRUM_BARS
        val gap = width / (barCount * 1.9f)
        val barWidth = (width - gap * (barCount - 1)) / barCount
        val centerY = height * 0.5f
        val inactiveAlpha = if (isPlaying) 1f else 0.52f
        currentLevels.forEachIndexed { index, level ->
            if (level <= 0.005f) return@forEachIndexed
            val barHeight = (height * level).coerceAtLeast(height * 0.16f)
            val left = index * (barWidth + gap)
            val top = centerY - barHeight / 2f
            paint.alpha = (((0.32f + level * 0.58f) * inactiveAlpha) * 255)
                .toInt()
                .coerceIn(0, 255)
            barRect.set(left, top, left + barWidth, top + barHeight)
            canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureFrameCallback()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        isFrameCallbackPosted = false
        lastFrameTimeNanos = 0L
        super.onDetachedFromWindow()
    }

    private fun ensureFrameCallback() {
        if (!isAttachedToWindow || isFrameCallbackPosted) return
        if (!hasAnimationWork()) return
        isFrameCallbackPosted = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun updateTargetLevels(source: (Int) -> Float): Boolean {
        var changed = false
        targetLevels.forEachIndexed { index, current ->
            val next = (source(index) * 2f).coerceIn(0f, 1f)
            if (current != next) {
                targetLevels[index] = next
                changed = true
            }
        }
        return changed
    }

    private fun frameElapsedSeconds(frameTimeNanos: Long): Float {
        val previousFrameTimeNanos = lastFrameTimeNanos
        lastFrameTimeNanos = frameTimeNanos
        if (previousFrameTimeNanos <= 0L || frameTimeNanos <= previousFrameTimeNanos) {
            return READ_ALOUD_HOME_SPECTRUM_DEFAULT_FRAME_SECONDS
        }
        return (frameTimeNanos - previousFrameTimeNanos) / READ_ALOUD_NANOS_PER_SECOND
    }

    private fun applyLevelFrame(elapsedSeconds: Float): Boolean {
        val maxDrop = (
            elapsedSeconds.coerceAtLeast(0f) *
                READ_ALOUD_HOME_SPECTRUM_DROP_RANGE_PER_SECOND
            )
        var changed = false
        currentLevels.forEachIndexed { index, current ->
            val target = if (isPlaying) targetLevels[index] else 0f
            val next = when {
                isPlaying && target >= current -> target
                current <= READ_ALOUD_HOME_SPECTRUM_IDLE_EPSILON &&
                    target <= READ_ALOUD_HOME_SPECTRUM_IDLE_EPSILON -> 0f
                current - target <= READ_ALOUD_HOME_SPECTRUM_IDLE_EPSILON -> target
                current - target <= maxDrop -> target
                else -> current - maxDrop
            }.coerceIn(0f, 1f)
            if (current != next) {
                currentLevels[index] = next
                changed = true
            }
        }
        return changed
    }

    private fun hasAnimationWork(): Boolean {
        currentLevels.forEachIndexed { index, current ->
            val target = if (isPlaying) targetLevels[index] else 0f
            if (isPlaying && target > current) return true
            if (current - target > READ_ALOUD_HOME_SPECTRUM_IDLE_EPSILON) return true
        }
        return false
    }
}

@Composable
private fun HomeAddEntry(
    onAddRssClick: () -> Unit,
    interactionsEnabled: Boolean
) {
    val buttonSize = watchDimensionResource(R.dimen.hey_button_height)
    val padding = watchDimensionResource(R.dimen.hey_distance_4dp)
    val radius = watchDimensionResource(R.dimen.hey_button_default_radius)
    val shape = RoundedCornerShape(radius)
    val pressState = rememberPressScaleState(enabled = interactionsEnabled)
    val pressScale = pressState.scale
    val scaleModifier = if (pressScale != 1f) {
        Modifier.graphicsLayer(
            scaleX = pressScale,
            scaleY = pressScale
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = padding),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .then(scaleModifier)
                .graphicsLayer {
                    this.shape = shape
                    clip = true
                }
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(HomeTestTags.ADD_ENTRY)
                .clickableWithRipple(
                    enabled = interactionsEnabled,
                    onClick = onAddRssClick,
                    interactionSource = pressState.interactionSource
                )
                .semantics { contentDescription = "添加RSS" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = textSize(R.dimen.hey_s_title)
            )
        }
    }
}

@Composable
private fun HomeChannelEntry(
    channel: RssChannel,
    platformLoginState: HomePlatformLoginState,
    openSwipeId: Long?,
    onOpenSwipe: (Long) -> Unit,
    onCloseSwipe: () -> Unit,
    draggingSwipeId: Long?,
    onDragStart: (Long) -> Unit,
    onDragEnd: () -> Unit,
    onChannelClick: () -> Unit,
    onChannelLongClick: () -> Unit,
    onSwipeBack: () -> Unit,
    swipeInteractionsEnabled: Boolean,
    interactionsEnabled: Boolean,
    onMoveTopClick: () -> Unit,
    onMarkReadClick: () -> Unit
) {
    val builtinType = BuiltinChannelType.fromUrl(channel.url)
    val summary = remember(
        channel.id,
        channel.description,
        channel.url,
        channel.isPinned,
        channel.unreadCount,
        channel.lastFetchedAt,
        platformLoginState
    ) {
        buildChannelSummary(channel, platformLoginState)
    }

    val cardContent: @Composable (Modifier) -> Unit = { offsetModifier ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(offsetModifier)
                .testTag(HomeTestTags.channelRow(channel.id))
        ) {
            HomeDefaultItem(
                title = channel.title,
                summary = summary,
                backgroundColor = if (channel.isPinned) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
                titleFontSize = 16.sp,
                showIndicator = shouldShowUnreadUi(builtinType) && channel.unreadCount > 0,
                indicatorTestTag = HomeTestTags.channelIndicator(channel.id),
                testTag = HomeTestTags.channelCard(channel.id),
                onClick = onChannelClick,
                onLongClick = onChannelLongClick,
                interactionsEnabled = interactionsEnabled,
            )
        }
    }

    if (!swipeInteractionsEnabled) {
        cardContent(Modifier)
        return
    }

    val actionPadding = watchDimensionResource(R.dimen.hey_distance_4dp)
    val actionWidth = watchDimensionResource(R.dimen.watch_swipe_action_button_width)
    val actionsWidthPx = with(LocalDensity.current) {
        (actionWidth * 2 + actionPadding * 2).toPx()
    }
    val revealGapPx = with(LocalDensity.current) { (actionPadding * 2).toPx() }

    HomeSwipeRow(
        itemId = channel.id,
        openSwipeId = openSwipeId,
        onOpenSwipe = onOpenSwipe,
        onCloseSwipe = onCloseSwipe,
        draggingSwipeId = draggingSwipeId,
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        onSwipeBack = onSwipeBack,
        enabled = swipeInteractionsEnabled,
        actionsWidthPx = actionsWidthPx,
        revealGapPx = revealGapPx
    ) { offsetModifier, actionsVisible ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (actionsVisible) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(end = actionPadding),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(actionPadding, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeSwipeActionButton(
                            text = "移到顶",
                            width = actionWidth,
                            testTag = HomeTestTags.moveTopAction(channel.id),
                            onClick = {
                                onCloseSwipe()
                                onMoveTopClick()
                            },
                            icon = Icons.Outlined.VerticalAlignTop
                        )
                        val isBuiltin = BuiltinChannelType.fromUrl(channel.url) != null
                        val canMarkRead = channel.unreadCount > 0 && !isBuiltin
                        HomeSwipeActionButton(
                            text = "标记已读",
                            width = actionWidth,
                            alpha = if (canMarkRead) 1f else 0.5f,
                            testTag = HomeTestTags.markReadAction(channel.id),
                            onClick = {
                                onCloseSwipe()
                                if (canMarkRead) {
                                    onMarkReadClick()
                                }
                            },
                            icon = Icons.Outlined.DoneAll
                        )
                    }
                }
            }
            cardContent(offsetModifier)
        }
    }
}

@Composable
private fun HomeSwipeActionButton(
    text: String,
    width: Dp,
    alpha: Float = 1f,
    testTag: String? = null,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val radius = watchDimensionResource(R.dimen.hey_card_normal_bg_radius)
    val shape = RoundedCornerShape(radius)
    val textSize = textSize(R.dimen.feed_card_action_text_size)
    val textPadding = watchDimensionResource(R.dimen.hey_distance_8dp)
    val iconSize = watchDimensionResource(R.dimen.hey_distance_16dp)
    val iconSpacing = watchDimensionResource(R.dimen.hey_distance_4dp)
    val dangerColor = watchColorResource(R.color.danger_red)
    val actionColor = if (text.contains("删除")) dangerColor else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .then(testTag?.let(Modifier::testTag) ?: Modifier)
            .clickableWithRipple(onClick = onClick)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = textPadding)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = actionColor,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.height(iconSpacing))
                Text(
                    text = text,
                    color = actionColor,
                    fontSize = textSize,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = text,
                color = actionColor,
                fontSize = textSize,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = textPadding)
            )
        }
    }
}

@Composable
private fun HomeSwipeRow(
    itemId: Long,
    openSwipeId: Long?,
    onOpenSwipe: (Long) -> Unit,
    onCloseSwipe: () -> Unit,
    draggingSwipeId: Long?,
    onDragStart: (Long) -> Unit,
    onDragEnd: () -> Unit,
    onSwipeBack: () -> Unit,
    enabled: Boolean,
    actionsWidthPx: Float,
    revealGapPx: Float,
    content: @Composable (Modifier, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val revealWidth = (actionsWidthPx + revealGapPx).coerceAtLeast(0f)
    val dragThreshold = revealWidth * 0.5f
    val backSwipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val openSwipeIdState = rememberUpdatedState(openSwipeId)

    LaunchedEffect(openSwipeId, actionsWidthPx, revealGapPx, draggingSwipeId, enabled) {
        if (!enabled) {
            if (offsetX.value != 0f) {
                offsetX.snapTo(0f)
            }
            if (openSwipeId == itemId) {
                onCloseSwipe()
            }
            return@LaunchedEffect
        }
        if (draggingSwipeId != itemId && openSwipeId != itemId && offsetX.value != 0f) {
            offsetX.animateTo(0f, animationSpec = tween(durationMillis = 180))
        }
        if (revealWidth > 0f && offsetX.value < -revealWidth) {
            offsetX.snapTo(-revealWidth)
        }
    }

    val dragModifier = if (!enabled || revealWidth <= 0f) {
        Modifier
    } else {
        Modifier.pointerInput(itemId, actionsWidthPx, revealGapPx, backSwipeThreshold) {
            if (revealWidth <= 0f) return@pointerInput
            var handlingRowDrag = false
            var handlingBackSwipe = false
            var backSwipeDistance = 0f
            detectHorizontalDragGestures(
                onDragStart = {
                    handlingRowDrag = false
                    handlingBackSwipe = false
                    backSwipeDistance = 0f
                },
                onDragEnd = {
                    when {
                        handlingRowDrag -> {
                            val shouldOpen = offsetX.value <= -dragThreshold
                            val target = if (shouldOpen) -revealWidth else 0f
                            scope.launch {
                                offsetX.animateTo(target, animationSpec = tween(durationMillis = 180))
                            }
                            if (shouldOpen) {
                                onOpenSwipe(itemId)
                            } else if (openSwipeIdState.value == itemId) {
                                onCloseSwipe()
                            }
                            onDragEnd()
                        }
                        handlingBackSwipe && backSwipeDistance >= backSwipeThreshold -> {
                            onSwipeBack()
                        }
                    }
                    handlingRowDrag = false
                    handlingBackSwipe = false
                    backSwipeDistance = 0f
                },
                onDragCancel = {
                    if (handlingRowDrag) {
                        scope.launch {
                            offsetX.animateTo(0f, animationSpec = tween(durationMillis = 180))
                        }
                        if (openSwipeIdState.value == itemId) {
                            onCloseSwipe()
                        }
                        onDragEnd()
                    }
                    handlingRowDrag = false
                    handlingBackSwipe = false
                    backSwipeDistance = 0f
                }
            ) { change, dragAmount ->
                val isRowOpen = offsetX.value < 0f || openSwipeIdState.value == itemId
                if (!handlingRowDrag && !handlingBackSwipe) {
                    if (!isRowOpen && dragAmount > 0f) {
                        handlingBackSwipe = true
                    } else {
                        handlingRowDrag = true
                        onDragStart(itemId)
                        if (openSwipeIdState.value != null && openSwipeIdState.value != itemId) {
                            onCloseSwipe()
                        }
                    }
                }
                change.consume()
                if (handlingBackSwipe) {
                    backSwipeDistance = (backSwipeDistance + dragAmount).coerceAtLeast(0f)
                } else {
                    val newOffset = (offsetX.value + dragAmount).coerceIn(-revealWidth, 0f)
                    scope.launch {
                        offsetX.snapTo(newOffset)
                    }
                }
            }
        }
    }

    val offsetModifier = Modifier
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .then(dragModifier)
    val actionsVisible = offsetX.value < -0.5f ||
        openSwipeId == itemId ||
        draggingSwipeId == itemId

    content(offsetModifier, actionsVisible)
}

@Composable
private fun HomeDefaultItem(
    title: String,
    summary: String,
    backgroundColor: Color,
    showIndicator: Boolean,
    modifier: Modifier = Modifier,
    titleFontSize: TextUnit? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    indicatorTestTag: String? = null,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionsEnabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val paddingStart = watchDimensionResource(R.dimen.hey_content_horizontal_distance_6_0)
    val paddingEnd = watchDimensionResource(R.dimen.hey_listitem_padding_right)
    val verticalPadding = watchDimensionResource(R.dimen.hey_multiple_default_summary_alone_padding_vertical)
    val titleSize = titleFontSize ?: textSize(R.dimen.hey_s_title)
    val summarySize = textSize(R.dimen.hey_m_desription)
    val summaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val arrowMargin = watchDimensionResource(R.dimen.hey_listitem_widget_margin_left)
    val minorMarginRight = watchDimensionResource(R.dimen.hey_listitem_widget_minor_margin_right)
    val indicatorSize = watchDimensionResource(R.dimen.hey_distance_6dp)
    val shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_card_normal_bg_radius))
    val clickModifier = if (onClick != null) {
        Modifier.clickableWithRipple(
            enabled = interactionsEnabled,
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .background(backgroundColor, shape)
            .then(
                borderColor?.let { color ->
                    Modifier.border(borderWidth, color, shape)
                } ?: Modifier
            )
            .then(clickModifier)
            .then(testTag?.let(Modifier::testTag) ?: Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = paddingStart,
                    end = paddingEnd,
                    top = verticalPadding,
                    bottom = verticalPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = titleSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    color = summaryColor,
                    fontSize = summarySize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingContent != null) {
                Box(
                    modifier = Modifier.padding(start = arrowMargin),
                    contentAlignment = Alignment.Center
                ) {
                    trailingContent()
                }
            }
            if (showIndicator) {
                Box(
                    modifier = Modifier
                        .padding(start = arrowMargin)
                        .offset(x = minorMarginRight)
                        .size(indicatorSize)
                        .then(indicatorTestTag?.let(Modifier::testTag) ?: Modifier)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

private fun buildChannelSummary(
    channel: RssChannel,
    platformLoginState: HomePlatformLoginState
): String {
    val builtinType = BuiltinChannelType.fromUrl(channel.url)
    val isRealtime = when (builtinType) {
        BuiltinChannelType.BILI -> platformLoginState.isBiliLoggedIn
        BuiltinChannelType.DOUYIN -> platformLoginState.isDouyinLoggedIn
        null -> false
    }
    val summary = when {
        builtinType == BuiltinChannelType.BILI && platformLoginState.isBiliLoggedIn ->
            "进入以获取推荐内容"
        builtinType == BuiltinChannelType.DOUYIN && platformLoginState.isDouyinLoggedIn ->
            "进入以获取推荐内容"
        else -> channel.description?.takeIf { it.isNotBlank() } ?: channel.url
    }
    val pinLabel = if (channel.isPinned) "置顶 · " else ""
    val unreadLabel = if (
        shouldShowUnreadUi(builtinType) &&
        channel.unreadCount > 0
    ) {
        "未读 ${channel.unreadCount} · "
    } else {
        ""
    }
    val updateLabel = if (isRealtime) {
        "更新：实时"
    } else {
        "更新: ${formatTime(channel.lastFetchedAt)}"
    }
    return "$pinLabel$summary\n${unreadLabel}$updateLabel"
}

private fun shouldShowUnreadUi(builtinType: BuiltinChannelType?): Boolean {
    return builtinType != BuiltinChannelType.BILI &&
        builtinType != BuiltinChannelType.DOUYIN
}

@Composable
private fun HomeBeianEntry(
    onBeianClick: () -> Unit,
    interactionsEnabled: Boolean
) {
    val padding = watchDimensionResource(R.dimen.hey_distance_4dp)
    val textSize = textSize(R.dimen.hey_caption)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "浙ICP备2024111886号-5A",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textSize,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag(HomeTestTags.BEIAN_ENTRY)
                .clickableWithRipple(
                    enabled = interactionsEnabled,
                    onClick = onBeianClick
                )
        )
    }
}

@Stable
private data class PressScaleState(
    val scale: Float,
    val interactionSource: MutableInteractionSource
)

@Composable
private fun rememberPressScaleState(enabled: Boolean = true): PressScaleState {
    val interactionSource = remember { MutableInteractionSource() }
    if (!enabled) {
        return PressScaleState(1f, interactionSource)
    }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 240 else 360),
        label = "pressScale"
    )
    return PressScaleState(scale, interactionSource)
}

@Composable
private fun textSize(id: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { watchDimensionResource(id).toSp() }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.clickableWithRipple(
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null
): Modifier {
    return composed {
        val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
        val indication = LocalIndication.current
        if (onLongClick != null) {
            combinedClickable(
                interactionSource = resolvedInteractionSource,
                indication = indication,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
        } else {
            clickable(
                interactionSource = resolvedInteractionSource,
                indication = indication,
                enabled = enabled,
                onClick = onClick
            )
        }
    }
}

private const val READ_ALOUD_HOME_SPECTRUM_BARS = 5
private const val READ_ALOUD_NANOS_PER_SECOND = 1_000_000_000f
private const val READ_ALOUD_HOME_SPECTRUM_DEFAULT_FRAME_SECONDS = 1f / 60f
private const val READ_ALOUD_HOME_SPECTRUM_DROP_RANGE_PER_SECOND = 2f
private const val READ_ALOUD_HOME_SPECTRUM_IDLE_EPSILON = 0.002f
private const val READ_ALOUD_HOME_SPECTRUM_VIEW_AWAIT_DELAY_MS = 16L
private const val DEBUG_AUTOSCROLL_MIN_ENTRIES = 8
private const val DEBUG_AUTOSCROLL_START_DELAY_MS = 8_000L
private const val DEBUG_AUTOSCROLL_DURATION_MS = 1_100
private const val DEBUG_AUTOSCROLL_TURNAROUND_DELAY_MS = 160L
private const val DEBUG_AUTOSCROLL_SEGMENT_COUNT = 2
private const val DEBUG_AUTOSCROLL_DISTANCE_PX = 320f
