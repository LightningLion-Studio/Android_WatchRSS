package com.lightningstudio.watchrss

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.phoneconnection.bluetooth.BluetoothTransferScreenOnController
import com.lightningstudio.watchrss.phoneconnection.bluetooth.BluetoothSyncProtocol
import com.lightningstudio.watchrss.phoneconnection.bluetooth.WatchBluetoothSyncServer
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BluetoothConnectionActivity : BaseWatchActivity() {
    private val screenOnController = BluetoothTransferScreenOnController()
    private var preferredAbility: PhoneConnectionAbility? = null
    private var returnRemoteUrl: Boolean = false
    private var statusMessage by mutableStateOf("等待手机蓝牙连接…")
    private var detailMessage by mutableStateOf<String?>(null)
    private var isBusy by mutableStateOf(false)

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                startListening()
            } else {
                statusMessage = "缺少蓝牙权限"
                detailMessage = "请允许蓝牙权限后重试"
                isBusy = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        preferredAbility = PhoneConnectionAbility.fromNameOrNull(intent.getStringExtra(EXTRA_PREFERRED_ABILITY))
        returnRemoteUrl = intent.getBooleanExtra(EXTRA_RETURN_REMOTE_URL, false)

        setContent {
            WatchRSSTheme {
                BluetoothConnectionScreen(
                    abilityLabel = preferredAbility?.displayName,
                    statusMessage = statusMessage,
                    detailMessage = detailMessage,
                    isBusy = isBusy,
                    onRetry = { startWithPermissionCheck() },
                    onDismiss = { finish() }
                )
            }
        }

        startWithPermissionCheck()
    }

    override fun onResume() {
        super.onResume()
        screenOnController.setCurrentActivity(this)
    }

    override fun onPause() {
        screenOnController.clearActivity(this)
        super.onPause()
    }

    override fun onDestroy() {
        screenOnController.setTransferInProgress(false)
        screenOnController.clearActivity(this)
        super.onDestroy()
    }

    private fun startWithPermissionCheck() {
        val missing = bluetoothPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startListening()
        } else {
            bluetoothPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startListening() {
        isBusy = true
        statusMessage = "等待手机蓝牙连接…"
        detailMessage = "请在手机 Companion 中选择对应操作"
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    WatchBluetoothSyncServer(
                        context = applicationContext,
                        expectedAbility = preferredAbility,
                        onClientAccepted = {
                            screenOnController.setTransferInProgress(true)
                        }
                    ).acceptOnce()
                }
            }.onSuccess { result ->
                screenOnController.setTransferInProgress(false)
                when (result.request.optString("action")) {
                    BluetoothSyncProtocol.ACTION_REMOTE_INPUT -> {
                        val url = result.request.optString("url").trim()
                        statusMessage = "已收到 RSS 地址"
                        detailMessage = "正在打开添加页面…"
                        handleRemoteInput(url)
                    }

                    BluetoothSyncProtocol.ACTION_PULL_SAVED_ITEMS -> {
                        val count = result.response.optInt("count")
                        statusMessage = "已通过蓝牙同步"
                        detailMessage = "已向手机发送 $count 条${preferredAbility?.displayName ?: "数据"}"
                        isBusy = false
                    }

                    BluetoothSyncProtocol.ACTION_SYNC_LIBRARY -> {
                        val stats = result.response.optJSONObject("stats")
                        statusMessage = "蓝牙同步完成"
                        detailMessage = "收到 ${result.request.optJSONArray("articles")?.length() ?: 0} 条，应用 ${stats?.optInt("applied") ?: 0} 项变更"
                        isBusy = false
                    }

                    else -> {
                        statusMessage = "蓝牙连接成功"
                        detailMessage = "手机已完成握手"
                        isBusy = false
                    }
                }
            }.onFailure { throwable ->
                screenOnController.setTransferInProgress(false)
                statusMessage = "蓝牙同步失败"
                detailMessage = throwable.message ?: "连接失败"
                isBusy = false
            }
        }
    }

    private fun handleRemoteInput(url: String) {
        isBusy = false
        if (returnRemoteUrl) {
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(ServerActivity.EXTRA_REMOTE_URL, url)
            )
            finish()
            return
        }
        startActivity(
            Intent(this, AddRssActivity::class.java).apply {
                putExtra(AddRssActivity.EXTRA_URL, url)
            }
        )
        finish()
    }

    private fun bluetoothPermissions(): List<String> {
        return buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    companion object {
        private const val EXTRA_PREFERRED_ABILITY = "preferred_ability"
        private const val EXTRA_RETURN_REMOTE_URL = "return_remote_url"

        fun createIntent(
            context: Context,
            preferredAbility: PhoneConnectionAbility?,
            returnRemoteUrl: Boolean
        ): Intent {
            return Intent(context, BluetoothConnectionActivity::class.java).apply {
                putExtra(EXTRA_PREFERRED_ABILITY, preferredAbility?.name)
                putExtra(EXTRA_RETURN_REMOTE_URL, returnRemoteUrl)
            }
        }
    }
}

@Composable
private fun BluetoothConnectionScreen(
    abilityLabel: String?,
    statusMessage: String,
    detailMessage: String?,
    isBusy: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val scrollState = rememberScrollState()

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding),
            verticalArrangement = Arrangement.spacedBy(com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "蓝牙同步",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            abilityLabel?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "当前操作：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (isBusy) {
                CircularProgressIndicator()
            }
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            detailMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!isBusy) {
                Button(onClick = onRetry) {
                    Text(text = "重新等待")
                }
                Button(onClick = onDismiss) {
                    Text(text = "关闭")
                }
            }
        }
    }
}
