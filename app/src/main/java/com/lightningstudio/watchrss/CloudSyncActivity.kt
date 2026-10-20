package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.lightningstudio.watchrss.data.cloud.WatchCloudApprovalRequest
import com.lightningstudio.watchrss.ui.screen.cloud.CloudApprovalDialog
import com.lightningstudio.watchrss.ui.screen.cloud.friendlyApprovalError
import androidx.compose.runtime.setValue
import com.lightningstudio.watchrss.ui.screen.cloud.CloudSyncAction
import com.lightningstudio.watchrss.ui.screen.cloud.CloudSyncScreen
import com.lightningstudio.watchrss.ui.screen.cloud.cloudSyncUiState
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

/** 我的 → 云同步。打开页面只读状态；只有用户点击才会发起同步。 */
class CloudSyncActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        val app = application as WatchRssApplication
        // 云同步仍仅 DEBUG 开放；release 只展示“暂未开放”。
        val service = if (BuildConfig.DEBUG) app.cloudSyncService else null
        setContent {
            WatchRSSTheme {
                val account by app.accountStore.state.collectAsState()
                val status = service?.status?.collectAsState()?.value
                val scope = rememberCoroutineScope()
                var approvalGeneration by remember { mutableStateOf(0) }
                var approvalOpen by remember { mutableStateOf(false) }
                var approvalBusy by remember { mutableStateOf(false) }
                var approvalSuccess by remember { mutableStateOf(false) }
                var approvalExpired by remember { mutableStateOf(false) }
                var approvalRequest by remember { mutableStateOf<WatchCloudApprovalRequest?>(null) }
                var approvalMessage by remember { mutableStateOf("") }
                var approvalCode by remember { mutableStateOf("") }
                var approvalJob by remember { mutableStateOf<Job?>(null) }
                fun closeApproval() {
                    approvalGeneration += 1
                    approvalOpen = false
                    approvalBusy = false
                    approvalJob?.cancel()
                    approvalJob = null
                    approvalCode = ""
                    approvalRequest = null
                }
                LaunchedEffect(account?.userId) { closeApproval() }
                DisposableEffect(Unit) { onDispose { approvalJob?.cancel() } }
                LaunchedEffect(approvalOpen, approvalRequest) {
                    val request = approvalRequest
                    if (approvalOpen && request != null) {
                        delay((request.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
                        if (!approvalSuccess) {
                            approvalExpired = true
                            approvalCode = ""
                            approvalMessage = "本次请求已过期，请重新请求批准。"
                        }
                    }
                }
                fun requestApproval() {
                    if (service == null || approvalBusy) return
                    approvalGeneration += 1
                    val generation = approvalGeneration
                    approvalOpen = true
                    approvalBusy = true
                    approvalSuccess = false
                    approvalExpired = false
                    approvalRequest = null
                    approvalCode = ""
                    approvalMessage = "正在请求旧设备批准…"
                    approvalJob = scope.launch {
                        try {
                            approvalRequest = service.requestCodeApproval()
                            approvalMessage = "请在已授权的手机或小程序上批准手表，再输入显示的8位配对码。5分钟内有效。"
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            approvalMessage = friendlyApprovalError(error).first
                        } finally { if (generation == approvalGeneration) approvalBusy = false }
                    }
                }
                var manualSync by remember { mutableStateOf(app.activeManualCloudSync()) }
                LaunchedEffect(manualSync) {
                    // 离开页面只取消等待，不取消同步本身。
                    manualSync?.join()
                    manualSync = null
                }
                CloudSyncScreen(
                    state = cloudSyncUiState(account, status, manualPending = manualSync != null),
                    onAction = { action ->
                        when (action) {
                            CloudSyncAction.REQUEST_APPROVAL -> requestApproval()
                            CloudSyncAction.SYNC -> if (manualSync == null) {
                                manualSync = app.requestManualCloudSync()
                            }
                            CloudSyncAction.OPEN_PHONE_CONNECTION -> if (allowNavigation()) {
                                startActivity(PhoneConnectionActivity.createIntent(this))
                            }
                        }
                    }
                )
                if (approvalOpen) {
                    CloudApprovalDialog(
                        busy = approvalBusy, requested = approvalRequest != null,
                        success = approvalSuccess, expired = approvalExpired,
                        code = approvalCode, message = approvalMessage,
                        onCode = { approvalCode = it }, onRestart = { requestApproval() },
                        onDismiss = { closeApproval() },
                        onConfirm = {
                            val request = approvalRequest
                            if (request != null && service != null && !approvalBusy) {
                                val code = approvalCode
                                val generation = approvalGeneration
                                approvalBusy = true
                                approvalMessage = "正在验证配对码…"
                                approvalJob = scope.launch {
                                    try {
                                        service.redeemCodeApproval(request, code)
                                        approvalCode = ""
                                        approvalSuccess = true
                                        approvalMessage = "云同步已开启，正在同步你的订阅和阅读记录。"
                                        manualSync = app.requestManualCloudSync()
                                    } catch (error: Exception) {
                                        if (error is CancellationException) throw error
                                        val (message, expired) = friendlyApprovalError(error)
                                        approvalMessage = message
                                        approvalExpired = expired
                                    } finally { if (generation == approvalGeneration) approvalBusy = false }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
