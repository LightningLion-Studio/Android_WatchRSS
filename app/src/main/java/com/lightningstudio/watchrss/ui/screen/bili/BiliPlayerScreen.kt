package com.lightningstudio.watchrss.ui.screen.bili

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.PlayerVolumeOverlay
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.components.rememberPlayerVolumeState
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownVolumeHandler
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.normalizeUserFacingMessage
import com.lightningstudio.watchrss.ui.util.offlineToastMessageOrNull
import com.lightningstudio.watchrss.ui.util.showAppToast
import com.lightningstudio.watchrss.ui.viewmodel.BiliPlaybackSource
import com.lightningstudio.watchrss.ui.viewmodel.BiliPlayerUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class PlayerScaleMode {
    Standard,
    Expanded,
    Shrunk;

    fun next(): PlayerScaleMode {
        return when (this) {
            Standard -> Expanded
            Expanded -> Shrunk
            Shrunk -> Standard
        }
    }
}

internal data class PlayerScale(
    val scaleX: Float,
    val scaleY: Float
)

private data class PlayerScaleToggleAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String
)

@Composable
fun BiliPlayerScreen(
    uiState: BiliPlayerUiState,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
    onPlaybackError: () -> Boolean = { false },
    onPanStateChange: (Float, Float) -> Unit,
    onPlaybackProgress: (positionMs: Int, durationMs: Int, force: Boolean) -> Unit = { _, _, _ -> },
    onPlaybackEnded: () -> Unit = {},
    playbackDataSourceFactoryProvider: ((Map<String, String>, String?) -> DataSource.Factory)? = null,
    allowPan: Boolean = true,
    isActive: Boolean = true,
    digitalCrownVolumeEnabled: Boolean = true
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val spacing = watchDimensionResource(R.dimen.hey_distance_6dp)
    val accent = watchColorResource(R.color.brand_orange)
    val timeSize = textSize(R.dimen.hey_caption)
    val controlSize = watchDimensionResource(R.dimen.hey_button_height)
    val iconSize = watchDimensionResource(R.dimen.hey_listitem_widget_size)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val view = LocalView.current
    var playerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var isTextureAvailable by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
    var activeSource by remember { mutableStateOf<BiliPlaybackSource?>(null) }
    var pendingSeekPositionMs by remember { mutableStateOf(0) }
    var pendingPreparedPlayWhenReady by remember { mutableStateOf(false) }
    var playbackCompleted by remember { mutableStateOf(false) }
    var autoPlayInitialized by remember { mutableStateOf(false) }
    var scaleMode by remember { mutableStateOf(PlayerScaleMode.Standard) }
    var rotationAngle by remember { mutableStateOf(0f) }
    var rotationTargetDeg by remember { mutableStateOf(0f) }
    val animatedRotationAngle by animateFloatAsState(
        targetValue = rotationTargetDeg,
        animationSpec = tween(durationMillis = 300),
        label = "screenRotation"
    )
    var controlsVisible by remember { mutableStateOf(true) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var videoSize by remember { mutableStateOf(IntSize.Zero) }
    var videoRotation by remember { mutableStateOf(0) }
    var playWhenReady by remember { mutableStateOf(false) }
    var panOffsetX by remember { mutableStateOf(0f) }
    val panAnimator = remember { Animatable(0f) }
    val panDecay = remember { exponentialDecay<Float>() }
    val panScope = rememberCoroutineScope()
    val panFlingJob = remember { mutableStateOf<Job?>(null) }
    val volumeState = rememberPlayerVolumeState()
    val hasConfiguredSource = uiState.initialSource != null
    val shouldKeepScreenOn = isActive &&
        hasConfiguredSource &&
        playbackError.isNullOrBlank() &&
        (isPlaying || isBuffering || (activeSource != null && isTextureAvailable))

    fun stopPanFling() {
        panFlingJob.value?.cancel()
        panFlingJob.value = null
    }

    fun currentPlayerPosition(): Int {
        val current = runCatching { playerRef?.currentPosition ?: positionMs.toLong() }
            .getOrDefault(positionMs.toLong())
            .coerceAtLeast(0L)
        return current.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun pausePlayback(clearPlayWhenReady: Boolean = true) {
        if (clearPlayWhenReady) {
            playWhenReady = false
            pendingPreparedPlayWhenReady = false
        }
        val player = playerRef
        if (player != null) {
            runCatching {
                player.playWhenReady = false
                player.pause()
                positionMs = currentPlayerPosition()
                pendingSeekPositionMs = positionMs
            }
        }
        if (!playbackCompleted && (positionMs > 0 || durationMs > 0 || isPrepared)) {
            onPlaybackProgress(positionMs, durationMs, true)
        }
        isPlaying = false
        isBuffering = false
    }

    fun togglePlayback() {
        val player = playerRef ?: return
        if (!isPrepared) return
        if (player.isPlaying) {
            pausePlayback()
            return
        }
        runCatching {
            playWhenReady = true
            pendingPreparedPlayWhenReady = false
            player.playWhenReady = isActive
            if (isActive) {
                player.play()
                isPlaying = true
                isBuffering = false
            }
        }
    }

    fun releasePlayer(resetActiveSource: Boolean = true) {
        val player = playerRef ?: return
        textureViewRef?.let { textureView ->
            runCatching { player.clearVideoTextureView(textureView) }
        }
        player.release()
        playerRef = null
        isPrepared = false
        isPlaying = false
        isBuffering = false
        if (resetActiveSource) {
            activeSource = null
        }
    }

    fun buildDataSourceFactory(source: BiliPlaybackSource): DataSource.Factory {
        return playbackDataSourceFactoryProvider?.invoke(source.headers, source.cacheKey)
            ?: buildDefaultPlaybackDataSourceFactory(context, source)
    }

    fun preparePlayer(
        source: BiliPlaybackSource,
        startPositionMs: Int = 0,
        shouldPlay: Boolean = playWhenReady
    ) {
        val textureView = textureViewRef ?: return
        releasePlayer(resetActiveSource = false)
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(buildDataSourceFactory(source)))
            .build()
        playerRef = player
        activeSource = source
        pendingSeekPositionMs = startPositionMs.coerceAtLeast(0)
        pendingPreparedPlayWhenReady = shouldPlay
        playbackCompleted = false
        durationMs = 0
        positionMs = pendingSeekPositionMs
        videoSize = IntSize.Zero
        videoRotation = 0
        isPrepared = false
        isPlaying = false
        isBuffering = true
        playbackError = null
        player.setVideoTextureView(textureView)
        player.setMediaItem(MediaItem.fromUri(source.url))
        if (pendingSeekPositionMs > 0) {
            player.seekTo(pendingSeekPositionMs.toLong())
        }
        player.playWhenReady = isActive && shouldPlay
        player.prepare()
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPanFling()
            pausePlayback()
            releasePlayer()
            textureViewRef = null
            isTextureAvailable = false
        }
    }

    val isVerticalPan = remember(videoSize, videoRotation) {
        isPortraitVideo(videoSize, videoRotation)
    }
    val panRangePx = remember(viewSize, videoSize, scaleMode, videoRotation, isVerticalPan) {
        calculatePanRange(viewSize, videoSize, scaleMode, videoRotation, isVerticalPan)
    }

    LaunchedEffect(panRangePx) {
        val clamped = panOffsetX.coerceIn(-panRangePx, panRangePx)
        if (clamped != panOffsetX) {
            panOffsetX = clamped
        }
        stopPanFling()
        panAnimator.snapTo(panOffsetX)
        updateTextureTransform(
            textureViewRef,
            viewSize,
            videoSize,
            scaleMode,
            videoRotation,
            panOffsetX,
            isVerticalPan
        )
    }

    LaunchedEffect(isVerticalPan) {
        if (panOffsetX != 0f) {
            stopPanFling()
            panOffsetX = 0f
            panAnimator.snapTo(0f)
        }
    }

    LaunchedEffect(scaleMode) {
        if (scaleMode != PlayerScaleMode.Expanded && panOffsetX != 0f) {
            stopPanFling()
            panOffsetX = 0f
            panAnimator.snapTo(0f)
        }
    }

    LaunchedEffect(panOffsetX, panRangePx, isVerticalPan, rotationAngle) {
        val rotationStep = normalizeRotationStep(rotationAngle)
        val backPanSign = panSignForBack(rotationStep, isVerticalPan)
        if (backPanSign == 0f) {
            onPanStateChange(0f, 0f)
        } else {
            onPanStateChange(panOffsetX * backPanSign, panRangePx)
        }
    }

    LaunchedEffect(
        scaleMode,
        viewSize,
        videoSize,
        textureViewRef,
        videoRotation,
        panOffsetX,
        isVerticalPan
    ) {
        updateTextureTransform(
            textureViewRef,
            viewSize,
            videoSize,
            scaleMode,
            videoRotation,
            panOffsetX,
            isVerticalPan
        )
    }

    LaunchedEffect(uiState.isLoading, uiState.initialSource) {
        if (!uiState.isLoading || hasConfiguredSource) {
            return@LaunchedEffect
        }
        playbackError = null
        durationMs = 0
        positionMs = 0
        videoSize = IntSize.Zero
        videoRotation = 0
        activeSource = null
        pendingSeekPositionMs = 0
        pendingPreparedPlayWhenReady = false
        playWhenReady = false
        autoPlayInitialized = false
        controlsVisible = true
        stopPanFling()
        panOffsetX = 0f
        panAnimator.snapTo(0f)
        releasePlayer()
    }

    LaunchedEffect(hasConfiguredSource, uiState.isLoading) {
        if (uiState.isLoading && !hasConfiguredSource) {
            autoPlayInitialized = false
            playWhenReady = false
            return@LaunchedEffect
        }
        if (hasConfiguredSource && !autoPlayInitialized) {
            playWhenReady = true
            pendingPreparedPlayWhenReady = true
            autoPlayInitialized = true
        }
    }

    DisposableEffect(scaleMode, view) {
        val activity = view.context.findActivity() ?: return@DisposableEffect onDispose { }
        val controller = WindowInsetsControllerCompat(activity.window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (scaleMode == PlayerScaleMode.Expanded) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
        onDispose { }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                pausePlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            pausePlayback(clearPlayWhenReady = false)
        } else {
            val player = playerRef
            if (player != null && isPrepared && playWhenReady && !player.isPlaying) {
                runCatching {
                    player.playWhenReady = true
                    player.play()
                    isPlaying = true
                    isBuffering = false
                }
            }
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

    DisposableEffect(playerRef) {
        val player = playerRef ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(isLoadingNow: Boolean) {
                isBuffering = isLoadingNow || player.playbackState == Player.STATE_BUFFERING
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        isPrepared = false
                        isPlaying = false
                        isBuffering = false
                    }
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        isPrepared = true
                        durationMs = player.duration
                            .takeIf { it > 0L }
                            ?.coerceAtMost(Int.MAX_VALUE.toLong())
                            ?.toInt()
                            ?: 0
                        positionMs = currentPlayerPosition()
                        pendingSeekPositionMs = 0
                        isBuffering = false
                        if (isActive && pendingPreparedPlayWhenReady) {
                            player.playWhenReady = true
                            player.play()
                        }
                        pendingPreparedPlayWhenReady = false
                    }
                    Player.STATE_ENDED -> {
                        positionMs = durationMs.takeIf { it > 0 } ?: currentPlayerPosition()
                        pendingSeekPositionMs = 0
                        isPlaying = false
                        isBuffering = false
                        playWhenReady = false
                        pendingPreparedPlayWhenReady = false
                        playbackCompleted = true
                        onPlaybackEnded()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
                if (isPlayingNow) {
                    isBuffering = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                positionMs = currentPlayerPosition()
                pendingSeekPositionMs = positionMs
                isPrepared = false
                isPlaying = false
                isBuffering = false
                pendingPreparedPlayWhenReady = false
                playWhenReady = false
                runCatching { player.playWhenReady = false }
                if (!playbackCompleted && (positionMs > 0 || durationMs > 0)) {
                    onPlaybackProgress(positionMs, durationMs, true)
                }
                playbackError = if (onPlaybackError()) null else "播放失败"
            }

            override fun onVideoSizeChanged(videoSizeNow: VideoSize) {
                videoSize = IntSize(videoSizeNow.width, videoSizeNow.height)
                videoRotation = ((videoSizeNow.unappliedRotationDegrees % 360) + 360) % 360
                updateTextureTransform(
                    textureViewRef,
                    viewSize,
                    videoSize,
                    scaleMode,
                    videoRotation,
                    panOffsetX,
                    isVerticalPan
                )
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(
        uiState.initialSource,
        uiState.isLoading,
        isTextureAvailable,
        activeSource
    ) {
        if (uiState.isLoading) {
            return@LaunchedEffect
        }
        if (!isTextureAvailable) {
            return@LaunchedEffect
        }
        val nextSource = uiState.initialSource ?: return@LaunchedEffect
        if (activeSource == nextSource && playerRef != null) {
            return@LaunchedEffect
        }
        preparePlayer(
            source = nextSource,
            startPositionMs = pendingSeekPositionMs.takeIf { it > 0 } ?: uiState.resumePositionMs,
            shouldPlay = pendingPreparedPlayWhenReady || playWhenReady
        )
    }

    LaunchedEffect(playerRef, isPrepared, isActive) {
        while (isActive) {
            val player = playerRef
            if (player != null && isPrepared) {
                positionMs = currentPlayerPosition()
                if (durationMs <= 0) {
                    val duration = player.duration
                    if (duration > 0L) {
                        durationMs = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    }
                }
                if (videoSize.width <= 0 || videoSize.height <= 0) {
                    val currentVideoSize = player.videoSize
                    val width = currentVideoSize.width
                    val height = currentVideoSize.height
                    if (width > 0 && height > 0) {
                        videoSize = IntSize(width, height)
                        videoRotation = ((currentVideoSize.unappliedRotationDegrees % 360) + 360) % 360
                        updateTextureTransform(
                            textureViewRef,
                            viewSize,
                            videoSize,
                            scaleMode,
                            videoRotation,
                            panOffsetX,
                            isVerticalPan
                        )
                    }
                }
                isPlaying = player.isPlaying
                if (!playbackCompleted && (positionMs > 0 || durationMs > 0)) {
                    onPlaybackProgress(positionMs, durationMs, false)
                }
            }
            delay(400)
        }
    }

    InstallDigitalCrownVolumeHandler(
        enabled = digitalCrownVolumeEnabled,
        showSystemUi = false,
        reverseDirection = true,
        supportsDigitalCrown = true,
        onVolumeAdjust = volumeState::adjustByDelta
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                rotationZ = animatedRotationAngle,
                transformOrigin = TransformOrigin.Center
            )
            .background(Color.Black)
    ) {
        val showPlayerSurface = activeSource != null || hasConfiguredSource || uiState.isLoading
        if (showPlayerSurface) {
            AndroidView(
                factory = {
                    TextureView(context).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                viewSize = IntSize(width, height)
                                isTextureAvailable = true
                                runCatching { playerRef?.setVideoTextureView(this@apply) }
                                updateTextureTransform(
                                    this@apply,
                                    viewSize,
                                    videoSize,
                                    scaleMode,
                                    videoRotation,
                                    panOffsetX,
                                    isVerticalPan
                                )
                                if (isActive && isPrepared && playWhenReady) {
                                    runCatching {
                                        playerRef?.playWhenReady = true
                                        playerRef?.play()
                                    }
                                }
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                viewSize = IntSize(width, height)
                                updateTextureTransform(
                                    this@apply,
                                    viewSize,
                                    videoSize,
                                    scaleMode,
                                    videoRotation,
                                    panOffsetX,
                                    isVerticalPan
                                )
                            }

                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                isTextureAvailable = false
                                pausePlayback(clearPlayWhenReady = false)
                                runCatching { playerRef?.clearVideoTextureView(this@apply) }
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                        }
                    }.also { textureViewRef = it }
                },
                update = { viewNow ->
                    textureViewRef = viewNow
                    val size = IntSize(viewNow.width, viewNow.height)
                    if (size.width > 0 && size.height > 0 && size != viewSize) {
                        viewSize = size
                        updateTextureTransform(
                            viewNow,
                            viewSize,
                            videoSize,
                            scaleMode,
                            videoRotation,
                            panOffsetX,
                            isVerticalPan
                        )
                    }
                    if (viewNow.isAvailable) {
                        isTextureAvailable = true
                        runCatching { playerRef?.setVideoTextureView(viewNow) }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isPrepared) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = {
                                if (isPrepared) {
                                    togglePlayback()
                                }
                            }
                        )
                    }
                    .draggable(
                        orientation = if (isVerticalPan) Orientation.Vertical else Orientation.Horizontal,
                        enabled = allowPan && panRangePx > 0f,
                        state = rememberDraggableState { delta ->
                            if (panRangePx <= 0f) return@rememberDraggableState
                            stopPanFling()
                            val next = (panOffsetX + delta).coerceIn(-panRangePx, panRangePx)
                            if (next != panOffsetX) {
                                panOffsetX = next
                                updateTextureTransform(
                                    textureViewRef,
                                    viewSize,
                                    videoSize,
                                    scaleMode,
                                    videoRotation,
                                    panOffsetX,
                                    isVerticalPan
                                )
                            }
                        },
                        onDragStarted = { stopPanFling() },
                        onDragStopped = { velocity ->
                            if (panRangePx <= 0f || velocity == 0f) return@draggable
                            stopPanFling()
                            panFlingJob.value = panScope.launch {
                                panAnimator.snapTo(panOffsetX)
                                panAnimator.animateDecay(velocity, panDecay) {
                                    val clamped = value.coerceIn(-panRangePx, panRangePx)
                                    if (clamped != panOffsetX) {
                                        panOffsetX = clamped
                                        updateTextureTransform(
                                            textureViewRef,
                                            viewSize,
                                            videoSize,
                                            scaleMode,
                                            videoRotation,
                                            panOffsetX,
                                            isVerticalPan
                                        )
                                    }
                                }
                            }
                        }
                    )
            )
        }

        val rawErrorText = playbackError ?: uiState.message
        val errorText = normalizeUserFacingMessage(
            context,
            rawErrorText
        )?.toString() ?: rawErrorText
        LaunchedEffect(errorText) {
            offlineToastMessageOrNull(context, errorText)?.let { message ->
                showAppToast(context, message)
            }
        }
        val showLoading = (uiState.isLoading && activeSource == null) ||
            (activeSource != null && !isPrepared) ||
            isBuffering
        if (showLoading && errorText.isNullOrBlank()) {
            WatchCircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (!errorText.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(safePadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    BiliPillButton(text = "重试", onClick = onRetry)
                    BiliPillButton(text = "浏览器打开", onClick = onOpenWeb)
                }
            }
        }

        PlayerVolumeOverlay(
            state = volumeState,
            modifier = Modifier.align(Alignment.Center)
        )

        if (errorText.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (controlsVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(horizontal = safePadding, vertical = spacing)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val scaleToggleAction = scaleMode.toggleAction()
                            PlayerIconButton(
                                icon = scaleToggleAction.icon,
                                contentDescription = scaleToggleAction.contentDescription,
                                size = controlSize,
                                iconSize = iconSize,
                                onClick = { scaleMode = scaleMode.next() }
                            )
                            PlayerIconButton(
                                icon = Icons.Filled.ScreenRotation,
                                contentDescription = "旋转",
                                size = controlSize,
                                iconSize = iconSize,
                                onClick = {
                                    rotationTargetDeg += 90f
                                    rotationAngle = (rotationAngle + 90f) % 360f
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = safePadding)
                            .padding(bottom = 40.dp)
                            .widthIn(max = 186.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        Text(
                            text = formatTime(positionMs),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = timeSize
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                            },
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(100))
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = timeSize
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = safePadding)
                            .padding(bottom = 0.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerSeekButton(
                            icon = Icons.Filled.FastRewind,
                            contentDescription = "后退4秒",
                            size = controlSize,
                            iconSize = iconSize,
                            enabled = isPrepared,
                            baseStepMs = 4_000,
                            direction = -1,
                            onSeek = { delta -> seekBy(playerRef, durationMs, delta) }
                        )
                        PlayerIconButton(
                            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            size = controlSize + spacing,
                            iconSize = iconSize + 4.dp,
                            enabled = isPrepared,
                            onClick = { togglePlayback() }
                        )
                        PlayerSeekButton(
                            icon = Icons.Filled.FastForward,
                            contentDescription = "前进4秒",
                            size = controlSize,
                            iconSize = iconSize,
                            enabled = isPrepared,
                            baseStepMs = 4_000,
                            direction = 1,
                            onSeek = { delta -> seekBy(playerRef, durationMs, delta) }
                        )
                    }
                }
            }
        }
    }
}

