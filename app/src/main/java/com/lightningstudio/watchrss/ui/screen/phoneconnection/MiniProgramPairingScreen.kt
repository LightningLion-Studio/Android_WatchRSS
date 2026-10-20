package com.lightningstudio.watchrss.ui.screen.phoneconnection

import android.os.SystemClock
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.testing.PhoneConnectionTestTags
import kotlinx.coroutines.delay

class PairingActions(
    val start: () -> Unit,
    val confirm: () -> Unit,
    val decline: () -> Unit,
    val cancel: () -> Unit,
    val close: () -> Unit
)

/** One page per stage, so the current step always owns the centre of the round screen. */
@Composable
fun MiniProgramPairingScreen(state: PairingUiState, transport: PairingTransport, actions: PairingActions) {
    if (state is PairingUiState.WaitingForScan) { ScanPage(state, actions); return }
    RoundPage(modifier = Modifier.testTag(PhoneConnectionTestTags.PAIRING_ROOT)) {
        when (state) {
            is PairingUiState.Starting -> StartingPage(state.transport, actions)
            is PairingUiState.NeedsPermission -> RestartPage("请重新配对", "联网后重新生成配对码", transport, actions)
            is PairingUiState.WaitingForScan -> Unit
            is PairingUiState.Confirming -> ConfirmPage(state.account, actions)
            is PairingUiState.Working -> WorkingPage(state.current, actions)
            is PairingUiState.Done -> DonePage(state.cloudReady, actions)
            is PairingUiState.Failed -> FailedPage(state, transport, actions)
            PairingUiState.Cancelled -> RestartPage("已取消配对", "本次配对已结束", transport, actions)
            PairingUiState.Paused -> RestartPage("配对已暂停", "离开页面后配对码已失效，需要时重新生成", transport, actions)
        }
    }
}

@Composable
private fun StartingPage(transport: PairingTransport, actions: PairingActions) {
    WatchCircularProgressIndicator(modifier = Modifier.size(40.dp))
    Spacer(Modifier.height(14.dp))
    PageBody(pairingStartingLabel(transport), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    Spacer(Modifier.height(18.dp))
    PairingPill("取消", onClick = actions.cancel)
}

@Composable
private fun ScanPage(state: PairingUiState.WaitingForScan, actions: PairingActions) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.expiresAtElapsed) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val scroll = rememberScrollState()
    InstallDigitalCrownScrollHandler(scroll)
    BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).testTag(PhoneConnectionTestTags.PAIRING_ROOT)) {
        val viewportHeight = maxHeight
        val tile = pairingQrTileDp(minOf(maxWidth.value, maxHeight.value)).dp
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(viewportHeight), contentAlignment = Alignment.Center) {
                PairingQrTile(state.qr, tileSize = tile, testTag = PhoneConnectionTestTags.PAIRING_QR)
                Text("下滑查看操作", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "用小程序扫码",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                PageBody(pairingScanHint(state.transport))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pairingCountdown(state.expiresAtElapsed - now) + "，仅限本次使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Spacer(Modifier.height(8.dp))
                PairingPill("取消", onClick = actions.cancel)
            }
        }
    }
}

@Composable
private fun ConfirmPage(account: String, actions: PairingActions) {
    PageBody("绑定到这个账号？")
    Spacer(Modifier.height(4.dp))
    Text(
        text = account,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "确认后手表将登录此账号",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(PhoneConnectionTestTags.PAIRING_DECLINE)
                .clickable(role = Role.Button, onClick = actions.decline)
                .semantics { contentDescription = "不绑定" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        }
        PairingPill(
            "确认绑定",
            onClick = actions.confirm,
            primary = true,
            testTag = PhoneConnectionTestTags.PAIRING_CONFIRM,
            modifier = Modifier.size(width = 112.dp, height = 48.dp)
        )
    }
}

@Composable
private fun WorkingPage(current: PairingStep, actions: PairingActions) {
    PageTitle("正在设置手表")
    Spacer(Modifier.height(12.dp))
    PairingSteps(current, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    Spacer(Modifier.height(10.dp))
    PageBody("请保持本页打开")
    Spacer(Modifier.height(12.dp))
    PairingPill("停止", onClick = actions.cancel)
}

@Composable
private fun DonePage(cloudReady: Boolean, actions: PairingActions) {
    OutcomeMark(success = true)
    Spacer(Modifier.height(12.dp))
    PageTitle(pairingDoneTitle(cloudReady), modifier = Modifier.testTag(PhoneConnectionTestTags.PAIRING_DONE))
    Spacer(Modifier.height(6.dp))
    PageBody(pairingDoneDetail(cloudReady))
    Spacer(Modifier.height(14.dp))
    PairingPill("完成", onClick = actions.close, primary = true)
}

@Composable
private fun FailedPage(state: PairingUiState.Failed, transport: PairingTransport, actions: PairingActions) {
    OutcomeMark(success = false)
    Spacer(Modifier.height(12.dp))
    PageTitle(state.title)
    Spacer(Modifier.height(6.dp))
    PageBody(state.detail)
    Spacer(Modifier.height(14.dp))
    PairingPill("重试", onClick = actions.start, primary = true)
    Spacer(Modifier.height(8.dp))
    PairingPill("返回", onClick = actions.close)
}

@Composable
private fun RestartPage(title: String, detail: String, transport: PairingTransport, actions: PairingActions) {
    PageTitle(title)
    Spacer(Modifier.height(6.dp))
    PageBody(detail)
    Spacer(Modifier.height(14.dp))
    PairingPill("重新开始", onClick = actions.start, primary = true)
    Spacer(Modifier.height(8.dp))
    Spacer(Modifier.height(8.dp))
    PairingPill("返回", onClick = actions.close)
}
