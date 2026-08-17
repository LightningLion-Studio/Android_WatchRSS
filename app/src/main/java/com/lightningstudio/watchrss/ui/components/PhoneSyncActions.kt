package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.testing.PhoneSyncActionsTestTags
import com.lightningstudio.watchrss.ui.theme.WatchDimens

/** 「开始{operation}」主操作文案（纯函数，可测）。 */
fun phoneSyncStartLabel(operation: String): String = "开始$operation"

/** 手机同步的两个独立入口：主按钮启动同步，次要链接仅打开手机下载引导。 */
@Composable
fun PhoneSyncActions(
    operation: String,
    onStartSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WatchSettingsPillRow(
            label = phoneSyncStartLabel(operation),
            testTag = PhoneSyncActionsTestTags.START_SYNC_BUTTON,
            onClick = onStartSync
        )
        Spacer(modifier = Modifier.height(WatchDimens.hey_distance_4dp))
        DownloadPhoneAppCompactLink(
            operation = operation,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
