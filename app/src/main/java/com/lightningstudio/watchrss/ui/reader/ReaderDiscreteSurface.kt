package com.lightningstudio.watchrss.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lightningstudio.watchrss.data.rss.ARTICLE_TEXT_CHUNK_BYTES
import com.lightningstudio.watchrss.data.rss.ImportedTextReader
import com.lightningstudio.watchrss.data.rss.NovelNavigation
import com.lightningstudio.watchrss.ui.components.WatchTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/** A page renders a bounded set of native text lines. No scroll list sits below it. */
@Composable
internal fun ReaderDiscreteSurface(
    itemId: Long,
    title: String,
    texts: List<String>,
    mediaSegments: Set<Int> = emptySet(),
    mediaContent: @Composable (Int) -> Unit = {},
    imported: ImportedTextReader?,
    initialProgress: Float,
    pageSeconds: Int,
    autoEnabled: Boolean,
    suspended: Boolean,
    color: Color,
    navigation: NovelNavigation?,
    loadChunk: suspend (String, Int) -> String?,
    saveProgress: suspend (Float) -> Unit,
    onPosition: (Float) -> Unit,
    onOpenChapter: (Long) -> Unit,
    onBack: (Boolean) -> Unit,
    onReadAloud: (Float) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val style = readerTextStyle(ReaderTextRole.BODY)
    var chunk by rememberSaveable(itemId, imported?.marker) {
        mutableStateOf(if (imported == null) 0 else
            ((imported.byteLength.toDouble() * initialProgress.coerceIn(0f, 1f)) / ARTICLE_TEXT_CHUNK_BYTES)
                .toInt().coerceIn(0, (imported.chunkCount - 1).coerceAtLeast(0)))
    }
    var chunkProgress by rememberSaveable(itemId, imported?.marker) {
        mutableStateOf(if (imported == null) initialProgress else {
            val length = minOf(ARTICLE_TEXT_CHUNK_BYTES.toLong(), imported.byteLength - chunk.toLong() * ARTICLE_TEXT_CHUNK_BYTES)
            if (length <= 0L) 0f else
                ((imported.byteLength.toDouble() * initialProgress - chunk.toLong() * ARTICLE_TEXT_CHUNK_BYTES) / length).toFloat().coerceIn(0f, 1f)
        })
    }
    var restoreLastPage by rememberSaveable(itemId, imported?.marker) { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf(0) }
    var retry by remember { mutableStateOf(0) }
    var loadFailed by remember(imported?.marker, chunk) { mutableStateOf(false) }
    val loadedChunk by produceState<Triple<String, Int, String>?>(null, imported?.marker, chunk, retry) {
        value = null
        loadFailed = false
        imported?.let { reader ->
            try {
                val text = loadChunk(reader.marker, chunk)
                if (text == null) loadFailed = true
                else value = Triple(reader.marker, chunk, text)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadFailed = true
            }
        }
    }
    val chunkText = loadedChunk?.takeIf { it.first == imported?.marker && it.second == chunk }?.third
    val pageTexts = remember(texts, imported, chunkText) {
        if (imported == null) texts else chunkText?.let(::listOf).orEmpty()
    }
    var anchorSegment by rememberSaveable(itemId, imported?.marker, chunk) { mutableStateOf(-1) }
    var anchorOffset by rememberSaveable(itemId, imported?.marker, chunk) { mutableStateOf(0) }
    var sessionAuto by rememberSaveable(itemId, autoEnabled) { mutableStateOf(autoEnabled) }
    var openingChapter by remember(itemId) { mutableStateOf(false) }
    var position by remember(itemId, imported?.marker) { mutableStateOf<Float?>(null) }
    var completed by rememberSaveable(itemId) { mutableStateOf(initialProgress >= 1f) }
    val latestSave = rememberUpdatedState(saveProgress)
    val latestPosition = rememberUpdatedState(position)
    val latestCompleted = rememberUpdatedState(completed)
    val latestOnPosition = rememberUpdatedState(onPosition)
    val latestBack = rememberUpdatedState(onBack)

    fun progressFor(anchor: ReaderTextAnchor): Float {
        val local = readerAnchorProgress(pageTexts, anchor)
        if (imported == null) return local
        if (imported.byteLength <= 0L) return 0f
        val before = chunk.toLong() * ARTICLE_TEXT_CHUNK_BYTES
        val length = pageTexts.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
        return ((before + length * local) / imported.byteLength).coerceIn(0f, 1f)
    }

    LaunchedEffect(pageTexts, chunk) {
        if (pageTexts.isNotEmpty() && anchorSegment < 0) {
            val anchor = readerAnchorFromProgress(pageTexts, chunkProgress)
            anchorSegment = anchor.segment
            anchorOffset = anchor.offset
        }
    }
    LaunchedEffect(anchorSegment, anchorOffset, chunk, pageTexts, completed) {
        if (anchorSegment >= 0 && pageTexts.isNotEmpty()) {
            val progress = if (completed) 1f else progressFor(ReaderTextAnchor(anchorSegment, anchorOffset))
            position = progress
            latestOnPosition.value(progress)
            latestSave.value(progress)
        }
    }
    DisposableEffect(owner, itemId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                latestPosition.value?.let { progress -> runBlocking(Dispatchers.IO) { latestSave.value(progress) } }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler {
        latestPosition.value?.let { progress -> runBlocking(Dispatchers.IO) { latestSave.value(progress) } }
        latestBack.value(latestCompleted.value)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { pageHeight.toPx() }
        val lineHeight = measurer.measure("国Ag", style = style).size.height.toFloat().coerceAtLeast(1f)
        val pages = remember(pageTexts, mediaSegments, width, height, style, measurer, lineHeight) {
            paginateReaderText(pageTexts, width, height, lineHeight, if (imported == null) mediaSegments else emptySet()) { text, start, available ->
                val layout = measurer.measure(
                    text.substring(start), style = style,
                    constraints = Constraints(maxWidth = available), maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                start + layout.getLineEnd(0)
            }
        }
        LaunchedEffect(pages, chunk, restoreLastPage) {
            if (restoreLastPage && pages.isNotEmpty()) {
                // A backward chunk transition shows its final page, rather than
                // saving the byte just after that page's visible text.
                anchorSegment = pages.last().anchor.segment
                anchorOffset = pages.last().anchor.offset
                restoreLastPage = false
            }
        }
        val anchor = ReaderTextAnchor(anchorSegment.coerceAtLeast(0), anchorOffset)
        val pageIndex = readerPageForAnchor(pages, anchor).coerceAtMost((pages.size - 1).coerceAtLeast(0))
        val lastChunk = imported == null || chunk >= imported.chunkCount - 1
        val atEnd = pages.isNotEmpty() && pageIndex == pages.lastIndex && lastChunk

        fun turn(direction: Int) {
            if (pages.isEmpty()) return
            if (completed && direction < 0) {
                completed = false
                return
            }
            val target = pageIndex + direction
            completed = false
            if (target in pages.indices) {
                anchorSegment = pages[target].anchor.segment
                anchorOffset = pages[target].anchor.offset
            } else if (direction > 0 && !lastChunk) {
                position = null
                chunkProgress = 0f
                chunk++
            } else if (direction < 0 && imported != null && chunk > 0) {
                position = null
                chunkProgress = 1f
                restoreLastPage = true
                chunk--
            } else if (direction > 0) {
                completed = true
                sessionAuto = false
            }
        }

        LaunchedEffect(itemId, chunk, pageIndex, pages, sessionAuto, pageSeconds, suspended, anchorSegment, manualInput) {
            if (!sessionAuto || suspended || pages.isEmpty() || anchorSegment < 0) return@LaunchedEffect
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                awaitReaderAutoPage(pageSeconds)
                turn(1)
            }
        }
        Box(Modifier.fillMaxSize().pointerInput(pageIndex, pages, chunk, suspended) {
            if (!suspended) detectTapGestures(
                onTap = { point ->
                    manualInput++
                    if (point.x < size.width / 3f) turn(-1)
                    else if (point.x > size.width * 2f / 3f) turn(1)
                },
                onLongPress = { position?.let(onReadAloud) }
            )
        }) {
            Text(title, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp, modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = pageHeight * 0.09f).fillMaxWidth(0.50f))
            pages.getOrNull(pageIndex)?.takeUnless { completed && navigation != null }?.lines?.forEach { line ->
                Text(pageTexts[line.anchor.segment].substring(line.anchor.offset, line.end),
                    style = style, color = color, softWrap = false, maxLines = 1,
                    modifier = Modifier.offset { IntOffset(line.left.roundToInt(), line.top.roundToInt()) }
                        .width(with(density) { line.width.toDp() }))
            }
            pages.getOrNull(pageIndex)?.mediaSegment?.takeUnless { completed && navigation != null }?.let { segment ->
                Box(Modifier.align(Alignment.TopCenter).padding(top = pageHeight * 0.17f)
                    .fillMaxWidth(0.68f).height(pageHeight * 0.50f)) {
                    mediaContent(segment)
                }
            }
            if (pages.isEmpty()) {
                Column(Modifier.align(Alignment.Center).fillMaxWidth(0.65f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(when {
                        loadFailed -> "正文加载失败"
                        pageTexts.isEmpty() -> "正文加载中"
                        else -> "当前字号无法排版"
                    }, color = color)
                    if (imported != null) WatchTextButton(onClick = { retry++ }) { Text("重试") }
                }
            }
            Row(Modifier.align(Alignment.TopCenter).padding(top = pageHeight * 0.70f).fillMaxWidth(0.60f),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Text(if (pages.isEmpty()) "" else "${pageIndex + 1}/${pages.size}", color = color, fontSize = 11.sp)
                Text(if (sessionAuto) "暂停翻页" else "自动翻页", color = color, fontSize = 11.sp,
                    modifier = Modifier.clickable(enabled = pages.isNotEmpty()) { sessionAuto = !sessionAuto }
                        .padding(vertical = 2.dp))
            }
            // Chapter actions occupy an explicit final page, outside the tappable paper.
            if (atEnd && navigation != null) {
                Row(Modifier.align(Alignment.Center).fillMaxWidth(0.68f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    // Shown after an extra forward tap, so the last text page remains readable.
                    if (completed) listOf("上一章" to navigation.previous, "下一章" to navigation.next).forEach { (label, chapter) ->
                        WatchTextButton(enabled = chapter != null && !openingChapter && !suspended, onClick = {
                            if (chapter != null && !openingChapter) {
                                openingChapter = true
                                sessionAuto = false
                                scope.launch {
                                    try {
                                        val progress = requireNotNull(position)
                                        latestSave.value(progress)
                                        onOpenChapter(chapter.id)
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        openingChapter = false
                                        com.lightningstudio.watchrss.ui.util.showAppToast(context, "章节切换失败，请重试")
                                    }
                                }
                            }
                        }) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}
