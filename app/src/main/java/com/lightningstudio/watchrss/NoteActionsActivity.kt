package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.ui.screen.ActionDialogScreen
import com.lightningstudio.watchrss.ui.screen.ActionItem
import com.lightningstudio.watchrss.ui.screen.DeleteConfirmDialog
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.launch

/** Uses the same translucent action surface as standard RSS content management. */
class NoteActionsActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        if (noteId.isNullOrBlank()) {
            finish()
            return
        }
        val container = (application as WatchRssApplication).container
        val repository = container.watchNoteRepository
        val deviceId = WatchDeviceIdentity(this).deviceId

        setContent {
            WatchRSSTheme {
                val note by repository.observe(noteId).collectAsState(initial = null)
                val folders by repository.observeFolders().collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()
                var selectingFolder by remember { mutableStateOf(false) }
                var showDeleteConfirm by remember { mutableStateOf(false) }
                val current = note

                val actionItems = if (selectingFolder) {
                    buildList {
                        add(
                            ActionItem(
                                label = if (current?.folderId == null) "未分类（当前位置）" else "未分类",
                                enabled = current?.folderId != null,
                                onClick = {
                                    scope.launch {
                                        repository.move(noteId, null, deviceId)
                                        finish()
                                    }
                                }
                            )
                        )
                        folders.forEach { folder ->
                            add(
                                ActionItem(
                                    label = if (folder.folderId == current?.folderId) {
                                        "${folder.name}（当前位置）"
                                    } else {
                                        folder.name
                                    },
                                    enabled = folder.folderId != current?.folderId,
                                    onClick = {
                                        scope.launch {
                                            repository.move(noteId, folder.folderId, deviceId)
                                            finish()
                                        }
                                    }
                                )
                            )
                        }
                        add(ActionItem(label = "返回", onClick = { selectingFolder = false }))
                    }
                } else {
                    buildList {
                        add(
                            ActionItem(
                                label = if (current?.pinned == true) "取消置顶" else "置顶",
                                enabled = current != null,
                                onClick = {
                                    scope.launch {
                                        repository.setPinned(noteId, current?.pinned != true, deviceId)
                                        finish()
                                    }
                                }
                            )
                        )
                        add(
                            ActionItem(
                                label = "移动到…",
                                enabled = current != null,
                                onClick = { selectingFolder = true }
                            )
                        )
                        add(
                            ActionItem(
                                label = "删除",
                                enabled = current != null,
                                onClick = { showDeleteConfirm = true }
                            )
                        )
                        add(ActionItem(label = "取消", onClick = { finish() }))
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    ActionDialogScreen(items = actionItems, extraTopPadding = 4.dp)
                    if (showDeleteConfirm) {
                        DeleteConfirmDialog(
                            title = "删除备忘录",
                            message = "删除后会从本机和下次同步中移除此备忘录",
                            onConfirm = {
                                showDeleteConfirm = false
                                scope.launch {
                                    repository.delete(noteId, deviceId)
                                    finish()
                                }
                            },
                            onCancel = { showDeleteConfirm = false }
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_NOTE_ID = "noteId"
        private const val EXTRA_NOTE_TITLE = "noteTitle"

        fun createIntent(context: Context, noteId: String, noteTitle: String): Intent =
            Intent(context, NoteActionsActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_NOTE_TITLE, noteTitle)
            }
    }
}
