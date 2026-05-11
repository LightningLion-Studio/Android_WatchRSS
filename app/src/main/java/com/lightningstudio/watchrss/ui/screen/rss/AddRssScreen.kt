package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.ui.components.QrCodePanel
import com.lightningstudio.watchrss.ui.components.WatchButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.ActionButtonTextStyle
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchActionButtonWidthFor
import com.lightningstudio.watchrss.ui.theme.watchQrSizeFor
import com.lightningstudio.watchrss.ui.testing.AddRssTestTags
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import com.lightningstudio.watchrss.ui.viewmodel.AddRssUiState
import com.lightningstudio.watchrss.ui.viewmodel.AddRssStep
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

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
        InstallDigitalCrownScrollHandler(scrollState)
        val actionShape = RoundedCornerShape(WatchDimens.hey_button_default_radius)
        val actionHeight = WatchDimens.watch_action_button_height
        val actionColor = MaterialTheme.colorScheme.surfaceVariant
        val actionTextColor = MaterialTheme.colorScheme.onSurface
        val titleStyle = if (state.step == AddRssStep.INPUT) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.headlineSmall.copy(fontSize = 12.sp)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .testTag(AddRssTestTags.ROOT),
        ) {
            val horizontalPadding = WatchDimens.detail_page_horizontal_padding
            val verticalPadding = WatchDimens.watch_safe_vertical_padding
            val actionWidth = watchActionButtonWidthFor(maxWidth - horizontalPadding * 2)
            val qrSize = watchQrSizeFor(
                availableWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp),
                availableHeight = maxHeight,
                preferredSize = 200.dp
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = verticalPadding,
                        bottom = verticalPadding
                    )
                    .semantics { contentDescription = "添加RSS订阅页面" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "添加 RSS",
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )

                Spacer(modifier = Modifier.height(9.dp))
                when (state.step) {
                    AddRssStep.INPUT -> {
                        OutlinedTextField(
                            value = state.url,
                            onValueChange = onUrlChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddRssTestTags.URL_INPUT)
                                .semantics { contentDescription = "RSS订阅地址输入框，当前内容：${state.url}" },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            label = {
                                Text(
                                    text = "订阅地址",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            placeholder = {
                                Text(
                                    text = "https://example.com/feed.xml",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            singleLine = true,
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.outline,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                cursorColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )

                        Text(
                            text = "支持 RSS/Atom/RDF，添加后会自动拉取最新内容。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "支持的订阅格式说明" }
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        if (state.isLoadingPreview) {
                            Text(
                                text = "解析中...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag(AddRssTestTags.LOADING_TEXT)
                            )
                        }

                        state.errorMessage?.let { message ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(AddRssTestTags.ERROR_TEXT)
                                    .semantics { contentDescription = "错误信息：$message" }
                            )
                            val showActions = message != "请输入 RSS 地址" && message != "URL 不合法"
                            if (showActions) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    WatchButton(
                                        onClick = onSubmit,
                                        colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                                        shape = actionShape,
                                        modifier = Modifier
                                            .testTag(AddRssTestTags.RETRY_BUTTON)
                                            .semantics { contentDescription = "重试按钮" }
                                    ) {
                                        Text(text = "重试", color = actionTextColor)
                                    }
                                    WatchButton(
                                        onClick = onClearError,
                                        colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                                        shape = actionShape,
                                        modifier = Modifier
                                            .testTag(AddRssTestTags.CANCEL_ERROR_BUTTON)
                                            .semantics { contentDescription = "取消按钮" }
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
                            if (state.url.isNotEmpty()) {
                                WatchButton(
                                    onClick = onSubmit,
                                    enabled = !state.isSubmitting && !state.isLoadingPreview,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag(AddRssTestTags.SUBMIT_BUTTON)
                                        .semantics {
                                            contentDescription = if (state.isSubmitting) "添加中" else "添加订阅按钮"
                                        },
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                                ) {
                                    Text(
                                        text = "+",
                                        color = actionTextColor,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            } else if (showRemoteInputButton) {
                                WatchButton(
                                    onClick = onRemoteInput,
                                    colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                                    shape = actionShape,
                                    modifier = Modifier
                                        .width(actionWidth)
                                        .height(actionHeight)
                                        .testTag(AddRssTestTags.REMOTE_INPUT_BUTTON)
                                        .semantics { contentDescription = "从手机输入按钮" }
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
                        Column(
                            modifier = Modifier
                                .testTag(AddRssTestTags.PREVIEW_PANEL)
                                .semantics { contentDescription = "频道预览：${preview?.title ?: "未知"}" },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = preview?.title ?: "频道预览",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { heading() }
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(AddRssTestTags.ERROR_TEXT)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WatchButton(
                                onClick = onConfirm,
                                enabled = !state.isSubmitting,
                                colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                                shape = actionShape,
                                modifier = Modifier
                                    .width(actionWidth)
                                    .height(actionHeight)
                                    .testTag(AddRssTestTags.CONFIRM_BUTTON)
                                    .semantics { contentDescription = if (state.isSubmitting) "添加中" else "确认添加订阅按钮" }
                            ) {
                                Text(
                                    text = if (state.isSubmitting) "添加中" else "确认添加",
                                    color = actionTextColor,
                                    style = ActionButtonTextStyle,
                                    textAlign = TextAlign.Center
                                )
                            }
                            WatchButton(
                                onClick = onBackToInput,
                                colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                                shape = actionShape,
                                modifier = Modifier
                                    .width(actionWidth)
                                    .height(actionHeight)
                                    .testTag(AddRssTestTags.BACK_TO_INPUT_BUTTON)
                                    .semantics { contentDescription = "修改地址按钮" }
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
                        Column(
                            modifier = Modifier
                                .testTag(AddRssTestTags.EXISTING_PANEL)
                                .semantics { contentDescription = "已存在的订阅频道" },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = existing?.title?.let { "已存在：$it" } ?: "已存在该订阅",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { heading() }
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(AddRssTestTags.ERROR_TEXT)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        WatchButton(
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
                                .testTag(AddRssTestTags.OPEN_EXISTING_BUTTON)
                                .semantics { contentDescription = "跳转到已有频道按钮" }
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
                            val qrBitmapSizePx = with(androidx.compose.ui.platform.LocalDensity.current) {
                                qrSize.toPx().roundToInt().coerceAtLeast(1)
                            }
                            val qrBitmap = remember(serverAddress, qrBitmapSizePx) {
                                QrCodeGenerator.createWatchRssQrCode(serverAddress, qrBitmapSizePx)
                            }

                            QrCodePanel(
                                qrBitmap = qrBitmap,
                                qrSizeDp = qrSize,
                                qrContentDescription = "RSS服务器二维码，使用手机版腕上RSS扫描后可在手机端输入RSS地址",
                                title = "手机扫码添加 RSS",
                                subtitle = "使用手机版腕上 RSS 扫码后，可在手机上直接输入 RSS 地址",
                                titleContentDescription = "标题：手机扫码添加 RSS",
                                subtitleContentDescription = "操作提示：使用手机版腕上 RSS 扫码后，可在手机上直接输入 RSS 地址",
                                qrTestTag = AddRssTestTags.QR_IMAGE,
                                modifier = Modifier
                                    .testTag(AddRssTestTags.QR_PANEL)
                                    .semantics { contentDescription = "二维码扫描面板" },
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            WatchButton(
                                onClick = onBackToInput,
                                colors = ButtonDefaults.buttonColors(containerColor = actionColor),
                                shape = actionShape,
                                modifier = Modifier
                                    .width(actionWidth)
                                    .height(actionHeight)
                                    .testTag(AddRssTestTags.BACK_TO_INPUT_BUTTON)
                                    .semantics { contentDescription = "返回按钮" }
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
}
