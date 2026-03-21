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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.phoneconnection.AcousticConnectionProtocol
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionFeature
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionMode
import com.lightningstudio.watchrss.phoneconnection.SavedItemsSyncPayload
import com.lightningstudio.watchrss.phoneconnection.acoustic.AcousticAudioPlayer
import com.lightningstudio.watchrss.phoneconnection.acoustic.AcousticAudioReceiver
import com.lightningstudio.watchrss.phoneconnection.acoustic.AcousticCodec
import com.lightningstudio.watchrss.phoneconnection.acoustic.AcousticPacket
import com.lightningstudio.watchrss.phoneconnection.guided.WatchGuidedWifiClient
import com.lightningstudio.watchrss.ui.components.WatchButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallRotaryScrollHandler
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.ceil

class AcousticConnectionActivity : BaseWatchActivity() {
    private val acousticPlayer = AcousticAudioPlayer()
    private val acousticReceiver = AcousticAudioReceiver()
    private val guidedWifiClient by lazy { WatchGuidedWifiClient(this) }

    private var mode: PhoneConnectionMode = PhoneConnectionMode.PURE_SOUND
    private var preferredAbility: PhoneConnectionAbility? = null
    private var returnRemoteUrl: Boolean = false

    private var ability by mutableStateOf<PhoneConnectionAbility?>(null)
    private var statusMessage by mutableStateOf("")
    private var detailMessage by mutableStateOf<String?>(null)
    private var primaryButtonLabel by mutableStateOf<String?>(null)
    private var isBusy by mutableStateOf(false)
    private var estimatedTimeLabel by mutableStateOf<String?>(null)