private fun buildDefaultPlaybackDataSourceFactory(
    context: Context,
    source: BiliPlaybackSource
): DataSource.Factory {
    val upstreamFactory = OkHttpDataSource.Factory(OkHttpClient()).apply {
        if (source.headers.isNotEmpty()) {
            setDefaultRequestProperties(source.headers)
        }
    }
    return DefaultDataSource.Factory(context, upstreamFactory)
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize).then(iconModifier)
        )
    }
}

private fun seekBy(player: ExoPlayer?, durationMs: Int, deltaMs: Int) {
    val target = player ?: return
    if (durationMs <= 0) return
    val next = (target.currentPosition + deltaMs).coerceIn(0L, durationMs.toLong())
    target.seekTo(next)
}

@Composable
private fun textSize(id: Int): TextUnit {
    return androidx.compose.ui.platform.LocalDensity.current.run {
        watchDimensionResource(id).toSp()
    }
}

private fun formatTime(ms: Int): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
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

private fun updateTextureTransform(
    textureView: TextureView?,
    viewSize: IntSize,
    videoSize: IntSize,
    scaleMode: PlayerScaleMode,
    videoRotation: Int,
    panOffsetX: Float,
    isVerticalPan: Boolean
) {
    val view = textureView ?: return
    if (viewSize.width <= 0 || viewSize.height <= 0 || videoSize.width <= 0 || videoSize.height <= 0) {
        return
    }
    val viewWidth = viewSize.width.toFloat()
    val viewHeight = viewSize.height.toFloat()
    val videoWidth = videoSize.width.toFloat()
    val videoHeight = videoSize.height.toFloat()
    if (videoWidth <= 0f || videoHeight <= 0f) return
    val (scaleX, scaleY) = calculatePlayerScale(
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        scaleMode = scaleMode
    )
    val centerX = viewWidth / 2f
    val centerY = viewHeight / 2f
    val contentWidth = viewWidth * scaleX
    val contentHeight = viewHeight * scaleY
    val rotated = videoRotation % 180 != 0
    val effectiveWidth = if (rotated) contentHeight else contentWidth
    val effectiveHeight = if (rotated) contentWidth else contentHeight
    val maxPan = if (isVerticalPan) {
        calculateVerticalPanRange(viewWidth, viewHeight, effectiveWidth, effectiveHeight)
    } else {
        calculateHorizontalPanRange(viewWidth, viewHeight, effectiveWidth, effectiveHeight)
    }
    val clampedOffset = panOffsetX.coerceIn(-maxPan, maxPan)
    val matrix = Matrix().apply {
        setScale(scaleX, scaleY, centerX, centerY)
        if (videoRotation != 0) {
            postRotate(videoRotation.toFloat(), centerX, centerY)
        }
        if (clampedOffset != 0f) {
            if (isVerticalPan) {
                postTranslate(0f, clampedOffset)
            } else {
                postTranslate(clampedOffset, 0f)
            }
        }
    }
    view.setTransform(matrix)
    view.invalidate()
}

