package com.lightningstudio.watchrss.ui.screen.rss

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.RssPlayerActivity
import com.lightningstudio.watchrss.data.douyin.buildDouyinPlaybackWebUrl
import com.lightningstudio.watchrss.data.douyin.parseDouyinAwemeId
import com.lightningstudio.watchrss.data.rss.OfflineMedia
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.settings.RssInlineImagePrefetchMode
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.screen.WarningConfirmDialog
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.WatchReadingBackgroundLight
import com.lightningstudio.watchrss.ui.theme.WatchReadingTextLight
import com.lightningstudio.watchrss.ui.theme.WatchTextPrimary
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.ContentBlock
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import com.lightningstudio.watchrss.ui.util.RssInlineImageLoader
import com.lightningstudio.watchrss.ui.util.TextStyle as ContentTextStyle
import com.lightningstudio.watchrss.ui.util.isSystemShareSettingSupported
import com.lightningstudio.watchrss.ui.viewmodel.DetailViewModel
import com.lightningstudio.watchrss.ui.viewmodel.LlmSummaryUiState
import com.lightningstudio.watchrss.ui.viewmodel.SummaryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    llmSummaryState: LlmSummaryUiState = LlmSummaryUiState(),
    onOpenAiSummary: () -> Unit = {},
    onOpenReadAloud: () -> Unit = {},
    onBack: (Long, Boolean, Boolean) -> Unit
) {
    val item by viewModel.item.collectAsState()
    val channel by viewModel.channel.collectAsState()
    val savedState by viewModel.savedState.collectAsState()
    val offlineMedia by viewModel.offlineMedia.collectAsState()
    val isRetryingOfflineMedia by viewModel.isRetryingOfflineMedia.collectAsState()
    val contentBlocks by viewModel.contentBlocks.collectAsState()
    val readingThemeDark by viewModel.readingThemeDark.collectAsState()
    val readingFontSizeSp by viewModel.readingFontSizeSp.collectAsState()
    val shareUseSystem by viewModel.shareUseSystem.collectAsState(initial = false)
    val rssInlineImagePrefetchMode by viewModel.rssInlineImagePrefetchMode.collectAsState()
    val llmFeatureEnabled by viewModel.llmFeatureEnabled.collectAsState()
    val llmAutoSummarize by viewModel.llmAutoSummarize.collectAsState()
    val effectiveUseOriginalContent by viewModel.effectiveUseOriginalContent.collectAsState()

    val hasOfflineFailures = remember(offlineMedia) { offlineMedia.any { it.localPath == null } }
    val offlineMap = remember(offlineMedia) { offlineMedia.associateBy { it.originUrl } }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
        DetailContent(
            item = item,
            showOriginalLoadingNotice = effectiveUseOriginalContent &&
                item?.originalContent.isNullOrBlank(),
            contentBlocks = contentBlocks,
            offlineMedia = offlineMap,
            hasOfflineFailures = hasOfflineFailures,
            isRetryingOfflineMedia = isRetryingOfflineMedia,
            isFavorite = savedState.isFavorite,
            isWatchLater = savedState.isWatchLater,
            originalContentEnabled = effectiveUseOriginalContent,
            readingThemeDark = readingThemeDark,
            readingFontSizeSp = readingFontSizeSp,
            shareUseSystem = shareUseSystem,
            rssInlineImagePrefetchMode = rssInlineImagePrefetchMode,
            llmFeatureEnabled = llmFeatureEnabled,
            llmAutoSummarize = llmAutoSummarize,
            llmSummaryState = llmSummaryState,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleOriginalContent = viewModel::toggleOriginalContent,
            onRetryOfflineMedia = viewModel::retryOfflineMedia,
            onSaveReadingProgress = viewModel::updateReadingProgress,
            onOpenAiSummary = onOpenAiSummary,
            onOpenReadAloud = onOpenReadAloud,
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
    isRetryingOfflineMedia: Boolean,
    isFavorite: Boolean,
    isWatchLater: Boolean,
    originalContentEnabled: Boolean,
    readingThemeDark: Boolean,
    readingFontSizeSp: Int,
    shareUseSystem: Boolean,
    rssInlineImagePrefetchMode: RssInlineImagePrefetchMode,
    llmFeatureEnabled: Boolean = false,
    llmAutoSummarize: Boolean = false,
    llmSummaryState: LlmSummaryUiState = LlmSummaryUiState(),
    onToggleFavorite: () -> Unit,
    onToggleOriginalContent: () -> Unit,
    onRetryOfflineMedia: () -> Unit,
    onSaveReadingProgress: (Float) -> Unit,
    onOpenAiSummary: () -> Unit = {},
    onOpenReadAloud: () -> Unit = {},
    onBack: (Long, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val useSystemShare = remember(context, shareUseSystem) {
        shareUseSystem && isSystemShareSettingSupported(context)
    }
    val listState = rememberLazyListState()
    InstallDigitalCrownLazyListHandler(listState)
    val safePadding = WatchDimens.watch_safe_padding
    val pagePadding = WatchDimens.detail_page_horizontal_padding
    val blockSpacing = WatchDimens.detail_block_spacing
    val titlePadding = WatchDimens.detail_title_safe_padding
    val actionVerticalSpacing = 15.dp
    val actionHorizontalSpacing = 12.dp
    val actionIconSize = 32.dp
    val actionIconPadding = watchDimensionResource(R.dimen.hey_distance_6dp)
    val extraSafePadding = 0.dp

    val backgroundColor = if (readingThemeDark) Color.Black else WatchReadingBackgroundLight
    val textColor = if (readingThemeDark) WatchTextPrimary else WatchReadingTextLight
    val activeColor = MaterialTheme.colorScheme.primary
    val normalIconColor = textColor
    val actionContainerColor = if (readingThemeDark) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.White.copy(alpha = 0.96f)
    }
    val actionBorderColor = if (readingThemeDark) {
        Color.Transparent
    } else {
        Color(0xFFD9CFC3)
    }
    val activeActionContainerColor = if (readingThemeDark) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color(0xFFFFF0E6)
    }
    val activeActionBorderColor = if (readingThemeDark) {
        Color.Transparent
    } else {
        activeColor.copy(alpha = 0.6f)
    }
    val mediaCardContainerColor = if (readingThemeDark) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.White
    }
    val mediaCardBorderColor = Color.Transparent
    val prefetchScope = rememberCoroutineScope()
    var showOriginalModeWarning by remember(item?.id, item?.link) { mutableStateOf(false) }

    val maxImageWidthPx = remember(context) {
        val pagePaddingPx = with(density) { pagePadding.roundToPx() }
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

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { isScrolling ->
                if (isScrolling || !hasRestoredPositionState.value) return@collectLatest
                maybeSaveReadingProgress(
                    readingProgress = calculateReadingProgress(listState),
                    force = false,
                    lastSavedProgress = { lastSavedProgress },
                    lastProgressSavedAt = { lastProgressSavedAt },
                    updateLastSavedProgress = { lastSavedProgress = it },
                    updateLastProgressSavedAt = { lastProgressSavedAt = it },
                    onSave = onSaveReadingProgressState.value
                )
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
            .semantics {
                contentDescription = "文章详情页面"
            }
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
        val baseItemCount = remember(link, hasOfflineFailures, isRetryingOfflineMedia) {
            4 + (if (link.isNotEmpty()) 1 else 0) +
                (if (hasOfflineFailures || isRetryingOfflineMedia) 1 else 0)
        }
        val prefetchedUrls = remember(item?.id) { mutableSetOf<String>() }
        val articlePrefetchedUrls = remember(item?.id) { mutableSetOf<String>() }
        val blockPrefetchTargets = remember(contentBlocks) {
            contentBlocks.map(::buildPrefetchTargets)
        }
        val articleInlineImageUrls = remember(contentBlocks, offlineMedia, baseLink) {
            contentBlocks.asSequence()
                .filterIsInstance<ContentBlock.Image>()
                .map { block -> resolveMediaUrl(block.url, offlineMedia, baseLink) }
                .filter { url -> url.isNotBlank() && !isLocalMedia(url) }
                .distinct()
                .toList()
        }

        LaunchedEffect(item?.id, articleInlineImageUrls, rssInlineImagePrefetchMode, maxImageWidthPx) {
            val prefetchCount = rssInlineImagePrefetchMode.prefetchCount
            if (prefetchCount == 0 || articleInlineImageUrls.isEmpty()) return@LaunchedEffect
            val targets = if (prefetchCount == null) {
                articleInlineImageUrls
            } else {
                articleInlineImageUrls.take(prefetchCount)
            }
            targets.forEach { url ->
                val key = "$url@$maxImageWidthPx"
                if (!articlePrefetchedUrls.add(key)) return@forEach
                launch {
                    RssInlineImageLoader.prefetch(context, url, maxImageWidthPx)
                }
            }
        }

        LaunchedEffect(blockPrefetchTargets, maxImageWidthPx, isScrolling) {
            if (isScrolling || blockPrefetchTargets.isEmpty()) return@LaunchedEffect
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
                        if (resolvedUrl.isNotBlank() && isLocalMedia(resolvedUrl)) {
                            RssImageLoader.preload(context, resolvedUrl, prefetchScope, maxImageWidthPx)
                        }
                    PrefetchType.VideoFrame ->
                        if (isLocalMedia(resolvedUrl)) {
                            loadCachedVideoFrame(context, resolvedUrl, maxImageWidthPx)
                        }
                }
            }
        }

        LaunchedEffect(listState, blockPrefetchTargets, baseItemCount, maxImageWidthPx, isScrolling) {
            if (isScrolling || blockPrefetchTargets.isEmpty()) return@LaunchedEffect
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
                                if (resolvedUrl.isNotBlank() && isLocalMedia(resolvedUrl)) {
                                    RssImageLoader.preload(
                                        context,
                                        resolvedUrl,
                                        prefetchScope,
                                        maxImageWidthPx
                                    )
                                }
                            PrefetchType.VideoFrame ->
                                if (isLocalMedia(resolvedUrl)) {
                                    loadCachedVideoFrame(context, resolvedUrl, maxImageWidthPx)
                                }
                        }
                    }
                }
        }

        val listSemanticsModifier = Modifier.clearAndSetSemantics { }
        val showAiBanner = llmFeatureEnabled && llmAutoSummarize &&
            (llmSummaryState.status == SummaryStatus.WaitingForContent ||
                llmSummaryState.status == SummaryStatus.Generating ||
                llmSummaryState.text.isNotBlank() ||
                llmSummaryState.status is SummaryStatus.Error)
        val showAiButton = llmFeatureEnabled && !llmAutoSummarize
        val showReadAloudAction = BuildConfig.DEBUG && item != null

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
                Spacer(modifier = Modifier.height(watchDimensionResource(R.dimen.hey_distance_4dp)))
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
                        text = if (originalContentEnabled) "取消阅读原文" else "阅读原文",
                        fontSize = bodyFontSize,
                        containerColor = actionContainerColor,
                        contentColor = textColor,
                        borderColor = actionBorderColor,
                        onClick = onToggleOriginalContent,
                        onLongClick = { showOriginalModeWarning = true }
                    )
                }
            }
            if (showReadAloudAction) {
                item(key = "readAloudAction") {
                    Spacer(modifier = Modifier.height(blockSpacing))
                    DetailActionButton(
                        text = "大声朗读",
                        fontSize = bodyFontSize,
                        containerColor = activeActionContainerColor,
                        contentColor = activeColor,
                        borderColor = activeActionBorderColor,
                        onClick = onOpenReadAloud
                    )
                }
            }
            if (hasOfflineFailures || isRetryingOfflineMedia) {
                item(key = "offlineAction") {
                    Spacer(modifier = Modifier.height(blockSpacing))
                    DetailActionButton(
                        text = if (isRetryingOfflineMedia) "重试中..." else "离线媒体下载失败，点此重试",
                        fontSize = bodyFontSize,
                        containerColor = actionContainerColor,
                        contentColor = textColor,
                        borderColor = actionBorderColor,
                        enabled = !isRetryingOfflineMedia,
                        onClick = onRetryOfflineMedia
                    )
                }
            }
            if (showAiBanner) {
                item(key = "aiSummaryBanner") {
                    Spacer(modifier = Modifier.height(blockSpacing))
                    AiSummaryBanner(
                        summaryState = llmSummaryState,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        activeColor = activeColor,
                        onClick = onOpenAiSummary
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
                item(key = "loading") {}
            } else if (contentBlocks.isEmpty() && !showOriginalLoadingNotice) {
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
                            is ContentBlock.Video -> "vid:$index:${block.url}:${block.poster.orEmpty()}"
                            is ContentBlock.Text -> "txt:${block.style}:${block.text.hashCode()}:$index"
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
                                initialAspectRatio = block.aspectRatio,
                                maxWidthPx = maxImageWidthPx,
                                containerColor = mediaCardContainerColor,
                                borderColor = mediaCardBorderColor,
                                topPadding = topPadding,
                                isScrolling = isScrolling,
                                onClick = { openImagePreview(context, resolvedUrl, block.alt) }
                            )
                        }
                        is ContentBlock.Video -> {
                            val resolvedUrl = resolveMediaUrl(block.url, offlineMedia, baseLink)
                            val defaultWebUrl = resolveRemoteUrl(block.url, baseLink)
                            val douyinAwemeId = item?.link?.let(::parseDouyinAwemeId)
                            val douyinWebUrl = buildDouyinPlaybackWebUrl(
                                awemeId = douyinAwemeId,
                                fallbackUrl = item?.link
                            )
                            DetailVideoBlock(
                                poster = block.poster?.let { resolveMediaUrl(it, offlineMedia, baseLink) },
                                videoUrl = resolvedUrl,
                                maxWidthPx = maxImageWidthPx,
                                containerColor = mediaCardContainerColor,
                                borderColor = mediaCardBorderColor,
                                topPadding = topPadding,
                                isScrolling = isScrolling,
                                onClick = {
                                    val targetWebUrl = douyinWebUrl ?: defaultWebUrl
                                    if (douyinAwemeId != null || !douyinWebUrl.isNullOrBlank()) {
                                        context.startActivity(
                                            RssPlayerActivity.createIntent(
                                                context = context,
                                                playUrl = resolvedUrl,
                                                webUrl = targetWebUrl,
                                                awemeId = douyinAwemeId
                                            )
                                        )
                                    } else {
                                        openRssVideo(context, resolvedUrl, targetWebUrl)
                                    }
                                }
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
                            containerColor = if (isFavorite) activeActionContainerColor else actionContainerColor,
                            borderColor = if (isFavorite) activeActionBorderColor else actionBorderColor,
                            iconSize = actionIconSize,
                            iconPadding = actionIconPadding,
                            enabled = !isScrolling,
                            onClick = onToggleFavorite
                        )
                        Spacer(modifier = Modifier.width(actionHorizontalSpacing))
                        CircleIconButton(
                            icon = Icons.Outlined.Share,
                            contentDescription = "分享",
                            tint = normalIconColor,
                            containerColor = actionContainerColor,
                            borderColor = actionBorderColor,
                            size = actionIconSize,
                            padding = actionIconPadding,
                            iconOffsetX = (-1).dp,
                            enabled = !isScrolling,
                            onClick = {
                                val title = item?.title.orEmpty()
                                val shareLink = item?.link?.trim().orEmpty().ifBlank { null }
                                if (useSystemShare) {
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
                Spacer(modifier = Modifier.height(if (showAiButton) 56.dp else actionVerticalSpacing))
            }
        }

        val aiButtonVisible = showAiButton && !isScrolling
        val aiTransition = updateTransition(targetState = aiButtonVisible, label = "AiButton")
        val aiScale by aiTransition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                } else {
                    tween(160)
                }
            },
            label = "aiScale"
        ) { if (it) 1f else 0.72f }
        val aiAlpha by aiTransition.animateFloat(
            transitionSpec = { if (targetState) tween(200) else tween(160) },
            label = "aiAlpha"
        ) { if (it) 1f else 0f }
        val aiBlurDp by aiTransition.animateFloat(
            transitionSpec = { if (targetState) tween(220) else tween(160) },
            label = "aiBlur"
        ) { if (it) 0f else 6f }

        if (aiTransition.currentState || aiTransition.targetState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 14.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                AiFloatingButton(
                    activeColor = activeColor,
                    containerColor = actionContainerColor,
                    borderColor = actionBorderColor,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = aiScale
                            scaleY = aiScale
                            alpha = aiAlpha
                        }
                        .then(if (aiBlurDp > 0f) Modifier.blur(aiBlurDp.dp) else Modifier),
                    onClick = onOpenAiSummary
                )
            }
        }

        if (showOriginalModeWarning && link.isNotEmpty()) {
            WarningConfirmDialog(
                title = "内测功能",
                message = "原文网页阅读仍在内测，可能出现排版异常或兼容性问题，确定继续吗？",
                onConfirm = {
                    showOriginalModeWarning = false
                    openLinkInApp(context, link)
                },
                onCancel = { showOriginalModeWarning = false }
            )
        }
    }
}
