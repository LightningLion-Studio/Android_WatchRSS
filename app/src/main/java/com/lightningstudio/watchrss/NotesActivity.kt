package com.lightningstudio.watchrss

import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.data.note.RawTextUndoManager
import com.lightningstudio.watchrss.data.note.WatchNoteEntity
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownHandler
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.input.NoteCursorCrownAccelerator
import com.lightningstudio.watchrss.ui.reader.ReaderPageLayout
import com.lightningstudio.watchrss.ui.screen.rss.DetailTitle
import com.lightningstudio.watchrss.ui.screen.rss.FeedEmptyState
import com.lightningstudio.watchrss.ui.screen.rss.FeedHeader
import com.lightningstudio.watchrss.ui.screen.rss.FeedItemEntry
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.theme.LocalRubberBandOverscrollOffset
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The watch deliberately exposes only the raw Markdown/TXT source, without rich-text controls. */
class NotesActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        val repository = (application as WatchRssApplication).container.watchNoteRepository
        val deviceId = WatchDeviceIdentity(this).deviceId
        setContent {
            WatchRSSTheme {
                val notes by repository.observe().collectAsState(initial = emptyList())
                var selectedNoteId by remember { mutableStateOf<String?>(null) }
                var editingNoteId by remember { mutableStateOf<String?>(null) }
                var creating by remember { mutableStateOf(false) }
                WatchSurface(pureBlack = true) {
                    val editorSafeInset = notesEditorSafeInset()
                    val selected = selectedNoteId?.let { id -> notes.firstOrNull { it.noteId == id } }
                    val editing = editingNoteId?.let { id -> notes.firstOrNull { it.noteId == id } }
                    val documentKey = selected?.noteId ?: editing?.noteId ?: NEW_NOTE_DOCUMENT_KEY
                    val documentScrollState = remember(documentKey) { ScrollState(0) }
                    if (selected == null && editing == null && !creating) {
                        NotesFeedList(
                            notes = notes,
                            onNoteClick = { note ->
                                    creating = false
                                    selectedNoteId = note.noteId
                            },
                            onCreateNote = { creating = true }
                        )
                    } else if (selected != null && editing == null && !creating) {
                        WatchNoteReader(
                            note = selected,
                            scrollState = documentScrollState,
                            onBack = { selectedNoteId = null },
                            onEdit = { editingNoteId = selected.noteId }
                        )
                    } else {
                        WatchNoteRawEditor(
                            note = editing,
                            safeInset = editorSafeInset,
                            scrollState = documentScrollState,
                            saveDraft = { noteId, markdown ->
                                repository.saveRawMarkdown(noteId, markdown, deviceId)
                            },
                            onClose = {
                                editingNoteId = null
                                creating = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NotesFeedList(
    notes: List<WatchNoteEntity>,
    onNoteClick: (WatchNoteEntity) -> Unit,
    onCreateNote: () -> Unit
) {
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
        val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
        val itemSpacing = watchDimensionResource(R.dimen.hey_distance_8dp)
        val listState = rememberLazyListState()
        InstallDigitalCrownLazyListHandler(listState)
        val isScrolling by remember(listState) {
            derivedStateOf { listState.isScrollInProgress }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = safePadding),
            state = listState,
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = itemSpacing
            )
        ) {
            item(key = "header") {
                Box(modifier = Modifier.padding(bottom = itemSpacing)) {
                    FeedHeader(
                        title = "备忘录",
                        isRefreshing = false,
                        enabled = false,
                        showInfoHint = false,
                        idleHint = "${notes.size} 条备忘录",
                        onClick = {}
                    )
                }
            }
            if (notes.isEmpty()) {
                item(key = "empty") {
                    FeedEmptyState()
                }
            } else {
                items(notes, key = { it.noteId }) { note ->
                    val feedItem = remember(note) { note.asFeedItem() }
                    Box(modifier = Modifier.padding(bottom = itemSpacing)) {
                        FeedItemEntry(
                            item = feedItem,
                            thumbUrl = null,
                            maxImageWidthPx = 1,
                            isScrolling = isScrolling,
                            useOriginalContent = false,
                            openSwipeId = null,
                            onOpenSwipe = {},
                            onCloseSwipe = {},
                            draggingSwipeId = null,
                            onDragStart = {},
                            onDragEnd = {},
                            onClick = { onNoteClick(note) },
                            onLongClick = { onNoteClick(note) },
                            onFavoriteClick = {},
                            onWatchLaterClick = {},
                            swipeActionsEnabled = false,
                            semanticItemLabel = "备忘录"
                        )
                    }
                }
            }
            item(key = "create") {
                NewNoteButton(onClick = onCreateNote)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NewNoteButton(onClick: () -> Unit) {
    val buttonSize = watchDimensionResource(R.dimen.hey_button_height)
    val verticalPadding = watchDimensionResource(R.dimen.hey_distance_4dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建备忘录")
        }
    }
}

private fun WatchNoteEntity.asFeedItem(): RssItem = RssItem(
    id = noteId.hashCode().toLong(),
    channelId = NOTES_CHANNEL_ID,
    title = title.ifBlank { "未命名笔记" },
    description = null,
    content = plainText,
    originalContent = null,
    link = null,
    pubDate = null,
    imageUrl = null,
    audioUrl = null,
    videoUrl = null,
    summary = notePreview(this).ifBlank { "暂无摘要" },
    previewImageUrl = null,
    isRead = true,
    isLiked = pinned,
    readingProgress = 0f,
    fetchedAt = updatedAt
)

@androidx.compose.runtime.Composable
private fun WatchNoteReader(
    note: WatchNoteEntity,
    scrollState: ScrollState,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    BackHandler(onBack = onBack)
    InstallDigitalCrownScrollHandler(scrollState)
    Box(modifier = Modifier.fillMaxSize()) {
        NoteDocumentContent(
            title = note.title.ifBlank { "未命名笔记" },
            metadata = noteMetadata(note),
            scrollState = scrollState
        ) {
            SelectionContainer {
                Text(
                    text = note.plainText.ifBlank { "这条备忘录还没有正文。" },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (note.plainText.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Justify),
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(NOTE_TOOL_OVERLAY_HEIGHT),
            contentAlignment = Alignment.Center
        ) {
            CompactEditButton(onClick = onEdit)
        }
    }
}

@androidx.compose.runtime.Composable
private fun NoteDocumentContent(
    title: String,
    metadata: String?,
    scrollState: ScrollState,
    body: @androidx.compose.runtime.Composable () -> Unit
) {
    val horizontalPadding = ReaderPageLayout.horizontalPadding
    val titlePadding = ReaderPageLayout.titleHorizontalPadding
    val verticalPadding = ReaderPageLayout.topSafePadding
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = horizontalPadding)
            .padding(top = verticalPadding, bottom = NOTE_DOCUMENT_BOTTOM_PADDING),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "阅读备忘录",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        DetailTitle(
            title = title,
            titlePadding = titlePadding,
            textColor = MaterialTheme.colorScheme.onSurface
        )
        if (metadata != null) {
            Text(
                text = metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        body()
    }
}

@androidx.compose.runtime.Composable
private fun CompactEditButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "编辑",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@androidx.compose.runtime.Composable
private fun WatchNoteRawEditor(
    note: WatchNoteEntity?,
    safeInset: androidx.compose.ui.unit.Dp,
    scrollState: ScrollState,
    saveDraft: suspend (noteId: String?, markdown: String) -> WatchNoteEntity,
    onClose: () -> Unit
) {
    val initialValue = remember(note?.noteId) {
        val markdown = note?.markdown.orEmpty()
        TextFieldValue(
            text = markdown,
            selection = TextRange(
                estimateNoteCursorForScroll(
                    textLength = markdown.length,
                    scrollValue = scrollState.value,
                    scrollMaxValue = scrollState.maxValue
                )
            )
        )
    }
    val history = remember(note?.noteId) { RawTextUndoManager(initialValue) }
    var editorValue by remember(note?.noteId) { mutableStateOf(initialValue) }
    var persistedNoteId by remember(note?.noteId) { mutableStateOf(note?.noteId) }
    var lastSavedText by remember(note?.noteId) { mutableStateOf(note?.markdown.orEmpty()) }
    var saveState by remember(note?.noteId) {
        mutableStateOf(if (note == null) DraftSaveState.EMPTY else DraftSaveState.SAVED)
    }
    var closing by remember(note?.noteId) { mutableStateOf(false) }
    val saveMutex = remember(note?.noteId) { Mutex() }
    val scope = rememberCoroutineScope()
    val crownAccelerator = remember(note?.noteId) { NoteCursorCrownAccelerator() }
    var textLayoutResult by remember(note?.noteId) { mutableStateOf<TextLayoutResult?>(null) }
    var textFieldFocused by remember(note?.noteId) { mutableStateOf(false) }

    InstallDigitalCrownHandler(supportsDigitalCrown = true) { delta ->
        if (delta == 0f) return@InstallDigitalCrownHandler false
        val steps = crownAccelerator.consume(-delta, SystemClock.uptimeMillis())
        if (steps == 0) return@InstallDigitalCrownHandler true
        val direction = if (steps > 0) 1 else -1
        var next = editorValue
        repeat(kotlin.math.abs(steps)) {
            next = moveNoteCursor(next, direction)
        }
        if (next.selection != editorValue.selection) {
            editorValue = history.record(next, SystemClock.uptimeMillis())
        }
        true
    }

    suspend fun persist(markdown: String): Boolean = saveMutex.withLock {
        if (markdown == lastSavedText) {
            saveState = if (persistedNoteId == null) DraftSaveState.EMPTY else DraftSaveState.SAVED
            return@withLock true
        }
        if (persistedNoteId == null && markdown.isBlank()) {
            saveState = DraftSaveState.EMPTY
            return@withLock true
        }
        saveState = DraftSaveState.SAVING
        try {
            val saved = saveDraft(persistedNoteId, markdown)
            persistedNoteId = saved.noteId
            lastSavedText = saved.markdown
            saveState = if (editorValue.text == saved.markdown) {
                DraftSaveState.SAVED
            } else {
                DraftSaveState.PENDING
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            saveState = DraftSaveState.ERROR
            false
        }
    }

    fun requestClose() {
        if (closing) return
        closing = true
        scope.launch {
            if (persist(editorValue.text)) onClose() else closing = false
        }
    }

    LaunchedEffect(editorValue.text) {
        val pendingText = editorValue.text
        when {
            pendingText == lastSavedText -> {
                saveState = if (persistedNoteId == null) DraftSaveState.EMPTY else DraftSaveState.SAVED
            }
            persistedNoteId == null && pendingText.isBlank() -> saveState = DraftSaveState.EMPTY
            else -> {
                saveState = DraftSaveState.PENDING
                delay(AUTO_SAVE_DELAY_MILLIS)
                persist(pendingText)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestEditorValue by rememberUpdatedState(editorValue)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                scope.launch { persist(latestEditorValue.text) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(onBack = ::requestClose)
    val bodyTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Justify,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f
    )
    val cursorColor = MaterialTheme.colorScheme.primary
    val minimumBodyHeight = LocalConfiguration.current.screenHeightDp.dp * 0.58f

    val rubberBandOffset = LocalRubberBandOverscrollOffset.current
    Box(modifier = Modifier.fillMaxSize()) {
        NoteDocumentContent(
            title = note?.title?.ifBlank { "未命名笔记" } ?: "新建备忘录",
            metadata = note?.let(::noteMetadata),
            scrollState = scrollState
        ) {
            BasicTextField(
                value = editorValue,
                onValueChange = {
                    editorValue = history.record(it, SystemClock.uptimeMillis())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minimumBodyHeight)
                    .onFocusChanged { textFieldFocused = it.isFocused }
                    .drawWithContent {
                        drawContent()
                        if (!textFieldFocused) {
                            val cursorRect = textLayoutResult
                                ?.getCursorRect(editorValue.selection.end.coerceIn(0, editorValue.text.length))
                            if (cursorRect != null) {
                                drawLine(
                                    color = cursorColor,
                                    start = androidx.compose.ui.geometry.Offset(
                                        cursorRect.left,
                                        cursorRect.top
                                    ),
                                    end = androidx.compose.ui.geometry.Offset(
                                        cursorRect.left,
                                        cursorRect.bottom
                                    ),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    },
                textStyle = bodyTextStyle,
                cursorBrush = SolidColor(cursorColor),
                onTextLayout = { textLayoutResult = it },
                decorationBox = { innerTextField ->
                    if (editorValue.text.isEmpty()) {
                        Text(
                            text = "直接输入原始文本",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = bodyTextStyle
                        )
                    }
                    innerTextField()
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The theme translates the whole screen during rubber-band overscroll. Apply
                    // the inverse only to editor chrome so its controls and scrims stay screen-fixed.
                    translationY = -(rubberBandOffset?.value ?: 0f)
                }
        ) {
            NoteEditorTopOverlay(
                modifier = Modifier.align(Alignment.TopCenter),
                safeInset = safeInset,
                undoEnabled = history.canUndo && !closing,
                redoEnabled = history.canRedo && !closing,
                onUndo = { editorValue = history.undo() },
                onRedo = { editorValue = history.redo() }
            )
            NoteEditorBottomOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                safeInset = safeInset,
                saveState = saveState,
                enabled = !closing,
                onClose = ::requestClose
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun NoteEditorTopOverlay(
    modifier: Modifier,
    safeInset: androidx.compose.ui.unit.Dp,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NOTE_TOOL_OVERLAY_HEIGHT)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black, Color.Black.copy(alpha = 0.9f), Color.Transparent)
                )
            )
            .padding(horizontal = safeInset),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUndo, enabled = undoEnabled) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = onRedo, enabled = redoEnabled) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NoteEditorBottomOverlay(
    modifier: Modifier,
    safeInset: androidx.compose.ui.unit.Dp,
    saveState: DraftSaveState,
    enabled: Boolean,
    onClose: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NOTE_TOOL_OVERLAY_HEIGHT)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f), Color.Black)
                )
            )
            .padding(horizontal = safeInset),
        contentAlignment = Alignment.BottomCenter
    ) {
        IconButton(onClick = onClose, enabled = enabled) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭编辑，${saveState.label}",
                tint = if (saveState == DraftSaveState.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

internal fun estimateNoteCursorForScroll(
    textLength: Int,
    scrollValue: Int,
    scrollMaxValue: Int
): Int {
    if (textLength <= 0 || scrollMaxValue <= 0) return 0
    val fraction = scrollValue.toFloat() / scrollMaxValue.toFloat()
    return (textLength * fraction).toInt().coerceIn(0, textLength)
}

internal fun moveNoteCursor(value: TextFieldValue, direction: Int): TextFieldValue {
    if (direction == 0 || value.text.isEmpty()) return value.copy(composition = null)
    val anchor = if (direction > 0) value.selection.max else value.selection.min
    val target = when {
        direction > 0 && anchor < value.text.length ->
            Character.offsetByCodePoints(value.text, anchor, 1)
        direction < 0 && anchor > 0 ->
            Character.offsetByCodePoints(value.text, anchor, -1)
        else -> anchor
    }
    return value.copy(selection = TextRange(target), composition = null)
}

@androidx.compose.runtime.Composable
private fun notesEditorSafeInset(): androidx.compose.ui.unit.Dp {
    val configuration = LocalConfiguration.current
    return if (configuration.isScreenRound) {
        // Keep every control inside the largest square that fits in a circular display.
        configuration.screenWidthDp.dp * ROUND_SCREEN_SAFE_INSET_FRACTION
    } else {
        16.dp
    }
}

internal fun notePreview(note: WatchNoteEntity): String = notePreview(note.title, note.plainText)

internal fun notePreview(title: String, plainText: String): String {
    val lines = plainText.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    val withoutRepeatedTitle = if (lines.firstOrNull() == title.trim()) lines.drop(1) else lines
    return withoutRepeatedTitle.joinToString(" ").take(NOTE_PREVIEW_LENGTH)
}

private fun noteMetadata(note: WatchNoteEntity): String {
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        note.updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    )
    val characterCount = note.plainText.count { !it.isWhitespace() }
    return "$relativeTime · $characterCount 字"
}

private const val ROUND_SCREEN_SAFE_INSET_FRACTION = 0.1465f
private const val AUTO_SAVE_DELAY_MILLIS = 600L
private const val NOTE_PREVIEW_LENGTH = 120
private const val NOTES_CHANNEL_ID = -1L
private const val NEW_NOTE_DOCUMENT_KEY = "__new_note__"
// REL_WHEEL reports roughly one delta unit per crown detent on the watch. Accumulate about three
// detents for each Unicode code point so precise cursor placement is not too sensitive.
private val NOTE_TOOL_OVERLAY_HEIGHT = 56.dp
private val NOTE_DOCUMENT_BOTTOM_PADDING = 64.dp

private enum class DraftSaveState(val label: String) {
    EMPTY("输入后自动保存"),
    PENDING("等待自动保存…"),
    SAVING("正在保存…"),
    SAVED("已自动保存"),
    ERROR("保存失败；继续编辑或退出时会重试")
}