private fun calculatePanRange(
    viewSize: IntSize,
    videoSize: IntSize,
    scaleMode: PlayerScaleMode,
    videoRotation: Int,
    isVerticalPan: Boolean
): Float {
    if (viewSize.width <= 0 || viewSize.height <= 0 || videoSize.width <= 0 || videoSize.height <= 0) {
        return 0f
    }
    val videoWidth = videoSize.width.toFloat()
    val videoHeight = videoSize.height.toFloat()
    if (videoWidth <= 0f || videoHeight <= 0f) return 0f
    val viewWidth = viewSize.width.toFloat()
    val viewHeight = viewSize.height.toFloat()
    val (scaleX, scaleY) = calculatePlayerScale(
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        scaleMode = scaleMode
    )
    val contentWidth = viewWidth * scaleX
    val contentHeight = viewHeight * scaleY
    val rotated = videoRotation % 180 != 0
    val effectiveWidth = if (rotated) contentHeight else contentWidth
    val effectiveHeight = if (rotated) contentWidth else contentHeight
    return if (isVerticalPan) {
        calculateVerticalPanRange(viewWidth, viewHeight, effectiveWidth, effectiveHeight)
    } else {
        calculateHorizontalPanRange(viewWidth, viewHeight, effectiveWidth, effectiveHeight)
    }
}

