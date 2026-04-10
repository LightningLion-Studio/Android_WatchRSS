package com.lightningstudio.watchrss.ui.screen.douyin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.text.TextPaint
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.douyin.DOUYIN_PLAYBACK_PREVIEW_ENTRY_LIMIT
import com.lightningstudio.watchrss.data.douyin.DouyinCodecRuntimePolicy
import com.lightningstudio.watchrss.data.douyin.DouyinCodecSupport
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewCache
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceKind
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.douyin.effectiveDouyinVideoCodecPreference
import com.lightningstudio.watchrss.data.douyin.resolveDouyinLookaheadItemIndices
import com.lightningstudio.watchrss.data.douyin.selectPreferredVariant
import com.lightningstudio.watchrss.data.settings.DouyinVideoCodecPreference
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import kotlin.math.abs
import coil.compose.AsyncImage
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoCodec
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoVariant
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

internal enum class DouyinPlayerSlotKey {
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

private data class DouyinPreparedSlotTarget(
    val awemeId: String,
    val mediaUri: String,
    val remoteResolvedAtMs: Long,
    val prepareKey: String,
    val codec: DouyinVideoCodec,
    val trackAutoHevcAttempt: Boolean
)

private data class DouyinResolvedPlaybackTarget(
    val mediaUri: String,
    val codec: DouyinVideoCodec,
    val trackAutoHevcAttempt: Boolean
)

internal class DouyinPlayerSlotState(
    val key: DouyinPlayerSlotKey,
    val player: ExoPlayer
) {
    var textureView by mutableStateOf<TextureView?>(null)
    var attachedTextureView: TextureView? = null
    var viewSize by mutableStateOf(IntSize.Zero)
    var boundAwemeId by mutableStateOf<String?>(null)
    var mediaUri by mutableStateOf<String?>(null)
    var remoteResolvedAtMs by mutableStateOf(0L)
    var preparedSourceKey by mutableStateOf<String?>(null)
    var boundCodec by mutableStateOf(DouyinVideoCodec.UNKNOWN)
    var isReady by mutableStateOf(false)
    var hasRenderedFirstFrame by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var hasError by mutableStateOf(false)
    var videoSize by mutableStateOf(IntSize.Zero)
    var videoRotation by mutableIntStateOf(0)
    var loggedPlaybackStartKey by mutableStateOf<String?>(null)
    var trackedAutoHevcAttemptKey by mutableStateOf<String?>(null)
    var trackedAutoHevcFailureKey by mutableStateOf<String?>(null)
    var inFlightPrewarmKey by mutableStateOf<String?>(null)
}

internal data class DouyinPlayerPoolSession(
    val headersSignature: String,
    val slots: List<DouyinPlayerSlotState>
)

private data class DouyinPlayerPoolEntry(
    val session: DouyinPlayerPoolSession,
    var refCount: Int
)

private object DouyinPlayerPool {
    private val lock = Any()
    private val entriesBySignature = linkedMapOf<String, DouyinPlayerPoolEntry>()

    fun acquire(
        context: Context,
        headers: Map<String, String>
    ): DouyinPlayerPoolSession {
        val headersSignature = buildDouyinPlayerHeadersSignature(headers)
        synchronized(lock) {
            entriesBySignature[headersSignature]?.let { entry ->
                entry.refCount += 1
                entry.session.slots.forEach(::syncDouyinPlayerSlotState)
                AppLogger.d(
                    TAG,
                    "reuse pooled players signature=$headersSignature refCount=${entry.refCount}"
                )
                return entry.session
            }
        }

        val createdSession = DouyinPlayerPoolSession(
            headersSignature = headersSignature,
            slots = listOf(
                DouyinPlayerSlotState(
                    key = DouyinPlayerSlotKey.Primary,
                    player = buildDouyinExoPlayer(
                        context = context.applicationContext,
                        headers = headers,
                        lightweight = true
                    )
                ),
                DouyinPlayerSlotState(
                    key = DouyinPlayerSlotKey.Secondary,
                    player = buildDouyinExoPlayer(
                        context = context.applicationContext,
                        headers = headers,
                        lightweight = true
                    )
                )
            )
        )
        synchronized(lock) {
            entriesBySignature[headersSignature] = DouyinPlayerPoolEntry(
                session = createdSession,
                refCount = 1
            )
        }
        AppLogger.d(TAG, "create pooled players signature=$headersSignature refCount=1")
        return createdSession
    }

    fun release(releasedSession: DouyinPlayerPoolSession) {
        val shouldDispose = synchronized(lock) {
            val entry = entriesBySignature[releasedSession.headersSignature]
            if (entry?.session !== releasedSession) {
                null
            } else {
                entry.refCount -= 1
                if (entry.refCount <= 0) {
                    entriesBySignature.remove(releasedSession.headersSignature)
                    0
                } else {
                    entry.refCount
                }
            }
        }
        when (shouldDispose) {
            null -> {
                AppLogger.w(
                    TAG,
                    "release unknown pooled session signature=${releasedSession.headersSignature}"
                )
                disposeSession(releasedSession)
            }

            0 -> {
                disposeSession(releasedSession)
                AppLogger.d(
                    TAG,
                    "release pooled players signature=${releasedSession.headersSignature} refCount=0"
                )
            }

            else -> {
                AppLogger.d(
                    TAG,
                    "release pooled players signature=${releasedSession.headersSignature} refCount=$shouldDispose"
                )
            }
        }
    }

