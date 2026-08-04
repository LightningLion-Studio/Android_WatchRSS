package com.lightningstudio.watchrss.ui.screen.rss

import android.os.SystemClock
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.BiliPlayerActivity
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.RssPlayerActivity
import com.lightningstudio.watchrss.data.bili.parseBiliVideoTarget
import com.lightningstudio.watchrss.data.douyin.buildDouyinPlaybackWebUrl
import com.lightningstudio.watchrss.data.douyin.parseDouyinAwemeId
import com.lightningstudio.watchrss.data.rss.ARTICLE_TEXT_CHUNK_BYTES
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.ImportedTextReader
import com.lightningstudio.watchrss.data.rss.OfflineMedia
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.settings.RssInlineImagePrefetchMode
import com.lightningstudio.watchrss.data.tts.ReadAloudStartAnchor
import com.lightningstudio.watchrss.data.tts.ReadAloudHighlightRange
import com.lightningstudio.watchrss.data.tts.ReadAloudTextSegmenter
import com.lightningstudio.watchrss.data.tts.ReadAloudUiState
import com.lightningstudio.watchrss.ui.components.BlurFadeVisibility
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
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
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    llmSummaryState: LlmSummaryUiState = LlmSummaryUiState(),
    readAloudState: ReadAloudUiState = ReadAloudUiState(),
    isStartingActivity: Boolean = false,
    onOpenAiSummary: () -> Unit = {},
    onOpenReadAloud: (ReadAloudStartAnchor?, Boolean) -> Unit = { _, _ -> },
    onOpenReadAloudControls: (ReadAloudStartAnchor?, Boolean) -> Unit = { _, _ -> },
    onBack: (Long, Boolean, Boolean) -> Unit
) {
    val item by viewModel.item.collectAsState()
    val channel by viewModel.channel.collectAsState()
    val savedState by viewModel.savedState.collectAsState()
    val offlineMedia by viewModel.offlineMedia.collectAsState()
    val isRetryingOfflineMedia by viewModel.isRetryingOfflineMedia.collectAsState()
    val contentBlocks by viewModel.contentBlocks.collectAsState()
    val importedTextReader by viewModel.importedTextReader.collectAsState()
    val readingThemeDark by viewModel.readingThemeDark.collectAsState()
    val readingFontSizeSp by viewModel.readingFontSizeSp.collectAsState()
    val shareUseSystem by viewModel.shareUseSystem.collectAsState(initial = false)
    val rssInlineImagePrefetchMode by viewModel.rssInlineImagePrefetchMode.collectAsState()
    val llmEnabled by viewModel.llmEnabled.collectAsState()
    val llmAutoSummarize by viewModel.llmAutoSummarize.collectAsState()
    val effectiveUseOriginalContent by viewModel.effectiveUseOriginalContent.collectAsState()

    val hasOfflineFailures = remember(offlineMedia) { offlineMedia.any { it.localPath == null } }
    val offlineMap = remember(offlineMedia) { offlineMedia.associateBy { it.originUrl } }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
        val isImportedText = ImportedContentIds.isImportedTextItemUrl(item?.link)
        DetailContent(
            item = item,
            showOriginalLoadingNotice = !isImportedText &&
                effectiveUseOriginalContent &&
                item?.originalContent.isNullOrBlank(),
            contentBlocks = contentBlocks,
            importedTextReader = importedTextReader,
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
            llmEnabled = llmEnabled,
            llmAutoSummarize = llmAutoSummarize,
            llmSummaryState = llmSummaryState,
            readAloudState = readAloudState,
            isStartingActivity = isStartingActivity,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleOriginalContent = viewModel::toggleOriginalContent,
            onRetryOfflineMedia = viewModel::retryOfflineMedia,
            onSaveReadingProgress = viewModel::saveReadingProgress,
            onLoadImportedTextChunk = viewModel::loadImportedTextChunk,
            onOpenAiSummary = onOpenAiSummary,
            onOpenReadAloud = onOpenReadAloud,
            onOpenReadAloudControls = onOpenReadAloudControls,
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
    importedTextReader: ImportedTextReader? = null,
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
    llmEnabled: Boolean = true,
    llmAutoSummarize: Boolean = false,
    llmSummaryState: LlmSummaryUiState = LlmSummaryUiState(),
    readAloudState: ReadAloudUiState = ReadAloudUiState(),
    isStartingActivity: Boolean = false,
    onToggleFavorite: () -> Unit,
    onToggleOriginalContent: () -> Unit,
    onRetryOfflineMedia: () -> Unit,
    onSaveReadingProgress: suspend (Float) -> Unit,
    onLoadImportedTextChunk: suspend (String, Int) -> String? = { _, _ -> null },
    onOpenAiSummary: () -> Unit = {},
    onOpenReadAloud: (ReadAloudStartAnchor?, Boolean) -> Unit = { _, _ -> },
    onOpenReadAloudControls: (ReadAloudStartAnchor?, Boolean) -> Unit = { _, _ -> },
    onBack: (Long, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val useSystemShare = remember(context, shareUseSystem) {
        shareUseSystem && isSystemShareSettingSupported(context)
    }
    val listState = rememberLazyListState()
    InstallDigitalCrownLazyListHandler(listState)
    val safePadding = com.lightningstudio.watchrss.ui.reader.ReaderPageLayout.topSafePadding
    val pagePadding = com.lightningstudio.watchrss.ui.reader.ReaderPageLayout.horizontalPadding
    val blockSpacing = com.lightningstudio.watchrss.ui.reader.ReaderPageLayout.blockSpacing
    val titlePadding =
        com.lightningstudio.watchrss.ui.reader.ReaderPageLayout.titleHorizontalPadding
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
    val totalItemsCount by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }
    var hasRestoredPosition by remember { mutableStateOf(false) }
    var lastItemId by remember { mutableStateOf<Long?>(null) }
    var lastSavedProgress by remember { mutableStateOf(-1f) }
    var lastProgressSavedAt by remember { mutableStateOf(0L) }
    var lastImportedTextAnchoredProgress by remember { mutableStateOf<Float?>(null) }
    var lastContentTextAnchoredProgress by remember { mutableStateOf<Float?>(null) }
    val savedOnBackState = remember { mutableStateOf(false) }
    var backInProgress by remember { mutableStateOf(false) }
    var pendingImportedTextOffsetRestore by remember { mutableStateOf<ImportedTextByteRestoreTarget?>(null) }
    var pendingContentTextOffsetRestore by remember { mutableStateOf<ContentTextRestoreTarget?>(null) }
    val importedTextChunkLayouts = remember(item?.id, importedTextReader?.marker) {
        mutableStateMapOf<Int, TextLayoutResult>()
    }
    val importedTextChunkTexts = remember(item?.id, importedTextReader?.marker) {
        mutableStateMapOf<Int, String>()
    }
    val contentTextBlockLayouts = remember(item?.id, contentBlocks) {
        mutableStateMapOf<Int, TextLayoutResult>()
    }
    var titleTextLayout by remember(item?.id) { mutableStateOf<TextLayoutResult?>(null) }
    var lastManualReadAloudScrollAt by remember(item?.id) { mutableStateOf(0L) }
    var lastReadAloudLongPressAt by remember(item?.id) { mutableStateOf(0L) }
    var readAloudAutoScrollInProgress by remember(item?.id) { mutableStateOf(false) }
    val activeReadAloudHighlight = readAloudState.highlightRange
        ?.takeIf { range ->
            item?.id == range.itemId &&
                (
                    ImportedContentIds.isImportedTextItemUrl(item?.link) ||
                        range.useOriginalContent == originalContentEnabled
                    )
        }
    val readAloudHighlightColor = if (readingThemeDark) {
        activeColor.copy(alpha = 0.34f)
    } else {
        activeColor.copy(alpha = 0.22f)
    }

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
            pendingImportedTextOffsetRestore = null
            pendingContentTextOffsetRestore = null
            lastImportedTextAnchoredProgress = null
            lastContentTextAnchoredProgress = null
            savedOnBackState.value = false
            backInProgress = false
            lastSavedProgress = -1f
            lastProgressSavedAt = 0L
        }
    }

    val link = item?.link?.trim().orEmpty()
    val isImportedText = ImportedContentIds.isImportedTextItemUrl(link)
    val isNovelContent = ImportedContentIds.isNovelContentItemUrl(link)
    val canToggleOriginalContent = link.isNotEmpty() && !ImportedContentIds.isImportedContentUrl(link)
    val baseLink = link.takeIf { it.isNotBlank() }
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
    val showAiBanner = llmEnabled && !isNovelContent && llmAutoSummarize &&
        (llmSummaryState.status == SummaryStatus.WaitingForContent ||
            llmSummaryState.status == SummaryStatus.Generating ||
            llmSummaryState.text.isNotBlank() ||
            llmSummaryState.status is SummaryStatus.Error)
    val showAiButton = llmEnabled && !isNovelContent && !llmAutoSummarize
    val showReadAloudAction = item != null && !ImportedContentIds.isImportedContentUrl(link)
    val articleActionItemCount = if (canToggleOriginalContent || showReadAloudAction) 1 else 0
    val importedTextFirstItemIndex = remember(
        articleActionItemCount,
        hasOfflineFailures,
        isRetryingOfflineMedia,
        showAiBanner,
        showOriginalLoadingNotice
    ) {
        DETAIL_CONTENT_START_ITEM_INDEX +
            articleActionItemCount +
            (if (hasOfflineFailures || isRetryingOfflineMedia) 1 else 0) +
            (if (showAiBanner) 1 else 0) +
            (if (showOriginalLoadingNotice) 1 else 0) +
            DETAIL_LOADING_SKELETON_ITEM_COUNT
    }
    val importedTextChunkCount = importedTextReader?.chunkCount ?: 0
    val contentBlockFirstItemIndex = importedTextFirstItemIndex +
        if (item != null && importedTextReader != null) importedTextChunkCount else 0

    fun freshImportedTextReadingProgress(): Float? {
        if (!isImportedText || importedTextReader == null || importedTextChunkCount <= 0) {
            return null
        }
        return calculateImportedTextByteReadingProgressFromLayout(
            listState = listState,
            marker = importedTextReader.marker,
            byteLength = importedTextReader.byteLength,
            chunkCount = importedTextChunkCount,
            chunkTexts = importedTextChunkTexts,
            chunkLayouts = importedTextChunkLayouts
        )
    }

    fun freshContentTextReadingProgress(): Float? {
        if (isImportedText || contentBlocks.isEmpty()) {
            return null
        }
        return calculateContentTextReadingProgressFromLayout(
            listState = listState,
            firstContentItemIndex = contentBlockFirstItemIndex,
            contentBlocks = contentBlocks,
            textLayouts = contentTextBlockLayouts
        )
    }

    fun rememberImportedTextProgress(progress: Float?): Float? {
        if (progress != null) {
            lastImportedTextAnchoredProgress = progress
        }
        return progress
    }

    fun rememberContentTextProgress(progress: Float?): Float? {
        if (progress != null) {
            lastContentTextAnchoredProgress = progress
        }
        return progress
    }

    suspend fun awaitImportedTextReadingProgress(): Float? {
        rememberImportedTextProgress(freshImportedTextReadingProgress())?.let { return it }
        val progress = withTimeoutOrNull(IMPORTED_TEXT_SAVE_LAYOUT_TIMEOUT_MS) {
            snapshotFlow { freshImportedTextReadingProgress() }
                .filterNotNull()
                .first()
        }
        return rememberImportedTextProgress(progress)
    }

    suspend fun awaitContentTextReadingProgress(): Float? {
        rememberContentTextProgress(freshContentTextReadingProgress())?.let { return it }
        val progress = withTimeoutOrNull(CONTENT_TEXT_SAVE_LAYOUT_TIMEOUT_MS) {
            snapshotFlow { freshContentTextReadingProgress() }
                .filterNotNull()
                .first()
        }
        return rememberContentTextProgress(progress)
    }

    fun currentReadingProgress(allowCachedImportedTextProgress: Boolean = true): Float? {
        if (isImportedText && (importedTextReader == null || importedTextChunkCount <= 0)) {
            return null
        }
        return if (isImportedText) {
            rememberImportedTextProgress(freshImportedTextReadingProgress())
                ?: lastImportedTextAnchoredProgress.takeIf { allowCachedImportedTextProgress }
        } else {
            rememberContentTextProgress(freshContentTextReadingProgress())
                ?: lastContentTextAnchoredProgress
                ?: calculateReadingProgress(listState)
        }
    }

    fun currentReadAloudStartAnchor(): ReadAloudStartAnchor? {
        if (item == null) return null
        if (isImportedText) {
            val reader = importedTextReader ?: return null
            return currentImportedTextReadAloudStartAnchor(
                listState = listState,
                marker = reader.marker,
                byteLength = reader.byteLength,
                chunkCount = importedTextChunkCount,
                chunkTexts = importedTextChunkTexts,
                chunkLayouts = importedTextChunkLayouts
            ) ?: currentReadingProgress()?.let { progress ->
                ReadAloudStartAnchor(progress = progress)
            }
        }
        return currentContentTextReadAloudStartAnchor(
            listState = listState,
            firstContentItemIndex = contentBlockFirstItemIndex,
            contentBlocks = contentBlocks,
            textLayouts = contentTextBlockLayouts
        ) ?: currentReadingProgress()?.let { progress ->
            ReadAloudStartAnchor(progress = progress)
        }
    }

    fun currentReadAloudScrollTarget(): ReadAloudScrollTarget? {
        val highlight = activeReadAloudHighlight ?: return null
        if (item == null) return null
        if (highlight.isTitle) {
            findReadAloudHighlightRange(item.title, highlight)?.let { titleRange ->
                return ReadAloudScrollTarget(
                    itemIndex = DETAIL_TITLE_ITEM_INDEX,
                    textRange = titleRange,
                    layout = titleTextLayout,
                    itemInfo = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == DETAIL_TITLE_ITEM_INDEX }
                )
            }
        }
        val importedChunkIndex = highlight.importedChunkIndex
        if (importedChunkIndex != null && importedTextReader != null) {
            val text = importedTextChunkTexts[importedChunkIndex]
            val range = text?.let { chunkText ->
                directImportedReadAloudHighlightRange(chunkText, highlight)
                    ?: findReadAloudHighlightRange(chunkText, highlight)
            }
            return ReadAloudScrollTarget(
                itemIndex = importedTextFirstItemIndex + importedChunkIndex,
                textRange = range,
                layout = importedTextChunkLayouts[importedChunkIndex],
                itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                    it.index == importedTextFirstItemIndex + importedChunkIndex
                }
            )
        }
        val directContentBlockIndex = highlight.contentBlockIndex
        if (directContentBlockIndex != null) {
            val textBlock = contentBlocks.getOrNull(directContentBlockIndex) as? ContentBlock.Text
            if (textBlock != null) {
                val itemIndex = contentBlockFirstItemIndex + directContentBlockIndex
                return ReadAloudScrollTarget(
                    itemIndex = itemIndex,
                    textRange = directContentReadAloudHighlightRange(
                        text = textBlock.text,
                        highlight = highlight,
                        blockIndex = directContentBlockIndex
                    ),
                    layout = contentTextBlockLayouts[directContentBlockIndex],
                    itemInfo = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == itemIndex }
                )
            }
        }
        contentBlocks.forEachIndexed { index, block ->
            val textBlock = block as? ContentBlock.Text ?: return@forEachIndexed
            findReadAloudHighlightRange(textBlock.text, highlight)?.let { blockRange ->
                val itemIndex = contentBlockFirstItemIndex + index
                return ReadAloudScrollTarget(
                    itemIndex = itemIndex,
                    textRange = blockRange,
                    layout = contentTextBlockLayouts[index],
                    itemInfo = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == itemIndex }
                )
            }
        }
        return null
    }

    fun openReadAloudControlsOnce(startAnchor: ReadAloudStartAnchor?) {
        if (item == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastReadAloudLongPressAt < READ_ALOUD_LONG_PRESS_DEBOUNCE_MS) return
        lastReadAloudLongPressAt = now
        onOpenReadAloudControls(startAnchor, originalContentEnabled)
    }

    fun openReadAloudFromBeginning() {
        openReadAloudControlsOnce(null)
    }

    fun openReadAloudFromVisibleAnchor() {
        openReadAloudControlsOnce(currentReadAloudStartAnchor())
    }

    fun Modifier.readAloudLongPressTarget(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
        detectTapGestures(
            onLongPress = {
                onLongPress()
            }
        )
    }

    LaunchedEffect(listState, item?.id) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { isScrolling ->
                if (
                    isScrolling &&
                    !readAloudAutoScrollInProgress &&
                    hasRestoredPositionState.value
                ) {
                    lastManualReadAloudScrollAt = SystemClock.uptimeMillis()
                }
            }
    }

    LaunchedEffect(
        activeReadAloudHighlight?.itemId,
        activeReadAloudHighlight?.segmentIndex,
        activeReadAloudHighlight?.rangeStart,
        activeReadAloudHighlight?.rangeEnd,
        readAloudState.isPlaying,
        hasRestoredPosition
    ) {
        val highlight = activeReadAloudHighlight
        if (highlight == null || !readAloudState.isPlaying || !hasRestoredPosition) {
            return@LaunchedEffect
        }
        val manualScrollElapsedMs = SystemClock.uptimeMillis() - lastManualReadAloudScrollAt
        if (manualScrollElapsedMs < READ_ALOUD_MANUAL_SCROLL_GRACE_MS) {
            delay(READ_ALOUD_MANUAL_SCROLL_GRACE_MS - manualScrollElapsedMs)
        }
        if (!readAloudState.isPlaying) return@LaunchedEffect
        val target = currentReadAloudScrollTarget()
            ?: withTimeoutOrNull(READ_ALOUD_SCROLL_TARGET_TIMEOUT_MS) {
                snapshotFlow { currentReadAloudScrollTarget() }
                    .filterNotNull()
                    .first()
            }
            ?: return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return@LaunchedEffect
        readAloudAutoScrollInProgress = true
        try {
            scrollReadAloudTargetToCenter(
                listState = listState,
                target = target,
                totalItems = totalItems
            )
            val centeredTarget = withTimeoutOrNull(READ_ALOUD_SCROLL_LAYOUT_TIMEOUT_MS) {
                snapshotFlow {
                    currentReadAloudScrollTarget()
                        ?.takeIf { it.textRange != null && it.layout != null && it.itemInfo != null }
                }
                    .filterNotNull()
                    .first()
            }
            if (centeredTarget != null) {
                scrollReadAloudTargetToCenter(
                    listState = listState,
                    target = centeredTarget,
                    totalItems = listState.layoutInfo.totalItemsCount
                )
            }
        } finally {
            withContext(NonCancellable) {
                delay(READ_ALOUD_AUTO_SCROLL_SETTLE_MS)
                readAloudAutoScrollInProgress = false
            }
        }
    }

    suspend fun saveCurrentReadingProgressAfterLayout(force: Boolean): Boolean {
        val allowImportedTextBeforeRestore = isImportedText && force
        if (!hasRestoredPositionState.value && !allowImportedTextBeforeRestore) return false
        val progress = when {
            isImportedText -> awaitImportedTextReadingProgress()
            force -> awaitContentTextReadingProgress() ?: currentReadingProgress()
            else -> currentReadingProgress()
        } ?: return false
        maybeSaveReadingProgress(
            readingProgress = progress,
            force = force,
            lastSavedProgress = { lastSavedProgress },
            lastProgressSavedAt = { lastProgressSavedAt },
            updateLastSavedProgress = { lastSavedProgress = it },
            updateLastProgressSavedAt = { lastProgressSavedAt = it },
            onSave = onSaveReadingProgressState.value
        )
        if (BuildConfig.DEBUG && isImportedText && force && importedTextChunkCount > 0) {
            val target = importedTextByteRestoreTarget(
                progress = progress,
                firstChunkItemIndex = importedTextFirstItemIndex,
                byteLength = importedTextReader?.byteLength ?: 0L,
                chunkCount = importedTextChunkCount,
                chunkBytes = ARTICLE_TEXT_CHUNK_BYTES
            )
            AppLogger.d(
                IMPORTED_TEXT_PROGRESS_LOG_TAG,
                "save itemId=${item?.id ?: -1L} progress=$progress restored=${hasRestoredPositionState.value} " +
                    "targetChunk=${target.chunkIndex} " +
                    "targetByte=${target.byteOffsetInChunk}"
            )
        }
        return true
    }

    suspend fun saveImportedTextReadingProgress(progress: Float, force: Boolean) {
        if (!hasRestoredPositionState.value && !force) return
        maybeSaveReadingProgress(
            readingProgress = progress,
            force = force,
            lastSavedProgress = { lastSavedProgress },
            lastProgressSavedAt = { lastProgressSavedAt },
            updateLastSavedProgress = { lastSavedProgress = it },
            updateLastProgressSavedAt = { lastProgressSavedAt = it },
            onSave = onSaveReadingProgressState.value
        )
    }

    LaunchedEffect(readingFontSizeSp, readingThemeDark) {
        if (item == null || !hasRestoredPosition) return@LaunchedEffect
        pendingRestoreProgress = currentReadingProgress() ?: return@LaunchedEffect
        hasRestoredPosition = false
    }

    LaunchedEffect(
        pendingRestoreProgress,
        totalItemsCount,
        importedTextFirstItemIndex,
        importedTextChunkCount,
        contentBlockFirstItemIndex,
        contentBlocks
    ) {
        val progress = pendingRestoreProgress ?: return@LaunchedEffect
        val totalItems = totalItemsCount
        if (isImportedText && (importedTextReader == null || importedTextChunkCount <= 0)) {
            return@LaunchedEffect
        }
        if (!isImportedText && item != null && contentBlocks.isEmpty()) {
            if (progress <= 0f) {
                pendingRestoreProgress = null
                hasRestoredPosition = true
            }
            return@LaunchedEffect
        }
        if (totalItems == 0) {
            if (progress <= 0f) {
                pendingRestoreProgress = null
                hasRestoredPosition = true
            }
            return@LaunchedEffect
        }
        if (importedTextReader != null && importedTextChunkCount > 0) {
            val marker = importedTextReader.marker
            val chunkRestoreTarget = importedTextByteRestoreTarget(
                progress = progress,
                firstChunkItemIndex = 0,
                byteLength = importedTextReader.byteLength,
                chunkCount = importedTextChunkCount,
                chunkBytes = ARTICLE_TEXT_CHUNK_BYTES
            )
            val targetChunk = chunkRestoreTarget.chunkIndex
            val approximateTargetIndex = importedTextFirstItemIndex + targetChunk
            listState.scrollToItem(approximateTargetIndex.coerceIn(0, totalItems - 1))
            val actualFirstChunkItemIndex = withTimeoutOrNull(IMPORTED_TEXT_FIRST_INDEX_TIMEOUT_MS) {
                snapshotFlow {
                    visibleImportedTextFirstItemIndex(
                        layoutInfo = listState.layoutInfo,
                        marker = marker
                    )
                }
                    .filterNotNull()
                    .first()
            } ?: importedTextFirstItemIndex
            val restoreTarget = chunkRestoreTarget.copy(
                itemIndex = actualFirstChunkItemIndex + targetChunk,
                chunkIndex = targetChunk
            )
            if (BuildConfig.DEBUG) {
                AppLogger.d(
                    IMPORTED_TEXT_PROGRESS_LOG_TAG,
                    "restore itemId=${item?.id ?: -1L} progress=$progress " +
                        "firstIndex=$actualFirstChunkItemIndex fallbackFirstIndex=$importedTextFirstItemIndex " +
                        "targetChunk=$targetChunk " +
                        "targetByte=${restoreTarget.byteOffsetInChunk}"
                )
            }
            val targetIndex = restoreTarget.itemIndex.coerceIn(0, totalItems - 1)
            if (targetIndex != approximateTargetIndex) {
                listState.scrollToItem(targetIndex)
            }
            pendingImportedTextOffsetRestore = restoreTarget.copy(itemIndex = targetIndex)
            pendingRestoreProgress = null
            return@LaunchedEffect
        }
        contentTextRestoreTarget(
            progress = progress,
            firstContentItemIndex = contentBlockFirstItemIndex,
            contentBlocks = contentBlocks
        )?.let { restoreTarget ->
            val targetIndex = restoreTarget.itemIndex.coerceIn(0, totalItems - 1)
            listState.scrollToItem(targetIndex)
            pendingContentTextOffsetRestore = restoreTarget.copy(itemIndex = targetIndex)
            pendingRestoreProgress = null
            return@LaunchedEffect
        }
        val target = ((totalItems - 1) * progress)
            .roundToInt()
            .coerceIn(0, totalItems - 1)
        listState.scrollToItem(target)
        pendingRestoreProgress = null
        hasRestoredPosition = true
    }

    LaunchedEffect(pendingImportedTextOffsetRestore) {
        val restoreTarget = pendingImportedTextOffsetRestore ?: return@LaunchedEffect
        val offsetPx = withTimeoutOrNull(IMPORTED_TEXT_RESTORE_OFFSET_TIMEOUT_MS) {
            snapshotFlow {
                val chunkLayout = importedTextChunkLayouts[restoreTarget.chunkIndex]
                val chunkText = importedTextChunkTexts[restoreTarget.chunkIndex]
                val itemInfo = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == restoreTarget.itemIndex }
                if (chunkLayout == null || chunkText == null || itemInfo == null) {
                    null
                } else {
                    importedTextRestoreVisualOffsetPx(
                        restoreTarget = restoreTarget,
                        text = chunkText,
                        layout = chunkLayout,
                        itemInfo = itemInfo
                    )
                }
            }
                .filterNotNull()
                .first()
        }
        if (offsetPx != null) {
            if (BuildConfig.DEBUG) {
                AppLogger.d(
                    IMPORTED_TEXT_PROGRESS_LOG_TAG,
                    "restore-offset itemId=${item?.id ?: -1L} " +
                        "targetChunk=${restoreTarget.chunkIndex} " +
                        "targetByte=${restoreTarget.byteOffsetInChunk} offsetPx=$offsetPx"
                )
            }
            listState.scrollToItem(restoreTarget.itemIndex, offsetPx)
        }
        pendingImportedTextOffsetRestore = null
        hasRestoredPosition = true
    }

    LaunchedEffect(pendingContentTextOffsetRestore) {
        val restoreTarget = pendingContentTextOffsetRestore ?: return@LaunchedEffect
        val offsetPx = withTimeoutOrNull(CONTENT_TEXT_RESTORE_OFFSET_TIMEOUT_MS) {
            snapshotFlow {
                val block = contentBlocks.getOrNull(restoreTarget.blockIndex) as? ContentBlock.Text
                val layout = contentTextBlockLayouts[restoreTarget.blockIndex]
                val itemInfo = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == restoreTarget.itemIndex }
                if (block == null || layout == null || itemInfo == null) {
                    null
                } else {
                    contentTextRestoreVisualOffsetPx(
                        restoreTarget = restoreTarget,
                        text = block.text,
                        layout = layout,
                        itemInfo = itemInfo
                    )
                }
            }
                .filterNotNull()
                .first()
        }
        if (offsetPx != null) {
            listState.scrollToItem(restoreTarget.itemIndex, offsetPx)
        }
        pendingContentTextOffsetRestore = null
        hasRestoredPosition = true
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { isScrolling ->
                if (isScrolling || !hasRestoredPositionState.value) return@collectLatest
                saveCurrentReadingProgressAfterLayout(force = isImportedText)
            }
    }

    LaunchedEffect(listState, isImportedText, importedTextFirstItemIndex, importedTextChunkCount, importedTextReader?.marker) {
        if (!isImportedText || importedTextReader == null || importedTextChunkCount <= 0) {
            return@LaunchedEffect
        }
        snapshotFlow { freshImportedTextReadingProgress() }
            .filterNotNull()
            .distinctUntilChanged()
            .sample(IMPORTED_TEXT_SCROLL_SAVE_SAMPLE_MS)
            .collectLatest { progress ->
                saveImportedTextReadingProgress(progress, force = true)
            }
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (!savedOnBackState.value) {
                    val saved = runBlocking {
                        saveCurrentReadingProgressAfterLayout(force = true)
                    }
                    if (saved) {
                        savedOnBackState.value = true
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = !backInProgress) {
        backInProgress = true
        val saved = runBlocking {
            saveCurrentReadingProgressAfterLayout(force = true)
        }
        if (saved) {
            savedOnBackState.value = true
        }
        val thresholdPx = with(density) { 8.dp.toPx() }
        val reachedBottom = isReachedBottom(listState, thresholdPx)
        onBackState.value(item?.id ?: 0L, reachedBottom, isWatchLaterState.value)
    }

    com.lightningstudio.watchrss.ui.reader.ReaderBackgroundSurface(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "文章详情页面"
            }
    ) {
        // Activity starting overlay - shows when button triggers startActivity
        if (isStartingActivity) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                WatchCircularProgressIndicator(
                    color = activeColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

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

        val baseItemCount = remember(canToggleOriginalContent, hasOfflineFailures, isRetryingOfflineMedia) {
            4 + (if (canToggleOriginalContent) 1 else 0) +
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(listSemanticsModifier),
            state = listState,
            contentPadding = PaddingValues(horizontal = pagePadding)
        ) {
            item(key = "topSpacer") {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(safePadding + extraSafePadding)
                        .readAloudLongPressTarget(::openReadAloudFromBeginning)
                )
            }
            item(key = "titleGap") {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            com.lightningstudio.watchrss.ui.reader.ReaderPageLayout.titleGap
                        )
                        .readAloudLongPressTarget(::openReadAloudFromBeginning)
                )
            }
            item(key = "title") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .readAloudLongPressTarget(::openReadAloudFromBeginning)
                ) {
                    if (item != null) {
                        DetailTitle(
                            title = item.title,
                            titlePadding = titlePadding,
                            textColor = textColor,
                            highlightRange = activeReadAloudHighlight
                                ?.takeIf { highlight -> highlight.isTitle }
                                ?.let { highlight -> findReadAloudHighlightRange(item.title, highlight) },
                            highlightColor = readAloudHighlightColor,
                            onTextLayout = { titleTextLayout = it }
                        )
                    }
                    BlurFadeVisibility(
                        visible = item == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DetailTitleSkeleton(
                            titlePadding = titlePadding,
                            backgroundColor = backgroundColor,
                            textColor = textColor
                        )
                    }
                }
            }
            if (canToggleOriginalContent || showReadAloudAction) {
                item(key = "articleActions") {
                    Spacer(modifier = Modifier.height(blockSpacing))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canToggleOriginalContent) {
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
                        if (showReadAloudAction) {
                            Spacer(modifier = Modifier.weight(1f))
                            DetailActionButton(
                                text = "大声朗读",
                                fontSize = bodyFontSize,
                                containerColor = activeActionContainerColor,
                                contentColor = activeColor,
                                borderColor = activeActionBorderColor,
                                onClick = { onOpenReadAloud(null, originalContentEnabled) }
                            )
                        }
                    }
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
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(blockSpacing)
                        .readAloudLongPressTarget(::openReadAloudFromVisibleAnchor)
                )
            }
            if (showOriginalLoadingNotice) {
                item(key = "originalLoading") {
                    DetailTextBlock(
                        text = "原文加载中，您正在查看RSS内容...",
                        style = ContentTextStyle.QUOTE,
                        textColor = textColor,
                        fontSizeSp = bodyFontSize,
                        topPadding = 0.dp,
                        isScrolling = isScrolling,
                        onLongClick = ::openReadAloudFromVisibleAnchor
                    )
                }
            }
            item(key = "loadingSkeleton") {
                BlurFadeVisibility(
                    visible = item == null || (contentBlocks.isEmpty() && importedTextReader == null),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DetailContentSkeleton(
                        backgroundColor = backgroundColor,
                        textColor = textColor,
                        containerColor = actionContainerColor,
                        borderColor = actionBorderColor,
                        blockSpacing = blockSpacing
                    )
                }
            }
            if (item != null && importedTextReader != null) {
                items(
                    count = importedTextReader.chunkCount,
                    key = { index -> "importedText:${importedTextReader.marker}:$index" },
                    contentType = { "imported_text" }
                ) { index ->
                    val marker = importedTextReader.marker
                    val chunk by produceState<String?>(initialValue = null, marker, index) {
                        value = onLoadImportedTextChunk(marker, index)
                    }
                    val text = chunk
                    LaunchedEffect(marker, index, text) {
                        if (text == null) {
                            importedTextChunkTexts.remove(index)
                            importedTextChunkLayouts.remove(index)
                        } else {
                            importedTextChunkTexts[index] = text
                        }
                    }
                    val topPadding = if (index == 0) 0.dp else blockSpacing
                    if (text == null) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (index == 0) 1.dp else blockSpacing)
                                .readAloudLongPressTarget(::openReadAloudFromVisibleAnchor)
                        )
                    } else if (text.isNotBlank()) {
                        val highlightRange = activeReadAloudHighlight
                            ?.takeIf { highlight -> highlight.importedChunkIndex == index }
                            ?.let { highlight ->
                                directImportedReadAloudHighlightRange(text, highlight)
                                    ?: findReadAloudHighlightRange(text, highlight)
                            }
                        DetailTextBlock(
                            text = text,
                            style = ContentTextStyle.BODY,
                            textColor = textColor,
                            fontSizeSp = bodyFontSize,
                            topPadding = topPadding,
                            isScrolling = isScrolling,
                            highlightRange = highlightRange,
                            highlightColor = readAloudHighlightColor,
                            onLongClick = ::openReadAloudFromVisibleAnchor,
                            onTextLayout = { importedTextChunkLayouts[index] = it }
                        )
                    }
                }
            }
            if (item != null && contentBlocks.isNotEmpty()) {
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
                            val highlightRange = activeReadAloudHighlight
                                ?.takeIf { highlight ->
                                    highlight.importedChunkIndex == null &&
                                        (highlight.contentBlockIndex == null ||
                                            highlight.contentBlockIndex == index)
                                }
                                ?.let { highlight ->
                                    directContentReadAloudHighlightRange(block.text, highlight, index)
                                        ?: findReadAloudHighlightRange(block.text, highlight)
                                }
                            DetailTextBlock(
                                text = block.text,
                                style = block.style,
                                textColor = textColor,
                                fontSizeSp = blockFontSize,
                                topPadding = topPadding,
                                isScrolling = isScrolling,
                                // “少数派”返回的 RSS 就是这样的，只有摘要，在摘要结尾说“查看全文”。
                                inlineActionText = "查看全文",
                                onInlineActionClick = onToggleOriginalContent,
                                highlightRange = highlightRange,
                                highlightColor = readAloudHighlightColor,
                                onLongClick = ::openReadAloudFromVisibleAnchor,
                                onTextLayout = { contentTextBlockLayouts[index] = it }
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
                            val biliTarget = parseBiliVideoTarget(block.url)
                                ?: item?.link?.let(::parseBiliVideoTarget)
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
                                    if (biliTarget != null) {
                                        context.startActivity(
                                            BiliPlayerActivity.createIntent(
                                                context = context,
                                                aid = biliTarget.aid,
                                                bvid = biliTarget.bvid,
                                                cid = biliTarget.cid,
                                                title = item?.title
                                            )
                                        )
                                    } else if (douyinAwemeId != null || !douyinWebUrl.isNullOrBlank()) {
                                        context.startActivity(
                                            RssPlayerActivity.createIntent(
                                                context = context,
                                                playUrl = resolvedUrl,
                                                webUrl = targetWebUrl,
                                                awemeId = douyinAwemeId,
                                                channelId = item?.channelId ?: 0L
                                            )
                                        )
                                    } else {
                                        openRssVideo(
                                            context = context,
                                            playUrl = resolvedUrl,
                                            webUrl = targetWebUrl,
                                            channelId = item?.channelId ?: 0L
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            item(key = "actionSpacing") {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(actionVerticalSpacing)
                        .readAloudLongPressTarget(::openReadAloudFromVisibleAnchor)
                )
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
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            com.lightningstudio.watchrss.ui.reader.ReaderPageLayout
                                .bottomPadding(showAiButton)
                        )
                        .readAloudLongPressTarget(::openReadAloudFromVisibleAnchor)
                )
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

        // Warning dialog
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

@Composable
private fun DetailTitleSkeleton(
    titlePadding: Dp,
    backgroundColor: Color,
    textColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = titlePadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DetailSkeletonBar(
            widthFraction = 0.78f,
            height = 12.dp,
            backgroundColor = backgroundColor,
            tintColor = textColor
        )
        Spacer(modifier = Modifier.height(7.dp))
        DetailSkeletonBar(
            widthFraction = 0.52f,
            height = 12.dp,
            backgroundColor = backgroundColor,
            tintColor = textColor
        )
    }
}

@Composable
private fun DetailContentSkeleton(
    backgroundColor: Color,
    textColor: Color,
    containerColor: Color,
    borderColor: Color,
    blockSpacing: Dp
) {
    val isDarkPalette = backgroundColor.red + backgroundColor.green + backgroundColor.blue < 1.2f
    val cardShape = RoundedCornerShape(WatchDimens.hey_card_normal_bg_radius)
    val lineTint = if (isDarkPalette) {
        textColor.copy(alpha = 0.22f)
    } else {
        textColor.copy(alpha = 0.08f)
    }
    val cardTint = if (isDarkPalette) {
        containerColor.copy(alpha = 0.96f)
    } else {
        Color.White.copy(alpha = 0.94f)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        DetailSkeletonBar(
            widthFraction = 0.34f,
            height = 8.dp,
            backgroundColor = backgroundColor,
            tintColor = lineTint
        )
        Spacer(modifier = Modifier.height(10.dp))
        DetailSkeletonParagraph(
            backgroundColor = backgroundColor,
            tintColor = lineTint,
            lineHeights = listOf(11.dp, 10.dp, 10.dp),
            widthFractions = listOf(0.96f, 0.92f, 0.68f),
            lineSpacing = 8.dp
        )
        Spacer(modifier = Modifier.height(blockSpacing))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.08f)
                .clip(cardShape)
                .detailSkeletonPlaceholder(
                    backgroundColor = backgroundColor,
                    tintColor = cardTint,
                    cornerRadius = WatchDimens.hey_card_normal_bg_radius
                )
                .then(
                    if (borderColor.alpha > 0f) {
                        Modifier.border(BorderStroke(1.dp, borderColor.copy(alpha = 0.55f)), cardShape)
                    } else {
                        Modifier
                    }
                )
        )
        Spacer(modifier = Modifier.height(blockSpacing))
        DetailSkeletonChipRow(
            backgroundColor = backgroundColor,
            tintColor = lineTint
        )
        Spacer(modifier = Modifier.height(12.dp))
        DetailSkeletonParagraph(
            backgroundColor = backgroundColor,
            tintColor = lineTint,
            lineHeights = listOf(10.dp, 10.dp, 10.dp, 10.dp, 10.dp),
            widthFractions = listOf(0.94f, 0.88f, 0.9f, 0.82f, 0.56f),
            lineSpacing = 8.dp
        )
        Spacer(modifier = Modifier.height(14.dp))
        DetailSkeletonParagraph(
            backgroundColor = backgroundColor,
            tintColor = lineTint,
            lineHeights = listOf(10.dp, 10.dp, 10.dp),
            widthFractions = listOf(0.92f, 0.8f, 0.48f),
            lineSpacing = 8.dp
        )
    }
}