internal fun calculatePlayerScale(
    viewWidth: Float,
    viewHeight: Float,
    videoWidth: Float,
    videoHeight: Float,
    scaleMode: PlayerScaleMode
): PlayerScale {
    val standardScale = calculateStandardPlayerScale(
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        videoWidth = videoWidth,
        videoHeight = videoHeight
    )
    return when (scaleMode) {
        PlayerScaleMode.Standard -> standardScale
        PlayerScaleMode.Expanded -> calculateExpandedPlayerScale(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            videoWidth = videoWidth,
            videoHeight = videoHeight
        )
        PlayerScaleMode.Shrunk -> {
            val contentWidth = viewWidth * standardScale.scaleX
            val contentHeight = viewHeight * standardScale.scaleY
            val diagonal = sqrt(contentWidth * contentWidth + contentHeight * contentHeight)
            val shrinkFactor = if (diagonal > 0f) {
                (viewWidth / diagonal).coerceAtMost(1f)
            } else {
                1f
            }
            PlayerScale(
                scaleX = standardScale.scaleX * shrinkFactor,
                scaleY = standardScale.scaleY * shrinkFactor
            )
        }
    }
}

private fun calculateStandardPlayerScale(
    viewWidth: Float,
    viewHeight: Float,
    videoWidth: Float,
    videoHeight: Float
): PlayerScale {
    val viewAspect = viewWidth / viewHeight
    val videoAspect = videoWidth / videoHeight
    return if (videoAspect > viewAspect) {
        PlayerScale(scaleX = 1f, scaleY = viewAspect / videoAspect)
    } else {
        PlayerScale(scaleX = videoAspect / viewAspect, scaleY = 1f)
    }
}

