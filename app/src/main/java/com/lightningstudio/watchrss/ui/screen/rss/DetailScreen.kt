package com.lightningstudio.watchrss.ui.screen.rss

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.Trace
import android.text.TextPaint
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ImagePreviewActivity
import com.lightningstudio.watchrss.ShareQrActivity
import com.lightningstudio.watchrss.RssPlayerActivity
import com.lightningstudio.watchrss.WebViewActivity
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.data.rss.OfflineMedia
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.RssUrlResolver
import com.lightningstudio.watchrss.data.settings.DEFAULT_READING_FONT_SIZE_SP
import com.lightningstudio.watchrss.ui.theme.WatchBackgroundDeep
import com.lightningstudio.watchrss.ui.theme.WatchReadingBackgroundLight
import com.lightningstudio.watchrss.ui.theme.WatchReadingTextLight
import com.lightningstudio.watchrss.ui.theme.WatchTextPrimary
import com.lightningstudio.watchrss.ui.util.ContentBlock
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import com.lightningstudio.watchrss.ui.util.TextStyle as ContentTextStyle
import com.lightningstudio.watchrss.ui.viewmodel.DetailViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: (Long, Boolean, Boolean) -> Unit
) {
    val item by viewModel.item.collectAsState()
    val channel by viewModel.channel.collectAsState()
    val savedState by viewModel.savedState.collectAsState()
    val offlineMedia by viewModel.offlineMedia.collectAsState()
    val contentBlocks by viewModel.contentBlocks.collectAsState()
    val readingThemeDark by viewModel.readingThemeDark.collectAsState()
    val readingFontSizeSp by viewModel.readingFontSizeSp.collectAsState()
    val shareUseSystem by viewModel.shareUseSystem.collectAsState(initial = false)

    val hasOfflineFailures = remember(offlineMedia) { offlineMedia.any { it.localPath == null } }
    val offlineMap = remember(offlineMedia) { offlineMedia.associateBy { it.originUrl } }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
        DetailContent(
            item = item,
            showOriginalLoadingNotice = channel?.useOriginalContent == true &&
                item?.content.isNullOrBlank(),
            contentBlocks = contentBlocks,
            offlineMedia = offlineMap,
            hasOfflineFailures = hasOfflineFailures,
            isFavorite = savedState.isFavorite,
            isWatchLater = savedState.isWatchLater,
            readingThemeDark = readingThemeDark,
            readingFontSizeSp = readingFontSizeSp,
            shareUseSystem = shareUseSystem,
            onToggleFavorite = viewModel::toggleFavorite,
            onRetryOfflineMedia = viewModel::retryOfflineMedia,
            onSaveReadingProgress = viewModel::updateReadingProgress,
            onBack = onBack
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
internal fun DetailContent(
    item: RssItem?,
    showOriginalLoadingNotice: Boolean,
    contentBlocks: List<ContentBlock>,
    offlineMedia: Map<String, OfflineMedia>,
    hasOfflineFailures: Boolean,
    isFavorite: Boolean,
    isWatchLater: Boolean,
    readingThemeDark: Boolean,
    readingFontSizeSp: Int,
    shareUseSystem: Boolean,
    onToggleFavorite: () -> Unit,
    onRetryOfflineMedia: () -> Unit,
    onSaveReadingProgress: (Float) -> Unit,
    onBack: (Long, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val pagePadding = dimensionResource(R.dimen.detail_page_horizontal_padding)
    val blockSpacing = dimensionResource(R.dimen.detail_block_spacing)
    val titlePadding = dimensionResource(R.dimen.detail_title_safe_padding)
    val actionVerticalSpacing = 12.dp
    val actionHorizontalSpacing = 12.dp
    val actionIconSize = 32.dp
    val actionIconPadding = dimensionResource(R.dimen.hey_distance_6dp)
    val extraSafePadding = 0.dp

    val backgroundColor = if (readingThemeDark) WatchBackgroundDeep else WatchReadingBackgroundLight
    val textColor = if (readingThemeDark) WatchTextPrimary else WatchReadingTextLight
    val activeColor = MaterialTheme.colorScheme.primary
    val normalIconColor = textColor
    val prefetchScope = rememberCoroutineScope()

    val maxImageWidthPx = remember(context) {
        val pagePaddingPx =
            context.resources.getDimensionPixelSize(R.dimen.detail_page_horizontal_padding)
        (context.resources.displayMetrics.widthPixels - pagePaddingPx * 2).coerceAtLeast(1)
    }

    var pendingRestoreProgress by remember { mutableStateOf<Float?>(null) }
    var hasRestoredPosition by remember { mutableStateOf(false) }
    var lastItemId by remember { mutableStateOf<Long?>(null) }
    var lastSavedProgress by remember { mutableStateOf(-1f) }
    var lastProgressSavedAt by remember { mutableStateOf(0L) }

    val onSaveReadingProgressState = rememberUpdatedState(onSaveReadingProgress)
    val onBackState = rememberUpdatedState(onBack)
    val isWatchLaterState = rememberUpdatedState(isWatchLater)
    val hasRestoredPositionState = rememberUpdatedState(hasRestoredPosition)
    val offlineMediaState = rememberUpdatedState(offlineMedia)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(item?.id) {
        val itemId = item?.id ?: return@LaunchedEffect
        if (itemId != lastItemId) {
            lastItemId = itemId
            pendingRestoreProgress = item?.readingProgress
            hasRestoredPosition = false
            lastSavedProgress = -1f
            lastProgressSavedAt = 0L
        }
    }

    LaunchedEffect(readingFontSizeSp, readingThemeDark) {
        if (item == null || !hasRestoredPosition) return@LaunchedEffect
        pendingRestoreProgress = calculateReadingProgress(listState)
        hasRestoredPosition = false
    }

    LaunchedEffect(pendingRestoreProgress, listState.layoutInfo.totalItemsCount) {
        val progress = pendingRestoreProgress ?: return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems == 0) {
            if (progress <= 0f) {
                pendingRestoreProgress = null
                hasRestoredPosition = true
            }
            return@LaunchedEffect
        }
        val target = ((totalItems - 1) * progress)
            .roundToInt()
            .coerceIn(0, totalItems - 1)
        listState.scrollToItem(target)
        pendingRestoreProgress = null
        hasRestoredPosition = true
    }

    val readingProgressFlow = remember(listState) {
        snapshotFlow { calculateReadingProgress(listState) }
            .distinctUntilChanged()
            .shareIn(coroutineScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    LaunchedEffect(readingProgressFlow) {
        readingProgressFlow
            .sample(400)
            .collectLatest { readingProgress ->
                if (hasRestoredPositionState.value) {
                    maybeSaveReadingProgress(
                        readingProgress = readingProgress,
                        force = false,
                        lastSavedProgress = { lastSavedProgress },
                        lastProgressSavedAt = { lastProgressSavedAt },
                        updateLastSavedProgress = { lastSavedProgress = it },
                        updateLastProgressSavedAt = { lastProgressSavedAt = it },
                        onSave = onSaveReadingProgressState.value
                    )
                }
            }
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                maybeSaveReadingProgress(
                    readingProgress = calculateReadingProgress(listState),
                    force = true,
                    lastSavedProgress = { lastSavedProgress },
                    lastProgressSavedAt = { lastProgressSavedAt },
                    updateLastSavedProgress = { lastSavedProgress = it },
                    updateLastProgressSavedAt = { lastProgressSavedAt = it },
                    onSave = onSaveReadingProgressState.value
                )
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        val progress = calculateReadingProgress(listState)
        maybeSaveReadingProgress(
            readingProgress = progress,
            force = true,
            lastSavedProgress = { lastSavedProgress },
            lastProgressSavedAt = { lastProgressSavedAt },
            updateLastSavedProgress = { lastSavedProgress = it },
            updateLastProgressSavedAt = { lastProgressSavedAt = it },
            onSave = onSaveReadingProgressState.value
        )
        val thresholdPx = with(density) { 8.dp.toPx() }
        val reachedBottom = isReachedBottom(listState, thresholdPx)
        onBackState.value(item?.id ?: 0L, reachedBottom, isWatchLaterState.value)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val isScrolling by remember(listState) {
            derivedStateOf { listState.isScrollInProgress }
        }
        val rootView = LocalView.current
        val originalAccessibility = remember(rootView) { rootView.importantForAccessibility }
        val originalAccessibilityDelegate = remember(rootView) { rootView.captureAccessibilityDelegate() }
        val disabledAccessibilityDelegate = remember { View.AccessibilityDelegate() }

        DisposableEffect(rootView) {
            rootView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            rootView.accessibilityDelegate = disabledAccessibilityDelegate
            onDispose {
                rootView.importantForAccessibility = originalAccessibility
                rootView.accessibilityDelegate = originalAccessibilityDelegate
            }
        }
        val bodyFontSize = remember(readingFontSizeSp, density, context) {
            adjustedTextSizeSp(
                context = context,
                density = density,
                baseDimenRes = R.dimen.detail_body_text_size,
                currentFontSizeSp = readingFontSizeSp
            )
        }
        val titleBlockFontSize = remember(readingFontSizeSp, density, context) {
            adjustedTextSizeSp(
                context = context,
                density = density,
                baseDimenRes = R.dimen.detail_title_text_size,
                currentFontSizeSp = readingFontSizeSp
            )
        }
        val subtitleBlockFontSize = remember(readingFontSizeSp, density, context) {
            adjustedTextSizeSp(
                context = context,
                density = density,
                baseDimenRes = R.dimen.detail_subtitle_text_size,
                currentFontSizeSp = readingFontSizeSp
            )
        }
        val link = item?.link?.trim().orEmpty()
        val baseLink = link.takeIf { it.isNotBlank() }
        val baseItemCount = remember(link, hasOfflineFailures) {
            4 + (if (link.isNotEmpty()) 1 else 0) + (if (hasOfflineFailures) 1 else 0)
        }
        val prefetchedUrls = remember(item?.id) { mutableSetOf<String>() }
        val blockPrefetchTargets = remember(contentBlocks) {
            contentBlocks.map { block ->
                buildPrefetchTargets(block)
            }
        }

        LaunchedEffect(blockPrefetchTargets, maxImageWidthPx) {
            if (blockPrefetchTargets.isEmpty()) return@LaunchedEffect
            val targets = withContext(Dispatchers.Default) {
                collectPrefetchTargets(
                    blockPrefetchTargets = blockPrefetchTargets,
                    startIndex = 0,
                    maxIndex = blockPrefetchTargets.lastIndex,
                    maxTargets = PREFETCH_MEDIA_COUNT,
                    scanLimit = PREFETCH_SCAN_LIMIT
                )
            }
            var prefetched = 0
            targets.forEach { target ->
                if (prefetched >= PREFETCH_MEDIA_COUNT) return@forEach
                val key = target.cacheKey(maxImageWidthPx)
                if (!prefetchedUrls.add(key)) return@forEach
                prefetched++
                val resolvedUrl = resolveMediaUrl(target.url, offlineMediaState.value, baseLink)
                when (target.type) {
                    PrefetchType.Image ->
                        if (resolvedUrl.isNotBlank()) {
                            RssImageLoader.preload(context, resolvedUrl, prefetchScope, maxImageWidthPx)
                        }
                    PrefetchType.VideoFrame -> {
                        if (isLocalMedia(resolvedUrl)) {
                            loadCachedVideoFrame(context, resolvedUrl, maxImageWidthPx)
                        }
                    }
                }
            }
        }

        LaunchedEffect(listState, blockPrefetchTargets, baseItemCount, maxImageWidthPx) {
            if (blockPrefetchTargets.isEmpty()) return@LaunchedEffect
            val maxIndex = blockPrefetchTargets.lastIndex
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: baseItemCount
            }
                .distinctUntilChanged()
                .sample(120)
                .collectLatest { lastIndex ->
                    val startIndex = (lastIndex - baseItemCount + 1).coerceAtLeast(0)
                    val targets = withContext(Dispatchers.Default) {
                        collectPrefetchTargets(
                            blockPrefetchTargets = blockPrefetchTargets,
                            startIndex = startIndex,
                            maxIndex = maxIndex,
                            maxTargets = PREFETCH_MEDIA_COUNT,
                            scanLimit = PREFETCH_SCAN_LIMIT
                        )
                    }
                    var prefetched = 0
                    targets.forEach { target ->
                        if (prefetched >= PREFETCH_MEDIA_COUNT) return@forEach
                        val key = target.cacheKey(maxImageWidthPx)
                        if (!prefetchedUrls.add(key)) return@forEach
                        prefetched++
                        val resolvedUrl = resolveMediaUrl(target.url, offlineMediaState.value, baseLink)
                        when (target.type) {
                            PrefetchType.Image ->
                                if (resolvedUrl.isNotBlank()) {
                                    RssImageLoader.preload(
                                        context,
                                        resolvedUrl,
                                        prefetchScope,
                                        maxImageWidthPx
                                    )
                                }
                            PrefetchType.VideoFrame -> {
                                if (isLocalMedia(resolvedUrl)) {
                                    loadCachedVideoFrame(context, resolvedUrl, maxImageWidthPx)
                                }
                            }
                        }
                    }
                }
        }

        val listSemanticsModifier = Modifier.clearAndSetSemantics { }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(listSemanticsModifier),
            state = listState,
            contentPadding = PaddingValues(horizontal = pagePadding)
        ) {
            item(key = "topSpacer") {
                Spacer(modifier = Modifier.height(safePadding + extraSafePadding))
            }
            item(key = "titleGap") {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.hey_distance_4dp)))
            }
            item(key = "title") {
                DetailTitle(
                    title = item?.title ?: "加载中...",
                    titlePadding = titlePadding,
                    textColor = textColor
                )
            }
            if (link.isNotEmpty()) {
                item(key = "linkAction") {
                    Spacer(modifier = Modifier.height(blockSpacing))
                    DetailActionButton(
                        text = "打开原文",
                        fontSize = bodyFontSize,
                        onClick = { openLinkInApp(context, link) }
                    )
                }
            }
            if (hasOfflineFailures) {
                item(key = "offlineAction") {
                    Spacer(modifier = Modifier.height(blockSpacing))
                    DetailActionButton(
                        text = "离线媒体下载失败，点此重试",
                        fontSize = bodyFontSize,
                        onClick = onRetryOfflineMedia
                    )
                }
            }
            item(key = "contentGap") {
                Spacer(modifier = Modifier.height(blockSpacing))
            }
            if (showOriginalLoadingNotice) {
                item(key = "originalLoading") {
                    DetailTextBlock(
                        text = "原文加载中，您正在查看RSS内容...",
                        style = ContentTextStyle.QUOTE,
                        textColor = textColor,
                        fontSizeSp = bodyFontSize,
                        topPadding = 0.dp,
                        isScrolling = isScrolling
                    )
                }
            }
            if (item == null) {
                item(key = "loading") {
                    // Keep layout consistent while content loads.
                }
            } else if (contentBlocks.isEmpty()) {
                item(key = "emptyContent") {
                    DetailTextBlock(
                        text = "暂无正文",
                        style = ContentTextStyle.BODY,
                        textColor = textColor,
                        fontSizeSp = bodyFontSize,
                        topPadding = 0.dp,
                        isScrolling = isScrolling
                    )
                }
            } else {
                itemsIndexed(
                    items = contentBlocks,
                    key = { index, block ->
                        when (block) {
                            is ContentBlock.Image -> "img:$index:${block.url}"
                            is ContentBlock.Video ->
                                "vid:$index:${block.url}:${block.poster.orEmpty()}"
                            is ContentBlock.Text ->
                                "txt:${block.style}:${block.text.hashCode()}:$index"
                        }
                    },
                    contentType = { _, block ->
                        when (block) {
                            is ContentBlock.Text -> "text_${block.style}"
                            is ContentBlock.Image -> "image"
                            is ContentBlock.Video -> "video"
                        }
                    }
                ) { index, block ->
                    val topPadding = if (index == 0) 0.dp else blockSpacing
                    when (block) {
                        is ContentBlock.Text -> {
                            val blockFontSize = when (block.style) {
                                ContentTextStyle.TITLE -> titleBlockFontSize
                                ContentTextStyle.SUBTITLE -> subtitleBlockFontSize
                                ContentTextStyle.QUOTE -> bodyFontSize
                                ContentTextStyle.CODE -> bodyFontSize
                                ContentTextStyle.BODY -> bodyFontSize
                            }
                            DetailTextBlock(
                                text = block.text,
                                style = block.style,
                                textColor = textColor,
                                fontSizeSp = blockFontSize,
                                topPadding = topPadding,
                                isScrolling = isScrolling
                            )
                        }
                        is ContentBlock.Image -> {
                            val resolvedUrl = resolveMediaUrl(block.url, offlineMedia, baseLink)
                            DetailImageBlock(
                                url = resolvedUrl,
                                alt = block.alt,
                                maxWidthPx = maxImageWidthPx,
                                topPadding = topPadding,
                                isScrolling = isScrolling,
                                onClick = { openImagePreview(context, resolvedUrl, block.alt) }
                            )
                        }
                        is ContentBlock.Video -> {
                            val resolvedUrl = resolveMediaUrl(block.url, offlineMedia, baseLink)
                            val webUrl = resolveRemoteUrl(block.url, baseLink)
                            DetailVideoBlock(
                                poster = block.poster?.let { resolveMediaUrl(it, offlineMedia, baseLink) },
                                videoUrl = resolvedUrl,
                                maxWidthPx = maxImageWidthPx,
                                topPadding = topPadding,
                                isScrolling = isScrolling,
                                onClick = { openRssVideo(context, resolvedUrl, webUrl) }
                            )
                        }
                    }
                }
            }
            item(key = "actionSpacing") {
                Spacer(modifier = Modifier.height(actionVerticalSpacing))
            }
            item(key = "actions") {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FavoriteButtonWithStars(
                            isFavorite = isFavorite,
                            activeColor = activeColor,
                            normalIconColor = normalIconColor,
                            iconSize = actionIconSize,
                            iconPadding = actionIconPadding,
                            enabled = !isScrolling,
                            onClick = onToggleFavorite
                        )
                        Spacer(modifier = Modifier.width(actionHorizontalSpacing))
                        CircleIconButton(
                            iconRes = R.drawable.ic_action_share,
                            contentDescription = "分享",
                            tint = normalIconColor,
                            size = actionIconSize,
                            padding = actionIconPadding,
                            iconOffsetX = (-1).dp,
                            enabled = !isScrolling,
                            onClick = {
                                val title = item?.title.orEmpty()
                                val shareLink = item?.link?.trim().orEmpty().ifBlank { null }
                                if (shareUseSystem) {
                                    shareCurrent(context, title, shareLink)
                                } else {
                                    showShareQr(context, title, shareLink)
                                }
                            }
                        )
                    }
                }
            }
            item(key = "bottomSpacer") {
                Spacer(modifier = Modifier.height(actionVerticalSpacing))
            }
        }

    }
}

