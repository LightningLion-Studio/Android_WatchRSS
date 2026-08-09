package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
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
import com.lightningstudio.watchrss.data.note.watchPlainText
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownHandler
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.input.NoteCursorCrownAccelerator
import com.lightningstudio.watchrss.ui.reader.ProvideReaderPreset
import com.lightningstudio.watchrss.ui.reader.ReaderBackgroundSurface
import com.lightningstudio.watchrss.ui.reader.ReaderPageLayout
import com.lightningstudio.watchrss.ui.reader.ReaderTextRole
import com.lightningstudio.watchrss.ui.reader.readerTextStyle
import com.lightningstudio.watchrss.ui.screen.rss.DetailTitle
import com.lightningstudio.watchrss.ui.screen.rss.FeedEmptyState
import com.lightningstudio.watchrss.ui.screen.rss.FeedHeader
import com.lightningstudio.watchrss.ui.screen.rss.FeedItemEntry
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.theme.LocalRubberBandOverscrollOffset
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Rich reading stays separate from the lossless raw Markdown/TXT editor. */
class NotesActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        val container = (application as WatchRssApplication).container
        val repository = container.watchNoteRepository
        setContent {
            WatchRSSTheme {
                ProvideReaderPreset(container.readerPresetRepository) {
                    val notes by repository.observe().collectAsState(initial = emptyList())
                    WatchSurface(pureBlack = true) {
                        NotesFeedList(
                            notes = notes,
                            onNoteClick = { note -> openNote(note.noteId) },
                            onCreateNote = ::createNote
                        )
                    }
                }
            }
        }
    }

    private fun openNote(noteId: String) {
        startActivity(NoteDetailActivity.createIntent(this, noteId))
    }

    private fun createNote() {
        startActivity(NoteDetailActivity.createNewIntent(this))
    }
}

class NoteDetailActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val createMode = intent.getBooleanExtra(EXTRA_CREATE, false)
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        if (!createMode && noteId.isNullOrBlank()) {
            finish()
            return
        }

        val container = (application as WatchRssApplication).container
        val repository = container.watchNoteRepository
        val deviceId = WatchDeviceIdentity(this).deviceId
        setContent {
            WatchRSSTheme {
                ProvideReaderPreset(container.readerPresetRepository) {
                    var loaded by remember { mutableStateOf(createMode) }
                    var note by remember { mutableStateOf<WatchNoteEntity?>(null) }
                    var preparedBlocks by remember {
                        mutableStateOf<List<WatchNotePreviewBlock>>(emptyList())
                    }
                    var editing by remember { mutableStateOf(createMode) }
                    var editorAnchorOffset by remember { mutableStateOf<Int?>(null) }

                    LaunchedEffect(createMode, noteId) {
                        if (!createMode) {
                            // Commit the destination's loading shell before Room reads the note.
                            withFrameNanos { }
                            repository.observe(requireNotNull(noteId)).collectLatest { loadedNote ->
                                val nextBlocks = loadedNote?.let {
                                    withContext(Dispatchers.Default) {
                                        prepareWatchNotePreviewBlocks(it.markdown)
                                    }
                                }.orEmpty()
                                note = loadedNote
                                preparedBlocks = nextBlocks
                                loaded = true
                            }
                        }
                    }

                    WatchSurface(pureBlack = true) {
                        when {
                            !loaded -> NoteLoadingScreen()
                            note == null && !createMode -> NoteMissingScreen(onBack = ::finish)
                            editing -> {
                                val editorNote = if (createMode) null else note
                                WatchNoteRawEditor(
                                    note = editorNote,
                                    safeInset = notesEditorSafeInset(),
                                    scrollState = remember(editorNote?.noteId ?: NEW_NOTE_DOCUMENT_KEY) {
                                        ScrollState(0)
                                    },
                                    initialAnchorOffset = editorAnchorOffset,
                                    saveDraft = { persistedNoteId, markdown ->
                                        repository.saveRawMarkdown(
                                            persistedNoteId,
                                            markdown,
                                            deviceId
                                        ).also { saved ->
                                            note = saved
                                            preparedBlocks = withContext(Dispatchers.Default) {
                                                prepareWatchNotePreviewBlocks(saved.markdown)
                                            }
                                        }
                                    },
                                    onClose = { _, _ ->
                                        editorAnchorOffset = null
                                        if (createMode) {
                                            finish()
                                        } else {
                                            editing = false
                                        }
                                    }
                                )
                            }
                            else -> {
                                val loadedNote = requireNotNull(note)
                                WatchNoteReader(
                                    note = loadedNote,
                                    preparedBlocks = preparedBlocks,
                                    listState = rememberLazyListState(),
                                    onBack = ::finish,
                                    onEdit = { markdownOffset ->
                                        editorAnchorOffset = markdownOffset
                                        editing = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_NOTE_ID = "note_id"
        private const val EXTRA_CREATE = "create"

        fun createIntent(context: Context, noteId: String): Intent =
            Intent(context, NoteDetailActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
            }

        fun createNewIntent(context: Context): Intent =
            Intent(context, NoteDetailActivity::class.java).apply {
                putExtra(EXTRA_CREATE, true)
            }
    }
}

@androidx.compose.runtime.Composable
private fun NoteLoadingScreen() {
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    val indicatorColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    LaunchedEffect(Unit) {
        var startedAtNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (startedAtNanos == 0L) startedAtNanos = frameTimeNanos
                rotationDegrees = noteLoadingRotationDegrees(frameTimeNanos - startedAtNanos)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val strokeWidth = 3.dp.toPx()
            drawCircle(
                color = trackColor,
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = indicatorColor,
                startAngle = rotationDegrees - 90f,
                sweepAngle = 105f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

internal fun noteLoadingRotationDegrees(elapsedNanos: Long): Float {
    val position = elapsedNanos.coerceAtLeast(0L) % NOTE_LOADING_ROTATION_NANOS
    return position.toFloat() / NOTE_LOADING_ROTATION_NANOS.toFloat() * 360f
}

@androidx.compose.runtime.Composable
private fun NoteMissingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = notesEditorSafeInset()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "备忘录不存在",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
    preparedBlocks: List<WatchNotePreviewBlock>,
    listState: LazyListState,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenCenter = with(density) {
        Offset(
            configuration.screenWidthDp.dp.toPx() / 2f,
            configuration.screenHeightDp.dp.toPx() / 2f
        )
    }
    // Layout callbacks only feed the edit-button action. A plain map avoids invalidating and
    // re-laying out rich text every time TextLayoutResult is refreshed.
    val textBlocks = remember(note.noteId) { mutableMapOf<String, NoteReaderAnchorBlock>() }
    var editButtonVisible by remember(note.noteId, listState) { mutableStateOf(true) }

    LaunchedEffect(note.noteId, listState) {
        var previousPosition = listState.firstVisibleItemIndex to
            listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { currentPosition ->
            editButtonVisible = noteEditButtonVisibleAfterLazyScroll(
                wasVisible = editButtonVisible,
                previousItemIndex = previousPosition.first,
                previousItemOffset = previousPosition.second,
                currentItemIndex = currentPosition.first,
                currentItemOffset = currentPosition.second
            )
            previousPosition = currentPosition
        }
    }

    fun centerMarkdownOffset(): Int {
        val block = textBlocks.values.minByOrNull { candidate ->
            val top = candidate.topInRoot
            val right = candidate.leftInRoot + candidate.layout.size.width
            val bottom = top + candidate.layout.size.height
            val dx = when {
                screenCenter.x < candidate.leftInRoot -> candidate.leftInRoot - screenCenter.x
                screenCenter.x > right -> screenCenter.x - right
                else -> 0f
            }
            val dy = when {
                screenCenter.y < top -> top - screenCenter.y
                screenCenter.y > bottom -> screenCenter.y - bottom
                else -> 0f
            }
            dx * dx + dy * dy
        }
        if (block == null) {
            return 0
        }
        val top = block.topInRoot
        val renderedOffset = block.layout.getOffsetForPosition(
            Offset(
                x = (screenCenter.x - block.leftInRoot)
                    .coerceIn(0f, block.layout.size.width.toFloat()),
                y = (screenCenter.y - top)
                    .coerceIn(0f, block.layout.size.height.toFloat())
            )
        )
        return mapNoteTextAnchorOffset(
            sourceText = block.renderedText,
            targetText = note.markdown,
            sourceOffset = renderedOffset
        )
    }

    BackHandler(onBack = onBack)
    InstallDigitalCrownLazyListHandler(listState)
    ReaderBackgroundSurface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            WatchNoteRichTextLazyColumn(
                markup = note.markdown,
                blocks = preparedBlocks,
                emptyText = "这条备忘录还没有正文。",
                listState = listState,
                modifier = Modifier.fillMaxSize(),
                title = note.title.ifBlank { "未命名笔记" },
                metadata = noteMetadata(note),
                onImageClick = { image ->
                    context.startActivity(
                        ImagePreviewActivity.createIntent(
                            context = context,
                            url = resolveWatchNoteImageSource(context.filesDir, image.path),
                            alt = image.description
                        )
                    )
                },
                onTextBlockLayout = { block ->
                    textBlocks[block.key] = NoteReaderAnchorBlock(
                        renderedText = block.renderedText,
                        layout = block.layout,
                        leftInRoot = block.leftInRoot,
                        topInRoot = block.topInRoot
                    )
                },
                onTextBlockDisposed = textBlocks::remove
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(NOTE_TOOL_OVERLAY_HEIGHT),
                contentAlignment = Alignment.Center
            ) {
                if (editButtonVisible) {
                    CompactEditButton(
                        onClick = { onEdit(centerMarkdownOffset()) }
                    )
                }
            }
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
    val verticalPadding = ReaderPageLayout.titleTopPadding
    val titleTextColor = readerTextStyle(ReaderTextRole.TITLE).color
    val subtitleStyle = readerTextStyle(ReaderTextRole.SUBTITLE)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = horizontalPadding)
            .padding(top = verticalPadding, bottom = NOTE_DOCUMENT_BOTTOM_PADDING),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DetailTitle(
            title = title,
            titlePadding = titlePadding,
            textColor = titleTextColor
        )
        if (metadata != null) {
            Text(
                text = metadata,
                style = subtitleStyle,
                textAlign = TextAlign.Start
            )
        }
        body()
    }
}

@androidx.compose.runtime.Composable
private fun CompactEditButton(onClick: () -> Unit) {
    val bodyStyle = readerTextStyle(ReaderTextRole.BODY)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(bodyStyle.color.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "编辑",
            color = bodyStyle.color,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@androidx.compose.runtime.Composable
private fun WatchNoteRawEditor(
    note: WatchNoteEntity?,
    safeInset: androidx.compose.ui.unit.Dp,
    scrollState: ScrollState,
    initialAnchorOffset: Int?,
    saveDraft: suspend (noteId: String?, markdown: String) -> WatchNoteEntity,
    onClose: (markdown: String, markdownOffset: Int?) -> Unit
) {
    val initialValue = remember(note?.noteId, initialAnchorOffset) {
        val markdown = note?.markdown.orEmpty()
        TextFieldValue(
            text = markdown,
            selection = TextRange(
                initialAnchorOffset?.coerceIn(0, markdown.length)
                    ?: estimateNoteCursorForScroll(
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
    var editorBodyTopInDocument by remember(note?.noteId) { mutableStateOf<Float?>(null) }
    var centerInitialAnchor by remember(note?.noteId, initialAnchorOffset) {
        mutableStateOf(initialAnchorOffset != null)
    }
    var selectionToRestoreOnFocus by remember(note?.noteId) { mutableStateOf<TextRange?>(null) }
    val editorFocusRequester = remember(note?.noteId) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

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
            if (persist(editorValue.text)) {
                onClose(editorValue.text, editorValue.selection.end)
            } else {
                closing = false
            }
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
    LaunchedEffect(textFieldFocused, selectionToRestoreOnFocus) {
        val preservedSelection = selectionToRestoreOnFocus
        if (textFieldFocused && preservedSelection != null) {
            if (editorValue.selection != preservedSelection) {
                editorValue = history.record(
                    editorValue.copy(selection = preservedSelection, composition = null),
                    SystemClock.uptimeMillis()
                )
            }
            keyboardController?.show()
            selectionToRestoreOnFocus = null
        }
    }
    val bodyTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f
    )
    val cursorColor = MaterialTheme.colorScheme.primary
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val minimumBodyHeight = configuration.screenHeightDp.dp * 0.58f
    val screenCenterY = with(density) { configuration.screenHeightDp.dp.toPx() / 2f }
    val cursorVisibleTopPx = with(density) {
        (NOTE_TOOL_OVERLAY_HEIGHT + NOTE_CURSOR_VISIBLE_MARGIN).toPx()
    }
    val cursorVisibleBottomPx = with(density) {
        configuration.screenHeightDp.dp.toPx() -
            (NOTE_TOOL_OVERLAY_HEIGHT + NOTE_CURSOR_VISIBLE_MARGIN).toPx()
    }
    val scrollMaxValue = scrollState.maxValue

    LaunchedEffect(
        editorValue.selection,
        textLayoutResult,
        editorBodyTopInDocument,
        scrollMaxValue,
        centerInitialAnchor,
        cursorVisibleTopPx,
        cursorVisibleBottomPx
    ) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        val bodyTopInDocument = editorBodyTopInDocument ?: return@LaunchedEffect
        val cursorRect = layout.getCursorRect(
            editorValue.selection.end.coerceIn(0, editorValue.text.length)
        )
        val target = if (centerInitialAnchor) {
            noteCursorCenteredScrollTarget(
                scrollValue = scrollState.value,
                scrollMaxValue = scrollMaxValue,
                bodyTopInRoot = bodyTopInDocument - scrollState.value,
                cursorTopInBody = cursorRect.top,
                cursorBottomInBody = cursorRect.bottom,
                viewportCenterInRoot = screenCenterY
            )
        } else {
            noteCursorVisibleScrollTarget(
                scrollValue = scrollState.value,
                scrollMaxValue = scrollMaxValue,
                bodyTopInRoot = bodyTopInDocument - scrollState.value,
                cursorTopInBody = cursorRect.top,
                cursorBottomInBody = cursorRect.bottom,
                visibleTopInRoot = cursorVisibleTopPx,
                visibleBottomInRoot = cursorVisibleBottomPx
            )
        }
        if (target != scrollState.value) scrollState.scrollTo(target)
        centerInitialAnchor = false
    }

    val rubberBandOffset = LocalRubberBandOverscrollOffset.current
    Box(modifier = Modifier.fillMaxSize()) {
        NoteDocumentContent(
            title = note?.title?.ifBlank { "未命名笔记" } ?: "新建备忘录",
            metadata = note?.let(::noteMetadata),
            scrollState = scrollState
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = editorValue,
                    onValueChange = {
                        editorValue = history.record(it, SystemClock.uptimeMillis())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minimumBodyHeight)
                        .focusRequester(editorFocusRequester)
                        .onFocusChanged { textFieldFocused = it.isFocused }
                        .onGloballyPositioned { coordinates ->
                            editorBodyTopInDocument =
                                coordinates.positionInRoot().y + scrollState.value
                        }
                        .drawWithContent {
                            drawContent()
                            if (!textFieldFocused) {
                                val cursorRect = textLayoutResult
                                    ?.getCursorRect(
                                        editorValue.selection.end.coerceIn(0, editorValue.text.length)
                                    )
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
                if (!textFieldFocused) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(editorFocusRequester) {
                                detectTapGestures {
                                    selectionToRestoreOnFocus = editorValue.selection
                                    editorFocusRequester.requestFocus()
                                }
                            }
                    )
                }
            }
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

private data class NoteReaderAnchorBlock(
    val renderedText: String,
    val layout: TextLayoutResult,
    val leftInRoot: Float,
    val topInRoot: Float
)

internal fun noteEditButtonVisibleAfterLazyScroll(
    wasVisible: Boolean,
    previousItemIndex: Int,
    previousItemOffset: Int,
    currentItemIndex: Int,
    currentItemOffset: Int
): Boolean = when {
    currentItemIndex > previousItemIndex -> false
    currentItemIndex < previousItemIndex -> true
    currentItemOffset > previousItemOffset -> false
    currentItemOffset < previousItemOffset -> true
    else -> wasVisible
}

internal fun noteEditButtonVisibleAfterScroll(
    wasVisible: Boolean,
    previousScrollValue: Int,
    currentScrollValue: Int
): Boolean = when {
    currentScrollValue > previousScrollValue -> false
    currentScrollValue < previousScrollValue -> true
    else -> wasVisible
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

internal fun mapNoteTextAnchorOffset(
    sourceText: String,
    targetText: String,
    sourceOffset: Int
): Int {
    if (targetText.isEmpty()) return 0
    if (sourceText.isEmpty()) return sourceOffset.coerceIn(0, targetText.length)
    val safeOffset = sourceOffset.coerceIn(0, sourceText.length)
    if (sourceText == targetText) return safeOffset

    if (watchPlainText(sourceText) == targetText) {
        return watchPlainText(sourceText.substring(0, safeOffset)).length
            .coerceIn(0, targetText.length)
    }
    if (watchPlainText(targetText) == sourceText) {
        var low = 0
        var high = targetText.length
        while (low < high) {
            val middle = (low + high) ushr 1
            val visibleLength = watchPlainText(targetText.substring(0, middle)).length
            if (visibleLength < safeOffset) low = middle + 1 else high = middle
        }
        return low.coerceIn(0, targetText.length)
    }

    for (radius in listOf(32, 24, 16, 12, 8)) {
        val start = (safeOffset - radius).coerceAtLeast(0)
        val end = (safeOffset + radius).coerceAtMost(sourceText.length)
        val anchor = sourceText.substring(start, end).trim()
        if (anchor.length < 4) continue
        val match = targetText.indexOf(anchor)
        if (match >= 0) {
            return (match + safeOffset - start).coerceIn(0, targetText.length)
        }
    }

    return (targetText.length * safeOffset.toLong() / sourceText.length)
        .toInt()
        .coerceIn(0, targetText.length)
}

internal fun noteCursorVisibleScrollTarget(
    scrollValue: Int,
    scrollMaxValue: Int,
    bodyTopInRoot: Float,
    cursorTopInBody: Float,
    cursorBottomInBody: Float,
    visibleTopInRoot: Float,
    visibleBottomInRoot: Float
): Int {
    val cursorTopInRoot = bodyTopInRoot + cursorTopInBody
    val cursorBottomInRoot = bodyTopInRoot + cursorBottomInBody
    val correction = when {
        cursorTopInRoot < visibleTopInRoot -> cursorTopInRoot - visibleTopInRoot
        cursorBottomInRoot > visibleBottomInRoot -> cursorBottomInRoot - visibleBottomInRoot
        else -> 0f
    }
    return (scrollValue + correction).roundToInt().coerceIn(0, scrollMaxValue.coerceAtLeast(0))
}

internal fun noteCursorCenteredScrollTarget(
    scrollValue: Int,
    scrollMaxValue: Int,
    bodyTopInRoot: Float,
    cursorTopInBody: Float,
    cursorBottomInBody: Float,
    viewportCenterInRoot: Float
): Int {
    val cursorCenterInRoot = bodyTopInRoot + (cursorTopInBody + cursorBottomInBody) / 2f
    val correction = cursorCenterInRoot - viewportCenterInRoot
    return (scrollValue + correction).roundToInt().coerceIn(0, scrollMaxValue.coerceAtLeast(0))
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
private const val NOTE_LOADING_ROTATION_NANOS = 800_000_000L
private const val NOTES_CHANNEL_ID = -1L
private const val NEW_NOTE_DOCUMENT_KEY = "__new_note__"
// REL_WHEEL reports roughly one delta unit per crown detent on the watch. Accumulate about three
// detents for each Unicode code point so precise cursor placement is not too sensitive.
private val NOTE_TOOL_OVERLAY_HEIGHT = 56.dp
private val NOTE_DOCUMENT_BOTTOM_PADDING = 64.dp
private val NOTE_CURSOR_VISIBLE_MARGIN = 8.dp

private enum class DraftSaveState(val label: String) {
    EMPTY("输入后自动保存"),
    PENDING("等待自动保存…"),
    SAVING("正在保存…"),
    SAVED("已自动保存"),
    ERROR("保存失败；继续编辑或退出时会重试")
}