private fun calculateExpandedPlayerScale(
    viewWidth: Float,
    viewHeight: Float,
    videoWidth: Float,
    videoHeight: Float
): PlayerScale {
    val viewAspect = viewWidth / viewHeight
    val videoAspect = videoWidth / videoHeight
    return if (videoAspect > viewAspect) {
        PlayerScale(scaleX = videoAspect / viewAspect, scaleY = 1f)
    } else {
        PlayerScale(scaleX = 1f, scaleY = viewAspect / videoAspect)
    }
}

private fun calculateHorizontalPanRange(
    viewWidth: Float,
    viewHeight: Float,
    contentWidth: Float,
    contentHeight: Float
): Float {
    val radius = min(viewWidth, viewHeight) / 2f
    if (contentWidth <= 0f || contentHeight <= 0f) return 0f
    val halfHeight = min(contentHeight / 2f, radius)
    val circleHalfWidth = sqrt((radius * radius - halfHeight * halfHeight).coerceAtLeast(0f))
    return (contentWidth / 2f - circleHalfWidth).coerceAtLeast(0f)
}

private fun calculateVerticalPanRange(
    viewWidth: Float,
    viewHeight: Float,
    contentWidth: Float,
    contentHeight: Float
): Float {
    val radius = min(viewWidth, viewHeight) / 2f
    if (contentWidth <= 0f || contentHeight <= 0f) return 0f
    val halfWidth = min(contentWidth / 2f, radius)
    val circleHalfHeight = sqrt((radius * radius - halfWidth * halfWidth).coerceAtLeast(0f))
    return (contentHeight / 2f - circleHalfHeight).coerceAtLeast(0f)
}