@Composable
internal fun DetailTitle(
    title: String,
    titlePadding: Dp,
    textColor: Color
) {
    val hintSize = textSize(R.dimen.hey_m_title)
    val context = LocalContext.current
    val density = LocalDensity.current
    val titleSizePx = with(density) { dimensionResource(R.dimen.hey_m_title).toPx() }
    val firstLimitPx = with(density) {
        dimensionResource(R.dimen.detail_title_first_line_max_width).toPx()
    }
    val secondLimitPx = with(density) {
        dimensionResource(R.dimen.detail_title_second_line_max_width).toPx()
    }
    val typeface = remember(context) { ResourcesCompat.getFont(context, R.font.watch_sans) }
    val paint = remember(typeface, titleSizePx) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = titleSizePx
            this.typeface = typeface
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = titlePadding)
    ) {
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val formattedTitle = remember(title, availableWidthPx, titleSizePx, typeface) {
            formatTitleForWidthLimits(
                title = title,
                paint = paint,
                availableWidthPx = availableWidthPx,
                firstLimitPx = firstLimitPx,
                secondLimitPx = secondLimitPx
            )
        }
        Text(
            text = formattedTitle,
            color = textColor,
            fontSize = hintSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun DetailActionButton(
    text: String,
    fontSize: TextUnit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_button_default_radius))
    val padding = PaddingValues(
        horizontal = dimensionResource(R.dimen.hey_content_horizontal_distance),
        vertical = dimensionResource(R.dimen.hey_distance_6dp)
    )
    Box(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableWithoutRipple(onClick = onClick)
            .padding(padding)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSize
        )
    }
}

