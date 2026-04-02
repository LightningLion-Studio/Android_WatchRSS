package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.viewmodel.LlmSummaryUiState
import com.lightningstudio.watchrss.ui.viewmodel.LlmSummaryViewModel
import com.lightningstudio.watchrss.ui.viewmodel.SummaryStatus

@Composable
fun LlmSummaryScreen(
    viewModel: LlmSummaryViewModel,
    showTokenUsage: Boolean = false,
    onOpenConnectivityCheck: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LlmSummaryContent(
        state = state,
        showTokenUsage = showTokenUsage,
        onRetry = viewModel::retry,
        onOpenConnectivityCheck = onOpenConnectivityCheck
    )
}

@Composable
private fun LlmSummaryContent(
    state: LlmSummaryUiState,
    showTokenUsage: Boolean,
    onRetry: () -> Unit,
    onOpenConnectivityCheck: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val entrySpacing = WatchDimens.hey_distance_8dp
    val titleTopSpacing = WatchDimens.hey_distance_6dp
    val valueSpacing = WatchDimens.hey_distance_4dp
    val valueIndent = WatchDimens.hey_distance_10dp
    val scrollState = rememberScrollState()
    val isGenerating = state.status == SummaryStatus.Generating

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
                .semantics {
                    contentDescription = "AI总结页面"
                    stateDescription = when (state.status) {
                        SummaryStatus.Idle -> "空闲状态"
                        SummaryStatus.WaitingForContent -> "等待内容"
                        SummaryStatus.Generating -> "正在生成"
                        SummaryStatus.Done -> "已完成"
                        is SummaryStatus.Error -> "发生错误"
                    }
                }
        ) {
            Spacer(modifier = Modifier.height(titleTopSpacing))

            // 标题行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "AI图标",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AI 总结",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { contentDescription = "AI总结标题" }
                )
                if (isGenerating) {
                    Spacer(modifier = Modifier.width(8.dp))
                    WatchCircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 1.5.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(entrySpacing))

            when (val status = state.status) {
                is SummaryStatus.Idle -> {
                    Text(
                        text = "准备中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = valueIndent)
                            .semantics { contentDescription = "当前状态：准备中" }
                    )
                }
                is SummaryStatus.WaitingForContent -> {
                    Text(
                        text = "原文还在加载，准备好后会自动开始总结。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = valueIndent)
                            .semantics { contentDescription = "等待原文加载完成" }
                    )
                }
                is SummaryStatus.Generating -> {
                    if (state.text.isNotBlank()) {
                        SummaryTextCard(text = state.text, isGenerating = true)
                    } else {
                        Text(
                            text = "正在生成总结...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = valueIndent)
                                .semantics { contentDescription = "正在生成总结中" }
                        )
                    }
                }
                is SummaryStatus.Done -> {
                    SummaryTextCard(text = state.text, isGenerating = false)
                    if (showTokenUsage && state.totalTokens != null) {
                        Spacer(modifier = Modifier.height(entrySpacing))
                        TokenUsageCard(
                            promptTokens = state.promptTokens,
                            completionTokens = state.completionTokens,
                            totalTokens = state.totalTokens
                        )
                    }
                }
                is SummaryStatus.Error -> {
                    ErrorCard(message = status.message)
                    Spacer(modifier = Modifier.height(entrySpacing))
                    WatchSettingsPillRow(
                        label = "重试",
                        leadingIcon = Icons.Outlined.Refresh,
                        onClick = onRetry,
                        modifier = Modifier.semantics { contentDescription = "重试生成总结按钮" }
                    )
                    Spacer(modifier = Modifier.height(valueSpacing))
                    WatchSettingsPillRow(
                        label = "检测一下",
                        leadingIcon = Icons.Outlined.SettingsEthernet,
                        onClick = onOpenConnectivityCheck,
                        modifier = Modifier.semantics { contentDescription = "检测连接状态按钮" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(WatchDimens.hey_multiple_item_height))
        }
    }
}

@Composable
private fun SummaryTextCard(text: String, isGenerating: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics {
                contentDescription = "AI总结内容：${text.take(100)}${if (text.length > 100) "..." else ""}"
            }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TokenUsageCard(
    promptTokens: Int?,
    completionTokens: Int?,
    totalTokens: Int?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "Token用量：输入${promptTokens ?: 0}，输出${completionTokens ?: 0}，合计${totalTokens ?: 0}"
            }
    ) {
        Column {
            Text(
                text = "Token 用量",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            val detail = buildList {
                if (promptTokens != null) add("输入 $promptTokens")
                if (completionTokens != null) add("输出 $completionTokens")
                if (totalTokens != null) add("合计 $totalTokens")
            }.joinToString(" · ")
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFF6B6B).copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics {
                contentDescription = "错误信息：$message"
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "生成失败",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFF6B6B),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