@Composable
private fun DetailSkeletonChipRow(
    backgroundColor: Color,
    tintColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailSkeletonFixedBar(
            width = 48.dp,
            height = 18.dp,
            backgroundColor = backgroundColor,
            tintColor = tintColor,
            cornerRadius = 9.dp
        )
        DetailSkeletonFixedBar(
            width = 58.dp,
            height = 18.dp,
            backgroundColor = backgroundColor,
            tintColor = tintColor,
            cornerRadius = 9.dp
        )
        DetailSkeletonFixedBar(
            width = 42.dp,
            height = 18.dp,
            backgroundColor = backgroundColor,
            tintColor = tintColor,
            cornerRadius = 9.dp
        )
    }
}

@Composable
private fun DetailSkeletonParagraph(
    backgroundColor: Color,
    tintColor: Color,
    lineHeights: List<Dp>,
    widthFractions: List<Float>,
    lineSpacing: Dp
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        widthFractions.forEachIndexed { index, widthFraction ->
            DetailSkeletonBar(
                widthFraction = widthFraction,
                height = lineHeights.getOrElse(index) { lineHeights.lastOrNull() ?: 10.dp },
                backgroundColor = backgroundColor,
                tintColor = tintColor
            )
            if (index != widthFractions.lastIndex) {
                Spacer(modifier = Modifier.height(lineSpacing))
            }
        }
    }
}

