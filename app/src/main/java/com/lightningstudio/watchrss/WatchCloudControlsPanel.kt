package com.lightningstudio.watchrss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.cloud.WatchCloudNetworkPolicy
import com.lightningstudio.watchrss.data.cloud.WatchCloudSyncService
import kotlinx.coroutines.launch

@Composable
internal fun WatchCloudControlsPanel(service: WatchCloudSyncService) {
    val config by service.controls.state.collectAsState()
    val status by service.status.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("会员云同步", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text("需云会员及手机批准设备。同步文章、频道、阅读状态、阅读预设／字体／背景，以及备忘录、目录和图片。", style = MaterialTheme.typography.bodySmall)
        Text("数据使用账号密钥加密，设备批准不会由短信登录代替。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            if (config.automatic) service.controls.setAutomatic(false) else confirmation = "automatic"
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (config.automatic) "关闭自动云同步" else "开启自动云同步")
        }
        Text(if (config.automatic) "自动同步已开启" else "自动同步已关闭")
        WatchCloudNetworkPolicy.entries.forEach { policy ->
            OutlinedButton(onClick = { service.controls.setNetwork(policy) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text((if (config.network == policy) "✓ " else "") + policy.label)
            }
        }
        Text("状态：${status.message.ifBlank { "尚未同步" }}")
        if (status.lastCompletedAt > 0L) Text("上次完成：" + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(status.lastCompletedAt)))
        if (status.quotaBytes > 0L) Text("空间：${status.usedBytes / 1048576} / ${status.quotaBytes / 1048576} MiB")
        Text("首次开启后，请在手机的账号→云同步→设备批准中批准此手表。只读期仅接收资料，不上传。", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { confirmation = "manual" }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (busy) "正在同步" else "立即云同步") }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(if (action == "automatic") "开启自动云同步" else "确认立即同步") },
            text = { Text(if (action == "automatic") "按所选网络策略自动同步到已批准的同账号设备。可随时关闭；关闭不会删除云端资料。"
                else "本次同步完整文章、备忘录与阅读资源，会临时覆盖网络限制，可能产生移动数据费用。") },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                if (action == "automatic") service.controls.setAutomatic(true)
                else scope.launch { busy = true; try { service.syncNow(manual = true) } finally { busy = false } }
            }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } })
    }
}