@Composable
internal fun DetailTextBlock(
    text: String,
    style: ContentTextStyle,
    textColor: Color,
    fontSizeSp: TextUnit,
    topPadding: Dp,
    isScrolling: Boolean
) {
    if (BuildConfig.DEBUG) {
        Trace.beginSection("DetailTextBlock:${style.name}")
    }
    val lineHeight = fontSizeSp * 1.2f
    val fontFamily = if (style == ContentTextStyle.CODE) {
        FontFamily.Monospace
    } else {
        null
    }
    val color = if (style == ContentTextStyle.QUOTE) {
        textColor.copy(alpha = 0.8f)
    } else {
        textColor
    }
    Text(
        text = text,
        color = color,
        fontSize = fontSizeSp,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        style = TextStyle(textAlign = TextAlign.Start),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .scrollSemanticsDisabled(isScrolling)
            .debugTraceLayout("DetailTextBlock/layout")
            .debugTraceDraw("DetailTextBlock/draw")
    )
    if (BuildConfig.DEBUG) {
        Trace.endSection()
    }
}

@Composable
private fun DetailImageBlock(
    url: String,
    alt: String?,
    maxWidthPx: Int,
    topPadding: Dp,
    isScrolling: Boolean,
    onClick: () -> Unit
) {
    if (BuildConfig.DEBUG) {
        Trace.beginSection("DetailImageBlock")
    }
    val context = LocalContext.current
    val bitmapState = remember(url, maxWidthPx) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url, maxWidthPx, isScrolling) {
        if (isScrolling) return@LaunchedEffect
        if (bitmapState.value != null) return@LaunchedEffect
        if (url.isBlank()) return@LaunchedEffect
        decodeSemaphore.acquire()
        try {
            if (bitmapState.value == null) {
                bitmapState.value = RssImageLoader.loadBitmap(context, url, maxWidthPx)
            }
        } finally {
            decodeSemaphore.release()
        }
    }
    val safeBitmap = bitmapState.value
    val imageBitmap = remember(safeBitmap) { safeBitmap?.asImageBitmap() }
    val ratio = safeBitmap?.let { it.width.toFloat() / it.height.toFloat() }
        ?: RssImageLoader.getCachedAspectRatio(url)
    if (imageBitmap != null && ratio != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = alt,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .aspectRatio(ratio)
                .clickableWithoutRipple(enabled = !isScrolling, onClick = onClick)
                .scrollSemanticsDisabled(isScrolling)
                .debugTraceLayout("DetailImageBlock/layout")
                .debugTraceDraw("DetailImageBlock/draw")
        )
    } else {
        val placeholderModifier = if (ratio != null && ratio > 0f) {
            Modifier.aspectRatio(ratio)
        } else {
            Modifier.height(dimensionResource(R.dimen.hey_card_large_height))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding)
                .then(placeholderModifier)
                .background(MaterialTheme.colorScheme.surface)
                .scrollSemanticsDisabled(isScrolling)
                .debugTraceLayout("DetailImageBlock/placeholder/layout")
                .debugTraceDraw("DetailImageBlock/placeholder/draw")
        )
    }
    if (BuildConfig.DEBUG) {
        Trace.endSection()
    }
}