private fun isPortraitVideo(
    videoSize: IntSize,
    videoRotation: Int
): Boolean {
    if (videoSize.width <= 0 || videoSize.height <= 0) return false
    val rotated = videoRotation % 180 != 0
    val effectiveWidth = if (rotated) videoSize.height else videoSize.width
    val effectiveHeight = if (rotated) videoSize.width else videoSize.height
    return effectiveHeight > effectiveWidth
}

private fun normalizeRotationStep(rotationAngle: Float): Int {
    val normalized = ((rotationAngle % 360f) + 360f) % 360f
    return ((normalized / 90f).roundToInt()) % 4
}

private fun panSignForBack(rotationStep: Int, isVerticalPan: Boolean): Float {
    return if (isVerticalPan) {
        when (rotationStep) {
            1 -> -1f
            3 -> 1f
            else -> 0f
        }
    } else {
        when (rotationStep) {
            0 -> 1f
            2 -> -1f
            else -> 0f
        }
    }
}

private fun PlayerScaleMode.toggleAction(): PlayerScaleToggleAction {
    return when (this) {
        PlayerScaleMode.Standard -> PlayerScaleToggleAction(
            icon = Icons.Filled.Fullscreen,
            contentDescription = "放大"
        )
        PlayerScaleMode.Expanded -> PlayerScaleToggleAction(
            icon = Icons.Filled.Fullscreen,
            contentDescription = "缩小"
        )
        PlayerScaleMode.Shrunk -> PlayerScaleToggleAction(
            icon = Icons.Filled.FullscreenExit,
            contentDescription = "标准"
        )
    }
}

@Composable
private fun PlayerSeekButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    baseStepMs: Int,
    direction: Int,
    onSeek: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val viewConfig = LocalViewConfiguration.current
    var holdJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    DisposableEffect(Unit) {
        onDispose { holdJob?.cancel() }
    }

    fun startHold() {
        holdJob?.cancel()
        holdJob = scope.launch {
            val start = SystemClock.uptimeMillis()
            while (isActive) {
                val elapsedSec = (SystemClock.uptimeMillis() - start) / 1000f
                val multiplier = 1.35f.pow(elapsedSec)
                val step = (baseStepMs * multiplier).roundToInt().coerceAtMost(60_000)
                onSeek(step * direction)
                delay(200)
            }
        }
    }

    fun stopHold() {
        holdJob?.cancel()
        holdJob = null
    }

    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.5f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        var longPressStarted = false
                        val longPressJob = scope.launch {
                            delay(viewConfig.longPressTimeoutMillis.toLong())
                            longPressStarted = true
                            startHold()
                        }
                        val released = tryAwaitRelease()
                        longPressJob.cancel()
                        if (longPressStarted) {
                            stopHold()
                        } else if (released) {
                            onSeek(baseStepMs * direction)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize)
        )
    }
}