@Composable
private fun DetailSkeletonBar(
    widthFraction: Float,
    height: Dp,
    backgroundColor: Color,
    tintColor: Color,
    cornerRadius: Dp = 6.dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .detailSkeletonPlaceholder(
                backgroundColor = backgroundColor,
                tintColor = tintColor,
                cornerRadius = cornerRadius
            )
    )
}

@Composable
private fun DetailSkeletonFixedBar(
    width: Dp,
    height: Dp,
    backgroundColor: Color,
    tintColor: Color,
    cornerRadius: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .detailSkeletonPlaceholder(
                backgroundColor = backgroundColor,
                tintColor = tintColor,
                cornerRadius = cornerRadius
            )
    )
}

@Composable
private fun Modifier.detailSkeletonPlaceholder(
    backgroundColor: Color,
    tintColor: Color,
    cornerRadius: Dp
): Modifier {
    val transition = rememberInfiniteTransition(label = "DetailSkeleton")
    val shimmerProgress by transition.animateFloat(
        initialValue = -1.15f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DetailSkeletonShimmer"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 980, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DetailSkeletonPulse"
    )
    val highlightColor = if (backgroundColor.red + backgroundColor.green + backgroundColor.blue < 1.2f) {
        Color.White.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.62f)
    }

    return this
        .graphicsLayer { alpha = pulseAlpha }
        .background(tintColor)
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val widthPx = size.width.coerceAtLeast(1f)
            val heightPx = size.height.coerceAtLeast(1f)
            val startX = widthPx * shimmerProgress
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    highlightColor,
                    Color.Transparent
                ),
                start = Offset(startX - widthPx * 0.85f, 0f),
                end = Offset(startX + widthPx * 0.25f, heightPx)
            )
            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    brush = brush,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx)
                )
            }
        }
}