@Composable
private fun DetailVideoBlock(
    poster: String?,
    videoUrl: String,
    maxWidthPx: Int,
    topPadding: Dp,
    isScrolling: Boolean,
    onClick: () -> Unit
) {
    if (BuildConfig.DEBUG) {
        Trace.beginSection("DetailVideoBlock")
    }
    val context = LocalContext.current
    val coverState =
        remember(poster, videoUrl, maxWidthPx) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val ratioState = remember(poster, videoUrl) { mutableStateOf<Float?>(null) }
    LaunchedEffect(poster, videoUrl, maxWidthPx, isScrolling) {
        if (isScrolling) return@LaunchedEffect
        if (coverState.value != null) return@LaunchedEffect
        if (poster.isNullOrBlank() && !canExtractVideoFrame(videoUrl)) return@LaunchedEffect
        decodeSemaphore.acquire()
        try {
            if (coverState.value == null) {
                coverState.value = when {
                    !poster.isNullOrBlank() -> RssImageLoader.loadBitmap(context, poster, maxWidthPx)
                    canExtractVideoFrame(videoUrl) -> loadCachedVideoFrame(context, videoUrl, maxWidthPx)
                    else -> null
                }
            }
        } finally {
            decodeSemaphore.release()
        }
    }
    LaunchedEffect(poster, videoUrl, maxWidthPx) {
        if (ratioState.value != null) return@LaunchedEffect
        ratioState.value = when {
            !poster.isNullOrBlank() -> {
                RssImageLoader.getCachedAspectRatio(poster)
                    ?: RssImageLoader.preloadAndCacheRatio(context, poster, maxWidthPx)
            }
            canExtractVideoFrame(videoUrl) -> loadCachedVideoRatio(context, videoUrl)
            else -> null
        }
    }
    val coverRatio = coverState.value?.let { it.width.toFloat() / it.height.toFloat() }
        ?: poster?.let { RssImageLoader.getCachedAspectRatio(it) }
        ?: ratioState.value
    val shape = RoundedCornerShape(dimensionResource(R.dimen.hey_card_normal_bg_radius))
    val coverHeight = dimensionResource(R.dimen.hey_card_large_height)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickableWithoutRipple(enabled = !isScrolling, onClick = onClick)
            .scrollSemanticsDisabled(isScrolling)
            .debugTraceLayout("DetailVideoBlock/layout")
            .debugTraceDraw("DetailVideoBlock/draw")
    ) {
        val safeCover = coverState.value
        val coverBitmap = remember(safeCover) { safeCover?.asImageBitmap() }
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = "视频封面",
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (coverRatio != null && coverRatio > 0f) {
                            Modifier.aspectRatio(coverRatio)
                        } else {
                            Modifier.height(coverHeight)
                        }
                    )
            )
        } else {
            val placeholderRatio = coverRatio ?: DEFAULT_VIDEO_ASPECT_RATIO
            val placeholderModifier = if (placeholderRatio > 0f) {
                Modifier.aspectRatio(placeholderRatio)
            } else {
                Modifier.height(coverHeight)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(placeholderModifier)
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_play_circle),
            contentDescription = "播放",
            modifier = Modifier
                .align(Alignment.Center)
                .size(dimensionResource(R.dimen.hey_listitem_widget_size))
        )
    }
    if (BuildConfig.DEBUG) {
        Trace.endSection()
    }
}

