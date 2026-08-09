package com.lightningstudio.watchrss

import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.data.note.RawTextUndoManager
import com.lightningstudio.watchrss.data.note.WatchNoteEntity
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.reader.ReaderPageLayout
import com.lightningstudio.watchrss.ui.screen.rss.DetailTitle
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
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
                    if (selected == null && editing == null && !creating) {
                        val listSafePadding = watchDimensionResource(R.dimen.watch_safe_padding)
                        val itemSpacing = watchDimensionResource(R.dimen.hey_distance_6dp)
                        val listState = rememberLazyListState()
                        InstallDigitalCrownLazyListHandler(listState)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = listSafePadding),
                            state = listState,
                            contentPadding = PaddingValues(
                                top = listSafePadding,
                                bottom = listSafePadding + itemSpacing
                            ),
                            verticalArrangement = Arrangement.spacedBy(itemSpacing),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Text(
                                    text = "备忘录",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = itemSpacing),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (notes.isEmpty()) {
                                item {
                                    NotesEmptyCard()
                                }
                            }
                            items(notes, key = { it.noteId }) { note ->
                                NoteListCard(note = note, onClick = {
                                    creating = false
                                    selectedNoteId = note.noteId
                                })
                            }
                            item {
                                NewNoteButton(onClick = { creating = true })
                            }
                        }
                    } else if (selected != null && editing == null && !creating) {
                        WatchNoteReader(
                            note = selected,
                            onBack = { selectedNoteId = null },
                            onEdit = { editingNoteId = selected.noteId }
                        )
                    } else {
                        WatchNoteRawEditor(
                            note = editing,
                            safeInset = editorSafeInset,
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
private fun NoteListCard(note: WatchNoteEntity, onClick: () -> Unit) {
    val shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_card_normal_bg_radius))
    val contentPadding = watchDimensionResource(R.dimen.hey_content_horizontal_distance)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(contentPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.pinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "已置顶",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = note.title.ifBlank { "未命名笔记" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val preview = notePreview(note)
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = preview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = noteMetadata(note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun NotesEmptyCard() {
    val shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_card_normal_bg_radius))
    val contentPadding = watchDimensionResource(R.dimen.hey_content_horizontal_distance)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(contentPadding)
    ) {
        Text(text = "还没有备忘录", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "点击下方按钮新建，或从手机同步。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
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

@androidx.compose.runtime.Composable
private fun WatchNoteReader(
    note: WatchNoteEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    BackHandler(onBack = onBack)
    val listState = rememberLazyListState()
    InstallDigitalCrownLazyListHandler(listState)
    val horizontalPadding = ReaderPageLayout.horizontalPadding
    val titlePadding = ReaderPageLayout.titleHorizontalPadding
    val verticalPadding = ReaderPageLayout.topSafePadding
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            horizontal = horizontalPadding,
            vertical = verticalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "阅读备忘录",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        item {
            DetailTitle(
                title = note.title.ifBlank { "未命名笔记" },
                titlePadding = titlePadding,
                textColor = MaterialTheme.colorScheme.onSurface
            )
        }
        item {
            Text(
                text = noteMetadata(note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        item {
            CompactEditButton(onClick = onEdit)
        }
        item {
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
    saveDraft: suspend (noteId: String?, markdown: String) -> WatchNoteEntity,
    onClose: () -> Unit
) {
    val initialValue = remember(note?.noteId) {
        val markdown = note?.markdown.orEmpty()
        TextFieldValue(markdown, TextRange(markdown.length))
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(safeInset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = note?.title?.ifBlank { "未命名笔记" } ?: "新建备忘录",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = editorValue,
            onValueChange = {
                editorValue = history.record(it, SystemClock.uptimeMillis())
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("Markdown / TXT 原文") },
            placeholder = { Text("直接输入原始文本") }
        )
        Text(
            text = saveState.label,
            modifier = Modifier.fillMaxWidth(),
            color = if (saveState == DraftSaveState.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { editorValue = history.undo() },
                enabled = history.canUndo && !closing
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
            }
            IconButton(
                onClick = { editorValue = history.redo() },
                enabled = history.canRedo && !closing
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
            }
            IconButton(onClick = ::requestClose, enabled = !closing) {
                Icon(Icons.Default.Close, contentDescription = "关闭编辑")
            }
        }
    }
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

private enum class DraftSaveState(val label: String) {
    EMPTY("输入后自动保存"),
    PENDING("等待自动保存…"),
    SAVING("正在保存…"),
    SAVED("已自动保存"),
    ERROR("保存失败；继续编辑或退出时会重试")
}
