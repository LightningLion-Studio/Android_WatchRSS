package com.lightningstudio.watchrss.ui.screen.douyin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.text.TextPaint
import android.view.TextureView
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceKind
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.debug.DouyinPlaybackDebugController
import com.lightningstudio.watchrss.ui.components.ToastMessage
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.components.WatchIconButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.PlayerVolumeOverlay
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.rememberPullRefreshEnabled
import com.lightningstudio.watchrss.ui.components.rememberPlayerVolumeState
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownPagerHandler
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownVolumeHandler
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.hasValidatedInternetConnection
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedUiState
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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

private enum class DouyinPlayerSlotKey {
    Primary,
    Secondary;

    fun other(): DouyinPlayerSlotKey {
        return when (this) {
            Primary -> Secondary
            Secondary -> Primary
        }
    }
}

private data class DouyinPlayerScale(
    val scaleX: Float,
    val scaleY: Float
)

private class DouyinPlayerSlotState(
    val key: DouyinPlayerSlotKey,
    val player: ExoPlayer
) {
    var textureView by mutableStateOf<TextureView?>(null)
    var viewSize by mutableStateOf(IntSize.Zero)
    var boundAwemeId by mutableStateOf<String?>(null)
    var mediaUri by mutableStateOf<String?>(null)
    var remoteResolvedAtMs by mutableStateOf(0L)
    var preparedSourceKey by mutableStateOf<String?>(null)
    var hasRenderedFirstFrame by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var hasError by mutableStateOf(false)
    var videoSize by mutableStateOf(IntSize.Zero)
    var videoRotation by mutableIntStateOf(0)
}

