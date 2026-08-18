package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.account.WatchAccountState
import com.lightningstudio.watchrss.data.cloud.WatchCloudSyncService
import com.lightningstudio.watchrss.ui.components.DownloadPhoneAppButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.launch

class AccountStatusActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        setContent {
            WatchRSSTheme {
                val state by (application as WatchRssApplication).accountStore.state.collectAsState()
                AccountStatusScreen(
                    state,
                    (application as WatchRssApplication).cloudSyncService
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as WatchRssApplication).usageTelemetry.recordScreenOpen("watch_account")
    }
}

@Composable
private fun AccountStatusScreen(
    state: WatchAccountState?,
    cloudSyncService: WatchCloudSyncService
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    val cloudStatus by cloudSyncService.status.collectAsState()
    val scope = rememberCoroutineScope()
    InstallDigitalCrownScrollHandler(scrollState)
    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .then(Modifier)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "账号",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (state == null) {
                Text(
                    text = "未绑定",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "请在手机端登录后点击同步账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                DownloadPhoneAppButton(
                    operation = "登录账号与云同步",
                    modifier = Modifier.offset(y = (-4).dp)
                )
            } else {
                Text(
                    text = state.phoneMasked.ifBlank { "已绑定账号" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text("会员：${state.entitlement.plan}")
                Text("状态：${if (state.entitlement.active) "有效" else "未生效"}")
                Text("诊断：${if (state.telemetryConfig.diagnosticsEnabled) "已开启" else "未开启"}")
                Text("Access Token：${if (state.isTokenExpired) "待刷新" else "有效"}")
                Text(
                    "Refresh Token：${when {
                        state.watchRefreshToken.isBlank() -> "旧版，需重新同步"
                        state.isRefreshTokenExpired -> "已过期"
                        else -> "有效"
                    }}"
                )
                if (state.entitlement.plan == "member" && state.entitlement.active) {
                    Text("云中继：${cloudStatus.message}")
                    if (cloudStatus.quotaBytes > 0) {
                        Text(
                            "空间：${cloudStatus.usedBytes / 1048576} / " +
                                "${cloudStatus.quotaBytes / 1048576} MiB"
                        )
                    }
                    Button(
                        onClick = { scope.launch { cloudSyncService.syncNow(manual = true) } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("立即云同步")
                    }
                    Text(
                        "首次使用需在手机会员云空间中批准这块手表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "云存储：会员可用；本地资料与蓝牙同步不受影响。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
