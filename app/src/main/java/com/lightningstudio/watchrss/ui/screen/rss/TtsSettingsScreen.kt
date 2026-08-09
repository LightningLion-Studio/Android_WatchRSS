package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.tts.TtsProviderCatalog
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.viewmodel.TtsSettingsState
import com.lightningstudio.watchrss.ui.viewmodel.TtsSettingsViewModel
import com.lightningstudio.watchrss.ui.viewmodel.TtsTestStatus

@Composable
fun TtsSettingsScreen(
    viewModel: TtsSettingsViewModel,
    showDetailedConfiguration: Boolean,
    onOpenPhoneConfig: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    TtsSettingsContent(
        state = state,
        onSelectEngine = viewModel::selectEngine,
        onUseLocal = viewModel::useLocal,
        onUseBackendDefault = viewModel::useBackendDefault,
        onIncreaseSpeed = viewModel::increaseSpeed,
        onDecreaseSpeed = viewModel::decreaseSpeed,
        onRunTest = viewModel::runTest,
        onOpenPhoneConfig = onOpenPhoneConfig,
        showDetailedConfiguration = showDetailedConfiguration
    )
}

@Composable
private fun TtsSettingsContent(
    state: TtsSettingsState,
    onSelectEngine: (String) -> Unit,
    onUseLocal: () -> Unit,
    onUseBackendDefault: () -> Unit,
    onIncreaseSpeed: () -> Unit,
    onDecreaseSpeed: () -> Unit,
    onRunTest: () -> Unit,
    onOpenPhoneConfig: (() -> Unit)?,
    showDetailedConfiguration: Boolean
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val entrySpacing = WatchDimens.hey_distance_8dp
    val valueSpacing = WatchDimens.hey_distance_4dp
    val valueIndent = WatchDimens.hey_distance_10dp
    val pillHeight = WatchDimens.hey_multiple_item_height
    val scrollState = rememberScrollState()
    val isTesting = state.testStatus is TtsTestStatus.Testing
    var showEnginePicker by remember { mutableStateOf(false) }

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
        ) {
            SettingsHeader(
                title = if (showDetailedConfiguration) "朗读语音源" else "朗读语速"
            )

            Spacer(modifier = Modifier.height(WatchDimens.hey_content_horizontal_distance))

            if (!showDetailedConfiguration) {
                SpeechRateSetting(
                    speed = state.speed,
                    onIncreaseSpeed = onIncreaseSpeed,
                    onDecreaseSpeed = onDecreaseSpeed
                )
            } else if (showEnginePicker) {
                EnginePicker(
                    currentEngine = state.engine,
                    onSelect = { engine ->
                        onSelectEngine(engine)
                        showEnginePicker = false
                    }
                )
            } else {
                WatchSettingsPillRow(
                    label = "当前引擎",
                    onClick = { showEnginePicker = true }
                ) {
                    Text(
                        text = TtsProviderCatalog.displayName(state.engine),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "点击切换本地 TTS、应用默认语音或第三方语音",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                )

                Spacer(modifier = Modifier.height(entrySpacing))

                if (state.engine == TtsProviderCatalog.ENGINE_BACKEND_DEFAULT) {
                    WatchSettingsPillRow(
                        label = "应用默认语音",
                        onClick = onUseBackendDefault
                    ) {
                        Text(
                            text = "已选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (state.isLoggedIn) "已登录，将使用后端默认语音" else "请先登录以使用应用默认语音",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isLoggedIn) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFF6B6B),
                        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                    )
                }

                if (state.needsApiKey) {
                    Spacer(modifier = Modifier.height(entrySpacing))
                    WatchSettingsPillRow(label = "音色 / 模型") {
                        Text(
                            text = state.voiceId.ifBlank { "默认" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "当前音色 ID，可在手机端修改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                    )

                    Spacer(modifier = Modifier.height(entrySpacing))
                    WatchSettingsPillRow(label = "API Key") {
                        Text(
                            text = if (state.hasApiKey) "已配置" else "未配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.hasApiKey) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFF6B6B)
                        )
                    }
                    Text(
                        text = if (state.hasApiKey) "API Key 已安全存储在手表" else "请在手机端配置 API Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                    )
                }

                Spacer(modifier = Modifier.height(entrySpacing))

                SpeechRateSetting(
                    speed = state.speed,
                    onIncreaseSpeed = onIncreaseSpeed,
                    onDecreaseSpeed = onDecreaseSpeed
                )

                Spacer(modifier = Modifier.height(entrySpacing))

                if (onOpenPhoneConfig != null) {
                    WatchSettingsPillRow(
                        label = "手机扫码配置语音",
                        onClick = onOpenPhoneConfig
                    )
                    Text(
                        text = "在手机端选择音色、输入 API Key 并同步到手表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                    )

                    Spacer(modifier = Modifier.height(entrySpacing))
                }

                val canTest = when (state.engine) {
                    TtsProviderCatalog.ENGINE_LOCAL -> true
                    TtsProviderCatalog.ENGINE_BACKEND_DEFAULT -> state.isLoggedIn
                    else -> state.hasApiKey
                }
                WatchSettingsPillRow(
                    label = if (isTesting) "正在测试..." else "测试连接",
                    onClick = if (canTest && !isTesting) onRunTest else null
                ) {
                    if (isTesting) {
                        WatchCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            strokeWidth = 2.dp
                        )
                    }
                }
                if (!canTest && !isTesting) {
                    Text(
                        text = when (state.engine) {
                            TtsProviderCatalog.ENGINE_BACKEND_DEFAULT -> "请先登录以使用默认语音"
                            else -> "请先配置 API Key"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                    )
                }

                when (val status = state.testStatus) {
                    is TtsTestStatus.Idle -> { }
                    is TtsTestStatus.Testing -> { }
                    is TtsTestStatus.Success -> {
                        Spacer(modifier = Modifier.height(entrySpacing))
                        ResultCard(isSuccess = true) {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF4CAF50),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(valueSpacing))
                            Text(
                                text = "延迟 ${status.latencyMs} ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    is TtsTestStatus.Failure -> {
                        Spacer(modifier = Modifier.height(entrySpacing))
                        ResultCard(isSuccess = false) {
                            Text(
                                text = "连接失败",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFFF6B6B),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(valueSpacing))
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (state.configMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(entrySpacing))
                    Text(
                        text = state.configMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(pillHeight))
        }
    }
}

@Composable
private fun SpeechRateSetting(
    speed: Float,
    onIncreaseSpeed: () -> Unit,
    onDecreaseSpeed: () -> Unit
) {
    val valueSpacing = WatchDimens.hey_distance_4dp
    val valueIndent = WatchDimens.hey_distance_10dp

    WatchSettingsPillRow(label = "语速") {
        RoundIconButtonIcon(
            icon = Icons.Outlined.Remove,
            contentDescription = "减慢语速",
            enabled = speed > 0.55f,
            onClick = onDecreaseSpeed
        )
        Spacer(modifier = Modifier.width(WatchDimens.hey_distance_6dp))
        StepperValue(
            text = "${(speed * 10).toInt() / 10f}x",
            width = watchDimensionResource(R.dimen.watch_action_button_height)
        )
        Spacer(modifier = Modifier.width(WatchDimens.hey_distance_6dp))
        RoundIconButtonIcon(
            icon = Icons.Outlined.Add,
            contentDescription = "加快语速",
            enabled = speed < 1.95f,
            onClick = onIncreaseSpeed
        )
    }
    Text(
        text = "调节朗读语速",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
    )
}

internal fun isDetailedTtsConfigurationVisible(buildType: String): Boolean {
    return buildType == "debug"
}

@Composable
private fun EnginePicker(
    currentEngine: String,
    onSelect: (String) -> Unit
) {
    val engines = remember { TtsProviderCatalog.supportedEngines() }
    val valueIndent = WatchDimens.hey_distance_10dp
    val valueSpacing = WatchDimens.hey_distance_4dp

    Text(
        text = "选择朗读引擎",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = valueIndent)
    )

    Spacer(modifier = Modifier.height(valueSpacing))

    engines.forEach { engine ->
        val selected = engine == currentEngine
        WatchSettingsPillRow(
            label = TtsProviderCatalog.displayName(engine),
            onClick = { onSelect(engine) }
        ) {
            Text(
                text = if (selected) "✓" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(valueSpacing))
    }
}

@Composable
private fun ResultCard(isSuccess: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val accentColor = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFFF6B6B)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}