private data class VisibleImportedTextChunkWithLayout(
    val itemInfo: LazyListItemInfo,
    val chunkIndex: Int,
    val text: String,
    val layout: TextLayoutResult
)

private data class VisibleContentTextBlockWithLayout(
    val itemInfo: LazyListItemInfo,
    val blockIndex: Int,
    val text: String,
    val layout: TextLayoutResult
)

private data class ReadAloudScrollTarget(
    val itemIndex: Int,
    val textRange: DetailTextHighlightRange?,
    val layout: TextLayoutResult?,
    val itemInfo: LazyListItemInfo?
)

private data class ContentTextRestoreTarget(
    val itemIndex: Int,
    val blockIndex: Int,
    val byteOffsetInBlock: Int
)

private data class NormalizedHighlightText(
    val text: String,
    val sourceOffsets: IntArray
)

private suspend fun scrollReadAloudTargetToCenter(
    listState: LazyListState,
    target: ReadAloudScrollTarget,
    totalItems: Int
) {
    if (totalItems <= 0) return
    val itemIndex = target.itemIndex.coerceIn(0, totalItems - 1)
    val offsetPx = readAloudCenterScrollOffsetPx(
        target = target,
        viewportHeightPx = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
    )
    if (offsetPx == null) {
        listState.animateScrollToItem(itemIndex)
    } else {
        listState.animateScrollToItem(itemIndex, offsetPx)
    }
}

