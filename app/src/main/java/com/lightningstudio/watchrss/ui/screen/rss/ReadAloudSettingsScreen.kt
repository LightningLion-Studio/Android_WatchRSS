package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.tts.ReadAloudProvider
import com.lightningstudio.watchrss.ui.components.WatchButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.viewmodel.ReadAloudSettingsState
import com.lightningstudio.watchrss.ui.viewmodel.ReadAloudTestStatus

@Composable
fun ReadAloudSettingsScreen(
    state: ReadAloudSettingsState,
    onCycleProvider: () -> Unit,
    onModelChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onAppIdChange: (String) -> Unit,
    onResourceIdChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onRunTest: () -> Unit,
    onOpenPhoneConfig: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    InstallDigitalCrownScrollHandler(scrollState)
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val valueSpacing = WatchDimens.hey_distance_4dp
    val entrySpacing = WatchDimens.hey_distance_8dp
    val actionColor = MaterialTheme.colorScheme.surfaceVariant

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
        ) {
            SettingsHeader(title = "朗读 API")

            Spacer(modifier = Modifier.height(WatchDimens.hey_content_horizontal_distance))

            WatchSettingsPillRow(
                label = "服务商",
                onClick = onCycleProvider
            ) {
                Text(
                    text = state.provider.displayName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "点击切换，已内置 OpenAI、微软 Azure、ElevenLabs、火山引擎和自定义兼容接口",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = valueSpacing)
            )
            Text(
                text = readAloudProviderHint(state.provider),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ReadAloudTextField(
                label = state.provider.apiKeyLabel,
                value = state.apiKeyInput,
                placeholder = if (state.hasSavedApiKey) "已保存，留空则保持不变" else "请输入密钥",
                onValueChange = onApiKeyChange
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ReadAloudTextField(
                label = "模型",
                value = state.model,
                placeholder = state.provider.defaultModel,
                onValueChange = onModelChange
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ReadAloudTextField(
                label = state.provider.voiceLabel,
                value = state.voice,
                placeholder = state.provider.defaultVoice,
                onValueChange = onVoiceChange
            )

            if (state.provider.requiresRegion) {
                Spacer(modifier = Modifier.height(entrySpacing))
                ReadAloudTextField(
                    label = "区域",
                    value = state.region,
                    placeholder = "例如 eastasia / eastus",
                    onValueChange = onRegionChange
                )
            }

            if (state.provider.requiresAppId) {
                Spacer(modifier = Modifier.height(entrySpacing))
                ReadAloudTextField(
                    label = "App ID",
                    value = state.appId,
                    placeholder = "火山引擎控制台中的 APP ID",
                    onValueChange = onAppIdChange
                )
            }

            if (state.provider.requiresResourceId) {
                Spacer(modifier = Modifier.height(entrySpacing))
                ReadAloudTextField(
                    label = "Resource ID",
                    value = state.resourceId,
                    placeholder = state.provider.defaultResourceId,
                    onValueChange = onResourceIdChange
                )
            }

            if (state.provider.allowsCustomBaseUrl) {
                Spacer(modifier = Modifier.height(entrySpacing))
                ReadAloudTextField(
                    label = "Base URL",
                    value = state.baseUrl,
                    placeholder = "https://example.com/v1",
                    onValueChange = onBaseUrlChange
                )
            }

            if (onOpenPhoneConfig != null) {
                Spacer(modifier = Modifier.height(entrySpacing))
                WatchSettingsPillRow(
                    label = "手机辅助配置",
                    onClick = onOpenPhoneConfig
                )
                Text(
                    text = "也可以从手机端扫码填写，手表和手机在同一网络时更方便。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = valueSpacing)
                )
            }

            state.saveMessage?.let { message ->
                Spacer(modifier = Modifier.height(entrySpacing))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.contains("保存")) Color(0xFF6BD17E) else Color(0xFFFF8A80),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(entrySpacing))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                WatchButton(
                    onClick = onSave,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                ) {
                    Text(text = "保存", color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(12.dp))
                WatchButton(
                    onClick = onRunTest,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                ) {
                    Text(text = "测试", color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(entrySpacing))

            when (val testStatus = state.testStatus) {
                ReadAloudTestStatus.Idle -> Unit
                ReadAloudTestStatus.Testing -> {
                    Text(
                        text = "正在合成测试音频...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ReadAloudTestStatus.Success -> {
                    ReadAloudResultCard(isSuccess = true) {
                        Text(
                            text = "测试成功",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF4CAF50),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "已生成 ${(testStatus.fileSizeBytes / 1024).coerceAtLeast(1)} KB 音频",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is ReadAloudTestStatus.Failure -> {
                    ReadAloudResultCard(isSuccess = false) {
                        Text(
                            text = "测试失败",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFFF6B6B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = testStatus.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(WatchDimens.watch_action_button_height))
        }
    }
}

@Composable
private fun ReadAloudTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

fun readAloudProviderHint(provider: ReadAloudProvider): String {
    return when (provider) {
        ReadAloudProvider.OPENAI -> "推荐模型 ${provider.defaultModel}，语音如 ${provider.defaultVoice}"
        ReadAloudProvider.MICROSOFT_AZURE -> "需要区域与语音名，例如 ${provider.defaultVoice}"
        ReadAloudProvider.ELEVENLABS -> "语音字段填写 Voice ID"
        ReadAloudProvider.VOLCENGINE -> "按火山引擎大模型 TTS 配置 Access Key、App ID、Resource ID 和 Speaker，当前走 HTTP Chunked 单向流式接口"
        ReadAloudProvider.CUSTOM_OPENAI -> "兼容 /audio/speech 接口"
    }
}

@Composable
private fun ReadAloudResultCard(
    isSuccess: Boolean,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val accentColor = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFFF6B6B)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                accentColor.copy(alpha = 0.12f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}
