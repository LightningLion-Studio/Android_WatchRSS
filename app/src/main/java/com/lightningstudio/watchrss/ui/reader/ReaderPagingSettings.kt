package com.lightningstudio.watchrss.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.ui.components.WatchTextButton
import kotlinx.coroutines.launch

@Composable
fun ReaderPagingSettings(repository: SettingsRepository) {
    val pageMode by repository.readerPageMode.collectAsState(initial = false)
    val seconds by repository.readerPageSeconds.collectAsState(initial = 15)
    val auto by repository.readerAutoPageEnabled.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("小说点击翻页")
            Switch(checked = pageMode, onCheckedChange = { value -> scope.launch { repository.setReaderPageMode(value) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("前台自动翻页")
            Switch(checked = auto, onCheckedChange = { value -> scope.launch { repository.setReaderAutoPageEnabled(value) } })
        }
        Text("每页 ${seconds} 秒；仅在书页模式生效")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WatchTextButton(enabled = seconds > 5, onClick = { scope.launch { repository.setReaderPageSeconds(seconds - 5) } }) { Text("－5秒") }
            WatchTextButton(enabled = seconds < 120, onClick = { scope.launch { repository.setReaderPageSeconds(seconds + 5) } }) { Text("＋5秒") }
        }
    }
}