private fun readAloudCenterScrollOffsetPx(
    target: ReadAloudScrollTarget,
    viewportHeightPx: Int
): Int? {
    val range = target.textRange ?: return null
    val layout = target.layout ?: return null
    val itemInfo = target.itemInfo ?: return null
    val textLength = layout.layoutInput.text.length
    if (textLength <= 0 || viewportHeightPx <= 0) return null
    val startOffset = range.start.coerceIn(0, textLength - 1)
    val endOffset = (range.end - 1).coerceIn(startOffset, textLength - 1)
    val startBox = layout.getBoundingBox(startOffset)
    val endBox = layout.getBoundingBox(endOffset)
    val textTopPaddingPx = (itemInfo.size - layout.size.height).coerceAtLeast(0)
    val rangeCenterY = textTopPaddingPx +
        ((startBox.top + endBox.bottom) / 2f)
    return (rangeCenterY - viewportHeightPx / 2f)
        .roundToInt()
}

private fun findReadAloudHighlightRange(
    text: String,
    highlight: ReadAloudHighlightRange
): DetailTextHighlightRange? {
    if (text.isBlank() || highlight.segmentText.isBlank()) return null
    val start = highlight.rangeStart.coerceIn(0, highlight.segmentText.length)
    val end = highlight.rangeEnd.coerceIn(start, highlight.segmentText.length)
    findSegmentScopedHighlightRange(
        text = text,
        segmentText = highlight.segmentText,
        rangeStart = start,
        rangeEnd = end
    )?.let { return it }
    val selectedText = highlight.segmentText.substring(start, end)
    val candidates = buildReadAloudHighlightCandidates(
        selectedText = selectedText,
        segmentText = highlight.segmentText,
        isFallback = highlight.isFallback
    )
    candidates.forEach { candidate ->
        findExactHighlightRange(text, candidate)?.let { return it }
        findNormalizedHighlightRange(text, candidate, keepWhitespace = true)?.let { return it }
        findNormalizedHighlightRange(text, candidate, keepWhitespace = false)?.let { return it }
    }
    return null
}

