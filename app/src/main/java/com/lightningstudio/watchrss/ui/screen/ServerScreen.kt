package com.lightningstudio.watchrss.ui.screen

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.DownloadPhoneAppCompactLink
import com.lightningstudio.watchrss.ui.components.WatchButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.theme.ActionButtonTextStyle
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import com.lightningstudio.watchrss.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ServerScreen(
    port: Int,
    synced: Boolean,
    title: String,
    hint: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var networkError by remember { mutableStateOf(false) }

    // 背景渐变动画状态：从黑色(0f)渐变到白色(1f)
    // 原因：黑色背景下的二维码在手机扫码时会因为曝光过高导致二维码细节不明朗
    // 用户当然可以把手表放到一个白底的屏幕前来恢复正常曝光，但为何不直接把二维码界面变成白底呢？
    var shouldTransition by remember { mutableStateOf(false) }
    val backgroundProgress by animateFloatAsState(
        targetValue = if (shouldTransition) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "background_transition"
    )

    LaunchedEffect(port) {
        if (port > 0) {
            qrBitmap = withContext(Dispatchers.IO) {
                generateQrCode(context, port)
            }
            networkError = qrBitmap == null
        }
    }

    // 3秒后触发背景渐变
    LaunchedEffect(Unit) {
        delay(3000)
        shouldTransition = true
    }

    // 计算当前背景色和文字色
    val backgroundColor = lerp(Color.Black, Color.White, backgroundProgress)
    val textColor = lerp(Color.White, Color.Black, backgroundProgress)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .semantics { contentDescription = "$title 页面" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        heading()
                        contentDescription = "标题：$title"
                    }
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                port <= 0 -> {
                    // 服务器启动中
                    Text(
                        text = "正在启动服务器...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "状态：正在启动服务器"
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DownloadPhoneAppCompactLink(
                        operation = title,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
                networkError -> {
                    // 网络错误
                    Text(
                        text = "请连接WiFi网络",
                        style = MaterialTheme.typography.bodyMedium,
                        color = lerp(Color(0xFFCF6679), Color(0xFFB00020), backgroundProgress),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            contentDescription = "错误：请连接WiFi网络"
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            contentDescription = "操作说明：$hint"
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    WatchButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = lerp(Color(0xFF2C2C2C), Color(0xFFE0E0E0), backgroundProgress)
                        ),
                        shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_button_default_radius)),
                        modifier = Modifier
                            .width(watchDimensionResource(R.dimen.watch_action_button_width))
                            .height(watchDimensionResource(R.dimen.watch_action_button_height))
                    ) {
                        Text(
                            text = "关闭",
                            color = textColor,
                            style = ActionButtonTextStyle,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DownloadPhoneAppCompactLink(
                        operation = title,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
                else -> {
                    // 正常显示QR码
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "服务器连接二维码，使用手机版腕上RSS扫描此二维码",
                            modifier = Modifier
                                .size(120.dp)
                                .border(width = 6.dp, color = Color.Black)
                                .semantics {
                                    role = Role.Image
                                    contentDescription = "服务器连接二维码，使用手机版腕上RSS扫描此二维码"
                                }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (synced) {
                        Text(
                            text = "已同步至手机端",
                            style = MaterialTheme.typography.bodyMedium,
                            color = lerp(Color(0xFF64B5F6), Color(0xFF1976D2), backgroundProgress),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "状态：已同步至手机端"
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        WatchButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = lerp(Color(0xFF2C2C2C), Color(0xFFE0E0E0), backgroundProgress)
                            ),
                            shape = RoundedCornerShape(watchDimensionResource(R.dimen.hey_button_default_radius)),
                            modifier = Modifier
                                .width(watchDimensionResource(R.dimen.watch_action_button_width))
                                .height(watchDimensionResource(R.dimen.watch_action_button_height))
                        ) {
                            Text(
                                text = "关闭",
                                color = textColor,
                                style = ActionButtonTextStyle,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        DownloadPhoneAppCompactLink(
                            operation = title,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "操作说明：$hint"
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DownloadPhoneAppCompactLink(
                            operation = title,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

private fun generateQrCode(context: Context, port: Int): Bitmap? {
    val ipAddress = NetworkUtils.getLocalIpAddress(context) ?: return null
    val ipPort = "$ipAddress:$port"
    return QrCodeGenerator.createWatchRssQrCode(ipPort, 400)
}