private suspend fun loadCachedVideoFrame(
    context: Context,
    url: String,
    maxWidthPx: Int
): Bitmap? {
    val key = "video:$url@$maxWidthPx"
    videoFrameCache.get(key)?.let { return it }
    val frame = loadVideoFrame(context, url, maxWidthPx)
    if (frame != null) {
        cacheVideoAspectRatio(url, frame.width, frame.height, null)
        videoFrameCache.put(key, frame)
    }
    return frame
}

private suspend fun loadVideoFrame(
    context: Context,
    url: String,
    maxWidthPx: Int
): Bitmap? {
    if (url.isBlank()) return null
    return withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            setRetrieverDataSource(retriever, context, url)
            extractVideoAspectRatio(retriever)?.let { videoRatioCache.put(url, it) }
            val dstWidth = maxWidthPx.coerceAtLeast(1)
            val dstHeight = (maxWidthPx * 2).coerceAtLeast(1)
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val params = MediaMetadataRetriever.BitmapParams().apply {
                        setPreferredConfig(Bitmap.Config.RGB_565)
                    }
                    retriever.getScaledFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        dstWidth,
                        dstHeight,
                        params
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> {
                    retriever.getScaledFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        dstWidth,
                        dstHeight
                    )
                }
                else -> {
                    val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: return@withContext null
                    if (maxWidthPx > 0 && frame.width > maxWidthPx) {
                        val height =
                            (frame.height * (maxWidthPx.toFloat() / frame.width)).roundToInt()
                                .coerceAtLeast(1)
                        Bitmap.createScaledBitmap(frame, maxWidthPx, height, true)
                    } else {
                        frame
                    }
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

private suspend fun loadCachedVideoRatio(
    context: Context,
    url: String
): Float? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    videoRatioCache.get(trimmed)?.let { return it }
    val ratio = loadVideoMetadataRatio(context, trimmed)
    if (ratio != null) {
        videoRatioCache.put(trimmed, ratio)
    }
    return ratio
}