    private fun disposeSession(target: DouyinPlayerPoolSession) {
        target.slots.forEach(::disposeDouyinPlayerSlot)
    }
}

@Composable
internal fun rememberDouyinPlayerPoolSession(
    headers: Map<String, String>,
    enabled: Boolean
): DouyinPlayerPoolSession? {
    val context = LocalContext.current
    val headersSignature = remember(headers) {
        buildDouyinPlayerHeadersSignature(headers)
    }
    val playerSession = remember(context.applicationContext, headersSignature, enabled) {
        if (!enabled) {
            null
        } else {
            DouyinPlayerPool.acquire(
                context = context.applicationContext,
                headers = headers
            )
        }
    }
    DisposableEffect(playerSession) {
        onDispose {
            playerSession?.let(DouyinPlayerPool::release)
        }
    }
    return playerSession
}

private fun syncDouyinPlayerSlotState(slot: DouyinPlayerSlotState) {
    val playbackState = slot.player.playbackState
    slot.isReady = playbackState == Player.STATE_READY
    slot.isBuffering = playbackState == Player.STATE_BUFFERING
    slot.isPlaying = slot.player.isPlaying
    slot.hasError = slot.player.playerError != null
}

private fun disposeDouyinPlayerSlot(slot: DouyinPlayerSlotState) {
    runCatching { slot.player.release() }
    slot.textureView = null
    slot.attachedTextureView = null
    slot.viewSize = IntSize.Zero
    slot.boundAwemeId = null
    slot.mediaUri = null
    slot.remoteResolvedAtMs = 0L
    slot.preparedSourceKey = null
    slot.boundCodec = DouyinVideoCodec.UNKNOWN
    slot.isReady = false
    slot.hasRenderedFirstFrame = false
    slot.isBuffering = false
    slot.isPlaying = false
    slot.hasError = false
    slot.videoSize = IntSize.Zero
    slot.videoRotation = 0
    slot.loggedPlaybackStartKey = null
    slot.trackedAutoHevcAttemptKey = null
    slot.trackedAutoHevcFailureKey = null
    slot.inFlightPrewarmKey = null
}

private fun syncDouyinTextureAttachment(
    slot: DouyinPlayerSlotState,
    textureView: TextureView?,
    attachToPlayer: Boolean
) {
    val attachedTextureView = slot.attachedTextureView
    if (!attachToPlayer) {
        if (attachedTextureView != null) {
            runCatching { slot.player.clearVideoTextureView(attachedTextureView) }
            slot.attachedTextureView = null
        }
        return
    }
    if (textureView == null) return
    if (attachedTextureView === textureView) return
    attachedTextureView?.let { view ->
        runCatching { slot.player.clearVideoTextureView(view) }
    }
    runCatching { slot.player.setVideoTextureView(textureView) }
    slot.attachedTextureView = textureView
}

private fun DouyinStreamItem.findVariantByUrl(playUrl: String): DouyinVideoVariant? {
    val normalized = playUrl.trim()
    if (normalized.isEmpty()) return null
    return variants.firstOrNull { it.playUrl.trim() == normalized }
}

private fun resolveDouyinPlaybackTarget(
    item: DouyinStreamItem,
    preference: DouyinVideoCodecPreference,
    h265Supported: Boolean,
    overridePlayUrl: String?
): DouyinResolvedPlaybackTarget? {
    val normalizedOverride = overridePlayUrl?.trim()?.takeIf { it.isNotEmpty() }
    if (normalizedOverride != null) {
        return DouyinResolvedPlaybackTarget(
            mediaUri = normalizedOverride,
            codec = item.findVariantByUrl(normalizedOverride)?.codec ?: DouyinVideoCodec.UNKNOWN,
            trackAutoHevcAttempt = false
        )
    }
    val effectivePreference = effectiveDouyinVideoCodecPreference(preference)
    val selectedVariant = selectPreferredVariant(
        variants = item.variants,
        preference = preference,
        h265Supported = h265Supported
    )
    val targetUri = selectedVariant?.playUrl?.trim()?.takeIf { it.isNotEmpty() }
        ?: item.playUrl.trim().takeIf { it.isNotEmpty() }
        ?: return null
    return DouyinResolvedPlaybackTarget(
        mediaUri = targetUri,
        codec = selectedVariant?.codec ?: item.findVariantByUrl(targetUri)?.codec ?: DouyinVideoCodec.UNKNOWN,
        trackAutoHevcAttempt = preference == DouyinVideoCodecPreference.AUTO &&
            effectivePreference == DouyinVideoCodecPreference.AUTO &&
            selectedVariant?.codec == DouyinVideoCodec.H265
    )
}

private fun resolveDouyinCachedLocalPlayUri(
    awemeId: String?,
    localPlayPaths: Map<String, String>
): String? {
    val normalizedAwemeId = awemeId?.trim().orEmpty()
    if (normalizedAwemeId.isEmpty()) return null
    return localPlayPaths[normalizedAwemeId]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { "file://$it" }
}

private fun resolveDouyinH264FallbackTarget(
    item: DouyinStreamItem,
    currentUri: String?,
    h265Supported: Boolean
): DouyinResolvedPlaybackTarget? {
    val current = currentUri?.trim().orEmpty()
    val selectedVariant = selectPreferredVariant(
        variants = item.variants,
        preference = DouyinVideoCodecPreference.H264,
        h265Supported = h265Supported
    ) ?: return null
    val targetUri = selectedVariant.playUrl.trim()
    if (targetUri.isEmpty() || targetUri == current || selectedVariant.codec == DouyinVideoCodec.H265) {
        return null
    }
    return DouyinResolvedPlaybackTarget(
        mediaUri = targetUri,
        codec = selectedVariant.codec,
        trackAutoHevcAttempt = false
    )
}

private fun isLikelyDouyinCodecFailure(error: PlaybackException): Boolean {
    val summary = buildString {
        append(error.errorCodeName)
        append(' ')
        append(error.message.orEmpty())
        append(' ')
        append(error.cause?.message.orEmpty())
    }.lowercase()
    return summary.contains("decoder") ||
        summary.contains("codec") ||
        summary.contains("configure") ||
        summary.contains("format_unsupported") ||
        summary.contains("decoding")
}

@Composable
internal fun DouyinImmersiveScreen(
    playerSession: DouyinPlayerPoolSession,
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
    val h265Supported = remember { DouyinCodecSupport.isH265Supported() }
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
    var settledPagerPage by rememberSaveable { mutableIntStateOf(initialPage) }
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
    val playbackAnchorPagerPage = resolveDouyinPlaybackAnchorPagerPage(
        isScrollInProgress = pagerState.isScrollInProgress,
        pagerPage = pagerState.currentPage,
        settledPagerPage = settledPagerPage
    )
    val activeItemIndex = resolveDouyinItemIndexForPagerPage(
        pagerPage = playbackAnchorPagerPage,
        entryStartIndex = entryStartIndex
    )
    val activeItem = if (uiState.showTitlePage) {
        null
    } else {
        uiState.items.getOrNull(activeItemIndex ?: -1)
    }
    val preparedItemIndex = when {
        uiState.items.isEmpty() -> null
        uiState.showTitlePage -> entryStartIndex.coerceIn(0, uiState.items.lastIndex)
        else -> activeItemIndex
    }
    val preparedItem = uiState.items.getOrNull(preparedItemIndex ?: -1)
    val activePage = playbackAnchorPagerPage.takeIf { activeItem != null } ?: -1
    val useImmediateEntryPlayback = shouldUseDouyinImmediateEntryPlayback(
        activePage = activePage,
        activeItemIndex = activeItemIndex,
        entryStartIndex = entryStartIndex
    )
    val hasNextActiveItem = (activeItemIndex ?: -1) < uiState.items.lastIndex
    var injectedFailureNonce by remember(activeItem?.awemeId) { mutableStateOf(0L) }
    var runtimePlaybackOverrides by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var codecPolicyVersion by remember { mutableIntStateOf(0) }
    val activeIsPoisoned = activeItem?.awemeId?.let(poisonedAwemeIds::contains) == true
    val preparedIsPoisoned = preparedItem?.awemeId?.let(poisonedAwemeIds::contains) == true
    val preparedLocalUri = resolveDouyinCachedLocalPlayUri(
        awemeId = preparedItem?.awemeId,
        localPlayPaths = uiState.localPlayPaths
    )
    val preparedPlaybackOverrideUri = preparedItem?.awemeId?.let(runtimePlaybackOverrides::get)
    val preparedPlaybackTarget = when {
        preparedItem == null -> null
        preparedIsPoisoned -> DouyinResolvedPlaybackTarget(
            mediaUri = buildDouyinInjectedFailureUri(
                awemeId = preparedItem.awemeId,
                sequence = injectedFailureNonce
            ),
            codec = DouyinVideoCodec.UNKNOWN,
            trackAutoHevcAttempt = false
        )
        else -> {
            codecPolicyVersion
            resolveDouyinPlaybackTarget(
                item = preparedItem,
                preference = uiState.codecPreference,
                h265Supported = h265Supported,
                overridePlayUrl = preparedPlaybackOverrideUri ?: preparedLocalUri
            )
        }
    }
    var recentVisitedAwemeIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    fun recordRecentVisitedAwemeId(awemeId: String?) {
        val normalizedAwemeId = awemeId?.trim().orEmpty()
        if (normalizedAwemeId.isEmpty()) return
        recentVisitedAwemeIds = buildList {
            add(normalizedAwemeId)
            recentVisitedAwemeIds
                .asSequence()
                .filterNot { it == normalizedAwemeId }
                .take(DOUYIN_PLAYER_RECENT_HISTORY_SIZE - 1)
                .forEach(::add)
        }
    }
    val preparedBackgroundItemIndices = resolveDouyinPreparedBackgroundItemIndices(
        activeItemIndex = preparedItemIndex,
        itemCount = uiState.items.size,
        backgroundCount = 1
    )
    val lookaheadTargets = preparedBackgroundItemIndices.mapNotNull { itemIndex ->
        val item = uiState.items.getOrNull(itemIndex) ?: return@mapNotNull null
        val itemLocalUri = resolveDouyinCachedLocalPlayUri(
            awemeId = item.awemeId,
            localPlayPaths = uiState.localPlayPaths
        )
        codecPolicyVersion
        val itemPlaybackOverrideUri = runtimePlaybackOverrides[item.awemeId]
        val playbackTarget = resolveDouyinPlaybackTarget(
            item = item,
            preference = uiState.codecPreference,
            h265Supported = h265Supported,
            overridePlayUrl = itemPlaybackOverrideUri ?: itemLocalUri
        ) ?: return@mapNotNull null
        val mediaUri = playbackTarget.mediaUri
        val prepareKey = buildDouyinPlaybackPrepareKey(
            mediaUri = mediaUri,
            remoteResolvedAtMs = item.playUrlResolvedAtMs
        ) ?: return@mapNotNull null
        DouyinPreparedSlotTarget(
            awemeId = item.awemeId,
            mediaUri = mediaUri,
            remoteResolvedAtMs = item.playUrlResolvedAtMs,
            prepareKey = prepareKey,
            codec = playbackTarget.codec,
            trackAutoHevcAttempt = playbackTarget.trackAutoHevcAttempt
        )
    }
    val preparedPlaybackTargetMediaUri = preparedPlaybackTarget?.mediaUri
    var activeMediaUri by remember(preparedItem?.awemeId, preparedPlaybackTargetMediaUri) {
        mutableStateOf(preparedPlaybackTargetMediaUri)
    }
    var activeRemoteResolvedAtMs by remember(preparedItem?.awemeId) {
        mutableStateOf(preparedItem?.playUrlResolvedAtMs ?: 0L)
    }
    var activePrepareAttemptNonce by remember(preparedItem?.awemeId) { mutableIntStateOf(0) }
    var activeRetryCount by remember(preparedItem?.awemeId) { mutableIntStateOf(0) }
    var activePausedByGesture by remember(preparedItem?.awemeId) { mutableStateOf(false) }
    var activePausedByLifecycle by remember { mutableStateOf(false) }
    var activeAutoplayEnabled by remember(
        preparedItem?.awemeId,
        activeMediaUri,
        activeRemoteResolvedAtMs,
        activePrepareAttemptNonce
    ) {
        mutableStateOf(false)
    }
    var posterCacheVersion by remember { mutableIntStateOf(0) }
    val primarySlot = playerSession.slots[0]
    val secondarySlot = playerSession.slots[1]
    fun slotFor(key: DouyinPlayerSlotKey): DouyinPlayerSlotState {
        return when (key) {
            DouyinPlayerSlotKey.Primary -> primarySlot
            DouyinPlayerSlotKey.Secondary -> secondarySlot
        }
    }
    fun logSettledPlaybackStarted(
        settledItem: DouyinStreamItem,
        settledSlot: DouyinPlayerSlotState
    ) {
        if (settledSlot.boundAwemeId != settledItem.awemeId) return
        if (settledSlot.hasError) return
        val mode = when {
            settledSlot.hasRenderedFirstFrame -> "settled_first_frame"
            settledSlot.isPlaying -> "settled_playing"
            settledSlot.isReady -> "settled_ready"
            settledSlot.isBuffering -> "settled_buffering"
            else -> return
        }
        AppLogger.d(
            TAG,
            "TEST_EVENT playback_started awemeId=${settledItem.awemeId} mode=$mode slot=${settledSlot.key.name}"
        )
    }
    val allSlots = listOf(primarySlot, secondarySlot)
    val latestItems = rememberUpdatedState(uiState.items)
    val latestPrewarmForegroundSlotKey = rememberUpdatedState(foregroundSlotKey)
    val latestPrewarmPreparedAwemeId = rememberUpdatedState(preparedItem?.awemeId)
    val latestPrewarmPreparedPrepareKey = rememberUpdatedState(
        buildDouyinPlaybackPrepareKey(
            mediaUri = activeMediaUri,
            remoteResolvedAtMs = activeRemoteResolvedAtMs
        )
    )
    val latestPrewarmShowTitlePage = rememberUpdatedState(uiState.showTitlePage)
    fun resolveSlotItem(slot: DouyinPlayerSlotState): DouyinStreamItem? {
        val boundAwemeId = slot.boundAwemeId ?: return null
        val remoteResolvedAtMs = slot.remoteResolvedAtMs
        return latestItems.value.firstOrNull { item ->
            item.awemeId == boundAwemeId &&
                item.playUrlResolvedAtMs == remoteResolvedAtMs
        } ?: latestItems.value.firstOrNull { item ->
            item.awemeId == boundAwemeId
        }
    }
    suspend fun capturePosterFromSlot(
        slot: DouyinPlayerSlotState,
        reason: String
    ): Boolean {
        val item = resolveSlotItem(slot) ?: return false
        val textureView = slot.textureView ?: return false
        if (!textureView.isAvailable || textureView.width <= 0 || textureView.height <= 0) {
            return false
        }
        val bitmap = runCatching {
            textureView.getBitmap(textureView.width, textureView.height)
        }.getOrNull() ?: return false
        val posterBitmap = withContext(Dispatchers.Default) {
            renderDouyinPosterBitmap(
                bitmap = bitmap,
                viewSize = slot.viewSize.takeIf { it.width > 0 && it.height > 0 }
                    ?: IntSize(textureView.width, textureView.height),
                videoSize = slot.videoSize,
                scaleMode = scaleMode,
                videoRotation = slot.videoRotation
            )
        } ?: bitmap
        try {
            val stored = withContext(Dispatchers.Default) {
                DouyinPlaybackPreviewCache.storePoster(item, posterBitmap)
            }
            if (stored) {
                posterCacheVersion += 1
                AppLogger.d(
                    TAG,
                    "TEST_EVENT poster_cached awemeId=${item.awemeId} slot=${slot.key.name} reason=$reason"
                )
            }
            return stored || DouyinPlaybackPreviewCache.readPosterBytes(item) != null
        } finally {
            if (posterBitmap !== bitmap && !posterBitmap.isRecycled) {
                posterBitmap.recycle()
            }
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
    suspend fun capturePosterFromSlotWithRetries(
        slot: DouyinPlayerSlotState,
        reason: String
    ) {
        repeat(DOUYIN_POSTER_CAPTURE_RETRY_FRAMES) {
            if (capturePosterFromSlot(slot = slot, reason = reason)) {
                return
            }
            withFrameNanos { }
        }
        AppLogger.d(
            TAG,
            "TEST_EVENT poster_capture_miss awemeId=${slot.boundAwemeId.orEmpty()} slot=${slot.key.name} reason=$reason"
        )
    }
    suspend fun prewarmLookaheadSlotToFirstFrame(
        slot: DouyinPlayerSlotState,
        reason: String
    ) {
        val item = resolveSlotItem(slot) ?: return
        val prepareKey = slot.preparedSourceKey ?: return
        if (slot.inFlightPrewarmKey == prepareKey) {
            return
        }
        slot.inFlightPrewarmKey = prepareKey
        val previousVolume = slot.player.volume
        try {
            slot.player.volume = 0f
            slot.player.playWhenReady = true
            slot.player.play()
            AppLogger.d(
                TAG,
                "TEST_EVENT lookahead_prewarm_start awemeId=${item.awemeId} slot=${slot.key.name}"
            )

            withTimeoutOrNull(DOUYIN_LOOKAHEAD_PREWARM_TIMEOUT_MS) {
                snapshotFlow {
                    slot.hasRenderedFirstFrame ||
                        slot.hasError ||
                        latestPrewarmShowTitlePage.value ||
                        latestPrewarmForegroundSlotKey.value == slot.key ||
                        slot.boundAwemeId != item.awemeId ||
                        slot.preparedSourceKey != prepareKey
                }.first { it }
            }

            if (slot.hasRenderedFirstFrame) {
                capturePosterFromSlotWithRetries(
                    slot = slot,
                    reason = reason
                )
            }

            val slotBecamePreparedTarget = !latestPrewarmShowTitlePage.value &&
                latestPrewarmPreparedAwemeId.value == item.awemeId &&
                latestPrewarmPreparedPrepareKey.value == prepareKey
            val shouldPauseAfterWarmup = !latestPrewarmShowTitlePage.value &&
                latestPrewarmForegroundSlotKey.value != slot.key &&
                !slotBecamePreparedTarget &&
                slot.boundAwemeId == item.awemeId &&
                slot.preparedSourceKey == prepareKey
            if (shouldPauseAfterWarmup) {
                slot.player.playWhenReady = false
                slot.player.pause()
                slot.isPlaying = false
            }
            AppLogger.d(
                TAG,
                "TEST_EVENT lookahead_prewarm_end awemeId=${item.awemeId} slot=${slot.key.name} " +
                    "firstFrame=${slot.hasRenderedFirstFrame} error=${slot.hasError} " +
                    "keptForTarget=$slotBecamePreparedTarget"
            )
        } finally {
            slot.player.volume = previousVolume
            if (slot.inFlightPrewarmKey == prepareKey) {
                slot.inFlightPrewarmKey = null
            }
        }
    }
    val foregroundSlot = slotFor(foregroundSlotKey)
    val activeSlotMatchesCurrentItem = activeItem?.awemeId != null &&
        foregroundSlot.boundAwemeId == activeItem.awemeId
    val activeIsBuffering = activeSlotMatchesCurrentItem && foregroundSlot.isBuffering
    val activeIsPlaying = activeSlotMatchesCurrentItem && foregroundSlot.isPlaying
    val activeHasError = activeSlotMatchesCurrentItem && foregroundSlot.hasError
    val activeShouldShowLoadingIndicator = shouldShowDouyinLoadingIndicator(
        isActive = activeSlotMatchesCurrentItem,
        isBuffering = activeIsBuffering,
        isPlaying = activeIsPlaying,
        hasError = activeHasError
    )
    val shouldKeepScreenOn = activeItem != null &&
        !activeHasError &&
        (activeIsPlaying || activeIsBuffering)
    fun pauseSlotPlayback(
        slot: DouyinPlayerSlotState,
        suppressBuffering: Boolean = false
    ) {
        slot.player.playWhenReady = false
        slot.player.pause()
        slot.isPlaying = false
        if (suppressBuffering) {
            slot.isBuffering = false
        }
    }

    fun stopSlotPlayback(slot: DouyinPlayerSlotState) {
        pauseSlotPlayback(slot, suppressBuffering = true)
    }

    fun detachSlotTextureView(slot: DouyinPlayerSlotState) {
        slot.attachedTextureView?.let { attachedTextureView ->
            runCatching { slot.player.clearVideoTextureView(attachedTextureView) }
        }
        slot.attachedTextureView = null
        slot.textureView = null
        slot.viewSize = IntSize.Zero
        slot.hasRenderedFirstFrame = false
    }

    fun clearSlotBinding(slot: DouyinPlayerSlotState) {
        slot.player.playWhenReady = false
        runCatching {
            slot.player.stop()
            slot.player.clearMediaItems()
        }
        detachSlotTextureView(slot)
        slot.boundAwemeId = null
        slot.mediaUri = null
        slot.remoteResolvedAtMs = 0L
        slot.preparedSourceKey = null
        slot.boundCodec = DouyinVideoCodec.UNKNOWN
        slot.isReady = false
        slot.hasRenderedFirstFrame = false
        slot.isBuffering = false
        slot.isPlaying = false
        slot.hasError = false
        slot.videoSize = IntSize.Zero
        slot.videoRotation = 0
        slot.loggedPlaybackStartKey = null
        slot.trackedAutoHevcAttemptKey = null
        slot.trackedAutoHevcFailureKey = null
    }

    fun bindSlotTarget(
        slot: DouyinPlayerSlotState,
        awemeId: String,
        mediaUri: String,
        remoteResolvedAtMs: Long,
        prepareKey: String,
        codec: DouyinVideoCodec,
        trackAutoHevcAttempt: Boolean,
        shouldPlay: Boolean
    ) {
        val targetUri = mediaUri.trim()
        if (slot.boundAwemeId == awemeId && slot.preparedSourceKey == prepareKey) {
            slot.boundCodec = codec
            slot.player.playWhenReady = shouldPlay
            if (shouldPlay) {
                slot.player.play()
                val loggedPrepareKey = slot.preparedSourceKey ?: prepareKey
                if (slot.loggedPlaybackStartKey != loggedPrepareKey) {
                    slot.loggedPlaybackStartKey = loggedPrepareKey
                    AppLogger.d(
                        TAG,
                        "TEST_EVENT playback_started awemeId=$awemeId mode=${
                            when {
                                slot.hasRenderedFirstFrame -> "resume_first_frame"
                                slot.isReady -> "resume_ready"
                                else -> "bind_play"
                            }
                        } slot=${slot.key.name}"
                    )
                }
            } else {
                pauseSlotPlayback(slot)
            }
            return
        }
        slot.boundAwemeId = awemeId
        slot.mediaUri = targetUri
        slot.remoteResolvedAtMs = remoteResolvedAtMs
        slot.preparedSourceKey = prepareKey
        slot.boundCodec = codec
        slot.isReady = false
        slot.hasRenderedFirstFrame = false
        slot.isBuffering = true
        slot.isPlaying = false
        slot.hasError = false
        slot.videoSize = IntSize.Zero
        slot.videoRotation = 0
        slot.loggedPlaybackStartKey = null
        slot.trackedAutoHevcAttemptKey = null
        slot.trackedAutoHevcFailureKey = null
        if (trackAutoHevcAttempt && codec == DouyinVideoCodec.H265) {
            DouyinCodecRuntimePolicy.recordAutoHevcAttempt()
            slot.trackedAutoHevcAttemptKey = prepareKey
            AppLogger.d(
                TAG,
                "TEST_EVENT auto_hevc_attempt awemeId=$awemeId slot=${slot.key.name}"
            )
        }
        slot.player.playWhenReady = shouldPlay
        slot.player.setMediaItem(MediaItem.fromUri(targetUri))
        slot.player.prepare()
        if (shouldPlay) {
            slot.player.play()
        }
    }

    fun findPromotableSlot(
        targetAwemeId: String?,
        targetPrepareKey: String?
    ): DouyinPlayerSlotState? {
        return allSlots.firstOrNull { slot ->
            shouldPromoteDouyinStandbySlot(
                standbyAwemeId = slot.boundAwemeId,
                targetAwemeId = targetAwemeId,
                standbyPrepareKey = slot.preparedSourceKey,
                targetPrepareKey = targetPrepareKey,
                isReady = slot.isReady,
                hasRenderedFirstFrame = slot.hasRenderedFirstFrame,
                hasError = slot.hasError
            )
        }
    }

    fun bindLookaheadSlots(
        foregroundKey: DouyinPlayerSlotKey,
        targets: List<DouyinPreparedSlotTarget>
    ) {
        val backgroundSlots = allSlots.filter { it.key != foregroundKey }
        val assignments = linkedMapOf<DouyinPlayerSlotState, DouyinPreparedSlotTarget>()
        val matchedTargetIds = linkedSetOf<String>()

        backgroundSlots.forEach { slot ->
            val matchingTarget = targets.firstOrNull { target ->
                target.awemeId == slot.boundAwemeId &&
                    target.prepareKey == slot.preparedSourceKey
            } ?: return@forEach
            assignments[slot] = matchingTarget
            matchedTargetIds += matchingTarget.awemeId
        }

        val unusedSlots = backgroundSlots.filterNot(assignments::containsKey)
        val unmatchedTargets = targets.filterNot { matchedTargetIds.contains(it.awemeId) }
        unusedSlots.zip(unmatchedTargets).forEach { (slot, target) ->
            assignments[slot] = target
        }

        backgroundSlots.forEach { slot ->
            val target = assignments[slot]
            if (target == null) {
                clearSlotBinding(slot)
            } else {
                bindSlotTarget(
                    slot = slot,
                    awemeId = target.awemeId,
                    mediaUri = target.mediaUri,
                    remoteResolvedAtMs = target.remoteResolvedAtMs,
                    prepareKey = target.prepareKey,
                    codec = target.codec,
                    trackAutoHevcAttempt = target.trackAutoHevcAttempt,
                    shouldPlay = false
                )
            }
        }
    }

    fun stopForegroundPlayback() {
        stopSlotPlayback(slotFor(foregroundSlotKey))
    }

    fun stopAllPlayback() {
        allSlots.forEach(::stopSlotPlayback)
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
            activeAutoplayEnabled = false
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
            ?: preparedPlaybackTargetMediaUri
        if (targetUri.isNullOrBlank()) {
            foregroundSlot.hasError = false
            activePausedByGesture = false
            activeAutoplayEnabled = false
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
        activeAutoplayEnabled = false
        activePrepareAttemptNonce += 1
        if (sourceKind == DouyinPlaybackSourceKind.LOCAL && !preparedPlaybackTargetMediaUri.isNullOrBlank()) {
            activeMediaUri = preparedPlaybackTargetMediaUri
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
                settledPagerPage = pagerState.currentPage
                onPageSettled(settledPage)
                if (settledPage > 0) {
                    val settledItem = latestItems.value.getOrNull(settledPage - 1)
                    recordRecentVisitedAwemeId(settledItem?.awemeId)
                    allSlots.forEach { it.loggedPlaybackStartKey = null }
                    AppLogger.d(
                        TAG,
                        "TEST_EVENT page_settled awemeId=${settledItem?.awemeId.orEmpty()} page=$settledPage"
                    )
                    if (settledItem != null) {
                        logSettledPlaybackStarted(settledItem, foregroundSlot)
                        DouyinPlaybackDebugController.updatePlaybackContext(
                            activeAwemeId = settledItem.awemeId,
                            nextAwemeId = latestItems.value.getOrNull(settledPage)?.awemeId,
                            inVideoFlow = true
                        )
                    }
                }
            }
    }

    LaunchedEffect(pagerState.currentPage, uiState.items, uiState.showTitlePage, entryStartIndex) {
        val debugCurrentPage = resolveDouyinSettledPage(
            pagerPage = pagerState.currentPage,
            entryStartIndex = entryStartIndex
        )
        val (activeAwemeId, nextAwemeId) = resolveDouyinPlaybackDebugContext(
            items = uiState.items,
            currentPage = debugCurrentPage,
            showTitlePage = uiState.showTitlePage,
            entryStartIndex = entryStartIndex
        )
        DouyinPlaybackDebugController.updatePlaybackContext(
            activeAwemeId = activeAwemeId,
            nextAwemeId = nextAwemeId,
            inVideoFlow = !uiState.showTitlePage && pagerState.currentPage > 0
        )
    }

    LaunchedEffect(pagerState, pageCount) {
        DouyinPlaybackDebugController.advanceRequests.collect {
            if (pageCount <= 1) return@collect
            val targetPage = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
            if (targetPage == pagerState.currentPage) return@collect
            AppLogger.d(TAG, "TEST_EVENT debug_advance targetPage=$targetPage currentPage=${pagerState.currentPage}")
            pagerState.scrollToPage(targetPage)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            DouyinPlaybackDebugController.updatePlaybackContext(
                activeAwemeId = null,
                nextAwemeId = null,
                inVideoFlow = false
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

    val activePrepareKey = remember(activeMediaUri, activeRemoteResolvedAtMs, activePrepareAttemptNonce) {
        buildDouyinPlaybackPrepareKey(
            mediaUri = activeMediaUri,
            remoteResolvedAtMs = activeRemoteResolvedAtMs,
            attemptNonce = activePrepareAttemptNonce
        )
    }
    LaunchedEffect(preparedItem?.awemeId, preparedPlaybackTargetMediaUri, preparedItem?.playUrlResolvedAtMs) {
        if (preparedItem == null) {
            activeMediaUri = null
            activeRemoteResolvedAtMs = 0L
            activePrepareAttemptNonce = 0
            activePausedByGesture = false
            activeAutoplayEnabled = false
            allSlots.forEach(::clearSlotBinding)
            return@LaunchedEffect
        }
        val playbackResolutionLocalUri = if (preparedPlaybackOverrideUri.isNullOrBlank()) {
            preparedLocalUri
        } else {
            null
        }
        val resolvedState = resolveDouyinPlaybackState(
            currentUri = activeMediaUri,
            currentRemoteResolvedAtMs = activeRemoteResolvedAtMs,
            localUri = playbackResolutionLocalUri,
            remoteUri = preparedPlaybackTargetMediaUri,
            remoteResolvedAtMs = preparedItem.playUrlResolvedAtMs
        )
        if (resolvedState.mediaUri != activeMediaUri || resolvedState.remoteResolvedAtMs != activeRemoteResolvedAtMs) {
            activeMediaUri = resolvedState.mediaUri
            activeRemoteResolvedAtMs = resolvedState.remoteResolvedAtMs
            activePrepareAttemptNonce = 0
            activeAutoplayEnabled = false
            foregroundSlot.hasError = false
            foregroundSlot.hasRenderedFirstFrame = false
        }
    }
    LaunchedEffect(preparedItem?.awemeId, activeMediaUri, lookaheadTargets.map { it.awemeId }, uiState.showTitlePage) {
        val currentItem = preparedItem ?: return@LaunchedEffect
        AppLogger.d(
            TAG,
            "${if (uiState.showTitlePage) "prepared" else "active"} source awemeId=${currentItem.awemeId} source=${currentDouyinPlaybackSourceKind(activeMediaUri)} " +
                "lookahead=${lookaheadTargets.joinToString(",") { it.awemeId }} " +
                "recent=${recentVisitedAwemeIds.joinToString(",")}"
        )
    }
    LaunchedEffect(preparedItemIndex, uiState.items, uiState.playHeaders, uiState.showTitlePage) {
        DouyinPlaybackPreviewCache.updatePlaybackWindow(
            items = uiState.items,
            anchorIndex = preparedItemIndex,
            headers = uiState.playHeaders,
            reason = if (uiState.showTitlePage) "title_page" else "immersive_page"
        )
    }
    val latestActiveMediaUri = rememberUpdatedState(activeMediaUri)
    val latestForegroundSlotKey = rememberUpdatedState(foregroundSlotKey)
    val latestActiveAwemeId = rememberUpdatedState(activeItem?.awemeId)
    val latestPreparedItemIndex = rememberUpdatedState(preparedItemIndex)
    val latestPreparedAwemeId = rememberUpdatedState(preparedItem?.awemeId)
    val latestActivePrepareKey = rememberUpdatedState(activePrepareKey)
    val latestShowTitlePage = rememberUpdatedState(uiState.showTitlePage)
    val latestHasNextActiveItem = rememberUpdatedState(hasNextActiveItem)
    val latestRetryCount = rememberUpdatedState(activeRetryCount)
    val latestRequestActivePlaybackRefresh = rememberUpdatedState(
        newValue = { resetRetryCount: Boolean -> requestActivePlaybackRefresh(resetRetryCount) }
    )
    LaunchedEffect(
        uiState.showTitlePage,
        preparedItem?.awemeId,
        activePrepareKey,
        foregroundSlotKey,
        foregroundSlot.textureView,
        foregroundSlot.hasRenderedFirstFrame,
        foregroundSlot.hasError
    ) {
        if (!uiState.showTitlePage) return@LaunchedEffect
        val targetItem = preparedItem ?: return@LaunchedEffect
        val targetPrepareKey = activePrepareKey ?: return@LaunchedEffect
        val currentForegroundSlot = slotFor(foregroundSlotKey)
        if (currentForegroundSlot.boundAwemeId != targetItem.awemeId) return@LaunchedEffect
        if (currentForegroundSlot.preparedSourceKey != targetPrepareKey) return@LaunchedEffect
        if (currentForegroundSlot.textureView == null || currentForegroundSlot.viewSize == IntSize.Zero) return@LaunchedEffect
        if (currentForegroundSlot.hasRenderedFirstFrame || currentForegroundSlot.hasError) return@LaunchedEffect

        val previousVolume = currentForegroundSlot.player.volume
        currentForegroundSlot.player.volume = 0f
        currentForegroundSlot.player.playWhenReady = true
        currentForegroundSlot.player.play()
        AppLogger.d(
            TAG,
            "TEST_EVENT title_prewarm_start awemeId=${targetItem.awemeId} slot=${currentForegroundSlot.key.name}"
        )

        withTimeoutOrNull(DOUYIN_TITLE_PREWARM_TIMEOUT_MS) {
            snapshotFlow {
                currentForegroundSlot.hasRenderedFirstFrame ||
                    currentForegroundSlot.hasError ||
                    !latestShowTitlePage.value ||
                    currentForegroundSlot.boundAwemeId != targetItem.awemeId ||
                    currentForegroundSlot.preparedSourceKey != targetPrepareKey
            }.first { it }
        }

        if (currentForegroundSlot.hasRenderedFirstFrame) {
            capturePosterFromSlotWithRetries(
                slot = currentForegroundSlot,
                reason = "title_prewarm"
            )
        }

        currentForegroundSlot.player.volume = previousVolume
        val shouldPauseAfterWarmup = latestShowTitlePage.value &&
            currentForegroundSlot.boundAwemeId == targetItem.awemeId &&
            currentForegroundSlot.preparedSourceKey == targetPrepareKey
        if (shouldPauseAfterWarmup) {
            pauseSlotPlayback(currentForegroundSlot)
        }
        AppLogger.d(
            TAG,
            "TEST_EVENT title_prewarm_end awemeId=${targetItem.awemeId} slot=${currentForegroundSlot.key.name} " +
                "firstFrame=${currentForegroundSlot.hasRenderedFirstFrame} error=${currentForegroundSlot.hasError}"
        )
    }
    LaunchedEffect(primarySlot.boundAwemeId, primarySlot.preparedSourceKey, primarySlot.hasRenderedFirstFrame, primarySlot.textureView) {
        if (!primarySlot.hasRenderedFirstFrame) return@LaunchedEffect
        capturePosterFromSlotWithRetries(
            slot = primarySlot,
            reason = "rendered_first_frame"
        )
    }
    LaunchedEffect(secondarySlot.boundAwemeId, secondarySlot.preparedSourceKey, secondarySlot.hasRenderedFirstFrame, secondarySlot.textureView) {
        if (!secondarySlot.hasRenderedFirstFrame) return@LaunchedEffect
        capturePosterFromSlotWithRetries(
            slot = secondarySlot,
            reason = "rendered_first_frame"
        )
    }
    LaunchedEffect(
        uiState.showTitlePage,
        foregroundSlotKey,
        primarySlot.boundAwemeId,
        primarySlot.preparedSourceKey,
        primarySlot.inFlightPrewarmKey,
        primarySlot.hasRenderedFirstFrame,
        primarySlot.hasError,
        primarySlot.textureView,
        lookaheadTargets.map { it.prepareKey }
    ) {
        val hasMatchingLookaheadTarget = lookaheadTargets.any { target ->
            target.awemeId == primarySlot.boundAwemeId &&
                target.prepareKey == primarySlot.preparedSourceKey
        }
        if (
            !shouldPrewarmDouyinLookaheadSlot(
                showTitlePage = uiState.showTitlePage,
                isForegroundSlot = foregroundSlotKey == primarySlot.key,
                hasMatchingLookaheadTarget = hasMatchingLookaheadTarget,
                hasRenderedFirstFrame = primarySlot.hasRenderedFirstFrame,
                hasError = primarySlot.hasError,
                hasTextureView = primarySlot.textureView != null && primarySlot.viewSize != IntSize.Zero,
                isPrewarming = primarySlot.inFlightPrewarmKey == primarySlot.preparedSourceKey
            )
        ) {
            return@LaunchedEffect
        }
        prewarmLookaheadSlotToFirstFrame(
            slot = primarySlot,
            reason = "lookahead_prewarm"
        )
    }
    LaunchedEffect(
        uiState.showTitlePage,
        foregroundSlotKey,
        secondarySlot.boundAwemeId,
        secondarySlot.preparedSourceKey,
        secondarySlot.inFlightPrewarmKey,
        secondarySlot.hasRenderedFirstFrame,
        secondarySlot.hasError,
        secondarySlot.textureView,
        lookaheadTargets.map { it.prepareKey }
    ) {
        val hasMatchingLookaheadTarget = lookaheadTargets.any { target ->
            target.awemeId == secondarySlot.boundAwemeId &&
                target.prepareKey == secondarySlot.preparedSourceKey
        }
        if (
            !shouldPrewarmDouyinLookaheadSlot(
                showTitlePage = uiState.showTitlePage,
                isForegroundSlot = foregroundSlotKey == secondarySlot.key,
                hasMatchingLookaheadTarget = hasMatchingLookaheadTarget,
                hasRenderedFirstFrame = secondarySlot.hasRenderedFirstFrame,
                hasError = secondarySlot.hasError,
                hasTextureView = secondarySlot.textureView != null && secondarySlot.viewSize != IntSize.Zero,
                isPrewarming = secondarySlot.inFlightPrewarmKey == secondarySlot.preparedSourceKey
            )
        ) {
            return@LaunchedEffect
        }
        prewarmLookaheadSlotToFirstFrame(
            slot = secondarySlot,
            reason = "lookahead_prewarm"
        )
    }
    fun recordAutoHevcFailure(slot: DouyinPlayerSlotState) {
        val prepareKey = slot.preparedSourceKey ?: return
        if (slot.trackedAutoHevcAttemptKey != prepareKey || slot.trackedAutoHevcFailureKey == prepareKey) {
            return
        }
        val policyChanged = DouyinCodecRuntimePolicy.recordAutoHevcFailure()
        slot.trackedAutoHevcFailureKey = prepareKey
        AppLogger.d(
            TAG,
            "TEST_EVENT auto_hevc_failure awemeId=${slot.boundAwemeId.orEmpty()} slot=${slot.key.name}"
        )
        if (policyChanged) {
            codecPolicyVersion += 1
        }
    }
    fun applyH264FallbackIfAvailable(
        slot: DouyinPlayerSlotState,
        error: PlaybackException,
        isForegroundCurrentSlot: Boolean
    ): Boolean {
        if (!isLikelyDouyinCodecFailure(error)) {
            return false
        }
        if (slot.boundCodec == DouyinVideoCodec.H265) {
            recordAutoHevcFailure(slot)
        }
        val awemeId = slot.boundAwemeId ?: return false
        val item = latestItems.value.firstOrNull { it.awemeId == awemeId } ?: return false
        val fallbackTarget = resolveDouyinH264FallbackTarget(
            item = item,
            currentUri = slot.mediaUri,
            h265Supported = h265Supported
        ) ?: return false
        runtimePlaybackOverrides = runtimePlaybackOverrides + (awemeId to fallbackTarget.mediaUri)
        slot.hasError = false
        slot.isBuffering = true
        slot.hasRenderedFirstFrame = false
        slot.isPlaying = false
        AppLogger.d(
            TAG,
            "TEST_EVENT fallback_h264 awemeId=$awemeId slot=${slot.key.name} from=${slot.boundCodec.name} to=${fallbackTarget.codec.name}"
        )
        if (isForegroundCurrentSlot && latestActiveAwemeId.value == awemeId) {
            activePausedByGesture = false
            activeAutoplayEnabled = false
            activeMediaUri = fallbackTarget.mediaUri
            activeRemoteResolvedAtMs = item.playUrlResolvedAtMs
            activePrepareAttemptNonce += 1
        }
        return true
    }
    fun logPlaybackStarted(
        slot: DouyinPlayerSlotState,
        mode: String,
        expectedForegroundKey: DouyinPlayerSlotKey = latestForegroundSlotKey.value
    ) {
        val currentAwemeId = latestActiveAwemeId.value ?: return
        if (latestShowTitlePage.value) return
        if (expectedForegroundKey != slot.key) return
        if (slot.boundAwemeId != currentAwemeId) return
        val prepareKey = slot.preparedSourceKey ?: return
        if (slot.loggedPlaybackStartKey == prepareKey) return
        slot.loggedPlaybackStartKey = prepareKey
        AppLogger.d(
            TAG,
            "TEST_EVENT playback_started awemeId=$currentAwemeId mode=$mode slot=${slot.key.name}"
        )
    }
    DisposableEffect(primarySlot.player) {
        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                primarySlot.isBuffering = isLoading
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                primarySlot.isReady = playbackState == Player.STATE_READY
                primarySlot.isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    primarySlot.isPlaying = false
                }
                if (playbackState == Player.STATE_READY) {
                    primarySlot.hasError = false
                    logPlaybackStarted(primarySlot, mode = "ready")
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                primarySlot.isPlaying = isPlayingNow
            }

            override fun onRenderedFirstFrame() {
                primarySlot.hasRenderedFirstFrame = true
                logPlaybackStarted(primarySlot, mode = "first_frame")
            }

            override fun onVideoSizeChanged(videoSizeNow: androidx.media3.common.VideoSize) {
                primarySlot.videoSize = IntSize(videoSizeNow.width, videoSizeNow.height)
                primarySlot.videoRotation = videoSizeNow.unappliedRotationDegrees
            }

            override fun onPlayerError(error: PlaybackException) {
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
                if (applyH264FallbackIfAvailable(primarySlot, error, isForegroundCurrentSlot)) {
                    return
                }
                if (!isForegroundCurrentSlot) {
                    return
                }
                AppLogger.d(
                    TAG,
                    "TEST_EVENT playback_failed awemeId=$currentAwemeId slot=${primarySlot.key.name} retryCount=${latestRetryCount.value}"
                )
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
                secondarySlot.isReady = playbackState == Player.STATE_READY
                secondarySlot.isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    secondarySlot.isPlaying = false
                }
                if (playbackState == Player.STATE_READY) {
                    secondarySlot.hasError = false
                    logPlaybackStarted(secondarySlot, mode = "ready")
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                secondarySlot.isPlaying = isPlayingNow
            }

            override fun onRenderedFirstFrame() {
                secondarySlot.hasRenderedFirstFrame = true
                logPlaybackStarted(secondarySlot, mode = "first_frame")
            }

            override fun onVideoSizeChanged(videoSizeNow: androidx.media3.common.VideoSize) {
                secondarySlot.videoSize = IntSize(videoSizeNow.width, videoSizeNow.height)
                secondarySlot.videoRotation = videoSizeNow.unappliedRotationDegrees
            }

            override fun onPlayerError(error: PlaybackException) {
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
                if (applyH264FallbackIfAvailable(secondarySlot, error, isForegroundCurrentSlot)) {
                    return
                }
                if (!isForegroundCurrentSlot) {
                    return
                }
                AppLogger.d(
                    TAG,
                    "TEST_EVENT playback_failed awemeId=$currentAwemeId slot=${secondarySlot.key.name} retryCount=${latestRetryCount.value}"
                )
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

    val latestPreviewCacheSessionGeneration = rememberUpdatedState(
        DouyinPlaybackPreviewCache.captureSessionGeneration()
    )

    DisposableEffect(playerSession) {
        onDispose {
            val snapshotItems = latestItems.value
            val snapshotAnchorIndex = latestPreparedItemIndex.value
            val expectedGeneration = latestPreviewCacheSessionGeneration.value
            CoroutineScope(Dispatchers.IO).launch {
                DouyinPlaybackPreviewCache.persistExitSnapshotsIfCurrent(
                    items = snapshotItems,
                    anchorIndex = snapshotAnchorIndex,
                    expectedGeneration = expectedGeneration
                )
            }
        }
    }

    DisposableEffect(
        lifecycleOwner,
        foregroundSlotKey,
        primarySlot.player,
        secondarySlot.player
    ) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    activePausedByLifecycle = true
                    activeAutoplayEnabled = false
                    stopAllPlayback()
                }

                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> {
                    activePausedByLifecycle = false
                }

                else -> Unit
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
        preparedItem?.awemeId,
        activePrepareKey,
        lookaheadTargets.map { it.prepareKey },
        foregroundSlotKey,
        primarySlot.hasRenderedFirstFrame,
        secondarySlot.hasRenderedFirstFrame,
        uiState.showTitlePage
    ) {
        if (preparedItem == null || activePrepareKey.isNullOrBlank() || activeMediaUri.isNullOrBlank()) {
            allSlots.forEach(::clearSlotBinding)
            return@LaunchedEffect
        }
        val promotedSlot = findPromotableSlot(
            targetAwemeId = preparedItem.awemeId,
            targetPrepareKey = activePrepareKey
        )
        val desiredForegroundKey = promotedSlot?.key ?: foregroundSlotKey
        if (desiredForegroundKey != foregroundSlotKey) {
            AppLogger.d(
                TAG,
                "promote slot=${desiredForegroundKey.name} awemeId=${preparedItem.awemeId}"
            )
            foregroundSlotKey = desiredForegroundKey
            promotedSlot?.let { slot ->
                if (slot.isReady || slot.hasRenderedFirstFrame) {
                    logPlaybackStarted(
                        slot = slot,
                        mode = if (slot.hasRenderedFirstFrame) "promote_first_frame" else "promote_ready",
                        expectedForegroundKey = desiredForegroundKey
                    )
                }
            }
        }
        val desiredForegroundSlot = slotFor(desiredForegroundKey)
        val currentMediaUri = activeMediaUri ?: return@LaunchedEffect
        val shouldKeepForegroundPlaying = shouldPlayBoundDouyinForegroundSlot(
            showTitlePage = uiState.showTitlePage,
            autoplayEnabled = activeAutoplayEnabled,
            pausedByGesture = activePausedByGesture,
            pausedByLifecycle = activePausedByLifecycle,
            hasError = activeHasError,
            isScrollInProgress = pagerState.isScrollInProgress
        )
        bindSlotTarget(
            slot = desiredForegroundSlot,
            awemeId = preparedItem.awemeId,
            mediaUri = currentMediaUri,
            remoteResolvedAtMs = activeRemoteResolvedAtMs,
            prepareKey = activePrepareKey,
            codec = preparedPlaybackTarget?.codec ?: DouyinVideoCodec.UNKNOWN,
            trackAutoHevcAttempt = preparedPlaybackTarget?.trackAutoHevcAttempt == true,
            shouldPlay = shouldKeepForegroundPlaying
        )
        if (!uiState.showTitlePage && !desiredForegroundSlot.hasRenderedFirstFrame) {
            return@LaunchedEffect
        }
        bindLookaheadSlots(
            foregroundKey = desiredForegroundKey,
            targets = lookaheadTargets.filterNot { it.awemeId == preparedItem.awemeId }
        )
    }

    LaunchedEffect(
        pagerState.isScrollInProgress,
        preparedItem?.awemeId,
        activePrepareKey,
        foregroundSlotKey,
        foregroundSlot.boundAwemeId,
        foregroundSlot.preparedSourceKey,
        foregroundSlot.isReady,
        foregroundSlot.hasRenderedFirstFrame,
        activePausedByGesture,
        activePausedByLifecycle,
        activeHasError,
        uiState.showTitlePage,
        activeAutoplayEnabled,
        useImmediateEntryPlayback
    ) {
        val targetItem = preparedItem
        val targetPrepareKey = activePrepareKey
        val currentForegroundSlot = slotFor(foregroundSlotKey)
        val canAutoPlay = shouldAutoPlayDouyinActiveSlot(
            showTitlePage = uiState.showTitlePage,
            pausedByGesture = activePausedByGesture,
            pausedByLifecycle = activePausedByLifecycle,
            hasError = activeHasError
        )
        if (targetItem == null || targetPrepareKey.isNullOrBlank() || !canAutoPlay) {
            activeAutoplayEnabled = false
            return@LaunchedEffect
        }
        if (pagerState.isScrollInProgress) {
            if (currentForegroundSlot.isPlaying || currentForegroundSlot.player.playWhenReady) {
                AppLogger.d(
                    TAG,
                    "TEST_EVENT scroll_pause_current awemeId=${currentForegroundSlot.boundAwemeId.orEmpty()} slot=${currentForegroundSlot.key.name}"
                )
            }
            activeAutoplayEnabled = false
            return@LaunchedEffect
        }
        val slotMatchesTarget = currentForegroundSlot.boundAwemeId == targetItem.awemeId &&
            currentForegroundSlot.preparedSourceKey == targetPrepareKey
        if (!slotMatchesTarget || (!currentForegroundSlot.isReady && !currentForegroundSlot.hasRenderedFirstFrame)) {
            activeAutoplayEnabled = false
            return@LaunchedEffect
        }
        if (activeAutoplayEnabled) return@LaunchedEffect
        if (useImmediateEntryPlayback) {
            activeAutoplayEnabled = true
            AppLogger.d(
                TAG,
                "TEST_EVENT settle_autoplay_ready awemeId=${targetItem.awemeId} slot=${currentForegroundSlot.key.name} mode=entry_immediate"
            )
            return@LaunchedEffect
        }
        if (currentForegroundSlot.hasRenderedFirstFrame) {
            activeAutoplayEnabled = true
            AppLogger.d(
                TAG,
                "TEST_EVENT settle_autoplay_ready awemeId=${targetItem.awemeId} slot=${currentForegroundSlot.key.name} mode=prewarmed"
            )
            return@LaunchedEffect
        }

        AppLogger.d(
            TAG,
            "TEST_EVENT settle_delay_start awemeId=${targetItem.awemeId} slot=${currentForegroundSlot.key.name} frames=$DOUYIN_SETTLE_AUTOPLAY_DELAY_FRAMES"
        )
        awaitDouyinFrames(DOUYIN_SETTLE_AUTOPLAY_DELAY_FRAMES)
        activeAutoplayEnabled = true
        AppLogger.d(
            TAG,
            "TEST_EVENT settle_autoplay_ready awemeId=${targetItem.awemeId} slot=${currentForegroundSlot.key.name}"
        )
    }

    LaunchedEffect(
        preparedItem?.awemeId,
        activePrepareKey,
        foregroundSlotKey,
        activeAutoplayEnabled,
        activePausedByGesture,
        activePausedByLifecycle,
        activeHasError,
        uiState.showTitlePage,
        pagerState.isScrollInProgress
    ) {
        val targetItem = preparedItem
        val targetPrepareKey = activePrepareKey
        val currentForegroundSlot = slotFor(foregroundSlotKey)
        val shouldPlay = targetItem != null &&
            !targetPrepareKey.isNullOrBlank() &&
            currentForegroundSlot.boundAwemeId == targetItem.awemeId &&
            currentForegroundSlot.preparedSourceKey == targetPrepareKey &&
            activeAutoplayEnabled &&
            shouldAutoPlayDouyinActiveSlot(
                showTitlePage = uiState.showTitlePage,
                pausedByGesture = activePausedByGesture,
                pausedByLifecycle = activePausedByLifecycle,
                hasError = activeHasError
            ) &&
            !pagerState.isScrollInProgress
        if (shouldPlay) {
            currentForegroundSlot.player.playWhenReady = true
            currentForegroundSlot.player.play()
            AppLogger.d(
                TAG,
                "TEST_EVENT settle_autoplay_start awemeId=${targetItem?.awemeId.orEmpty()} slot=${currentForegroundSlot.key.name}"
            )
        } else {
            pauseSlotPlayback(
                slot = currentForegroundSlot,
                suppressBuffering = pagerState.isScrollInProgress ||
                    activePausedByGesture ||
                    activePausedByLifecycle ||
                    activeHasError ||
                    uiState.showTitlePage
            )
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
                val pageSlot = allSlots.firstOrNull { it.boundAwemeId == item.awemeId }
                val pageIsActive = playbackAnchorPagerPage == page
                val pageHasError = if (pageIsActive) activeHasError else pageSlot?.hasError == true
                val pageIsVideoVisible = pageSlot?.hasRenderedFirstFrame == true && !pageSlot.hasError
                val pagePosterBytes = remember(item.awemeId, item.playUrlResolvedAtMs, posterCacheVersion) {
                    DouyinPlaybackPreviewCache.readPosterBytes(item)
                }
                val transitionPosterBytes = remember(
                    activeItem?.awemeId,
                    activeItem?.playUrlResolvedAtMs,
                    posterCacheVersion
                ) {
                    activeItem?.let(DouyinPlaybackPreviewCache::readPosterBytes)
                }
                val showPosterFallback = shouldShowDouyinPosterFallback(
                    pagerPage = page,
                    currentPagerPage = pagerState.currentPage,
                    playbackAnchorPagerPage = playbackAnchorPagerPage,
                    isScrollInProgress = pagerState.isScrollInProgress,
                    isVideoVisible = pageIsVideoVisible,
                    hasError = pageHasError
                )
                val posterBytes = pagePosterBytes ?: transitionPosterBytes?.takeIf {
                    shouldUseDouyinTransitionPosterFallback(
                        pagerPage = page,
                        playbackAnchorPagerPage = playbackAnchorPagerPage,
                        isScrollInProgress = pagerState.isScrollInProgress
                    )
                }
                DouyinVideoPage(
                    item = item,
                    isActive = pageIsActive,
                    videoSlot = pageSlot,
                    controlsVisible = controlsVisible,
                    scaleMode = scaleMode,
                    isBuffering = if (pageIsActive) activeShouldShowLoadingIndicator else false,
                    isVideoVisible = pageIsVideoVisible,
                    showPosterFallback = showPosterFallback,
                    posterBytes = posterBytes,
                    hasError = pageHasError,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onToggleScaleMode = { scaleMode = scaleMode.next() },
                    onLongPress = { onItemLongPress(item) },
                    onDoubleTap = {
                        if (activeHasError) {
                            requestActivePlaybackRefresh(true)
                        } else if (foregroundSlot.player.isPlaying) {
                            activePausedByGesture = true
                            activeAutoplayEnabled = false
                            stopForegroundPlayback()
                        } else {
                            activePausedByGesture = false
                            activeAutoplayEnabled = true
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
    attachToPlayer: Boolean,
    renderVisible: Boolean
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            TextureView(context).apply {
                alpha = if (renderVisible) 1f else 0f
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        slot.textureView = this@apply
                        slot.viewSize = IntSize(width, height)
                        syncDouyinTextureAttachment(slot, this@apply, attachToPlayer)
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
                        if (slot.attachedTextureView === this@apply) {
                            slot.attachedTextureView = null
                        }
                        slot.viewSize = IntSize.Zero
                        slot.hasRenderedFirstFrame = false
                        runCatching { slot.player.clearVideoTextureView(this@apply) }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        },
        update = { textureView ->
            slot.textureView = textureView
            textureView.alpha = if (renderVisible) 1f else 0f
            if (textureView.isAvailable) {
                syncDouyinTextureAttachment(slot, textureView, attachToPlayer)
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
    showPosterFallback: Boolean,
    posterBytes: ByteArray?,
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
        if (showPosterFallback) {
            DouyinPosterFallback(
                posterBytes = posterBytes,
                coverUrl = item.coverUrl,
                scaleMode = scaleMode
            )
        }

        if (videoSlot != null) {
            DouyinTextureSlotHost(
                slot = videoSlot,
                scaleMode = scaleMode,
                attachToPlayer = true,
                renderVisible = isVideoVisible
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

@Composable
private fun DouyinPosterFallback(
    posterBytes: ByteArray?,
    coverUrl: String?,
    scaleMode: DouyinPlayerScaleMode
) {
    val posterBitmap by rememberDouyinPosterBitmap(posterBytes)
    val contentScale = remember(scaleMode) {
        resolveDouyinPosterContentScale(scaleMode)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            posterBitmap != null -> posterBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }

            !coverUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
        }
    }
}

@Composable
private fun rememberDouyinPosterBitmap(
    posterBytes: ByteArray?
) = produceState<ImageBitmap?>(initialValue = null, key1 = posterBytes) {
    value = posterBytes
        ?.takeIf { it.isNotEmpty() }
        ?.let { bytes ->
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?.asImageBitmap()
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
    val upstreamFactory = DefaultDataSource.Factory(
        context,
        OkHttpDataSource.Factory(OkHttpClient()).apply {
            setDefaultRequestProperties(headers)
        }
    )
    val playbackFactory = DouyinPlaybackPreviewCache.buildPlaybackDataSourceFactory(upstreamFactory)
    val loadControl = if (lightweight) {
        DefaultLoadControl.Builder()
            .setTargetBufferBytes(16 * 1024 * 1024)
            .setBufferDurationsMs(1_500, 5_000, 100, 250)
            .build()
    } else {
        DefaultLoadControl.Builder()
            .setTargetBufferBytes(48 * 1024 * 1024)
            .setBufferDurationsMs(4_000, 18_000, 150, 300)
            .build()
    }
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(playbackFactory))
        .setLoadControl(loadControl)
        .build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
}

private fun buildDouyinPlayerHeadersSignature(headers: Map<String, String>): String {
    return headers.entries
        .sortedBy { it.key }
        .joinToString(";") { (key, value) -> "$key=$value" }
}

private fun updateDouyinTextureTransform(
    textureView: TextureView?,
    viewSize: IntSize,
    videoSize: IntSize,
    scaleMode: DouyinPlayerScaleMode,
    videoRotation: Int
) {
    val view = textureView ?: return
    val matrix = buildDouyinTextureTransformMatrix(
        viewSize = viewSize,
        videoSize = videoSize,
        scaleMode = scaleMode,
        videoRotation = videoRotation
    ) ?: return
    view.setTransform(matrix)
    view.invalidate()
}

private fun buildDouyinTextureTransformMatrix(
    viewSize: IntSize,
    videoSize: IntSize,
    scaleMode: DouyinPlayerScaleMode,
    videoRotation: Int
): Matrix? {
    if (viewSize.width <= 0 || viewSize.height <= 0 || videoSize.width <= 0 || videoSize.height <= 0) {
        return null
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
    return Matrix().apply {
        setScale(scale.scaleX, scale.scaleY, centerX, centerY)
        if (videoRotation != 0) {
            postRotate(videoRotation.toFloat(), centerX, centerY)
        }
    }
}

private fun renderDouyinPosterBitmap(
    bitmap: Bitmap,
    viewSize: IntSize,
    videoSize: IntSize,
    scaleMode: DouyinPlayerScaleMode,
    videoRotation: Int
): Bitmap? {
    val matrix = buildDouyinTextureTransformMatrix(
        viewSize = viewSize,
        videoSize = videoSize,
        scaleMode = scaleMode,
        videoRotation = videoRotation
    ) ?: return null
    if (bitmap.width <= 0 || bitmap.height <= 0 || viewSize.width <= 0 || viewSize.height <= 0) {
        return null
    }
    val output = Bitmap.createBitmap(viewSize.width, viewSize.height, Bitmap.Config.ARGB_8888)
    Canvas(output).apply {
        drawColor(android.graphics.Color.BLACK)
        if (bitmap.width != viewSize.width || bitmap.height != viewSize.height) {
            val normalizedMatrix = Matrix().apply {
                setScale(
                    viewSize.width / bitmap.width.toFloat(),
                    viewSize.height / bitmap.height.toFloat()
                )
                postConcat(matrix)
            }
            drawBitmap(bitmap, normalizedMatrix, null)
        } else {
            drawBitmap(bitmap, matrix, null)
        }
    }
    return output
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

internal fun resolveDouyinPosterContentScale(
    scaleMode: DouyinPlayerScaleMode
): ContentScale {
    return when (scaleMode) {
        DouyinPlayerScaleMode.Expanded -> ContentScale.Crop
        DouyinPlayerScaleMode.Standard,
        DouyinPlayerScaleMode.Shrunk -> ContentScale.Fit
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

internal fun resolveDouyinPlaybackAnchorPagerPage(
    isScrollInProgress: Boolean,
    pagerPage: Int,
    settledPagerPage: Int
): Int {
    return if (isScrollInProgress) settledPagerPage else pagerPage
}

internal fun resolveDouyinPreparedBackgroundItemIndices(
    activeItemIndex: Int?,
    itemCount: Int,
    backgroundCount: Int
): List<Int> {
    if (activeItemIndex == null || itemCount <= 0 || backgroundCount <= 0) return emptyList()
    val result = linkedSetOf<Int>()
    var forwardIndex = activeItemIndex + 1
    while (result.size < backgroundCount && forwardIndex < itemCount) {
        result += forwardIndex
        forwardIndex += 1
    }
    return result.toList()
}

internal fun resolveDouyinPlaybackDebugContext(
    items: List<DouyinStreamItem>,
    currentPage: Int,
    showTitlePage: Boolean,
    entryStartIndex: Int
): Pair<String?, String?> {
    if (items.isEmpty()) return null to null
    if (showTitlePage) {
        val activeIndex = entryStartIndex.coerceIn(0, items.lastIndex)
        return items.getOrNull(activeIndex)?.awemeId to items.getOrNull(activeIndex + 1)?.awemeId
    }

    if (currentPage <= 0) {
        return null to items.firstOrNull()?.awemeId
    }

    val activeIndex = (currentPage - 1).coerceIn(0, items.lastIndex)
    val activeAwemeId = items.getOrNull(activeIndex)?.awemeId
    val nextAwemeId = items.getOrNull(activeIndex + 1)?.awemeId
    return activeAwemeId to nextAwemeId
}

internal fun resolveDouyinStandbyItemIndex(activeItemIndex: Int?, itemCount: Int): Int? {
    return resolveDouyinLookaheadItemIndices(
        activeItemIndex = activeItemIndex,
        itemCount = itemCount,
        extraCount = 1
    ).firstOrNull()
}

internal fun shouldPromoteDouyinStandbySlot(
    standbyAwemeId: String?,
    targetAwemeId: String?,
    standbyPrepareKey: String?,
    targetPrepareKey: String?,
    isReady: Boolean,
    hasRenderedFirstFrame: Boolean,
    hasError: Boolean
): Boolean {
    if (standbyAwemeId.isNullOrBlank() || targetAwemeId.isNullOrBlank()) return false
    if (standbyPrepareKey.isNullOrBlank() || targetPrepareKey.isNullOrBlank()) return false
    if (hasError) return false
    if (!isReady && !hasRenderedFirstFrame) return false
    return standbyAwemeId == targetAwemeId && standbyPrepareKey == targetPrepareKey
}

internal fun shouldUseDouyinImmediateEntryPlayback(
    activePage: Int,
    activeItemIndex: Int?,
    entryStartIndex: Int
): Boolean {
    return activePage == 1 && activeItemIndex == entryStartIndex
}

internal fun shouldShowDouyinPosterFallback(
    pagerPage: Int,
    currentPagerPage: Int,
    playbackAnchorPagerPage: Int,
    isScrollInProgress: Boolean,
    isVideoVisible: Boolean,
    hasError: Boolean
): Boolean {
    if (pagerPage <= 0 || hasError || isVideoVisible) return false
    return if (isScrollInProgress) {
        abs(pagerPage - currentPagerPage) <= 1
    } else {
        pagerPage == playbackAnchorPagerPage
    }
}

internal fun shouldUseDouyinTransitionPosterFallback(
    pagerPage: Int,
    playbackAnchorPagerPage: Int,
    isScrollInProgress: Boolean
): Boolean {
    if (!isScrollInProgress || pagerPage <= 0) return false
    return pagerPage != playbackAnchorPagerPage
}

internal fun shouldPrewarmDouyinLookaheadSlot(
    showTitlePage: Boolean,
    isForegroundSlot: Boolean,
    hasMatchingLookaheadTarget: Boolean,
    hasRenderedFirstFrame: Boolean,
    hasError: Boolean,
    hasTextureView: Boolean,
    isPrewarming: Boolean
): Boolean {
    if (showTitlePage || isForegroundSlot) return false
    if (isPrewarming) return false
    if (!hasMatchingLookaheadTarget || hasRenderedFirstFrame || hasError) return false
    return hasTextureView
}

internal fun shouldShowDouyinLoadingIndicator(
    isActive: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    hasError: Boolean
): Boolean {
    return isActive && isBuffering && !isPlaying && !hasError
}

internal fun shouldAutoPlayDouyinActiveSlot(
    showTitlePage: Boolean,
    pausedByGesture: Boolean,
    pausedByLifecycle: Boolean,
    hasError: Boolean
): Boolean {
    return !showTitlePage && !pausedByGesture && !pausedByLifecycle && !hasError
}

internal fun shouldPlayBoundDouyinForegroundSlot(
    showTitlePage: Boolean,
    autoplayEnabled: Boolean,
    pausedByGesture: Boolean,
    pausedByLifecycle: Boolean,
    hasError: Boolean,
    isScrollInProgress: Boolean
): Boolean {
    return autoplayEnabled &&
        !isScrollInProgress &&
        shouldAutoPlayDouyinActiveSlot(
            showTitlePage = showTitlePage,
            pausedByGesture = pausedByGesture,
            pausedByLifecycle = pausedByLifecycle,
            hasError = hasError
        )
}

private suspend fun awaitDouyinFrames(frameCount: Int) {
    repeat(frameCount.coerceAtLeast(0)) {
        withFrameNanos { }
    }
}

private const val TITLE_ORIGINAL_FIRST_LINE_RATIO = 0.68f
private const val TITLE_ORIGINAL_SECOND_LINE_RATIO = 0.82f
private const val DOUYIN_MAX_AUTO_RETRY_COUNT = 0
private const val DOUYIN_AUTO_SKIP_MESSAGE = "当前视频无法播放\n已为您自动跳过"
private const val DOUYIN_AUTO_SKIP_MESSAGE_DURATION_MS = 2_000L
private const val DOUYIN_SETTLE_AUTOPLAY_DELAY_FRAMES = 3
private const val DOUYIN_POSTER_CAPTURE_RETRY_FRAMES = 4
private const val DOUYIN_TITLE_PREWARM_TIMEOUT_MS = 1_500L
private const val DOUYIN_LOOKAHEAD_PREWARM_TIMEOUT_MS = 1_000L
private const val DOUYIN_PAGER_SNAP_POSITIONAL_THRESHOLD = 0.18f
private const val DOUYIN_INJECTED_FAILURE_URI_PREFIX = "watchrss-debug://douyin/force-fail/"
private const val DOUYIN_PLAYER_RECENT_HISTORY_SIZE = DOUYIN_PLAYBACK_PREVIEW_ENTRY_LIMIT
private const val TAG = "DouyinImmersive"