@Composable
fun DouyinImmersiveScreen(
    uiState: DouyinFeedUiState,
    onRefresh: () -> Unit,
    onPageSettled: (Int) -> Unit,
    onEnterFlow: () -> Unit,
    onItemLongPress: (DouyinStreamItem) -> Unit,
    onRequestPlaybackRefresh: (String, DouyinPlaybackSourceKind) -> Unit,
    onMessageShown: () -> Unit,
    onHeaderClick: () -> Unit
) {
    var entryStartIndex by rememberSaveable {
        mutableIntStateOf(
            resolveDouyinEntryStartIndex(
                currentPage = uiState.currentPage,
                itemCount = uiState.items.size
            )
        )
    }
    LaunchedEffect(uiState.showTitlePage, uiState.currentPage, uiState.items.size) {
        if (uiState.showTitlePage || entryStartIndex > uiState.items.lastIndex.coerceAtLeast(0)) {
            entryStartIndex = resolveDouyinEntryStartIndex(
                currentPage = uiState.currentPage,
                itemCount = uiState.items.size
            )
        }
    }
    val pageCount = resolveDouyinPageCount(
        itemCount = uiState.items.size,
        entryStartIndex = entryStartIndex
    )
    val initialPage = if (uiState.showTitlePage) {
        0
    } else {
        resolveDouyinPagerPage(
            currentPage = uiState.currentPage,
            entryStartIndex = entryStartIndex,
            pageCount = pageCount
        )
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount.coerceAtLeast(1) }
    )
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapPositionalThreshold = DOUYIN_PAGER_SNAP_POSITIONAL_THRESHOLD
    )
    var pendingAutoSkipPage by remember { mutableIntStateOf(-1) }
    var autoSkipMessage by remember { mutableStateOf<String?>(null) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var scaleMode by rememberSaveable { mutableStateOf(DouyinPlayerScaleMode.Standard) }
    val volumeState = rememberPlayerVolumeState()
    var foregroundSlotKey by rememberSaveable { mutableStateOf(DouyinPlayerSlotKey.Primary) }
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val poisonedAwemeIds by DouyinPlaybackDebugController.poisonedAwemeIds.collectAsState()
    val activeItemIndex = resolveDouyinItemIndexForPagerPage(
        pagerPage = pagerState.currentPage,
        entryStartIndex = entryStartIndex
    )
    val activeItem = if (uiState.showTitlePage) {
        null
    } else {
        uiState.items.getOrNull(activeItemIndex ?: -1)
    }
    val activePage = pagerState.currentPage.takeIf { activeItem != null } ?: -1
    val hasNextActiveItem = (activeItemIndex ?: -1) < uiState.items.lastIndex
    var injectedFailureNonce by remember(activeItem?.awemeId) { mutableStateOf(0L) }
    val activeIsPoisoned = activeItem?.awemeId?.let(poisonedAwemeIds::contains) == true
    val activeLocalUri = if (activeIsPoisoned) {
        null
    } else {
        activeItem?.awemeId
            ?.let(uiState.localPlayPaths::get)
            ?.takeIf { it.isNotBlank() }
            ?.let { "file://$it" }
    }
    val activeRemoteUri = when {
        activeItem == null -> null
        activeIsPoisoned -> buildDouyinInjectedFailureUri(
            awemeId = activeItem.awemeId,
            sequence = injectedFailureNonce
        )
        else -> activeItem.playUrl.takeIf { it.isNotBlank() }
    }
    val standbyItemIndex = resolveDouyinStandbyItemIndex(
        activeItemIndex = activeItemIndex,
        itemCount = uiState.items.size
    )
    val standbyItem = uiState.items.getOrNull(standbyItemIndex ?: -1)
    val standbyLocalUri = standbyItem?.awemeId
        ?.let(uiState.localPlayPaths::get)
        ?.takeIf { it.isNotBlank() }
        ?.let { "file://$it" }
    val standbyRemoteUri = standbyItem?.playUrl?.takeIf { it.isNotBlank() }
    val standbyMediaUri = standbyLocalUri ?: standbyRemoteUri
    val standbyRemoteResolvedAtMs = standbyItem?.playUrlResolvedAtMs ?: 0L
    var activeMediaUri by remember(activeItem?.awemeId) { mutableStateOf(activeLocalUri ?: activeRemoteUri) }
    var activeRemoteResolvedAtMs by remember(activeItem?.awemeId) {
        mutableStateOf(activeItem?.playUrlResolvedAtMs ?: 0L)
    }
    var activePrepareAttemptNonce by remember(activeItem?.awemeId) { mutableIntStateOf(0) }
    var activeRetryCount by remember(activeItem?.awemeId) { mutableIntStateOf(0) }
    var activePausedByGesture by remember(activeItem?.awemeId) { mutableStateOf(false) }
    val headersSignature = remember(uiState.playHeaders) {
        uiState.playHeaders.entries.sortedBy { it.key }.joinToString(";")
    }
    val primarySlot = remember(context, headersSignature) {
        DouyinPlayerSlotState(
            key = DouyinPlayerSlotKey.Primary,
            player = buildDouyinExoPlayer(
                context = context,
                headers = uiState.playHeaders,
                lightweight = false
            )
        )
    }
    val secondarySlot = remember(context, headersSignature) {
        DouyinPlayerSlotState(
            key = DouyinPlayerSlotKey.Secondary,
            player = buildDouyinExoPlayer(
                context = context,
                headers = uiState.playHeaders,
                lightweight = true
            )
        )
    }
    fun slotFor(key: DouyinPlayerSlotKey): DouyinPlayerSlotState {
        return when (key) {
            DouyinPlayerSlotKey.Primary -> primarySlot
            DouyinPlayerSlotKey.Secondary -> secondarySlot
        }
    }
    val foregroundSlot = slotFor(foregroundSlotKey)
    val standbySlot = slotFor(foregroundSlotKey.other())
    val activeSlotMatchesCurrentItem = activeItem?.awemeId != null &&
        foregroundSlot.boundAwemeId == activeItem.awemeId
    val activeIsBuffering = activeSlotMatchesCurrentItem && foregroundSlot.isBuffering
    val activeIsPlaying = activeSlotMatchesCurrentItem && foregroundSlot.isPlaying
    val activeHasError = activeSlotMatchesCurrentItem && foregroundSlot.hasError
    val shouldKeepScreenOn = activeItem != null &&
        !activeHasError &&
        (activeIsPlaying || activeIsBuffering)
    fun stopSlotPlayback(slot: DouyinPlayerSlotState) {
        slot.player.playWhenReady = false
        slot.player.pause()
        slot.isPlaying = false
        slot.isBuffering = false
    }

    fun clearSlotBinding(slot: DouyinPlayerSlotState) {
        slot.player.playWhenReady = false
        runCatching {
            slot.player.stop()
            slot.player.clearMediaItems()
        }
        slot.boundAwemeId = null
        slot.mediaUri = null
        slot.remoteResolvedAtMs = 0L
        slot.preparedSourceKey = null
        slot.hasRenderedFirstFrame = false
        slot.isBuffering = false
        slot.isPlaying = false
        slot.hasError = false
        slot.videoSize = IntSize.Zero
        slot.videoRotation = 0
    }

    fun bindSlotTarget(
        slot: DouyinPlayerSlotState,
        awemeId: String,
        mediaUri: String,
        remoteResolvedAtMs: Long,
        prepareKey: String,
        shouldPlay: Boolean
    ) {
        val targetUri = mediaUri.trim()
        if (slot.boundAwemeId == awemeId && slot.preparedSourceKey == prepareKey) {
            slot.player.playWhenReady = shouldPlay
            if (shouldPlay) {
                slot.player.play()
            } else {
                stopSlotPlayback(slot)
            }
            return
        }
        slot.boundAwemeId = awemeId
        slot.mediaUri = targetUri
        slot.remoteResolvedAtMs = remoteResolvedAtMs
        slot.preparedSourceKey = prepareKey
        slot.hasRenderedFirstFrame = false
        slot.isBuffering = true
        slot.isPlaying = false
        slot.hasError = false
        slot.videoSize = IntSize.Zero
        slot.videoRotation = 0
        slot.player.playWhenReady = shouldPlay
        slot.player.setMediaItem(MediaItem.fromUri(targetUri))
        slot.player.prepare()
        if (shouldPlay) {
            slot.player.play()
        }
    }

    fun stopForegroundPlayback() {
        stopSlotPlayback(slotFor(foregroundSlotKey))
    }

    fun scheduleAutoSkipCurrentPage() {
        if (activePage == pagerState.currentPage && activePage > 0) {
            pendingAutoSkipPage = activePage
        }
    }

    fun requestActivePlaybackRefresh(resetRetryCount: Boolean = false) {
        val item = activeItem ?: return
        if (activeIsPoisoned) {
            if (resetRetryCount) {
                activeRetryCount = 0
            }
            val nextFailureSequence = injectedFailureNonce + 1L
            injectedFailureNonce = nextFailureSequence
            foregroundSlot.hasError = false
            foregroundSlot.hasRenderedFirstFrame = false
            activePausedByGesture = false
            activeMediaUri = buildDouyinInjectedFailureUri(
                awemeId = item.awemeId,
                sequence = nextFailureSequence
            )
            AppLogger.d(
                TAG,
                "retry poisoned video with injected uri awemeId=${item.awemeId} sequence=$nextFailureSequence"
            )
            return
        }
        val sourceKind = currentDouyinPlaybackSourceKind(activeMediaUri)
        val targetUri = activeMediaUri?.trim()?.takeIf { it.isNotBlank() }
            ?: activeLocalUri
            ?: activeRemoteUri
        if (targetUri.isNullOrBlank()) {
            foregroundSlot.hasError = false
            activePausedByGesture = false
            clearSlotBinding(foregroundSlot)
            scheduleAutoSkipCurrentPage()
            return
        }
        if (resetRetryCount) {
            activeRetryCount = 0
        }
        foregroundSlot.hasError = false
        foregroundSlot.hasRenderedFirstFrame = false
        activePausedByGesture = false
        activePrepareAttemptNonce += 1
        if (sourceKind == DouyinPlaybackSourceKind.LOCAL && !activeRemoteUri.isNullOrBlank()) {
            activeMediaUri = activeRemoteUri
            activeRemoteResolvedAtMs = item.playUrlResolvedAtMs
        }
        onRequestPlaybackRefresh(item.awemeId, sourceKind)
    }

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

    LaunchedEffect(pagerState, entryStartIndex) {
        snapshotFlow {
            resolveDouyinSettledPageOrNull(
                isScrollInProgress = pagerState.isScrollInProgress,
                pagerPage = pagerState.currentPage,
                entryStartIndex = entryStartIndex
            )
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { settledPage ->
                onPageSettled(settledPage)
            }
    }

    LaunchedEffect(pagerState.currentPage, uiState.items, uiState.showTitlePage, entryStartIndex) {
        val activeItemIndex = resolveDouyinItemIndexForPagerPage(
            pagerPage = pagerState.currentPage,
            entryStartIndex = entryStartIndex
        )
        val activeAwemeId = if (!uiState.showTitlePage) {
            uiState.items.getOrNull(activeItemIndex ?: -1)?.awemeId
        } else {
            null
        }
        val nextAwemeId = when {
            uiState.items.isEmpty() -> null
            pagerState.currentPage <= 0 -> uiState.items.getOrNull(entryStartIndex)?.awemeId
            activeItemIndex == null -> null
            else -> uiState.items.getOrNull(activeItemIndex + 1)?.awemeId
        }
        DouyinPlaybackDebugController.updatePlaybackContext(
            activeAwemeId = activeAwemeId,
            nextAwemeId = nextAwemeId
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            DouyinPlaybackDebugController.updatePlaybackContext(
                activeAwemeId = null,
                nextAwemeId = null
            )
        }
    }

    LaunchedEffect(uiState.currentPage, uiState.showTitlePage, pageCount, entryStartIndex) {
        val target = when {
            uiState.showTitlePage || uiState.items.isEmpty() -> 0
            else -> resolveDouyinPagerPage(
                currentPage = uiState.currentPage,
                entryStartIndex = entryStartIndex,
                pageCount = pageCount
            )
        }
        if (target != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pendingAutoSkipPage, pageCount) {
        val failingPage = pendingAutoSkipPage
        if (failingPage <= 0) return@LaunchedEffect
        pendingAutoSkipPage = -1
        if (failingPage != pagerState.currentPage) return@LaunchedEffect

        val nextPage = resolveDouyinAutoSkipTargetPage(
            failingPage = failingPage,
            pageCount = pageCount
        ) ?: return@LaunchedEffect

        autoSkipMessage = DOUYIN_AUTO_SKIP_MESSAGE
        pagerState.scrollToPage(nextPage)
    }

    LaunchedEffect(autoSkipMessage) {
        if (autoSkipMessage.isNullOrBlank()) return@LaunchedEffect
        delay(DOUYIN_AUTO_SKIP_MESSAGE_DURATION_MS)
        autoSkipMessage = null
    }

    LaunchedEffect(activeItem?.awemeId, activeLocalUri, activeRemoteUri, activeItem?.playUrlResolvedAtMs) {
        if (activeItem == null) {
            activeMediaUri = null
            activeRemoteResolvedAtMs = 0L
            activePrepareAttemptNonce = 0
            activePausedByGesture = false
            clearSlotBinding(primarySlot)
            clearSlotBinding(secondarySlot)
            return@LaunchedEffect
        }
        val resolvedState = resolveDouyinPlaybackState(
            currentUri = activeMediaUri,
            currentRemoteResolvedAtMs = activeRemoteResolvedAtMs,
            localUri = activeLocalUri,
            remoteUri = activeRemoteUri,
            remoteResolvedAtMs = activeItem.playUrlResolvedAtMs
        )
        if (resolvedState.mediaUri != activeMediaUri || resolvedState.remoteResolvedAtMs != activeRemoteResolvedAtMs) {
            activeMediaUri = resolvedState.mediaUri
            activeRemoteResolvedAtMs = resolvedState.remoteResolvedAtMs
            activePrepareAttemptNonce = 0
            foregroundSlot.hasError = false
            foregroundSlot.hasRenderedFirstFrame = false
        }
    }

    val activePrepareKey = remember(activeMediaUri, activeRemoteResolvedAtMs, activePrepareAttemptNonce) {
        buildDouyinPlaybackPrepareKey(
            mediaUri = activeMediaUri,
            remoteResolvedAtMs = activeRemoteResolvedAtMs,
            attemptNonce = activePrepareAttemptNonce
        )
    }
    val standbyPrepareKey = remember(standbyMediaUri, standbyRemoteResolvedAtMs) {
        buildDouyinPlaybackPrepareKey(
            mediaUri = standbyMediaUri,
            remoteResolvedAtMs = standbyRemoteResolvedAtMs
        )
    }
    val latestActiveMediaUri = rememberUpdatedState(activeMediaUri)
    val latestForegroundSlotKey = rememberUpdatedState(foregroundSlotKey)
    val latestActiveAwemeId = rememberUpdatedState(activeItem?.awemeId)
    val latestShowTitlePage = rememberUpdatedState(uiState.showTitlePage)
    val latestHasNextActiveItem = rememberUpdatedState(hasNextActiveItem)
    val latestRetryCount = rememberUpdatedState(activeRetryCount)
    val latestRequestActivePlaybackRefresh = rememberUpdatedState(
        newValue = { resetRetryCount: Boolean -> requestActivePlaybackRefresh(resetRetryCount) }
    )
    DisposableEffect(primarySlot.player) {
        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                primarySlot.isBuffering = isLoading
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                primarySlot.isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    primarySlot.isPlaying = false
                }
                if (playbackState == Player.STATE_READY) {
                    primarySlot.hasError = false
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                primarySlot.isPlaying = isPlayingNow
            }

            override fun onRenderedFirstFrame() {
                primarySlot.hasRenderedFirstFrame = true
            }

            override fun onVideoSizeChanged(videoSizeNow: androidx.media3.common.VideoSize) {
                primarySlot.videoSize = IntSize(videoSizeNow.width, videoSizeNow.height)
                primarySlot.videoRotation = videoSizeNow.unappliedRotationDegrees
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                primarySlot.hasError = true
                primarySlot.isBuffering = false
                primarySlot.isPlaying = false
                val currentAwemeId = latestActiveAwemeId.value
                val isForegroundCurrentSlot = latestForegroundSlotKey.value == primarySlot.key &&
                    !latestShowTitlePage.value &&
                    !currentAwemeId.isNullOrBlank() &&
                    currentAwemeId == primarySlot.boundAwemeId
                AppLogger.w(
                    TAG,
                    "playback error slot=${primarySlot.key} awemeId=${primarySlot.boundAwemeId}, uri=${primarySlot.mediaUri}",
                    error
                )
                if (!isForegroundCurrentSlot) {
                    return
                }
                if (isDouyinInjectedFailureUri(latestActiveMediaUri.value)) {
                    if (latestRetryCount.value < DOUYIN_MAX_AUTO_RETRY_COUNT) {
                        activeRetryCount += 1
                        latestRequestActivePlaybackRefresh.value(false)
                    } else {
                        primarySlot.hasError = false
                        activePausedByGesture = false
                        stopForegroundPlayback()
                        scheduleAutoSkipCurrentPage()
                    }
                    return
                }
                when (
                    resolveDouyinPlaybackFailureAction(
                        retryCount = latestRetryCount.value,
                        maxAutoRetryCount = DOUYIN_MAX_AUTO_RETRY_COUNT,
                        hasValidatedInternetConnection = hasValidatedInternetConnection(context),
                        hasNextItem = latestHasNextActiveItem.value
                    )
                ) {
                    DouyinPlaybackFailureAction.Retry -> {
                        activeRetryCount += 1
                        latestRequestActivePlaybackRefresh.value(false)
                    }

                    DouyinPlaybackFailureAction.AutoSkip -> {
                        primarySlot.hasError = false
                        activePausedByGesture = false
                        stopForegroundPlayback()
                        scheduleAutoSkipCurrentPage()
                    }
                }
            }
        }
        primarySlot.player.addListener(listener)
        onDispose {
            primarySlot.player.removeListener(listener)
        }
    }

    DisposableEffect(secondarySlot.player) {
        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                secondarySlot.isBuffering = isLoading
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                secondarySlot.isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    secondarySlot.isPlaying = false
                }
                if (playbackState == Player.STATE_READY) {
                    secondarySlot.hasError = false
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                secondarySlot.isPlaying = isPlayingNow
            }

            override fun onRenderedFirstFrame() {
                secondarySlot.hasRenderedFirstFrame = true
            }

            override fun onVideoSizeChanged(videoSizeNow: androidx.media3.common.VideoSize) {
                secondarySlot.videoSize = IntSize(videoSizeNow.width, videoSizeNow.height)
                secondarySlot.videoRotation = videoSizeNow.unappliedRotationDegrees
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                secondarySlot.hasError = true
                secondarySlot.isBuffering = false
                secondarySlot.isPlaying = false
                val currentAwemeId = latestActiveAwemeId.value
                val isForegroundCurrentSlot = latestForegroundSlotKey.value == secondarySlot.key &&
                    !latestShowTitlePage.value &&
                    !currentAwemeId.isNullOrBlank() &&
                    currentAwemeId == secondarySlot.boundAwemeId
                AppLogger.w(
                    TAG,
                    "playback error slot=${secondarySlot.key} awemeId=${secondarySlot.boundAwemeId}, uri=${secondarySlot.mediaUri}",
                    error
                )
                if (!isForegroundCurrentSlot) {
                    return
                }
                if (isDouyinInjectedFailureUri(latestActiveMediaUri.value)) {
                    if (latestRetryCount.value < DOUYIN_MAX_AUTO_RETRY_COUNT) {
                        activeRetryCount += 1
                        latestRequestActivePlaybackRefresh.value(false)
                    } else {
                        secondarySlot.hasError = false
                        activePausedByGesture = false
                        stopForegroundPlayback()
                        scheduleAutoSkipCurrentPage()
                    }
                    return
                }
                when (
                    resolveDouyinPlaybackFailureAction(
                        retryCount = latestRetryCount.value,
                        maxAutoRetryCount = DOUYIN_MAX_AUTO_RETRY_COUNT,
                        hasValidatedInternetConnection = hasValidatedInternetConnection(context),
                        hasNextItem = latestHasNextActiveItem.value
                    )
                ) {
                    DouyinPlaybackFailureAction.Retry -> {
                        activeRetryCount += 1
                        latestRequestActivePlaybackRefresh.value(false)
                    }

                    DouyinPlaybackFailureAction.AutoSkip -> {
                        secondarySlot.hasError = false
                        activePausedByGesture = false
                        stopForegroundPlayback()
                        scheduleAutoSkipCurrentPage()
                    }
                }
            }
        }
        secondarySlot.player.addListener(listener)
        onDispose {
            secondarySlot.player.removeListener(listener)
        }
    }

    DisposableEffect(primarySlot.player, secondarySlot.player) {
        onDispose {
            primarySlot.player.release()
            secondarySlot.player.release()
        }
    }

    DisposableEffect(lifecycleOwner, foregroundSlotKey, primarySlot.player, secondarySlot.player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                stopForegroundPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(shouldKeepScreenOn, view) {
        val window = view.context.findActivity()?.window
        if (shouldKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(
        activeItem?.awemeId,
        activePrepareKey,
        standbyItem?.awemeId,
        standbyPrepareKey,
        activePausedByGesture,
        activeHasError,
        foregroundSlotKey
    ) {
        if (activeItem == null || activePrepareKey.isNullOrBlank() || activeMediaUri.isNullOrBlank()) {
            clearSlotBinding(primarySlot)
            clearSlotBinding(secondarySlot)
            return@LaunchedEffect
        }
        val promoteStandby = shouldPromoteDouyinStandbySlot(
            standbyAwemeId = standbySlot.boundAwemeId,
            targetAwemeId = activeItem.awemeId,
            standbyPrepareKey = standbySlot.preparedSourceKey,
            targetPrepareKey = activePrepareKey,
            hasRenderedFirstFrame = standbySlot.hasRenderedFirstFrame,
            hasError = standbySlot.hasError
        )
        val desiredForegroundKey = if (promoteStandby) {
            foregroundSlotKey.other()
        } else {
            foregroundSlotKey
        }
        if (desiredForegroundKey != foregroundSlotKey) {
            foregroundSlotKey = desiredForegroundKey
        }
        val desiredForegroundSlot = slotFor(desiredForegroundKey)
        val desiredStandbySlot = slotFor(desiredForegroundKey.other())
        val currentMediaUri = activeMediaUri ?: return@LaunchedEffect
        bindSlotTarget(
            slot = desiredForegroundSlot,
            awemeId = activeItem.awemeId,
            mediaUri = currentMediaUri,
            remoteResolvedAtMs = activeRemoteResolvedAtMs,
            prepareKey = activePrepareKey,
            shouldPlay = !activePausedByGesture && !activeHasError
        )
        if (
            standbyItem != null &&
            standbyPrepareKey != null &&
            !standbyMediaUri.isNullOrBlank() &&
            standbyItem.awemeId != activeItem.awemeId
        ) {
            val nextMediaUri = standbyMediaUri ?: return@LaunchedEffect
            bindSlotTarget(
                slot = desiredStandbySlot,
                awemeId = standbyItem.awemeId,
                mediaUri = nextMediaUri,
                remoteResolvedAtMs = standbyRemoteResolvedAtMs,
                prepareKey = standbyPrepareKey,
                shouldPlay = false
            )
        } else {
            clearSlotBinding(desiredStandbySlot)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = pagerFlingBehavior,
            beyondViewportPageCount = 1,
            userScrollEnabled = pageCount > 1
        ) { page ->
            if (page == 0) {
                DouyinTitlePage(
                    hasItems = uiState.items.isNotEmpty(),
                    isRefreshing = uiState.isLoading,
                    onRefresh = onRefresh,
                    onEnterFlow = onEnterFlow,
                    onHeaderClick = onHeaderClick
                )
            } else {
                val itemIndex = resolveDouyinItemIndexForPagerPage(
                    pagerPage = page,
                    entryStartIndex = entryStartIndex
                )
                val item = uiState.items.getOrNull(itemIndex ?: -1)
                if (item == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                    return@VerticalPager
                }
                val pageSlot = when {
                    foregroundSlot.boundAwemeId == item.awemeId -> foregroundSlot
                    standbySlot.boundAwemeId == item.awemeId -> standbySlot
                    else -> null
                }
                DouyinVideoPage(
                    item = item,
                    isActive = pagerState.currentPage == page,
                    videoSlot = pageSlot,
                    controlsVisible = controlsVisible,
                    scaleMode = scaleMode,
                    isBuffering = activeIsBuffering,
                    isVideoVisible = pageSlot?.hasRenderedFirstFrame == true && !pageSlot.hasError,
                    hasError = activeHasError,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onToggleScaleMode = { scaleMode = scaleMode.next() },
                    onLongPress = { onItemLongPress(item) },
                    onDoubleTap = {
                        if (activeHasError) {
                            requestActivePlaybackRefresh(true)
                        } else if (foregroundSlot.player.isPlaying) {
                            activePausedByGesture = true
                            stopForegroundPlayback()
                        } else {
                            activePausedByGesture = false
                            if (activeItem?.awemeId == item.awemeId && !activeHasError) {
                                foregroundSlot.player.playWhenReady = true
                                foregroundSlot.player.play()
                            }
                        }
                    }
                )
            }
        }

        if (uiState.isLoading && uiState.items.isEmpty()) {
            WatchCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        when {
            !autoSkipMessage.isNullOrBlank() -> {
                ToastMessage(
                    text = autoSkipMessage!!,
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                )
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
    hasItems: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onEnterFlow: () -> Unit,
    onHeaderClick: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val subtitleSpacing = watchDimensionResource(R.dimen.hey_distance_2dp)
    val buttonBottom = watchDimensionResource(R.dimen.hey_distance_12dp)
    val buttonSize = watchDimensionResource(R.dimen.hey_button_height)
    val listState = rememberLazyListState()
    val canRefresh = rememberPullRefreshEnabled(listState)

    PullRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        indicatorPadding = safePadding,
        canRefresh = canRefresh
    ) {
        WatchSurface(pureBlack = true) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                state = listState
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxSize()
                            // Keep the visual safe area without adding scroll range that
                            // would otherwise absorb upward fling before the pager sees it.
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
                                text = if (isRefreshing) {
                                    "刷新中..."
                                } else if (hasItems) {
                                    "下拉刷新，向上进入短视频流"
                                } else {
                                    "暂无内容，下拉刷新或点箭头重试"
                                },
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
                                .clickable {
                                    if (hasItems) {
                                        onEnterFlow()
                                    } else {
                                        onRefresh()
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowUpward,
                                    contentDescription = if (hasItems) {
                                        "向上进入视频流"
                                    } else {
                                        "刷新抖音内容"
                                    },
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(watchDimensionResource(R.dimen.hey_distance_16dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DouyinTextureSlotHost(
    slot: DouyinPlayerSlotState,
    scaleMode: DouyinPlayerScaleMode,
    visible: Boolean
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            TextureView(context).apply {
                alpha = if (visible) 1f else 0f
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        slot.textureView = this@apply
                        slot.viewSize = IntSize(width, height)
                        runCatching { slot.player.setVideoTextureView(this@apply) }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        slot.viewSize = IntSize(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        if (slot.textureView === this@apply) {
                            slot.textureView = null
                        }
                        slot.viewSize = IntSize.Zero
                        runCatching { slot.player.clearVideoTextureView(this@apply) }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        },
        update = { textureView ->
            slot.textureView = textureView
            textureView.alpha = if (visible) 1f else 0f
            if (textureView.isAvailable) {
                runCatching { slot.player.setVideoTextureView(textureView) }
            }
            val size = IntSize(textureView.width, textureView.height)
            if (size.width > 0 && size.height > 0 && size != slot.viewSize) {
                slot.viewSize = size
            }
            updateDouyinTextureTransform(
                textureView = textureView,
                viewSize = slot.viewSize,
                videoSize = slot.videoSize,
                scaleMode = scaleMode,
                videoRotation = slot.videoRotation
            )
        }
    )
}

@Composable
private fun DouyinVideoPage(
    item: DouyinStreamItem,
    isActive: Boolean,
    videoSlot: DouyinPlayerSlotState?,
    controlsVisible: Boolean,
    scaleMode: DouyinPlayerScaleMode,
    isBuffering: Boolean,
    isVideoVisible: Boolean,
    hasError: Boolean,
    onToggleControls: () -> Unit,
    onToggleScaleMode: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val density = LocalDensity.current
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val topPadding = watchDimensionResource(R.dimen.hey_distance_6dp)
    val bottomPadding = watchDimensionResource(R.dimen.hey_distance_8dp)
    val controlsSize = watchDimensionResource(R.dimen.hey_button_height)
    val controlsIconSize = watchDimensionResource(R.dimen.hey_listitem_widget_size)
    val titleFontSize = with(density) { watchDimensionResource(R.dimen.feed_card_title_text_size).toSp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.awemeId, controlsVisible, isActive, hasError) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = {
                        if (isActive) {
                            onDoubleTap()
                        }
                    }
                )
            }
    ) {
        if (videoSlot != null) {
            DouyinTextureSlotHost(
                slot = videoSlot,
                scaleMode = scaleMode,
                visible = isVideoVisible
            )
        }

        if (isBuffering && !hasError && isActive) {
            WatchCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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

private fun buildDouyinExoPlayer(
    context: Context,
    headers: Map<String, String>,
    lightweight: Boolean
): ExoPlayer {
    val requestFactory = OkHttpDataSource.Factory(OkHttpClient()).apply {
        setDefaultRequestProperties(headers)
    }
    val loadControl = if (lightweight) {
        DefaultLoadControl.Builder()
            .setTargetBufferBytes(24 * 1024 * 1024)
            .setBufferDurationsMs(2_000, 6_000, 250, 500)
            .build()
    } else {
        DefaultLoadControl.Builder()
            .setTargetBufferBytes(128 * 1024 * 1024)
            .setBufferDurationsMs(10_000, 45_000, 1_000, 2_000)
            .build()
    }
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(requestFactory))
        .setLoadControl(loadControl)
        .build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
}

private fun updateDouyinTextureTransform(
    textureView: TextureView?,
    viewSize: IntSize,
    videoSize: IntSize,
    scaleMode: DouyinPlayerScaleMode,
    videoRotation: Int
) {
    val view = textureView ?: return
    if (viewSize.width <= 0 || viewSize.height <= 0 || videoSize.width <= 0 || videoSize.height <= 0) {
        return
    }
    val rotated = videoRotation % 180 != 0
    val effectiveVideoWidth = if (rotated) videoSize.height.toFloat() else videoSize.width.toFloat()
    val effectiveVideoHeight = if (rotated) videoSize.width.toFloat() else videoSize.height.toFloat()
    val scale = calculateDouyinTexturePlayerScale(
        viewWidth = viewSize.width.toFloat(),
        viewHeight = viewSize.height.toFloat(),
        videoWidth = effectiveVideoWidth,
        videoHeight = effectiveVideoHeight,
        scaleMode = scaleMode
    )
    val centerX = viewSize.width / 2f
    val centerY = viewSize.height / 2f
    val matrix = Matrix().apply {
        setScale(scale.scaleX, scale.scaleY, centerX, centerY)
        if (videoRotation != 0) {
            postRotate(videoRotation.toFloat(), centerX, centerY)
        }
    }
    view.setTransform(matrix)
    view.invalidate()
}

private fun calculateDouyinTexturePlayerScale(
    viewWidth: Float,
    viewHeight: Float,
    videoWidth: Float,
    videoHeight: Float,
    scaleMode: DouyinPlayerScaleMode
): DouyinPlayerScale {
    val standardScale = calculateDouyinStandardPlayerScale(
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        videoWidth = videoWidth,
        videoHeight = videoHeight
    )
    return when (scaleMode) {
        DouyinPlayerScaleMode.Standard -> standardScale
        DouyinPlayerScaleMode.Expanded -> calculateDouyinExpandedPlayerScale(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            videoWidth = videoWidth,
            videoHeight = videoHeight
        )
        DouyinPlayerScaleMode.Shrunk -> {
            val shrinkFactor = calculateDouyinPlayerShrinkFactor(
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                videoWidth = videoWidth,
                videoHeight = videoHeight
            )
            DouyinPlayerScale(
                scaleX = standardScale.scaleX * shrinkFactor,
                scaleY = standardScale.scaleY * shrinkFactor
            )
        }
    }
}

private fun calculateDouyinStandardPlayerScale(
    viewWidth: Float,
    viewHeight: Float,
    videoWidth: Float,
    videoHeight: Float
): DouyinPlayerScale {
    val viewAspect = viewWidth / viewHeight
    val videoAspect = videoWidth / videoHeight
    return if (videoAspect > viewAspect) {
        DouyinPlayerScale(scaleX = 1f, scaleY = viewAspect / videoAspect)
    } else {
        DouyinPlayerScale(scaleX = videoAspect / viewAspect, scaleY = 1f)
    }
}

private fun calculateDouyinExpandedPlayerScale(
    viewWidth: Float,
    viewHeight: Float,
    videoWidth: Float,
    videoHeight: Float
): DouyinPlayerScale {
    val viewAspect = viewWidth / viewHeight
    val videoAspect = videoWidth / videoHeight
    return if (videoAspect > viewAspect) {
        DouyinPlayerScale(scaleX = videoAspect / viewAspect, scaleY = 1f)
    } else {
        DouyinPlayerScale(scaleX = 1f, scaleY = viewAspect / videoAspect)
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

private fun buildDouyinInjectedFailureUri(awemeId: String, sequence: Long): String {
    return "$DOUYIN_INJECTED_FAILURE_URI_PREFIX$awemeId?sequence=$sequence"
}

private fun isDouyinInjectedFailureUri(mediaUri: String?): Boolean {
    return mediaUri?.startsWith(DOUYIN_INJECTED_FAILURE_URI_PREFIX) == true
}

internal fun resolveDouyinAutoSkipTargetPage(
    failingPage: Int,
    pageCount: Int
): Int? {
    if (failingPage <= 0 || pageCount <= 1) return null
    return if (failingPage < pageCount - 1) {
        failingPage + 1
    } else {
        0
    }
}

internal fun resolveDouyinEntryStartIndex(currentPage: Int, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    if (currentPage <= 1) return 0
    return (currentPage - 1).coerceIn(0, itemCount - 1)
}

internal fun resolveDouyinPageCount(itemCount: Int, entryStartIndex: Int): Int {
    val visibleVideoCount = (itemCount - entryStartIndex).coerceAtLeast(0)
    return visibleVideoCount + 1
}

internal fun resolveDouyinPagerPage(
    currentPage: Int,
    entryStartIndex: Int,
    pageCount: Int
): Int {
    if (currentPage <= 0 || pageCount <= 1) return 0
    return (currentPage - entryStartIndex).coerceIn(1, pageCount - 1)
}

internal fun resolveDouyinAbsolutePage(
    pagerPage: Int,
    entryStartIndex: Int
): Int {
    if (pagerPage <= 0) return 0
    return entryStartIndex + pagerPage
}

internal fun resolveDouyinSettledPage(
    pagerPage: Int,
    entryStartIndex: Int
): Int {
    if (pagerPage <= 0) return 0
    return resolveDouyinAbsolutePage(
        pagerPage = pagerPage,
        entryStartIndex = entryStartIndex
    )
}

internal fun resolveDouyinSettledPageOrNull(
    isScrollInProgress: Boolean,
    pagerPage: Int,
    entryStartIndex: Int
): Int? {
    if (isScrollInProgress) return null
    return resolveDouyinSettledPage(
        pagerPage = pagerPage,
        entryStartIndex = entryStartIndex
    )
}

internal fun resolveDouyinItemIndexForPagerPage(
    pagerPage: Int,
    entryStartIndex: Int
): Int? {
    if (pagerPage <= 0) return null
    return entryStartIndex + pagerPage - 1
}

internal fun resolveDouyinStandbyItemIndex(activeItemIndex: Int?, itemCount: Int): Int? {
    val currentIndex = activeItemIndex ?: return null
    val nextIndex = currentIndex + 1
    return nextIndex.takeIf { it in 0 until itemCount }
}

internal fun shouldPromoteDouyinStandbySlot(
    standbyAwemeId: String?,
    targetAwemeId: String?,
    standbyPrepareKey: String?,
    targetPrepareKey: String?,
    hasRenderedFirstFrame: Boolean,
    hasError: Boolean
): Boolean {
    if (standbyAwemeId.isNullOrBlank() || targetAwemeId.isNullOrBlank()) return false
    if (standbyPrepareKey.isNullOrBlank() || targetPrepareKey.isNullOrBlank()) return false
    if (hasError || !hasRenderedFirstFrame) return false
    return standbyAwemeId == targetAwemeId && standbyPrepareKey == targetPrepareKey
}

private const val TITLE_ORIGINAL_FIRST_LINE_RATIO = 0.68f
private const val TITLE_ORIGINAL_SECOND_LINE_RATIO = 0.82f
private const val DOUYIN_MAX_AUTO_RETRY_COUNT = 1
private const val DOUYIN_AUTO_SKIP_MESSAGE = "当前视频无法播放\n已为您自动跳过"
private const val DOUYIN_AUTO_SKIP_MESSAGE_DURATION_MS = 2_000L
private const val DOUYIN_PAGER_SNAP_POSITIONAL_THRESHOLD = 0.18f
private const val DOUYIN_INJECTED_FAILURE_URI_PREFIX = "watchrss-debug://douyin/force-fail/"
private const val TAG = "DouyinImmersive"