private suspend fun loadVideoMetadataRatio(
    context: Context,
    url: String
): Float? {
    return withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            setRetrieverDataSource(retriever, context, url)
            extractVideoAspectRatio(retriever)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

private fun setRetrieverDataSource(
    retriever: MediaMetadataRetriever,
    context: Context,
    url: String
) {
    when {
        url.startsWith("file://") -> retriever.setDataSource(url.removePrefix("file://"))
        url.startsWith("/") -> retriever.setDataSource(url)
        url.startsWith("content://") -> retriever.setDataSource(context, Uri.parse(url))
        else -> retriever.setDataSource(url, VIDEO_FRAME_HEADERS)
    }
}

private fun extractVideoAspectRatio(retriever: MediaMetadataRetriever): Float? {
    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        ?.toIntOrNull()
    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        ?.toIntOrNull()
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull()
        ?: 0
    return if (rotation % 180 != 0) {
        height.toFloat() / width.toFloat()
    } else {
        width.toFloat() / height.toFloat()
    }
}

@Composable
private fun FavoriteButtonWithStars(
    isFavorite: Boolean,
    activeColor: Color,
    normalIconColor: Color,
    iconSize: Dp,
    iconPadding: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var triggerAnimation by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.size(iconSize),
        contentAlignment = Alignment.Center
    ) {
        // 星星粒子动画层
        if (triggerAnimation > 0) {
            StarParticles(
                key = triggerAnimation,
                color = activeColor
            )
        }

        // 收藏按钮
        CircleIconButton(
            iconRes = R.drawable.ic_action_favorite,
            contentDescription = "收藏",
            tint = if (isFavorite) activeColor else normalIconColor,
            size = iconSize,
            padding = iconPadding,
            enabled = enabled,
            onClick = {
                onClick()
                triggerAnimation++
            }
        )
    }
}

@Composable
private fun StarParticles(
    key: Int,
    color: Color
) {
    // 创建8个星星粒子，每个有不同的角度
    val particleCount = 8
    val angles = remember { List(particleCount) { it * 360f / particleCount } }

    angles.forEach { angle ->
        StarParticle(
            key = key,
            angle = angle,
            color = color
        )
    }
}

@Composable
private fun StarParticle(
    key: Int,
    angle: Float,
    color: Color
) {
    // 动画状态
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(key) {
        // 重置所有动画
        progress.snapTo(0f)
        scale.snapTo(0f)
        rotation.snapTo(0f)
        alpha.snapTo(1f)

        // 并行启动所有动画
        launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            rotation.animateTo(
                targetValue = 720f,
                animationSpec = tween(durationMillis = 1200)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 1200,
                    delayMillis = 200
                )
            )
        }
    }

    // 计算星星位置
    val distance = 60f * progress.value
    val radians = Math.toRadians(angle.toDouble())
    val offsetX = (distance * kotlin.math.cos(radians)).toFloat()
    val offsetY = (distance * kotlin.math.sin(radians)).toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha.value),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "★",
            color = color,
            fontSize = with(LocalDensity.current) { (16.dp * scale.value).toSp() },
            modifier = Modifier
                .offset(x = with(LocalDensity.current) { offsetX.toDp() }, y = with(LocalDensity.current) { offsetY.toDp() })
                .scale(scale.value)
                .rotate(rotation.value)
        )
    }
}

@Composable
private fun CircleIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    size: Dp,
    padding: Dp,
    iconOffsetX: Dp = 0.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableWithoutRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .offset(x = iconOffsetX),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
        )
    }
}

@Composable
private fun textSize(id: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { dimensionResource(id).toSp() }
}

@Composable
private fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    return clickable(
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

internal fun adjustedTextSizeSp(
    context: Context,
    density: androidx.compose.ui.unit.Density,
    baseDimenRes: Int,
    currentFontSizeSp: Int
): TextUnit {
    val basePx = context.resources.getDimension(baseDimenRes)
    val deltaSp = (currentFontSizeSp - DEFAULT_READING_FONT_SIZE_SP).toFloat()
    val deltaPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        deltaSp,
        context.resources.displayMetrics
    )
    val sizePx = (basePx + deltaPx).coerceAtLeast(10f)
    return with(density) { sizePx.toSp() }
}

