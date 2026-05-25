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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.llm.LlmProviderCatalog
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.viewmodel.LlmConnectivityState
import com.lightningstudio.watchrss.ui.viewmodel.LlmConnectivityViewModel
import com.lightningstudio.watchrss.ui.viewmodel.LlmTestStatus

@Composable
fun LlmConnectivityScreen(viewModel: LlmConnectivityViewModel) {
    val state by viewModel.state.collectAsState()
    LlmConnectivityContent(
        state = state,
        onRunTest = viewModel::runTest,
        onUsePublicWelfareSite = viewModel::usePublicWelfareSite,
        onOpenPhoneConfig = null
    )
}

@Composable
fun LlmConnectivityScreen(
    viewModel: LlmConnectivityViewModel,
    onOpenPhoneConfig: (() -> Unit)?
) {
    val state by viewModel.state.collectAsState()
    LlmConnectivityContent(
        state = state,
        onRunTest = viewModel::runTest,
        onUsePublicWelfareSite = viewModel::usePublicWelfareSite,
        onOpenPhoneConfig = onOpenPhoneConfig
    )
}

@Composable
private fun LlmConnectivityContent(
    state: LlmConnectivityState,
    onRunTest: () -> Unit,
    onUsePublicWelfareSite: () -> Unit,
    onOpenPhoneConfig: (() -> Unit)?
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val entrySpacing = WatchDimens.hey_distance_8dp
    val valueSpacing = WatchDimens.hey_distance_4dp
    val valueIndent = WatchDimens.hey_distance_10dp
    val pillHeight = WatchDimens.hey_multiple_item_height
    val scrollState = rememberScrollState()
    val isTesting = state.testStatus is LlmTestStatus.Testing

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
        ) {
            // 标题
            SettingsHeader(title = "AI 连通性")

            Spacer(modifier = Modifier.height(WatchDimens.hey_content_horizontal_distance))

            // 当前配置摘要
            val providerLabel = LlmProviderCatalog.displayName(state.provider)
            WatchSettingsPillRow(label = "服务商") {
                Text(
                    text = if (state.provider.isEmpty()) "未配置" else providerLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.provider.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (state.model.isEmpty()) "使用默认模型" else state.model,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = if (state.hasApiKey) "API Key 已配置" else "API Key 未配置",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.hasApiKey)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    Color(0xFFFF6B6B),
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            WatchSettingsPillRow(
                label = if (LlmProviderCatalog.isPublicWelfare(state.provider)) {
                    "已使用公益站点"
                } else {
                    "使用公益站点"
                },
                onClick = onUsePublicWelfareSite
            )
            Text(
                text = state.configMessage.ifBlank { "公益站不保证稳定性。" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            if (onOpenPhoneConfig != null) {
                Spacer(modifier = Modifier.height(entrySpacing))

                WatchSettingsPillRow(
                    label = "手机扫码配置大模型",
                    onClick = onOpenPhoneConfig
                )
                Text(
                    text = "进入手机版扫码页面，直接配置服务商、模型和 API Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                )
            }

            Spacer(modifier = Modifier.height(entrySpacing))

            // 测试按钮
            val canTest = state.hasApiKey && state.provider.isNotEmpty() && !isTesting
            WatchSettingsPillRow(
                label = if (isTesting) "正在测试..." else "开始测试",
                onClick = if (canTest) onRunTest else null
            ) {
                if (isTesting) {
                    WatchCircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        strokeWidth = 2.dp
                    )
                }
            }
            if (!state.hasApiKey || state.provider.isEmpty()) {
                Text(
                    text = if (state.provider.isEmpty()) "请先通过手机端配置 LLM 参数"
                    else "请先配置 API Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                )
            }

            Spacer(modifier = Modifier.height(entrySpacing))

            // 测试结果
            when (val status = state.testStatus) {
                is LlmTestStatus.Idle -> { /* 无结果时不显示 */ }
                is LlmTestStatus.Testing -> { /* 进行中 */ }
                is LlmTestStatus.Success -> {
                    ResultCard(isSuccess = true) {
                        Text(
                            text = "连接成功",
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
                        if (status.replySnippet.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(valueSpacing))
                            Text(
                                text = "回复：${status.replySnippet}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                is LlmTestStatus.Failure -> {
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

            Spacer(modifier = Modifier.height(pillHeight))
        }
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
