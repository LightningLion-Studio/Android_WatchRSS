package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.ActionButtonTextStyle
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import com.lightningstudio.watchrss.ui.viewmodel.AddRssUiState
import com.lightningstudio.watchrss.ui.viewmodel.AddRssStep
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AddRssScreen(
    uiState: StateFlow<AddRssUiState>,
    showRemoteInputButton: Boolean,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onBackToInput: () -> Unit,
    onOpenExisting: (RssChannel) -> Unit,
    onChannelAdded: (String?, Long) -> Unit,
    onConsumed: () -> Unit,
    onClearError: () -> Unit,
    onRemoteInput: () -> Unit
) {
    val state by uiState.collectAsState()

    LaunchedEffect(state.createdChannelId) {
        val channelId = state.createdChannelId
        if (channelId != null) {
            onChannelAdded(state.url, channelId)
            onConsumed()
        }
    }

    WatchSurface {
        val scrollState = rememberScrollState()
        val actionShape = RoundedCornerShape(dimensionResource(R.dimen.hey_button_default_radius))
        val actionWidth = dimensionResource(R.dimen.watch_action_button_width)
        val actionHeight = dimensionResource(R.dimen.watch_action_button_height)
        val actionColor = MaterialTheme.colorScheme.surfaceVariant
        val actionTextColor = MaterialTheme.colorScheme.onSurface

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "添加 RSS",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(9.dp))
            when (state.step) {
                AddRssStep.INPUT -> {
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "订阅地址") },
                        placeholder = { Text(text = "https://example.com/feed.xml") },
                        singleLine = true,
                        shape = CircleShape
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "支持 RSS/Atom/RDF，添加后会自动拉取最新内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.isLoadingPreview) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "解析中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    state.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val showActions = message != "请输入 RSS 地址" && message != "URL 不合法"
                        if (showActions) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = onSubmit,
                                    colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                                    shape = actionShape
                                ) {
                                    Text(text = "重试", color = actionTextColor)
                                }
                                Button(
                                    onClick = onClearError,
                                    colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                                    shape = actionShape
                                ) {
                                    Text(text = "取消", color = actionTextColor)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onSubmit,
                            enabled = !state.isSubmitting && !state.isLoadingPreview,
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                        ) {
                            Text(
                                text = "+",
                                color = actionTextColor,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (showRemoteInputButton) {
                            Button(
                                onClick = onRemoteInput,
                                colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                                shape = actionShape,
                                modifier = Modifier
                                    .width(actionWidth)
                                    .height(actionHeight)
                            ) {
                                Text(
                                    text = "从手机输入",
                                    color = actionTextColor,
                                    style = ActionButtonTextStyle,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                AddRssStep.PREVIEW -> {
                    val preview = state.preview
                    Text(
                        text = preview?.title ?: "频道预览",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preview?.description?.ifBlank { null } ?: "暂无简介",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onConfirm,
                            enabled = !state.isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                            shape = actionShape,
                            modifier = Modifier
                                .width(actionWidth)
                                .height(actionHeight)
                        ) {
                            Text(
                                text = if (state.isSubmitting) "添加中" else "确认添加",
                                color = actionTextColor,
                                style = ActionButtonTextStyle,
                                textAlign = TextAlign.Center
                            )
                        }
                        Button(
                            onClick = onBackToInput,
                            colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                            shape = actionShape,
                            modifier = Modifier
                                .width(actionWidth)
                                .height(actionHeight)
                        ) {
                            Text(
                                text = "修改地址",
                                color = actionTextColor,
                                style = ActionButtonTextStyle,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                AddRssStep.EXISTING -> {
                    val existing = state.existingChannel
                    Text(
                        text = existing?.title?.let { "已存在：$it" } ?: "已存在该订阅",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "无需重复添加，可直接进入频道。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (existing != null) {
                                onOpenExisting(existing)
                            }
                        },
                        enabled = existing != null,
                        colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                        shape = actionShape,
                        modifier = Modifier
                            .width(actionWidth)
                            .height(actionHeight)
                    ) {
                        Text(
                            text = "跳转频道",
                            color = actionTextColor,
                            style = ActionButtonTextStyle,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                AddRssStep.QR_CODE -> {
                    val serverAddress = state.serverAddress
                    if (serverAddress != null) {
                        // 生成二维码
                        val qrBitmap = remember(serverAddress) {
                            QrCodeGenerator.createWatchRssQrCode(serverAddress, 200)
                        }

                        Text(
                            text = "用手机扫码",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "二维码",
                                modifier = Modifier.size(200.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "使用手机版腕上RSS扫描二维码\n即可在手机上输入RSS地址",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onBackToInput,
                            colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                            shape = actionShape,
                            modifier = Modifier
                                .width(actionWidth)
                                .height(actionHeight)
                        ) {
                            Text(
                                text = "返回",
                                color = actionTextColor,
                                style = ActionButtonTextStyle,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
