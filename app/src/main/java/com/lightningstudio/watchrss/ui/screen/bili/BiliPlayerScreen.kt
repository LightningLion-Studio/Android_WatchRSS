package com.lightningstudio.watchrss.ui.screen.bili

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
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
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.PlayerVolumeOverlay
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownVolumeHandler
import com.lightningstudio.watchrss.ui.components.rememberPlayerVolumeState
import com.lightningstudio.watchrss.ui.util.normalizeUserFacingMessage
import com.lightningstudio.watchrss.ui.util.offlineToastMessageOrNull
import com.lightningstudio.watchrss.ui.util.showAppToast
import com.lightningstudio.watchrss.ui.viewmodel.BiliPlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onPanStateChange: (Float, Float) -> Unit,
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
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var surfaceRef by remember { mutableStateOf<Surface?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0) }
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
    var lastUrl by remember { mutableStateOf<String?>(null) }
    var playWhenReady by remember { mutableStateOf(!uiState.playUrl.isNullOrBlank()) }
    var panOffsetX by remember { mutableStateOf(0f) }
    val panAnimator = remember { Animatable(0f) }
    val panDecay = remember { exponentialDecay<Float>() }
    val panScope = rememberCoroutineScope()
    val panFlingJob = remember { mutableStateOf<Job?>(null) }
    val volumeState = rememberPlayerVolumeState()
    // Keep-screen-on is a window-level flag, not a per-composable flag.
    // In pager-based players there may be multiple BiliPlayerScreen instances
    // composed at the same time for preloading/adjacent pages, but they still
    // point to the same Activity window. If an off-screen page is allowed to
    // participate in this effect, its disposal can clear the flag that the
    // currently visible page still relies on, causing the screen to sleep while
    // video is actively playing. `isActive` gates the effect so only the page
    // that owns user focus can add or clear FLAG_KEEP_SCREEN_ON.
    val shouldKeepScreenOn = isActive &&
        !uiState.playUrl.isNullOrBlank() &&
        playbackError.isNullOrBlank() &&
        (isPlaying || isBuffering || (!isPrepared && surfaceRef != null))

    fun stopPanFling() {
        panFlingJob.value?.cancel()
        panFlingJob.value = null
    }

    fun pausePlayback(clearPlayWhenReady: Boolean = true) {
        if (clearPlayWhenReady) {
            playWhenReady = false
        }
        val player = mediaPlayerRef
        if (player != null) {
            runCatching {
                if (player.isPlaying) {
                    player.pause()
                }
                val current = player.currentPosition
                if (current >= 0) {
                    positionMs = current
                }
            }
        }
        isPlaying = false
        isBuffering = false
    }

    fun togglePlayback() {
        val player = mediaPlayerRef ?: return
        runCatching {
            if (player.isPlaying) {
                player.pause()
                playWhenReady = false
                isPlaying = false
                isBuffering = false
                val current = player.currentPosition
                if (current >= 0) {
                    positionMs = current
                }
            } else if (isPrepared) {
                player.start()
                playWhenReady = true
                isPlaying = true
                isBuffering = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPanFling()
            pausePlayback()
            runCatching { mediaPlayerRef?.setSurface(null) }
            mediaPlayerRef?.release()
            mediaPlayerRef = null
            surfaceRef?.release()
            surfaceRef = null
            textureViewRef = null
        }
    }

    LaunchedEffect(uiState.playUrl) {
        playbackError = null
        isPrepared = false
        isPlaying = false
        durationMs = 0
        positionMs = 0
        videoSize = IntSize.Zero
        videoRotation = 0
        lastUrl = null
        playWhenReady = !uiState.playUrl.isNullOrBlank()
        controlsVisible = true
        stopPanFling()
        panOffsetX = 0f
        panAnimator.snapTo(0f)
        isBuffering = false
        mediaPlayerRef?.reset()
    }

    LaunchedEffect(uiState.playUrl, uiState.headers) {
        val targetUrl = uiState.playUrl
        if (targetUrl.isNullOrBlank()) {
            videoRotation = 0
            return@LaunchedEffect
        }
        val headers = uiState.headers ?: emptyMap()
        val rotation = withContext(Dispatchers.IO) {
            readVideoRotation(targetUrl, headers)
        }
        videoRotation = rotation
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

    // This observer handles host lifecycle transitions such as app backgrounding.
    // It is intentionally separate from pager focus management below:
    // - Lifecycle ON_PAUSE means the whole Activity is leaving the foreground,
    //   so playback should stop unconditionally.
    // - Pager page switches do not trigger ON_PAUSE, because the Activity stays
    //   resumed while only the visible page changes.
    // Keeping these concerns separate makes it clear that "background pause"
    // and "active page ownership" solve different failure modes.
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

    // Activity ON_PAUSE only covers app/background transitions. In pager-based
    // players a page can lose focus while the host Activity stays resumed, so
    // we also need an explicit ownership handoff when this composable is no
    // longer the active page.
    //
    // The important subtlety here is that we pause without clearing
    // playWhenReady:
    // - If the page was auto-playing and the user swipes away, it should stop
    //   immediately because it no longer owns playback focus.
    // - If the user swipes back, that same page may resume automatically.
    // - If the user manually paused earlier, playWhenReady is already false, so
    //   the page stays paused across pager switches.
    //
    // Together with the ON_PAUSE observer above, this prevents two classes of
    // bugs:
    // 1. audio/video continuing from an off-screen pager page;
    // 2. an off-screen page finishing prepareAsync() and auto-starting while
    //    another page is currently visible.
    LaunchedEffect(isActive) {
        if (!isActive) {
            pausePlayback(clearPlayWhenReady = false)
        } else {
            val player = mediaPlayerRef
            if (player != null && isPrepared && playWhenReady && !player.isPlaying) {
                runCatching {
                    player.start()
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
            // Only the active page is allowed to clear the window flag.
            // Without this guard, a neighboring pager item that leaves the
            // composition can accidentally disable keep-screen-on for the page
            // that is still visible and playing, because both pages share the
            // same Activity window underneath.
            if (isActive) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    fun prepareMediaPlayer(surface: Surface, targetUrl: String) {
        val headers = uiState.headers ?: emptyMap()
        val player = mediaPlayerRef ?: MediaPlayer().also { mediaPlayerRef = it }
        isPrepared = false
        isPlaying = false
        isBuffering = true
        playbackError = null
        player.reset()
        player.setOnPreparedListener { mp ->
            isPrepared = true
            isBuffering = false
            durationMs = mp.duration.coerceAtLeast(0)
            videoSize = IntSize(mp.videoWidth, mp.videoHeight)
            updateTextureTransform(
                textureViewRef,
                viewSize,
                videoSize,
                scaleMode,
                videoRotation,
                panOffsetX,
                isVerticalPan
            )
            // prepareAsync() may complete after pager focus has already moved to
            // another page. Requiring both isActive and playWhenReady avoids an
            // off-screen page auto-starting just because it finished preparing.
            if (isActive && playWhenReady) {
                mp.start()
                isPlaying = true
            } else {
                isPlaying = false
            }
        }
        player.setOnVideoSizeChangedListener { _, width, height ->
            videoSize = IntSize(width, height)
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
        player.setOnCompletionListener {
            isPlaying = false
            isBuffering = false
            playWhenReady = false
        }
        player.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
                MediaPlayer.MEDIA_INFO_BUFFERING_END,
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isBuffering = false
            }
            false
        }
        player.setOnErrorListener { _, _, _ ->
            playbackError = "播放失败"
            isBuffering = false
            isPlaying = false
            playWhenReady = false
            true
        }
        player.setSurface(surface)
        try {
            player.setDataSource(context, Uri.parse(targetUrl), headers)
            player.prepareAsync()
        } catch (e: Exception) {
            playbackError = "播放失败"
            isBuffering = false
            isPlaying = false
        }
    }

    LaunchedEffect(uiState.playUrl, uiState.headers, uiState.isLoading, surfaceRef) {
        if (uiState.isLoading) {
            return@LaunchedEffect
        }
        val targetUrl = uiState.playUrl
        val surface = surfaceRef
        if (!targetUrl.isNullOrBlank() && surface != null && (targetUrl != lastUrl || !isPrepared)) {
            if (targetUrl != lastUrl || !playbackError.isNullOrBlank()) {
                playWhenReady = true
            }
            lastUrl = targetUrl
            prepareMediaPlayer(surface, targetUrl)
        }
    }

    LaunchedEffect(mediaPlayerRef, isPrepared) {
        while (isActive) {
            val player = mediaPlayerRef
            if (player != null && isPrepared) {
                val current = player.currentPosition
                if (current >= 0) positionMs = current
                if (durationMs <= 0) {
                    val duration = player.duration
                    if (duration > 0) durationMs = duration
                }
                if (videoSize.width <= 0 || videoSize.height <= 0) {
                    val width = player.videoWidth
                    val height = player.videoHeight
                    if (width > 0 && height > 0) {
                        videoSize = IntSize(width, height)
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
        if (!uiState.playUrl.isNullOrBlank()) {
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
                                surfaceRef?.release()
                                surfaceRef = Surface(surfaceTexture)
                                runCatching { mediaPlayerRef?.setSurface(surfaceRef) }
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
                                pausePlayback()
                                runCatching { mediaPlayerRef?.setSurface(null) }
                                surfaceRef?.release()
                                surfaceRef = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                        }
                    }.also { textureViewRef = it }
                },
                update = { view ->
                    textureViewRef = view
                    val size = IntSize(view.width, view.height)
                    if (size.width > 0 && size.height > 0 && size != viewSize) {
                        viewSize = size
                        updateTextureTransform(
                            view,
                            viewSize,
                            videoSize,
                            scaleMode,
                            videoRotation,
                            panOffsetX,
                            isVerticalPan
                        )
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

        val errorText = normalizeUserFacingMessage(
            context,
            playbackError ?: uiState.message
        )?.toString() ?: (playbackError ?: uiState.message)
        LaunchedEffect(errorText) {
            offlineToastMessageOrNull(context, errorText)?.let { message ->
                showAppToast(context, message)
            }
        }
        val showLoading = uiState.isLoading ||
            (!isPrepared && !uiState.playUrl.isNullOrBlank()) ||
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
                            onSeek = { delta -> seekBy(mediaPlayerRef, durationMs, delta) }
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
                            onSeek = { delta -> seekBy(mediaPlayerRef, durationMs, delta) }
                        )
                    }
                }
            }
        }
    }
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

private fun seekBy(player: MediaPlayer?, durationMs: Int, deltaMs: Int) {
    val target = player ?: return
    if (durationMs <= 0) return
    val next = (target.currentPosition + deltaMs).coerceIn(0, durationMs)
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

private fun readVideoRotation(
    url: String,
    headers: Map<String, String>
): Int {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(url, headers)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?.let { rotation -> ((rotation % 360) + 360) % 360 }
            ?: 0
    } catch (_: Exception) {
        0
    } finally {
        retriever.release()
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