    private var pendingPermissionAction: PendingPermissionAction? = null
    private var preparedPacket: AcousticPacket? = null
    private var prepareJob: Job? = null

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) {
                when (pendingPermissionAction) {
                    PendingPermissionAction.LISTEN_PURE_SOUND -> startPureSoundListening()
                    PendingPermissionAction.LISTEN_GUIDED_WIFI -> startGuidedWifiListening()
                    null -> Unit
                }
            } else {
                statusMessage = "未授予所需权限"
                detailMessage = "声波接收需要麦克风，引导 WiFi 还需要附近 WiFi/位置权限"
                isBusy = false
            }
            pendingPermissionAction = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PhoneConnectionFeature.isDebugBuild) {
            finish()
            return
        }
        setupSystemBars()

        mode = PhoneConnectionMode.valueOf(
            intent.getStringExtra(EXTRA_MODE) ?: PhoneConnectionMode.PURE_SOUND.name
        )
        preferredAbility = PhoneConnectionAbility.fromNameOrNull(intent.getStringExtra(EXTRA_PREFERRED_ABILITY))
        returnRemoteUrl = intent.getBooleanExtra(EXTRA_RETURN_REMOTE_URL, false)
        ability = preferredAbility

        refreshUi()

        setContent {
            WatchRSSTheme {
                AcousticConnectionScreen(
                    mode = mode,
                    ability = ability,
                    statusMessage = statusMessage,
                    detailMessage = detailMessage,
                    estimatedTimeLabel = estimatedTimeLabel,
                    primaryButtonLabel = primaryButtonLabel,
                    isBusy = isBusy,
                    onAbilitySelected = { selected ->
                        ability = selected
                        refreshUi()
                    },
                    onPrimaryAction = ::handlePrimaryAction,
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun refreshUi() {
        prepareJob?.cancel()
        preparedPacket = null
        estimatedTimeLabel = null

        when (val currentAbility = ability) {
            null -> {
                statusMessage = when (mode) {
                    PhoneConnectionMode.PURE_SOUND -> "选择要通过纯声波完成的操作"
                    PhoneConnectionMode.SOUND_GUIDED_WIFI -> "选择要通过声波引导 WiFi 完成的操作"
                    PhoneConnectionMode.MANUAL_WIFI -> "请选择手动 WiFi 连接"
                }
                detailMessage = "从手机输入支持手机播音到手表；收藏和稍后再看支持手表发给手机"
                primaryButtonLabel = null
                isBusy = false
            }

            PhoneConnectionAbility.REMOTE_INPUT -> {
                statusMessage = when (mode) {
                    PhoneConnectionMode.PURE_SOUND -> "请在手机上选择“纯声波 > 发送 RSS 到手表”"
                    PhoneConnectionMode.SOUND_GUIDED_WIFI -> "请在手机上选择“声波引导 WiFi 连接 > 引导手表接收 RSS”"
                    PhoneConnectionMode.MANUAL_WIFI -> ""
                }
                detailMessage = when (mode) {
                    PhoneConnectionMode.PURE_SOUND -> "手表会开始监听手机播出的 RSS 地址"
                    PhoneConnectionMode.SOUND_GUIDED_WIFI -> "手表会先听取热点信息，再连接手机并拉取 RSS"
                    PhoneConnectionMode.MANUAL_WIFI -> null
                }
                primaryButtonLabel = when (mode) {
                    PhoneConnectionMode.PURE_SOUND -> "开始监听"
                    PhoneConnectionMode.SOUND_GUIDED_WIFI -> "开始监听并连接"
                    PhoneConnectionMode.MANUAL_WIFI -> null
                }
                isBusy = false
            }

            PhoneConnectionAbility.SYNC_FAVORITES,
            PhoneConnectionAbility.SYNC_WATCH_LATER -> {
                when (mode) {
                    PhoneConnectionMode.PURE_SOUND -> preparePureSoundSync(currentAbility)
                    PhoneConnectionMode.SOUND_GUIDED_WIFI -> {
                        statusMessage = when (currentAbility) {
                            PhoneConnectionAbility.SYNC_FAVORITES -> "请在手机上选择“引导同步收藏”"
                            PhoneConnectionAbility.SYNC_WATCH_LATER -> "请在手机上选择“引导同步稍后再看”"
                            else -> ""
                        }
                        detailMessage = "手表会先听取手机热点信息，连入后再通过局域网上传数据"
                        primaryButtonLabel = "开始监听并连接"
                        isBusy = false
                    }

                    PhoneConnectionMode.MANUAL_WIFI -> Unit
                }
            }
        }
    }

    private fun preparePureSoundSync(currentAbility: PhoneConnectionAbility) {
        isBusy = true
        statusMessage = "正在整理要发送的数据…"
        detailMessage = null
        primaryButtonLabel = null

        prepareJob = lifecycleScope.launch {
            runCatching {
                val payloadBytes = when (currentAbility) {
                    PhoneConnectionAbility.SYNC_FAVORITES -> {
                        val items = (application as WatchRssApplication).container.rssRepository
                            .observeSavedItems(SaveType.FAVORITE)
                            .first()
                        require(items.isNotEmpty()) { "暂无收藏可同步" }
                        AcousticConnectionProtocol.buildPureSoundSavedItems(currentAbility, items)
                    }

                    PhoneConnectionAbility.SYNC_WATCH_LATER -> {
                        val items = (application as WatchRssApplication).container.rssRepository
                            .observeSavedItems(SaveType.WATCH_LATER)
                            .first()
                        require(items.isNotEmpty()) { "暂无稍后再看可同步" }
                        AcousticConnectionProtocol.buildPureSoundSavedItems(currentAbility, items)
                    }

                    else -> error("不支持的声波同步能力")
                }
                AcousticCodec.encode(payloadBytes)
            }.onSuccess { packet ->
                preparedPacket = packet
                estimatedTimeLabel = "预计耗时 ${formatDuration(packet.durationMs)}"
                statusMessage = "请在手机上选择“纯声波 > 接收手表同步”"
                detailMessage = "声波方式较慢，播放期间请保持手机靠近手表"
                primaryButtonLabel = "开始发送"
            }.onFailure { throwable ->
                statusMessage = throwable.message ?: "准备同步数据失败"
                detailMessage = null
                primaryButtonLabel = null
            }
            isBusy = false
        }
    }

    private fun handlePrimaryAction() {
        when (mode) {
            PhoneConnectionMode.PURE_SOUND -> when (ability) {
                PhoneConnectionAbility.REMOTE_INPUT -> requestPermissionsAndRun(
                    PendingPermissionAction.LISTEN_PURE_SOUND,
                    pureSoundPermissions()
                )

                PhoneConnectionAbility.SYNC_FAVORITES,
                PhoneConnectionAbility.SYNC_WATCH_LATER -> startPureSoundPlayback()
                null -> Unit
            }

            PhoneConnectionMode.SOUND_GUIDED_WIFI -> requestPermissionsAndRun(
                PendingPermissionAction.LISTEN_GUIDED_WIFI,
                guidedWifiPermissions()
            )

            PhoneConnectionMode.MANUAL_WIFI -> Unit
        }
    }

    private fun startPureSoundPlayback() {
        val packet = preparedPacket ?: return
        lifecycleScope.launch {
            isBusy = true
            statusMessage = "正在播放声波…"
            detailMessage = "请把手表扬声器靠近手机麦克风"
            primaryButtonLabel = null
            runCatching { acousticPlayer.play(packet) }
                .onSuccess {
                    statusMessage = "声波发送完成"
                    detailMessage = "请到手机端查看接收结果"
                }
                .onFailure { throwable ->
                    statusMessage = throwable.message ?: "声波播放失败"
                    detailMessage = null
                }
            isBusy = false
        }
    }

    private fun startPureSoundListening() {
        lifecycleScope.launch {
            isBusy = true
            statusMessage = "正在聆听手机发来的声波…"
            detailMessage = "请把手表麦克风靠近手机扬声器"
            primaryButtonLabel = null
            runCatching {
                val bytes = acousticReceiver.listen(timeoutMs = 120_000L)
                    ?: error("未收到有效的声波数据")
                val envelope = AcousticConnectionProtocol.parsePureSound(bytes)
                require(envelope.ability == PhoneConnectionAbility.REMOTE_INPUT) { "当前声波内容不是 RSS 输入" }
                envelope.url ?: error("声波中未包含 RSS 地址")
            }.onSuccess { url ->
                statusMessage = "已收到 RSS 地址"
                detailMessage = url
                handleRemoteInput(url)
            }.onFailure { throwable ->
                statusMessage = throwable.message ?: "纯声波接收失败"
                detailMessage = null
                primaryButtonLabel = "重新监听"
                isBusy = false
            }
        }
    }

    private fun startGuidedWifiListening() {
        lifecycleScope.launch {
            isBusy = true
            statusMessage = "正在听取手机热点信息…"
            detailMessage = "请把手表麦克风靠近手机扬声器"
            primaryButtonLabel = null

            runCatching {
                val bytes = acousticReceiver.listen(timeoutMs = 120_000L)
                    ?: error("未收到有效的引导声波")
                val envelope = AcousticConnectionProtocol.parseGuidedWifi(bytes)
                val currentAbility = ability ?: envelope.ability
                require(currentAbility == envelope.ability) { "手机端引导的能力与当前操作不一致" }

                statusMessage = "正在连接手机热点 ${envelope.ssid}…"
                val connection = guidedWifiClient.connectToHotspot(envelope.ssid, envelope.passphrase)
                connection.use {
                    when (currentAbility) {
                        PhoneConnectionAbility.REMOTE_INPUT -> {
                            statusMessage = "正在从手机拉取 RSS…"
                            val url = guidedWifiClient.fetchRemoteUrl(
                                connection = it,
                                host = envelope.host,
                                port = envelope.port,
                                token = envelope.token
                            )
                            handleRemoteInput(url)
                        }

                        PhoneConnectionAbility.SYNC_FAVORITES,
                        PhoneConnectionAbility.SYNC_WATCH_LATER -> {
                            statusMessage = "正在通过局域网同步到手机…"
                            val items = loadSavedItemsJson(currentAbility)
                            val path = when (currentAbility) {
                                PhoneConnectionAbility.SYNC_FAVORITES -> "pushFavorites"
                                PhoneConnectionAbility.SYNC_WATCH_LATER -> "pushWatchLater"
                                else -> error("不支持的同步能力")
                            }
                            guidedWifiClient.uploadSavedItems(
                                connection = it,
                                host = envelope.host,
                                port = envelope.port,
                                token = envelope.token,
                                path = path,
                                items = items
                            )
                            statusMessage = "已同步至手机"
                            detailMessage = "手机 companion 中已经可以看到最新数据"
                            isBusy = false
                            primaryButtonLabel = null
                        }
                    }
                }
            }.onFailure { throwable ->
                statusMessage = throwable.message ?: "声波引导 WiFi 连接失败"
                detailMessage = null
                primaryButtonLabel = "重新开始"
                isBusy = false
            }
        }
    }

    private suspend fun loadSavedItemsJson(currentAbility: PhoneConnectionAbility): JSONArray {
        val saveType = when (currentAbility) {
            PhoneConnectionAbility.SYNC_FAVORITES -> SaveType.FAVORITE
            PhoneConnectionAbility.SYNC_WATCH_LATER -> SaveType.WATCH_LATER
            else -> error("不支持的保存类型")
        }
        val items = (application as WatchRssApplication).container.rssRepository
            .observeSavedItems(saveType)
            .first()
        require(items.isNotEmpty()) { "暂无可同步数据" }

        return SavedItemsSyncPayload.buildLinksOnly(items)
    }

    private fun requestPermissionsAndRun(action: PendingPermissionAction, permissions: List<String>) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            when (action) {
                PendingPermissionAction.LISTEN_PURE_SOUND -> startPureSoundListening()
                PendingPermissionAction.LISTEN_GUIDED_WIFI -> startGuidedWifiListening()
            }
            return
        }
        pendingPermissionAction = action
        permissionsLauncher.launch(missing.toTypedArray())
    }

    private fun pureSoundPermissions(): List<String> = listOf(Manifest.permission.RECORD_AUDIO)

    private fun guidedWifiPermissions(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    private fun handleRemoteInput(url: String) {
        if (returnRemoteUrl) {
            setResult(RESULT_OK, Intent().putExtra(ServerActivity.EXTRA_REMOTE_URL, url))
        } else {
            startActivity(
                Intent(this, AddRssActivity::class.java).apply {
                    putExtra(AddRssActivity.EXTRA_URL, url)
                }
            )
        }
        finish()
    }

    override fun onDestroy() {
        prepareJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PREFERRED_ABILITY = "preferred_ability"
        private const val EXTRA_RETURN_REMOTE_URL = "return_remote_url"

        fun createIntent(
            context: Context,
            mode: PhoneConnectionMode,
            preferredAbility: PhoneConnectionAbility?,
            returnRemoteUrl: Boolean
        ): Intent {
            return Intent(context, AcousticConnectionActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_PREFERRED_ABILITY, preferredAbility?.name)
                putExtra(EXTRA_RETURN_REMOTE_URL, returnRemoteUrl)
            }
        }
    }
}