private fun resolveMediaUrl(
    url: String,
    offlineMedia: Map<String, OfflineMedia>,
    baseLink: String?
): String {
    val local = offlineMedia[url]?.localPath
    if (!local.isNullOrBlank()) return local
    return RssUrlResolver.resolveMediaUrl(url, baseLink) ?: url
}

private fun resolveRemoteUrl(url: String, baseLink: String?): String? {
    return RssUrlResolver.resolveMediaUrl(url, baseLink)
}

private const val PREFETCH_MEDIA_COUNT = 8
private const val PREFETCH_SCAN_LIMIT = 120
private const val VIDEO_FRAME_CACHE_BYTES = 4 * 1024 * 1024

private val decodeSemaphore = Semaphore(permits = 2)

private val videoFrameCache = object : LruCache<String, Bitmap>(VIDEO_FRAME_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}
private val videoRatioCache = object : LruCache<String, Float>(200) {}

private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
private val VIDEO_FRAME_HEADERS = mapOf(
    "User-Agent" to "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"
)

private enum class PrefetchType {
    Image,
    VideoFrame
}

private data class PrefetchTarget(
    val url: String,
    val type: PrefetchType
) {
    fun cacheKey(maxWidthPx: Int): String {
        return when (type) {
            PrefetchType.Image -> url
            PrefetchType.VideoFrame -> "video:$url@$maxWidthPx"
        }
    }
}

private fun buildPrefetchTargets(
    block: ContentBlock
): List<PrefetchTarget> {
    return when (block) {
        is ContentBlock.Image -> {
            val url = block.url.trim()
            if (url.isBlank()) emptyList()
            else listOf(PrefetchTarget(url, PrefetchType.Image))
        }
        is ContentBlock.Video -> {
            val poster = block.poster?.trim().orEmpty()
            if (poster.isNotBlank()) {
                listOf(PrefetchTarget(poster, PrefetchType.Image))
            } else {
                val url = block.url.trim()
                if (url.isBlank()) emptyList()
                else listOf(PrefetchTarget(url, PrefetchType.VideoFrame))
            }
        }
        is ContentBlock.Text -> emptyList()
    }
}

private fun collectPrefetchTargets(
    blockPrefetchTargets: List<List<PrefetchTarget>>,
    startIndex: Int,
    maxIndex: Int,
    maxTargets: Int,
    scanLimit: Int
): List<PrefetchTarget> {
    if (startIndex > maxIndex || blockPrefetchTargets.isEmpty()) return emptyList()
    val result = ArrayList<PrefetchTarget>(maxTargets)
    var blockIndex = startIndex
    var scanned = 0
    while (blockIndex <= maxIndex && result.size < maxTargets && scanned < scanLimit) {
        val targets = blockPrefetchTargets[blockIndex]
        if (targets.isNotEmpty()) {
            for (target in targets) {
                if (result.size >= maxTargets) break
                result.add(target)
            }
        }
        blockIndex++
        scanned++
    }
    return result
}

private fun isLocalMedia(url: String): Boolean {
    return url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")
}

private fun canExtractVideoFrame(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    if (isLocalMedia(trimmed)) return true
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
}

private fun cacheVideoAspectRatio(url: String, width: Int, height: Int, rotation: Int?) {
    if (width <= 0 || height <= 0) return
    val rotated = rotation?.let { it % 180 != 0 } ?: false
    val w = if (rotated) height else width
    val h = if (rotated) width else height
    if (w > 0 && h > 0) {
        videoRatioCache.put(url, w.toFloat() / h.toFloat())
    }
}

private fun calculateReadingProgress(listState: androidx.compose.foundation.lazy.LazyListState): Float {
    if (BuildConfig.DEBUG) {
        Trace.beginSection("ReadingProgress")
    }
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) {
        if (BuildConfig.DEBUG) {
            Trace.endSection()
        }
        return 1f
    }
    if (layoutInfo.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward) {
        if (BuildConfig.DEBUG) {
            Trace.endSection()
        }
        return 1f
    }
    val firstIndex = listState.firstVisibleItemIndex
    val firstOffset = listState.firstVisibleItemScrollOffset
    val firstSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
    val offsetProgress = if (firstSize > 0) firstOffset.toFloat() / firstSize.toFloat() else 0f
    val denominator = (totalItems - 1).coerceAtLeast(1)
    val rawProgress = (firstIndex + offsetProgress) / denominator.toFloat()
    val clamped = rawProgress.coerceIn(0f, 1f)
    if (BuildConfig.DEBUG) {
        Trace.endSection()
    }
    return clamped
}

