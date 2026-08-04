package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.note.WatchNoteEntity
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.launch

/** The watch edits only the plain-text projection; Markdown image tokens survive unchanged. */
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
                WatchSurface(pureBlack = true) {
                    val selected = editing
                    if (selected == null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item { Text("备忘录") }
                            if (notes.isEmpty()) item { Text("手机端创建后，执行蓝牙同步即可显示。") }
                            items(notes, key = { it.noteId }) { note ->
                                Card(Modifier.fillMaxWidth().clickable { editing = note }) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(note.title.ifBlank { "未命名笔记" })
                                        Spacer(Modifier.height(4.dp))
                                        Text(note.plainText.take(100))
                                    }
                                }
                            }
                        }
                    } else {
                        WatchNotePlainTextEditor(
                            note = selected,
                            onSave = { text ->
                                lifecycleScope.launch {
                                    repository.savePlainText(selected.noteId, text, deviceId)
                                    editing = null
                                }
                            },
                            onCancel = { editing = null }
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WatchNotePlainTextEditor(
    note: WatchNoteEntity,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember(note.noteId) { mutableStateOf(note.plainText) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(note.title.ifBlank { "未命名笔记" })
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("纯文本（图片占位符会保留）") }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { onSave(text) }) { Text("保存") }
            Button(onClick = onCancel) { Text("取消") }
        }
    }
}
