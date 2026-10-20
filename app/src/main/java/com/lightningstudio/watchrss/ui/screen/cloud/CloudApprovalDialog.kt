package com.lightningstudio.watchrss.ui.screen.cloud

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PageBody
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PageTitle
import com.lightningstudio.watchrss.ui.screen.phoneconnection.RoundPage

/** Round-screen modal: all controls stay in the inner 168dp and the page scrolls with the crown. */
@Composable
fun CloudApprovalDialog(
    busy: Boolean,
    requested: Boolean,
    success: Boolean,
    expired: Boolean,
    code: String,
    message: String,
    onCode: (String) -> Unit,
    onConfirm: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RoundPage(Modifier.testTag("cloud-approval-dialog")) {
            PageTitle(if (success) "授权成功" else "授权云同步")
            Spacer(Modifier.height(8.dp))
            PageBody(message)
            if (requested && !success && !expired) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { onCode(it.filter { ch -> ch in '0'..'9' }.take(8)) },
                    enabled = !busy,
                    singleLine = true,
                    placeholder = { Text("8位配对码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.widthIn(max = 168.dp).fillMaxWidth().testTag("cloud-approval-code")
                )
            }
            Spacer(Modifier.height(10.dp))
            CloudSyncPill(CloudSyncButton(
                label = when { success -> "完成"; busy -> "请稍候…"; expired || !requested -> "重新请求"; else -> "验证并开启" },
                action = CloudSyncAction.REQUEST_APPROVAL,
                enabled = !busy && (success || expired || !requested || validApprovalCode(code))
            )) {
                when { success -> onDismiss(); expired || !requested -> onRestart(); else -> onConfirm() }
            }
            if (!success) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp))
            }
        }
    }
}

internal fun validApprovalCode(code: String): Boolean = code.length == 8 && code.all { it in '0'..'9' }

/** No raw HTTP/JSON, codes, secrets or exception messages are shown in the modal. */
internal fun friendlyApprovalError(error: Throwable): Pair<String, Boolean> {
    val message = error.message.orEmpty().lowercase()
    return when {
        "同版本云密钥冲突" in message -> "本次授权与原有密钥不一致，原有授权已保留，请检查账号。" to false
        "approval_expired" in message -> "配对码已过期或尝试次数已用完，请重新请求批准。" to true
        "approval_code_invalid" in message -> "配对码不正确，请核对后重试。连续输错5次需要重新请求。" to false
        "approval_waiting" in message -> "请先在已授权的手机或小程序上批准这块手表，再输入配对码。" to false
        "approval_rate_limited" in message -> "请求次数较多，请稍后再试。" to true
        "账号" in message || "401" in message || "device_session_revoked" in message -> "登录状态已变化，请返回检查账号后重试。" to true
        "403" in message || "402" in message || "cloud_membership_required" in message -> "暂时无法获得授权，请检查账号的云同步服务后重试。" to false
        else -> "暂时连接不上云端，请检查网络后重试。" to false
    }
}