private fun directImportedReadAloudHighlightRange(
    text: String,
    highlight: ReadAloudHighlightRange
): DetailTextHighlightRange? {
    if (highlight.importedChunkIndex == null || text.isEmpty()) return null
    val start = highlight.importedCharOffset.coerceIn(0, text.length)
    val end = highlight.importedCharEndOffset.coerceIn(start, text.length)
    if (start >= end) return null
    return DetailTextHighlightRange(start = start, end = end)
}

private fun directContentReadAloudHighlightRange(
    text: String,
    highlight: ReadAloudHighlightRange,
    blockIndex: Int
): DetailTextHighlightRange? {
    if (highlight.contentBlockIndex != blockIndex || text.isEmpty()) return null
    val start = highlight.contentCharOffset.coerceIn(0, text.length)
    val end = highlight.contentCharEndOffset.coerceIn(start, text.length)
    if (start >= end) return null
    return DetailTextHighlightRange(start = start, end = end)
}

private fun buildReadAloudHighlightCandidates(
    selectedText: String,
    segmentText: String,
    isFallback: Boolean
): List<String> {
    val candidates = mutableListOf<String>()
    fun addCandidate(value: String) {
        val normalized = value.trim()
        if (normalized.isNotEmpty() && candidates.none { it == normalized }) {
            candidates.add(normalized)
        }
    }
    if (isFallback) {
        addCandidate(selectedText.take(READ_ALOUD_FALLBACK_MATCH_CHARS))
        addCandidate(trimReadAloudSentenceEnding(selectedText.take(READ_ALOUD_FALLBACK_MATCH_CHARS)))
    } else {
        addCandidate(selectedText)
        addCandidate(trimReadAloudSentenceEnding(selectedText))
    }
    return candidates
}

private fun findSegmentScopedHighlightRange(
    text: String,
    segmentText: String,
    rangeStart: Int,
    rangeEnd: Int
): DetailTextHighlightRange? {
    val exactIndex = text.indexOf(segmentText)
    if (exactIndex >= 0) {
        val start = exactIndex + rangeStart.coerceIn(0, segmentText.length)
        val end = exactIndex + rangeEnd.coerceIn(rangeStart, segmentText.length)
        if (start < end && end <= text.length) {
            return DetailTextHighlightRange(start = start, end = end)
        }
    }
    findNormalizedSegmentScopedHighlightRange(
        text = text,
        segmentText = segmentText,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        keepWhitespace = true
    )?.let { return it }
    return findNormalizedSegmentScopedHighlightRange(
        text = text,
        segmentText = segmentText,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        keepWhitespace = false
    )
}

private fun findNormalizedSegmentScopedHighlightRange(
    text: String,
    segmentText: String,
    rangeStart: Int,
    rangeEnd: Int,
    keepWhitespace: Boolean
): DetailTextHighlightRange? {
    val normalizedText = normalizeHighlightText(text, keepWhitespace)
    val normalizedSegment = normalizeHighlightText(segmentText, keepWhitespace)
    if (normalizedText.text.isBlank() || normalizedSegment.text.isBlank()) return null
    val segmentIndex = normalizedText.text.indexOf(normalizedSegment.text)
    if (segmentIndex < 0) return null
    val normalizedRange = normalizedRangeForSourceRange(
        normalized = normalizedSegment,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd
    ) ?: return null
    val normalizedStart = segmentIndex + normalizedRange.start
    val normalizedEnd = segmentIndex + normalizedRange.end - 1
    val sourceStart = normalizedText.sourceOffsets.getOrNull(normalizedStart) ?: return null
    val sourceEnd = (normalizedText.sourceOffsets.getOrNull(normalizedEnd) ?: return null) + 1
    if (sourceStart >= sourceEnd) return null
    return DetailTextHighlightRange(
        start = sourceStart,
        end = sourceEnd
    )
}

private fun normalizedRangeForSourceRange(
    normalized: NormalizedHighlightText,
    rangeStart: Int,
    rangeEnd: Int
): DetailTextHighlightRange? {
    val start = normalized.sourceOffsets.indexOfFirst { it >= rangeStart }
    val end = normalized.sourceOffsets.indexOfLast { it < rangeEnd } + 1
    if (start < 0 || end <= start) return null
    return DetailTextHighlightRange(start = start, end = end)
}

private fun trimReadAloudSentenceEnding(text: String): String {
    var end = text.length
    while (end > 0 && (text[end - 1].isWhitespace() || text[end - 1] in READ_ALOUD_HIGHLIGHT_TRIM_ENDINGS)) {
        end--
    }
    return text.substring(0, end)
}

private fun findExactHighlightRange(text: String, candidate: String): DetailTextHighlightRange? {
    if (candidate.isBlank()) return null
    val index = text.indexOf(candidate)
    if (index < 0) return null
    return DetailTextHighlightRange(
        start = index,
        end = index + candidate.length
    )
}

private fun findNormalizedHighlightRange(
    text: String,
    candidate: String,
    keepWhitespace: Boolean
): DetailTextHighlightRange? {
    val normalizedText = normalizeHighlightText(text, keepWhitespace)
    val normalizedCandidate = normalizeHighlightText(candidate, keepWhitespace).text
    if (normalizedText.text.isBlank() || normalizedCandidate.isBlank()) return null
    val index = normalizedText.text.indexOf(normalizedCandidate)
    if (index < 0) return null
    val endIndex = index + normalizedCandidate.length - 1
    val sourceStart = normalizedText.sourceOffsets.getOrNull(index) ?: return null
    val sourceEnd = (normalizedText.sourceOffsets.getOrNull(endIndex) ?: return null) + 1
    if (sourceStart >= sourceEnd) return null
    return DetailTextHighlightRange(
        start = sourceStart,
        end = sourceEnd
    )
}

private fun normalizeHighlightText(
    value: String,
    keepWhitespace: Boolean
): NormalizedHighlightText {
    val builder = StringBuilder(value.length)
    val offsets = ArrayList<Int>(value.length)
    var lastWasSpace = false
    value.forEachIndexed { index, char ->
        if (char.isWhitespace()) {
            if (keepWhitespace && builder.isNotEmpty() && !lastWasSpace) {
                builder.append(' ')
                offsets.add(index)
                lastWasSpace = true
            }
        } else {
            builder.append(char)
            offsets.add(index)
            lastWasSpace = false
        }
    }
    if (builder.isNotEmpty() && builder.last() == ' ') {
        builder.deleteAt(builder.length - 1)
        offsets.removeAt(offsets.lastIndex)
    }
    return NormalizedHighlightText(
        text = builder.toString(),
        sourceOffsets = offsets.toIntArray()
    )
}

private fun visibleImportedTextFirstItemIndex(
    layoutInfo: androidx.compose.foundation.lazy.LazyListLayoutInfo,
    marker: String
): Int? {
    return layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { itemInfo ->
        val chunkIndex = importedTextChunkIndexFromKey(itemInfo.key, marker)
            ?: return@firstNotNullOfOrNull null
        itemInfo.index - chunkIndex
    }
}

private fun importedTextChunkIndexFromKey(key: Any, marker: String): Int? {
    val prefix = "$IMPORTED_TEXT_KEY_PREFIX$marker:"
    val keyText = key as? String ?: return null
    if (!keyText.startsWith(prefix)) return null
    return keyText.substring(prefix.length).toIntOrNull()
}

