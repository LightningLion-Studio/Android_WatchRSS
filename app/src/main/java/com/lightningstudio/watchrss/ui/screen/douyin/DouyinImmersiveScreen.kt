package com.lightningstudio.watchrss.ui.screen.douyin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Paint
import android.text.TextPaint
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PanoramaFishEye
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceKind
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.ui.components.ToastMessage
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.components.WatchIconButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.PlayerVolumeOverlay
import com.lightningstudio.watchrss.ui.components.rememberPlayerVolumeState
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownPagerHandler
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownVolumeHandler
import com.lightningstudio.watchrss.ui.util.hasValidatedInternetConnection
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedUiState
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import kotlin.math.min
import kotlin.math.sqrt

internal enum class DouyinPlayerScaleMode {
    Standard,
    Expanded,
    Shrunk;

    fun next(): DouyinPlayerScaleMode {
        return when (this) {
            Standard -> Expanded
            Expanded -> Shrunk
            Shrunk -> Standard
        }
    }
}

private data class DouyinPlayerScaleToggleAction(
    val icon: ImageVector,
    val contentDescription: String
)

@Composable
fun DouyinImmersiveScreen(
    uiState: DouyinFeedUiState,
    onPageSettled: (Int) -> Unit,
    onEnterFlow: () -> Unit,
    onItemLongPress: (DouyinStreamItem) -> Unit,
    onRequestPlaybackRefresh: (String, DouyinPlaybackSourceKind) -> Unit,
    onMessageShown: () -> Unit,
    onHeaderClick: () -> Unit
) {
    val pageCount = uiState.items.size + 1
    val pagerState = rememberPagerState(
        initialPage = uiState.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount.coerceAtLeast(1) }
    )
    var pendingAutoSkipPage by remember { mutableIntStateOf(-1) }
    var autoSkipMessage by remember { mutableStateOf<String?>(null) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var scaleMode by rememberSaveable { mutableStateOf(DouyinPlayerScaleMode.Standard) }
    val volumeState = rememberPlayerVolumeState()

    InstallDigitalCrownPagerHandler(
        pagerState = pagerState,
        enabled = pagerState.currentPage == 0 && pageCount > 1
    )
    InstallDigitalCrownVolumeHandler(
        enabled = pagerState.currentPage > 0,
        showSystemUi = false,
        reverseDirection = true,
        supportsDigitalCrown = true,
        onVolumeAdjust = volumeState::adjustByDelta
    )

    LaunchedEffect(pagerState.currentPage) {
        onPageSettled(pagerState.currentPage)
    }

    LaunchedEffect(uiState.currentPage, uiState.showTitlePage, pageCount) {
        val target = when {
            uiState.showTitlePage || uiState.items.isEmpty() -> 0
            else -> uiState.currentPage.coerceIn(1, (pageCount - 1).coerceAtLeast(1))
        }
        if (target != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(pendingAutoSkipPage, pageCount) {
        val failingPage = pendingAutoSkipPage
        if (failingPage <= 0) return@LaunchedEffect
        pendingAutoSkipPage = -1
        if (failingPage != pagerState.currentPage) return@LaunchedEffect

        val nextPage = (failingPage + 1).coerceAtMost(pageCount - 1)
        if (nextPage == failingPage) return@LaunchedEffect

        autoSkipMessage = DOUYIN_AUTO_SKIP_MESSAGE
        pagerState.scrollToPage(nextPage)
    }

    LaunchedEffect(autoSkipMessage) {
        if (autoSkipMessage.isNullOrBlank()) return@LaunchedEffect
        delay(DOUYIN_AUTO_SKIP_MESSAGE_DURATION_MS)
        autoSkipMessage = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page == 0) {
                DouyinTitlePage(
                    onEnterFlow = onEnterFlow,
                    onHeaderClick = onHeaderClick
                )
            } else {
                val item = uiState.items[page - 1]
                DouyinVideoPage(
                    item = item,
                    headers = uiState.playHeaders,
                    localPlayPath = uiState.localPlayPaths[item.awemeId],
                    isActive = pagerState.currentPage == page,
                    controlsVisible = controlsVisible,
                    scaleMode = scaleMode,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onToggleScaleMode = { scaleMode = scaleMode.next() },
                    onLongPress = { onItemLongPress(item) },
                    onRequestPlaybackRefresh = onRequestPlaybackRefresh,
                    pageIndex = page,
                    hasNextItem = page < pageCount - 1,
                    onAutoSkipCurrentItem = { failingPage ->
                        if (failingPage == pagerState.currentPage) {
                            pendingAutoSkipPage = failingPage
                        }
                    }
                )
            }
        }

        if (uiState.isLoading && uiState.items.isEmpty()) {
            WatchCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (uiState.isLoadingMore) {
            WatchCircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }

        when {
            !autoSkipMessage.isNullOrBlank() -> {
                ToastMessage(text = autoSkipMessage!!)
            }

            !uiState.message.isNullOrBlank() -> {
                ToastMessage(text = uiState.message)
            }
        }

        if (!uiState.message.isNullOrBlank()) {
            LaunchedEffect(uiState.message) {
                onMessageShown()
            }
        }

        PlayerVolumeOverlay(
            state = volumeState,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun DouyinTitlePage(
    onEnterFlow: () -> Unit,
    onHeaderClick: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val subtitleSpacing = watchDimensionResource(R.dimen.hey_distance_2dp)
    val buttonBottom = watchDimensionResource(R.dimen.hey_distance_12dp)
    val buttonSize = watchDimensionResource(R.dimen.hey_button_height)

    WatchSurface(pureBlack = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(safePadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .clickable(onClick = onHeaderClick),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "抖音",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(subtitleSpacing))
                Text(
                    text = "向上进入短视频流",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = buttonBottom)
                    .size(buttonSize)
                    .clickable { onEnterFlow() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowUpward,
                        contentDescription = "向上进入视频流",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(watchDimensionResource(R.dimen.hey_distance_16dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun DouyinVideoPage(
    item: DouyinStreamItem,
    headers: Map<String, String>,
    localPlayPath: String?,
    isActive: Boolean,
    controlsVisible: Boolean,
    scaleMode: DouyinPlayerScaleMode,
    onToggleControls: () -> Unit,
    onToggleScaleMode: () -> Unit,
    onLongPress: () -> Unit,
    onRequestPlaybackRefresh: (String, DouyinPlaybackSourceKind) -> Unit,
    pageIndex: Int,
    hasNextItem: Boolean,
    onAutoSkipCurrentItem: (Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val topPadding = watchDimensionResource(R.dimen.hey_distance_6dp)
    val bottomPadding = watchDimensionResource(R.dimen.hey_distance_8dp)
    val controlsSize = watchDimensionResource(R.dimen.hey_button_height)
    val controlsIconSize = watchDimensionResource(R.dimen.hey_listitem_widget_size)
    val titleFontSize = with(density) { watchDimensionResource(R.dimen.feed_card_title_text_size).toSp() }

    val localUri = localPlayPath?.takeIf { it.isNotBlank() }?.let { "file://$it" }
    val remoteUri = item.playUrl.takeIf { it.isNotBlank() }
    var mediaUri by remember(item.awemeId) {
        mutableStateOf(localUri ?: remoteUri)
    }
    var remoteResolvedAtMs by remember(item.awemeId) {
        mutableStateOf(item.playUrlResolvedAtMs)
    }
    val headersSignature = remember(headers) { headers.entries.sortedBy { it.key }.joinToString(";") }
    var preparedSourceKey by remember(item.awemeId) { mutableStateOf<String?>(null) }
    var retryCount by remember(item.awemeId) { mutableIntStateOf(0) }
    var pausedByGesture by remember(item.awemeId) { mutableStateOf(false) }
    var isBuffering by remember(item.awemeId) { mutableStateOf(false) }
    var isPlaying by remember(item.awemeId) { mutableStateOf(false) }
    var hasError by remember(item.awemeId) { mutableStateOf(false) }
    var viewSize by remember(item.awemeId) { mutableStateOf(IntSize.Zero) }
    var videoSize by remember(item.awemeId) { mutableStateOf(IntSize.Zero) }
    val shouldKeepScreenOn = isActive && !hasError && (isPlaying || isBuffering)
    val shrinkFactor = remember(scaleMode, viewSize, videoSize) {
        if (scaleMode == DouyinPlayerScaleMode.Shrunk) {
            calculateDouyinPlayerShrinkFactor(
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
                videoWidth = videoSize.width.toFloat(),
                videoHeight = videoSize.height.toFloat()
            )
        } else {
            1f
        }
    }
    val playerResizeMode = remember(scaleMode) {
        if (scaleMode == DouyinPlayerScaleMode.Expanded) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    LaunchedEffect(localUri, remoteUri, item.playUrlResolvedAtMs) {
        val resolvedState = resolveDouyinPlaybackState(
            currentUri = mediaUri,
            currentRemoteResolvedAtMs = remoteResolvedAtMs,
            localUri = localUri,
            remoteUri = remoteUri,
            remoteResolvedAtMs = item.playUrlResolvedAtMs
        )
        if (resolvedState.mediaUri != mediaUri || resolvedState.remoteResolvedAtMs != remoteResolvedAtMs) {
            mediaUri = resolvedState.mediaUri
            remoteResolvedAtMs = resolvedState.remoteResolvedAtMs
            preparedSourceKey = null
            hasError = false
            isBuffering = resolvedState.mediaUri != null
        }
    }

    val player = remember(item.awemeId, headersSignature) {
        val requestFactory = OkHttpDataSource.Factory(OkHttpClient()).apply {
            setDefaultRequestProperties(headers)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(128 * 1024 * 1024)
            .setBufferDurationsMs(10_000, 45_000, 1_000, 2_000)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(requestFactory))
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    fun requestPlaybackRefresh(resetRetryCount: Boolean = false) {
        val sourceKind = currentDouyinPlaybackSourceKind(mediaUri)
        val targetUri = mediaUri?.trim()?.takeIf { it.isNotBlank() }
            ?: localUri
            ?: remoteUri
        if (targetUri.isNullOrBlank()) {
            hasError = true
            return
        }
        if (resetRetryCount) {
            retryCount = 0
        }
        hasError = sourceKind == DouyinPlaybackSourceKind.REMOTE ||
            (sourceKind == DouyinPlaybackSourceKind.LOCAL && remoteUri.isNullOrBlank())
        isBuffering = sourceKind == DouyinPlaybackSourceKind.LOCAL && !remoteUri.isNullOrBlank()
        pausedByGesture = false
        if (sourceKind == DouyinPlaybackSourceKind.LOCAL && !remoteUri.isNullOrBlank()) {
            mediaUri = remoteUri
            remoteResolvedAtMs = item.playUrlResolvedAtMs
            preparedSourceKey = null
        }
        onRequestPlaybackRefresh(item.awemeId, sourceKind)
    }

    fun stopPlayback() {
        player.playWhenReady = false
        player.pause()
        isPlaying = false
        isBuffering = false
    }

    val latestHasError = rememberUpdatedState(hasError)
    val latestIsActive = rememberUpdatedState(isActive)
    val latestRequestPlaybackRefresh = rememberUpdatedState(
        newValue = { resetRetryCount: Boolean -> requestPlaybackRefresh(resetRetryCount) }
    )
    val latestAutoSkipCurrentItem = rememberUpdatedState(newValue = { onAutoSkipCurrentItem(pageIndex) })

    DisposableEffect(player, item.awemeId, remoteUri) {
        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                isBuffering = isLoading
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
                if (playbackState == Player.STATE_READY) {
                    hasError = false
                    retryCount = 0
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onVideoSizeChanged(videoSizeNow: androidx.media3.common.VideoSize) {
                videoSize = IntSize(videoSizeNow.width, videoSizeNow.height)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLogger.w(TAG, "playback error awemeId=${item.awemeId}, uri=$mediaUri", error)
                when (
                    resolveDouyinPlaybackFailureAction(
                        retryCount = retryCount,
                        maxAutoRetryCount = DOUYIN_MAX_AUTO_RETRY_COUNT,
                        hasValidatedInternetConnection = hasValidatedInternetConnection(context),
                        hasNextItem = hasNextItem
                    )
                ) {
                    DouyinPlaybackFailureAction.Retry -> {
                        retryCount += 1
                        latestRequestPlaybackRefresh.value(false)
                    }

                    DouyinPlaybackFailureAction.AutoSkip -> {
                        hasError = false
                        isBuffering = false
                        stopPlayback()
                        latestAutoSkipCurrentItem.value()
                    }

                    DouyinPlaybackFailureAction.ShowError -> {
                        hasError = true
                        isBuffering = false
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                stopPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(shouldKeepScreenOn, isActive, view) {
        val window = view.context.findActivity()?.window
        if (shouldKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (isActive) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val prepareKey = remember(mediaUri, remoteResolvedAtMs) {
        buildDouyinPlaybackPrepareKey(mediaUri, remoteResolvedAtMs)
    }

    LaunchedEffect(prepareKey, player) {
        val targetPrepareKey = prepareKey
        val targetUri = mediaUri?.trim().orEmpty()
        if (targetPrepareKey.isNullOrBlank() || targetUri.isBlank()) {
            hasError = true
            isBuffering = false
            return@LaunchedEffect
        }
        if (preparedSourceKey == targetPrepareKey) {
            return@LaunchedEffect
        }
        hasError = false
        isBuffering = true
        preparedSourceKey = targetPrepareKey
        player.setMediaItem(MediaItem.fromUri(targetUri))
        player.prepare()
    }

    LaunchedEffect(isActive, pausedByGesture, hasError) {
        val shouldPlay = isActive && !pausedByGesture && !hasError
        player.playWhenReady = shouldPlay
        if (shouldPlay) {
            player.play()
        } else {
            stopPlayback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.awemeId, controlsVisible) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = {
                        if (latestHasError.value) {
                            latestRequestPlaybackRefresh.value(true)
                        } else if (player.isPlaying) {
                            pausedByGesture = true
                            player.pause()
                        } else {
                            pausedByGesture = false
                            if (latestIsActive.value && !latestHasError.value) {
                                player.playWhenReady = true
                                player.play()
                            }
                        }
                    }
                )
            }
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .scale(shrinkFactor),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = playerResizeMode
                    this.player = player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = {
                it.player = player
                it.resizeMode = playerResizeMode
            }
        )

        if (isBuffering && !hasError && isActive) {
            WatchCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (hasError && isActive) {
            Text(
                text = "播放失败，双击重试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = safePadding)
            )
        }

        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC000000), Color.Transparent)
                        )
                    )
                    .padding(top = topPadding, bottom = topPadding)
            ) {
                val scaleToggleAction = scaleMode.toggleAction()
                WatchIconButton(
                    onClick = onToggleScaleMode,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(controlsSize)
                ) {
                    Icon(
                        imageVector = scaleToggleAction.icon,
                        contentDescription = scaleToggleAction.contentDescription,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(controlsIconSize)
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000))
                        )
                    )
                    .padding(horizontal = safePadding)
                    .padding(
                        top = topPadding,
                        bottom = bottomPadding
                    )
            ) {
                val availableWidthPx = with(density) { maxWidth.toPx() }
                val titlePx = with(density) { titleFontSize.toPx() }
                val titlePaint = remember(titlePx) {
                    TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = titlePx }
                }
                val titleText = remember(item.title, availableWidthPx, titlePx) {
                    formatDouyinTitleForCircle(
                        title = item.title ?: "抖音视频",
                        paint = titlePaint,
                        availableWidthPx = availableWidthPx,
                        firstLimitPx = availableWidthPx * TITLE_ORIGINAL_SECOND_LINE_RATIO,
                        secondLimitPx = availableWidthPx * TITLE_ORIGINAL_FIRST_LINE_RATIO
                    )
                }
                val infoText = remember(item.author, item.likeCount) {
                    buildInfoText(item.author, item.likeCount)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(watchDimensionResource(R.dimen.hey_distance_2dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = titleText,
                        fontSize = titleFontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (infoText.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = infoText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun buildInfoText(author: String?, likeCount: Long): String {
    val safeAuthor = author?.trim().orEmpty().ifBlank { "未知作者" }
    val likeText = if (likeCount > 0) "赞 ${formatLikeCount(likeCount)}" else null
    return listOfNotNull(safeAuthor, likeText).joinToString(" · ")
}

private fun formatLikeCount(value: Long): String {
    if (value < 1_000L) return value.toString()
    return when {
        value < 10_000L -> formatWithSuffix(value, 1_000.0, "k")
        value < 1_000_000L -> formatWithSuffix(value, 10_000.0, "w")
        else -> formatWithSuffix(value, 1_000_000.0, "m")
    }
}

private fun formatWithSuffix(value: Long, divisor: Double, suffix: String): String {
    val scaled = value / divisor
    val rounded = kotlin.math.floor(scaled * 10.0) / 10.0
    val text = if (rounded >= 100 || rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
    return "$text$suffix"
}

private fun formatDouyinTitleForCircle(
    title: String,
    paint: TextPaint,
    availableWidthPx: Float,
    firstLimitPx: Float,
    secondLimitPx: Float
): String {
    val normalized = title.trim().replace('\n', ' ')
    if (normalized.isEmpty()) return "抖音视频"

    val firstLimit = min(firstLimitPx, availableWidthPx)
    val secondLimit = min(secondLimitPx, availableWidthPx)
    if (paint.measureText(normalized) <= firstLimit) {
        return normalized
    }

    val firstEnd = breakTextIndex(normalized, 0, firstLimit, paint).coerceAtLeast(1)
    val firstLine = normalized.substring(0, firstEnd).trimEnd()
    var secondLineSource = normalized.substring(firstEnd).trimStart()
    if (secondLineSource.isEmpty()) return firstLine

    if (paint.measureText(secondLineSource) <= secondLimit) {
        return "$firstLine\n$secondLineSource"
    }

    secondLineSource = ellipsizeToWidth(secondLineSource, secondLimit, paint)
    return "$firstLine\n$secondLineSource"
}

private fun breakTextIndex(text: String, start: Int, widthPx: Float, paint: TextPaint): Int {
    var low = start
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        val current = text.substring(start, mid)
        if (paint.measureText(current) <= widthPx) {
            low = mid
        } else {
            high = mid - 1
        }
    }
    return low
}

private fun ellipsizeToWidth(text: String, widthPx: Float, paint: TextPaint): String {
    val ellipsis = "…"
    if (text.isBlank()) return ellipsis
    if (paint.measureText(ellipsis) > widthPx) return ellipsis
    var low = 0
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        val current = text.substring(0, mid) + ellipsis
        if (paint.measureText(current) <= widthPx) {
            low = mid
        } else {
            high = mid - 1
        }
    }
    return if (low <= 0) ellipsis else text.substring(0, low) + ellipsis
}

internal fun calculateDouyinPlayerShrinkFactor(
    viewWidth: Float,
    viewHeight: Float,
    videoWidth: Float,
    videoHeight: Float
): Float {
    if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0f || videoHeight <= 0f) {
        return 1f
    }
    val viewAspect = viewWidth / viewHeight
    val videoAspect = videoWidth / videoHeight
    val contentWidth: Float
    val contentHeight: Float
    if (videoAspect > viewAspect) {
        contentWidth = viewWidth
        contentHeight = viewWidth / videoAspect
    } else {
        contentWidth = viewHeight * videoAspect
        contentHeight = viewHeight
    }
    val diagonal = sqrt(contentWidth * contentWidth + contentHeight * contentHeight)
    return if (diagonal > 0f) {
        (viewWidth / diagonal).coerceAtMost(1f)
    } else {
        1f
    }
}

private fun DouyinPlayerScaleMode.toggleAction(): DouyinPlayerScaleToggleAction {
    return when (this) {
        DouyinPlayerScaleMode.Standard -> DouyinPlayerScaleToggleAction(
            icon = Icons.Filled.Fullscreen,
            contentDescription = "放大"
        )
        DouyinPlayerScaleMode.Expanded -> DouyinPlayerScaleToggleAction(
            icon = Icons.Filled.PanoramaFishEye,
            contentDescription = "缩小"
        )
        DouyinPlayerScaleMode.Shrunk -> DouyinPlayerScaleToggleAction(
            icon = Icons.Filled.FullscreenExit,
            contentDescription = "标准"
        )
    }
}

private fun currentDouyinPlaybackSourceKind(mediaUri: String?): DouyinPlaybackSourceKind {
    return if (mediaUri?.startsWith("file://") == true) {
        DouyinPlaybackSourceKind.LOCAL
    } else {
        DouyinPlaybackSourceKind.REMOTE
    }
}

private const val TITLE_ORIGINAL_FIRST_LINE_RATIO = 0.68f
private const val TITLE_ORIGINAL_SECOND_LINE_RATIO = 0.82f
private const val DOUYIN_MAX_AUTO_RETRY_COUNT = 2
private const val DOUYIN_AUTO_SKIP_MESSAGE = "当前视频无法播放，已为您自动跳过"
private const val DOUYIN_AUTO_SKIP_MESSAGE_DURATION_MS = 2_000L
private const val TAG = "DouyinImmersive"
