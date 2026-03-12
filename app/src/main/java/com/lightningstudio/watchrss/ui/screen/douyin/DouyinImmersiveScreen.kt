package com.lightningstudio.watchrss.ui.screen.douyin

import android.graphics.Paint
import android.text.TextPaint
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.ui.components.ToastMessage
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedUiState
import com.lightningstudio.watchrss.util.AppLogger
import okhttp3.OkHttpClient
import kotlin.math.min

@Composable
fun DouyinImmersiveScreen(
    uiState: DouyinFeedUiState,
    onPageSettled: (Int) -> Unit,
    onEnterFlow: () -> Unit,
    onMessageShown: () -> Unit,
    onHeaderClick: () -> Unit
) {
    val pageCount = uiState.items.size + 1
    val pagerState = rememberPagerState(
        initialPage = uiState.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount.coerceAtLeast(1) }
    )
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var isFullscreen by rememberSaveable { mutableStateOf(true) }

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
                    isFullscreen = isFullscreen,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onToggleFullscreen = { isFullscreen = !isFullscreen }
                )
            }
        }

        if (uiState.isLoading && uiState.items.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (uiState.isLoadingMore) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }

        if (!uiState.message.isNullOrBlank()) {
            ToastMessage(text = uiState.message)
            LaunchedEffect(uiState.message) {
                onMessageShown()
            }
        }
    }
}

@Composable
private fun DouyinTitlePage(
    onEnterFlow: () -> Unit,
    onHeaderClick: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val subtitleSpacing = dimensionResource(R.dimen.hey_distance_2dp)
    val buttonBottom = dimensionResource(R.dimen.hey_distance_12dp)
    val buttonSize = dimensionResource(R.dimen.hey_button_height)

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
                        painter = painterResource(id = R.drawable.ic_action_douyin_up),
                        contentDescription = "向上进入视频流",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(dimensionResource(R.dimen.hey_distance_16dp))
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
    isFullscreen: Boolean,
    onToggleControls: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val topPadding = dimensionResource(R.dimen.hey_distance_6dp)
    val bottomPadding = dimensionResource(R.dimen.hey_distance_8dp)
    val controlsSize = dimensionResource(R.dimen.hey_button_height)
    val controlsIconSize = dimensionResource(R.dimen.hey_listitem_widget_size)
    val titleFontSize = with(density) { dimensionResource(R.dimen.feed_card_title_text_size).toSp() }

    val localUri = remember(item.awemeId) { localPlayPath?.let { "file://$it" } }
    val remoteUri = remember(item.awemeId) { item.playUrl }
    var mediaUri by remember(item.awemeId) {
        mutableStateOf(localUri ?: remoteUri)
    }
    val headersSignature = remember(headers) { headers.entries.sortedBy { it.key }.joinToString(";") }
    var preparedUri by remember(item.awemeId) { mutableStateOf<String?>(null) }
    var retryCount by remember(item.awemeId) { mutableIntStateOf(0) }
    var pausedByGesture by remember(item.awemeId) { mutableStateOf(false) }
    var isBuffering by remember(item.awemeId) { mutableStateOf(false) }
    var hasError by remember(item.awemeId) { mutableStateOf(false) }

    LaunchedEffect(item.awemeId, localPlayPath, remoteUri) {
        if (mediaUri.isNullOrBlank()) {
            mediaUri = localPlayPath?.let { "file://$it" } ?: remoteUri
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

    DisposableEffect(player, item.awemeId, remoteUri) {
        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                isBuffering = isLoading
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    hasError = false
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLogger.w(TAG, "playback error awemeId=${item.awemeId}, uri=$mediaUri", error)
                if (retryCount < 1) {
                    retryCount += 1
                    if (!mediaUri.isNullOrBlank() && mediaUri?.startsWith("file://") == true && !remoteUri.isNullOrBlank()) {
                        mediaUri = remoteUri
                        preparedUri = null
                    } else {
                        player.seekTo(0)
                        player.prepare()
                    }
                } else {
                    hasError = true
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

    LaunchedEffect(mediaUri, player) {
        val targetUri = mediaUri?.trim().orEmpty()
        if (targetUri.isBlank()) {
            hasError = true
            return@LaunchedEffect
        }
        if (preparedUri == targetUri) {
            return@LaunchedEffect
        }
        hasError = false
        retryCount = 0
        preparedUri = targetUri
        player.setMediaItem(MediaItem.fromUri(targetUri))
        player.prepare()
    }

    LaunchedEffect(isActive, pausedByGesture, hasError) {
        val shouldPlay = isActive && !pausedByGesture && !hasError
        player.playWhenReady = shouldPlay
        if (shouldPlay) {
            player.play()
        } else {
            player.pause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.awemeId, controlsVisible) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (player.isPlaying) {
                            pausedByGesture = true
                            player.pause()
                        } else {
                            pausedByGesture = false
                            if (isActive && !hasError) {
                                player.playWhenReady = true
                                player.play()
                            }
                        }
                    }
                )
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = if (isFullscreen) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    this.player = player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = {
                it.player = player
                it.resizeMode = if (isFullscreen) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        )

        if (isBuffering && !hasError && isActive) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                val fullscreenIcon = if (isFullscreen) {
                    R.drawable.ic_player_fullscreen_exit
                } else {
                    R.drawable.ic_player_fullscreen
                }
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(controlsSize)
                ) {
                    Icon(
                        painter = painterResource(id = fullscreenIcon),
                        contentDescription = if (isFullscreen) "退出全屏" else "全屏",
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
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.hey_distance_2dp)),
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

private const val TITLE_ORIGINAL_FIRST_LINE_RATIO = 0.68f
private const val TITLE_ORIGINAL_SECOND_LINE_RATIO = 0.82f
private const val TAG = "DouyinImmersive"