private enum class PendingPermissionAction {
    LISTEN_PURE_SOUND,
    LISTEN_GUIDED_WIFI
}

@androidx.compose.runtime.Composable
private fun AcousticConnectionScreen(
    mode: PhoneConnectionMode,
    ability: PhoneConnectionAbility?,
    statusMessage: String,
    detailMessage: String?,
    estimatedTimeLabel: String?,
    primaryButtonLabel: String?,
    isBusy: Boolean,
    onAbilitySelected: (PhoneConnectionAbility) -> Unit,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val scrollState = rememberScrollState()

    InstallRotaryScrollHandler(scrollState)

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
                text = when (mode) {
                    PhoneConnectionMode.PURE_SOUND -> "纯声波"
                    PhoneConnectionMode.SOUND_GUIDED_WIFI -> "声波引导 WiFi"
                    PhoneConnectionMode.MANUAL_WIFI -> "连接手机"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            ability?.let {
                Text(
                    text = "当前操作：${it.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (ability == null) {
                PhoneConnectionAbility.orderedValues.forEach { item ->
                    WatchSettingsPillRow(
                        label = item.displayName,
                        leadingIconRes = R.drawable.ic_action_share,
                        onClick = { onAbilitySelected(item) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp))

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            detailMessage?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            estimatedTimeLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            primaryButtonLabel?.let {
                WatchButton(
                    onClick = onPrimaryAction,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = it, textAlign = TextAlign.Center)
                }
            }

            WatchButton(
                onClick = onDismiss,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (isBusy) "处理中…" else "关闭", textAlign = TextAlign.Center)
            }
        }
    }
}

private fun formatDuration(durationMs: Int): String {
    val totalSeconds = ceil(durationMs / 1_000.0).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}分${seconds}秒"
    } else {
        "${seconds}秒"
    }
}