private fun Modifier.debugTraceLayout(name: String): Modifier {
    if (!BuildConfig.DEBUG) return this
    return this.layout { measurable, constraints ->
        Trace.beginSection(name)
        try {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        } finally {
            Trace.endSection()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.scrollSemanticsDisabled(isScrolling: Boolean): Modifier {
    if (!isScrolling) return this
    return this.semantics { invisibleToUser() }
}

private fun Modifier.debugTraceDraw(name: String): Modifier {
    if (!BuildConfig.DEBUG) return this
    return this.drawWithContent {
        Trace.beginSection(name)
        try {
            drawContent()
        } finally {
            Trace.endSection()
        }
    }
}

private fun View.captureAccessibilityDelegate(): View.AccessibilityDelegate? {
    return runCatching {
        val field = View::class.java.getDeclaredField("mAccessibilityDelegate")
        field.isAccessible = true
        field.get(this) as? View.AccessibilityDelegate
    }.getOrNull()
}

private fun isReachedBottom(
    listState: androidx.compose.foundation.lazy.LazyListState,
    thresholdPx: Float
): Boolean {
    val layoutInfo = listState.layoutInfo
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false
    val bottom = lastVisible.offset + lastVisible.size
    return lastVisible.index >= layoutInfo.totalItemsCount - 1 &&
        bottom >= layoutInfo.viewportEndOffset - thresholdPx
}

private fun maybeSaveReadingProgress(
    readingProgress: Float,
    force: Boolean,
    lastSavedProgress: () -> Float,
    lastProgressSavedAt: () -> Long,
    updateLastSavedProgress: (Float) -> Unit,
    updateLastProgressSavedAt: (Long) -> Unit,
    onSave: (Float) -> Unit
) {
    val clamped = readingProgress.coerceIn(0f, 1f)
    val now = SystemClock.elapsedRealtime()
    if (!force && lastSavedProgress() >= 0f) {
        val diff = abs(clamped - lastSavedProgress())
        if (diff < 0.02f && now - lastProgressSavedAt() < 1500L) return
    }
    updateLastSavedProgress(clamped)
    updateLastProgressSavedAt(now)
    onSave(clamped)
}

private fun openLinkInApp(context: Context, link: String) {
    val trimmed = link.trim()
    if (trimmed.isEmpty()) return
    context.startActivity(WebViewActivity.createIntent(context, trimmed))
}

private fun shareCurrent(context: Context, title: String, link: String?) {
    if (title.isBlank()) return
    val text = if (!link.isNullOrBlank()) {
        "$title\n$link"
    } else {
        title
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

private fun showShareQr(context: Context, title: String, link: String?) {
    val trimmed = link?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        com.lightningstudio.watchrss.ui.util.showAppToast(context, "暂无可分享链接", android.widget.Toast.LENGTH_SHORT)
        return
    }
    context.startActivity(ShareQrActivity.createIntent(context, title, trimmed))
}

private fun openExternalLink(context: Context, link: String) {
    val trimmed = link.trim()
    if (trimmed.isEmpty()) return
    val uri = if (trimmed.startsWith("/")) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(trimmed))
    } else {
        Uri.parse(trimmed)
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
}

private fun openImagePreview(context: Context, url: String, alt: String?) {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return
    context.startActivity(ImagePreviewActivity.createIntent(context, trimmed, alt))
}

private fun openRssVideo(context: Context, playUrl: String, webUrl: String?) {
    val trimmed = playUrl.trim()
    if (trimmed.isEmpty()) return
    context.startActivity(RssPlayerActivity.createIntent(context, trimmed, webUrl))
}

private fun formatTitleForWidthLimits(
    title: String,
    paint: TextPaint,
    availableWidthPx: Float,
    firstLimitPx: Float,
    secondLimitPx: Float
): String {
    val normalized = title.trim().replace('\n', ' ')
    if (normalized.isEmpty()) {
        return title
    }
    val cappedFirst = min(firstLimitPx, availableWidthPx)
    val cappedSecond = min(secondLimitPx, availableWidthPx)
    val lines = mutableListOf<String>()
    var start = 0
    var lineIndex = 0
    while (start < normalized.length) {
        val limitPx = if (lineIndex == 0) cappedFirst else cappedSecond
        val end = breakTextIndex(normalized, start, limitPx, paint)
        if (end <= start) {
            lines.add(normalized.substring(start, start + 1))
            start += 1
        } else {
            lines.add(normalized.substring(start, end))
            start = end
        }
        lineIndex++
    }
    balanceSingleCharLines(lines, paint, cappedFirst, cappedSecond)
    return lines.joinToString("\n")
}

private fun breakTextIndex(
    text: String,
    start: Int,
    maxWidthPx: Float,
    paint: TextPaint
): Int {
    if (start >= text.length || maxWidthPx <= 0f) {
        return text.length
    }
    val count = paint.breakText(text, start, text.length, true, maxWidthPx, null)
    if (count <= 0) {
        return start
    }
    var end = start + count
    while (end < text.length && text[end] == ' ') {
        end++
    }
    return end
}

private fun balanceSingleCharLines(
    lines: MutableList<String>,
    paint: TextPaint,
    firstLimitPx: Float,
    otherLimitPx: Float
) {
    var index = 1
    while (index < lines.size) {
        val current = lines[index]
        if (current.length == 1) {
            val prevIndex = index - 1
            val prev = lines[prevIndex]
            val prevLimit = if (prevIndex == 0) firstLimitPx else otherLimitPx
            val mergedPrev = prev + current
            if (paint.measureText(mergedPrev) <= prevLimit) {
                lines[prevIndex] = mergedPrev
                lines.removeAt(index)
                continue
            }
            if (prev.length > 1) {
                val shiftedPrev = prev.dropLast(1)
                val shiftedCurrent = prev.takeLast(1) + current
                val currentLimit = if (index == 0) firstLimitPx else otherLimitPx
                if (paint.measureText(shiftedCurrent) <= currentLimit) {
                    lines[prevIndex] = shiftedPrev
                    lines[index] = shiftedCurrent
                    if (prevIndex > 0) {
                        index--
                        continue
                    }
                }
            }
            if (index + 1 < lines.size) {
                val next = lines[index + 1]
                if (next.isNotEmpty()) {
                    val mergedCurrent = current + next.first()
                    val currentLimit = if (index == 0) firstLimitPx else otherLimitPx
                    if (paint.measureText(mergedCurrent) <= currentLimit) {
                        lines[index] = mergedCurrent
                        val remaining = next.substring(1)
                        if (remaining.isEmpty()) {
                            lines.removeAt(index + 1)
                            continue
                        } else {
                            lines[index + 1] = remaining
                        }
                    }
                }
            }
        }
        index++
    }
}