private fun calculateImportedTextByteReadingProgressFromLayout(
    listState: androidx.compose.foundation.lazy.LazyListState,
    marker: String,
    byteLength: Long,
    chunkCount: Int,
    chunkTexts: Map<Int, String>,
    chunkLayouts: Map<Int, TextLayoutResult>
): Float? {
    if (chunkCount <= 0 || byteLength <= 0L) return null
    val layoutInfo = listState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward) return 1f
    val visibleChunk = layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { itemInfo ->
        val chunkIndex = importedTextChunkIndexFromKey(itemInfo.key, marker)
            ?: return@firstNotNullOfOrNull null
        if (chunkIndex !in 0 until chunkCount) {
            null
        } else {
            val text = chunkTexts[chunkIndex] ?: return@firstNotNullOfOrNull null
            val layout = chunkLayouts[chunkIndex] ?: return@firstNotNullOfOrNull null
            if (layout.lineCount <= 0) return@firstNotNullOfOrNull null
            VisibleImportedTextChunkWithLayout(
                itemInfo = itemInfo,
                chunkIndex = chunkIndex,
                text = text,
                layout = layout
            )
        }
    } ?: return null
    val scrolledInItemPx = (layoutInfo.viewportStartOffset - visibleChunk.itemInfo.offset)
        .coerceIn(0, visibleChunk.itemInfo.size.coerceAtLeast(0))
    val textTopPaddingPx = (visibleChunk.itemInfo.size - visibleChunk.layout.size.height)
        .coerceAtLeast(0)
    val textY = (scrolledInItemPx - textTopPaddingPx)
        .coerceAtLeast(0)
        .toFloat()
    val lineIndex = visibleChunk.layout
        .getLineForVerticalPosition(textY)
        .coerceIn(0, visibleChunk.layout.lineCount - 1)
    val charOffset = visibleChunk.layout
        .getLineStart(lineIndex)
        .coerceIn(0, visibleChunk.text.length)
    val byteOffsetInChunk = utf8ByteCountBeforeCharOffset(
        text = visibleChunk.text,
        charOffset = charOffset
    )
    val absoluteByte = (visibleChunk.chunkIndex.toLong() * ARTICLE_TEXT_CHUNK_BYTES.toLong() + byteOffsetInChunk)
        .coerceIn(0L, byteLength)
    return (absoluteByte.toDouble() / byteLength.toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
}

private fun calculateContentTextReadingProgressFromLayout(
    listState: androidx.compose.foundation.lazy.LazyListState,
    firstContentItemIndex: Int,
    contentBlocks: List<ContentBlock>,
    textLayouts: Map<Int, TextLayoutResult>
): Float? {
    val totalTextBytes = contentBlocks.sumOf { block ->
        (block as? ContentBlock.Text)?.text?.let(::utf8ByteCount) ?: 0
    }
    if (totalTextBytes <= 0) return null
    val layoutInfo = listState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward) return 1f
    val visibleBlock = layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { itemInfo ->
        val blockIndex = itemInfo.index - firstContentItemIndex
        val block = contentBlocks.getOrNull(blockIndex) as? ContentBlock.Text
            ?: return@firstNotNullOfOrNull null
        val layout = textLayouts[blockIndex] ?: return@firstNotNullOfOrNull null
        if (layout.lineCount <= 0) return@firstNotNullOfOrNull null
        VisibleContentTextBlockWithLayout(
            itemInfo = itemInfo,
            blockIndex = blockIndex,
            text = block.text,
            layout = layout
        )
    } ?: return null

    val bytesBeforeBlock = contentBlocks.asSequence()
        .take(visibleBlock.blockIndex)
        .sumOf { block -> (block as? ContentBlock.Text)?.text?.let(::utf8ByteCount) ?: 0 }
    val scrolledInItemPx = (layoutInfo.viewportStartOffset - visibleBlock.itemInfo.offset)
        .coerceIn(0, visibleBlock.itemInfo.size.coerceAtLeast(0))
    val textTopPaddingPx = (visibleBlock.itemInfo.size - visibleBlock.layout.size.height)
        .coerceAtLeast(0)
    val textY = (scrolledInItemPx - textTopPaddingPx)
        .coerceAtLeast(0)
        .toFloat()
    val lineIndex = visibleBlock.layout
        .getLineForVerticalPosition(textY)
        .coerceIn(0, visibleBlock.layout.lineCount - 1)
    val charOffset = visibleBlock.layout
        .getLineStart(lineIndex)
        .coerceIn(0, visibleBlock.text.length)
    val byteOffsetInBlock = utf8ByteCountBeforeCharOffset(
        text = visibleBlock.text,
        charOffset = charOffset
    )
    val absoluteByte = (bytesBeforeBlock + byteOffsetInBlock)
        .coerceIn(0, totalTextBytes)
    return (absoluteByte.toDouble() / totalTextBytes.toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
}

private fun currentImportedTextReadAloudStartAnchor(
    listState: androidx.compose.foundation.lazy.LazyListState,
    marker: String,
    byteLength: Long,
    chunkCount: Int,
    chunkTexts: Map<Int, String>,
    chunkLayouts: Map<Int, TextLayoutResult>
): ReadAloudStartAnchor? {
    if (chunkCount <= 0 || byteLength <= 0L) return null
    val visibleChunk = firstVisibleImportedTextChunkWithLayout(
        listState = listState,
        marker = marker,
        chunkCount = chunkCount,
        chunkTexts = chunkTexts,
        chunkLayouts = chunkLayouts
    ) ?: return null
    val topCharOffset = topVisibleTextCharOffset(
        viewportStartOffset = listState.layoutInfo.viewportStartOffset,
        itemInfo = visibleChunk.itemInfo,
        text = visibleChunk.text,
        layout = visibleChunk.layout
    )
    val anchorCharOffset = readAloudBoundaryStartOffset(
        text = visibleChunk.text,
        startCharOffset = topCharOffset
    )
    val byteOffsetInChunk = utf8ByteCountBeforeCharOffset(
        text = visibleChunk.text,
        charOffset = anchorCharOffset
    )
    val absoluteByte = (visibleChunk.chunkIndex.toLong() * ARTICLE_TEXT_CHUNK_BYTES.toLong() + byteOffsetInChunk)
        .coerceIn(0L, byteLength)
    return ReadAloudStartAnchor(
        textSnippet = readAloudAnchorSnippet(visibleChunk.text, anchorCharOffset),
        progress = (absoluteByte.toDouble() / byteLength.toDouble()).toFloat().coerceIn(0f, 1f),
        importedChunkIndex = visibleChunk.chunkIndex,
        importedCharOffset = anchorCharOffset
    )
}

