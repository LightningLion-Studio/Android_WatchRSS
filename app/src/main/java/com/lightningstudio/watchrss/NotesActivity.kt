package com.lightningstudio.watchrss

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.data.note.RawTextUndoManager
import com.lightningstudio.watchrss.data.note.WatchNoteEntity
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownLazyListHandler
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
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
                var editing by remember { mutableStateOf<WatchNoteEntity?>(null) }
                var creating by remember { mutableStateOf(false) }
                WatchSurface(pureBlack = true) {
                    val safeInset = notesSafeInset()
                    val selected = editing
                    if (selected == null && !creating) {
                        val listState = rememberLazyListState()
                        InstallDigitalCrownLazyListHandler(listState)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(safeInset),
                            verticalArrangement = if (notes.isEmpty()) {
                                Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                            } else {
                                Arrangement.spacedBy(8.dp)
                            },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "备忘录",
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    IconButton(onClick = { creating = true }) {
                                        Icon(Icons.Default.Add, contentDescription = "新建备忘录")
                                    }
                                }
                            }
                            if (notes.isEmpty()) {
                                item {
                                    Text(
                                        text = "可在手表直接新建，或从手机同步。",
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            items(notes, key = { it.noteId }) { note ->
                                Card(Modifier.fillMaxWidth().clickable {
                                    creating = false
                                    editing = note
                                }) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(note.title.ifBlank { "未命名笔记" })
                                        Spacer(Modifier.height(4.dp))
                                        Text(note.plainText.take(100))
                                    }
                                }
                            }
                        }
                    } else {
                        WatchNoteRawEditor(
                            note = selected,
                            safeInset = safeInset,
                            saveDraft = { noteId, markdown ->
                                repository.saveRawMarkdown(noteId, markdown, deviceId)
                            },
                            onClose = {
                                editing = null
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
private fun notesSafeInset(): androidx.compose.ui.unit.Dp {
    val configuration = LocalConfiguration.current
    return if (configuration.isScreenRound) {
        // Keep every control inside the largest square that fits in a circular display.
        configuration.screenWidthDp.dp * ROUND_SCREEN_SAFE_INSET_FRACTION
    } else {
        16.dp
    }
}

private const val ROUND_SCREEN_SAFE_INSET_FRACTION = 0.1465f
private const val AUTO_SAVE_DELAY_MILLIS = 600L

private enum class DraftSaveState(val label: String) {
    EMPTY("输入后自动保存"),
    PENDING("等待自动保存…"),
    SAVING("正在保存…"),
    SAVED("已自动保存"),
    ERROR("保存失败；继续编辑或退出时会重试")
}