private fun currentContentTextReadAloudStartAnchor(
    listState: androidx.compose.foundation.lazy.LazyListState,
    firstContentItemIndex: Int,
    contentBlocks: List<ContentBlock>,
    textLayouts: Map<Int, TextLayoutResult>
): ReadAloudStartAnchor? {
    val visibleBlock = firstVisibleContentTextBlockWithLayout(
        listState = listState,
        firstContentItemIndex = firstContentItemIndex,
        contentBlocks = contentBlocks,
        textLayouts = textLayouts
    ) ?: return null
    val topCharOffset = topVisibleTextCharOffset(
        viewportStartOffset = listState.layoutInfo.viewportStartOffset,
        itemInfo = visibleBlock.itemInfo,
        text = visibleBlock.text,
        layout = visibleBlock.layout
    )
    val anchorCharOffset = readAloudBoundaryStartOffset(
        text = visibleBlock.text,
        startCharOffset = topCharOffset
    )
    val totalTextBytes = contentBlocks.sumOf { block ->
        (block as? ContentBlock.Text)?.text?.let(::utf8ByteCount) ?: 0
    }
    val bytesBeforeBlock = contentBlocks.asSequence()
        .take(visibleBlock.blockIndex)
        .sumOf { block -> (block as? ContentBlock.Text)?.text?.let(::utf8ByteCount) ?: 0 }
    val byteOffsetInBlock = utf8ByteCountBeforeCharOffset(
        text = visibleBlock.text,
        charOffset = anchorCharOffset
    )
    val progress = if (totalTextBytes > 0) {
        ((bytesBeforeBlock + byteOffsetInBlock).toDouble() / totalTextBytes.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    } else {
        null
    }
    return ReadAloudStartAnchor(
        textSnippet = readAloudAnchorSnippet(visibleBlock.text, anchorCharOffset),
        progress = progress,
        contentBlockIndex = visibleBlock.blockIndex,
        contentCharOffset = anchorCharOffset
    )
}

private fun firstVisibleImportedTextChunkWithLayout(
    listState: androidx.compose.foundation.lazy.LazyListState,
    marker: String,
    chunkCount: Int,
    chunkTexts: Map<Int, String>,
    chunkLayouts: Map<Int, TextLayoutResult>
): VisibleImportedTextChunkWithLayout? {
    return listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { itemInfo ->
        val chunkIndex = importedTextChunkIndexFromKey(itemInfo.key, marker)
            ?: return@firstNotNullOfOrNull null
        if (chunkIndex !in 0 until chunkCount) {
            null
        } else {
            val text = chunkTexts[chunkIndex] ?: return@firstNotNullOfOrNull null
            val layout = chunkLayouts[chunkIndex] ?: return@firstNotNullOfOrNull null
            if (layout.lineCount <= 0) return@firstNotNullOfOrNull null
            VisibleImportedTextChunkWithLayout(
                itemInfo = itemInfo,
                chunkIndex = chunkIndex,
                text = text,
                layout = layout
            )
        }
    }
}

private fun firstVisibleContentTextBlockWithLayout(
    listState: androidx.compose.foundation.lazy.LazyListState,
    firstContentItemIndex: Int,
    contentBlocks: List<ContentBlock>,
    textLayouts: Map<Int, TextLayoutResult>
): VisibleContentTextBlockWithLayout? {
    return listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { itemInfo ->
        val blockIndex = itemInfo.index - firstContentItemIndex
        val block = contentBlocks.getOrNull(blockIndex) as? ContentBlock.Text
            ?: return@firstNotNullOfOrNull null
        val layout = textLayouts[blockIndex] ?: return@firstNotNullOfOrNull null
        if (layout.lineCount <= 0) return@firstNotNullOfOrNull null
        VisibleContentTextBlockWithLayout(
            itemInfo = itemInfo,
            blockIndex = blockIndex,
            text = block.text,
            layout = layout
        )
    }
}

private fun topVisibleTextCharOffset(
    viewportStartOffset: Int,
    itemInfo: LazyListItemInfo,
    text: String,
    layout: TextLayoutResult
): Int {
    if (layout.lineCount <= 0) return 0
    val scrolledInItemPx = (viewportStartOffset - itemInfo.offset)
        .coerceIn(0, itemInfo.size.coerceAtLeast(0))
    val textTopPaddingPx = (itemInfo.size - layout.size.height)
        .coerceAtLeast(0)
    val textY = (scrolledInItemPx - textTopPaddingPx)
        .coerceAtLeast(0)
        .toFloat()
    val lineIndex = layout
        .getLineForVerticalPosition(textY)
        .coerceIn(0, layout.lineCount - 1)
    return layout
        .getLineStart(lineIndex)
        .coerceIn(0, text.length)
}

private fun readAloudBoundaryStartOffset(text: String, startCharOffset: Int): Int {
    var index = startCharOffset.coerceIn(0, text.length)
    while (index < text.length) {
        if (text[index] in READ_ALOUD_TOP_BOUNDARIES) {
            return skipWhitespace(text, index + 1)
        }
        index++
    }
    return startCharOffset.coerceIn(0, text.length)
}

private fun readAloudAnchorSnippet(text: String, charOffset: Int): String? {
    val normalized = ReadAloudTextSegmenter.normalizePlainText(
        text.substring(charOffset.coerceIn(0, text.length))
            .take(READ_ALOUD_ANCHOR_SNIPPET_CHARS)
    )
    return normalized.takeIf { it.length >= READ_ALOUD_ANCHOR_MIN_SNIPPET_CHARS }
}

private fun skipWhitespace(text: String, startCharOffset: Int): Int {
    var index = startCharOffset.coerceIn(0, text.length)
    while (index < text.length && text[index].isWhitespace()) {
        index++
    }
    return index
}

private fun contentTextRestoreTarget(
    progress: Float,
    firstContentItemIndex: Int,
    contentBlocks: List<ContentBlock>
): ContentTextRestoreTarget? {
    val textByteCounts = contentBlocks.map { block ->
        (block as? ContentBlock.Text)?.text?.let(::utf8ByteCount) ?: 0
    }
    val totalTextBytes = textByteCounts.sum()
    if (totalTextBytes <= 0) return null
    val targetByte = (totalTextBytes.toDouble() * progress.coerceIn(0f, 1f).toDouble())
        .roundToInt()
        .coerceIn(0, (totalTextBytes - 1).coerceAtLeast(0))
    var consumed = 0
    textByteCounts.forEachIndexed { index, byteCount ->
        if (byteCount <= 0) return@forEachIndexed
        val next = consumed + byteCount
        if (targetByte < next) {
            return ContentTextRestoreTarget(
                itemIndex = firstContentItemIndex + index,
                blockIndex = index,
                byteOffsetInBlock = (targetByte - consumed).coerceAtLeast(0)
            )
        }
        consumed = next
    }
    val lastTextIndex = textByteCounts.indexOfLast { it > 0 }
    if (lastTextIndex < 0) return null
    return ContentTextRestoreTarget(
        itemIndex = firstContentItemIndex + lastTextIndex,
        blockIndex = lastTextIndex,
        byteOffsetInBlock = (textByteCounts[lastTextIndex] - 1).coerceAtLeast(0)
    )
}

private fun importedTextRestoreVisualOffsetPx(
    restoreTarget: ImportedTextByteRestoreTarget,
    text: String,
    layout: TextLayoutResult,
    itemInfo: LazyListItemInfo
): Int {
    if (layout.lineCount <= 0) return 0
    val charOffset = utf8CharOffsetForByteOffset(
        text = text,
        byteOffset = restoreTarget.byteOffsetInChunk
    ).coerceIn(0, text.length)
    val lineIndex = layout
        .getLineForOffset(charOffset)
        .coerceIn(0, layout.lineCount - 1)
    val textTopPaddingPx = (itemInfo.size - layout.size.height)
        .coerceAtLeast(0)
    return (textTopPaddingPx + layout.getLineTop(lineIndex))
        .roundToInt()
        .coerceAtLeast(0)
}

private fun contentTextRestoreVisualOffsetPx(
    restoreTarget: ContentTextRestoreTarget,
    text: String,
    layout: TextLayoutResult,
    itemInfo: LazyListItemInfo
): Int {
    if (layout.lineCount <= 0) return 0
    val charOffset = utf8CharOffsetForByteOffset(
        text = text,
        byteOffset = restoreTarget.byteOffsetInBlock
    ).coerceIn(0, text.length)
    val lineIndex = layout
        .getLineForOffset(charOffset)
        .coerceIn(0, layout.lineCount - 1)
    val textTopPaddingPx = (itemInfo.size - layout.size.height)
        .coerceAtLeast(0)
    return (textTopPaddingPx + layout.getLineTop(lineIndex))
        .roundToInt()
        .coerceAtLeast(0)
}

private fun utf8ByteCount(text: String): Int {
    return utf8ByteCountBeforeCharOffset(text, text.length)
}

private fun utf8ByteCountBeforeCharOffset(text: String, charOffset: Int): Int {
    val targetCharOffset = charOffset.coerceIn(0, text.length)
    var byteCount = 0
    var index = 0
    while (index < targetCharOffset) {
        val codePoint = Character.codePointAt(text, index)
        byteCount += utf8ByteCountForCodePoint(codePoint)
        index += Character.charCount(codePoint)
    }
    return byteCount
}

private fun utf8CharOffsetForByteOffset(text: String, byteOffset: Int): Int {
    if (byteOffset <= 0) return 0
    var byteCount = 0
    var index = 0
    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        val codePointByteCount = utf8ByteCountForCodePoint(codePoint)
        if (byteCount + codePointByteCount > byteOffset) return index
        byteCount += codePointByteCount
        index += Character.charCount(codePoint)
    }
    return text.length
}

private fun utf8ByteCountForCodePoint(codePoint: Int): Int {
    return when {
        codePoint <= 0x7F -> 1
        codePoint <= 0x7FF -> 2
        codePoint <= 0xFFFF -> 3
        else -> 4
    }
}

private const val DETAIL_CONTENT_START_ITEM_INDEX = 4
private const val DETAIL_TITLE_ITEM_INDEX = 2
private const val DETAIL_LOADING_SKELETON_ITEM_COUNT = 1
private const val IMPORTED_TEXT_RESTORE_OFFSET_TIMEOUT_MS = 3_000L
private const val IMPORTED_TEXT_SAVE_LAYOUT_TIMEOUT_MS = 800L
private const val READ_ALOUD_MANUAL_SCROLL_GRACE_MS = 3_000L
private const val READ_ALOUD_LONG_PRESS_DEBOUNCE_MS = 600L
private const val READ_ALOUD_SCROLL_TARGET_TIMEOUT_MS = 700L
private const val READ_ALOUD_SCROLL_LAYOUT_TIMEOUT_MS = 1_200L
private const val READ_ALOUD_AUTO_SCROLL_SETTLE_MS = 120L
private const val READ_ALOUD_FALLBACK_MATCH_CHARS = 160
private const val READ_ALOUD_ANCHOR_SNIPPET_CHARS = 220
private const val READ_ALOUD_ANCHOR_MIN_SNIPPET_CHARS = 8
private val READ_ALOUD_TOP_BOUNDARIES = setOf(
    '，',
    ',',
    '。',
    '.',
    '；',
    ';',
    '！',
    '!',
    '？',
    '?'
)
private val READ_ALOUD_HIGHLIGHT_TRIM_ENDINGS = setOf(
    '，',
    ',',
    '。',
    '.',
    '；',
    ';',
    '：',
    ':',
    '！',
    '!',
    '？',
    '?'
)
private const val IMPORTED_TEXT_SCROLL_SAVE_SAMPLE_MS = 500L
private const val IMPORTED_TEXT_FIRST_INDEX_TIMEOUT_MS = 1_500L
private const val CONTENT_TEXT_RESTORE_OFFSET_TIMEOUT_MS = 3_000L
private const val CONTENT_TEXT_SAVE_LAYOUT_TIMEOUT_MS = 800L
private const val IMPORTED_TEXT_PROGRESS_LOG_TAG = "ImportedTextProgress"
private const val IMPORTED_TEXT_KEY_PREFIX = "importedText:"
