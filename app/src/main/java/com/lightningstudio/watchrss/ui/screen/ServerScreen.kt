package com.lightningstudio.watchrss.ui.screen

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ServerActivity
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
    serverType: ServerActivity.ServerType,
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val title = when (serverType) {
                ServerActivity.ServerType.REMOTE_INPUT -> "从手机输入"
                ServerActivity.ServerType.SYNC_FAVORITES -> "同步收藏"
                ServerActivity.ServerType.SYNC_WATCH_LATER -> "同步稍后再看"
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                port <= 0 -> {
                    // 服务器启动中
                    Text(
                        text = "正在启动服务器...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
                networkError -> {
                    // 网络错误
                    Text(
                        text = "请连接WiFi网络",
                        style = MaterialTheme.typography.bodyMedium,
                        color = lerp(Color(0xFFCF6679), Color(0xFFB00020), backgroundProgress),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "此功能需要手表和手机\n在同一WiFi网络下使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = lerp(Color(0xFF2C2C2C), Color(0xFFE0E0E0), backgroundProgress)
                        ),
                        shape = RoundedCornerShape(dimensionResource(R.dimen.hey_button_default_radius)),
                        modifier = Modifier
                            .width(dimensionResource(R.dimen.watch_action_button_width))
                            .height(dimensionResource(R.dimen.watch_action_button_height))
                    ) {
                        Text(
                            text = "关闭",
                            color = textColor,
                            style = ActionButtonTextStyle,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    // 正常显示QR码
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(120.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (synced) {
                        Text(
                            text = "已同步至手机端",
                            style = MaterialTheme.typography.bodyMedium,
                            color = lerp(Color(0xFF64B5F6), Color(0xFF1976D2), backgroundProgress),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = lerp(Color(0xFF2C2C2C), Color(0xFFE0E0E0), backgroundProgress)
                            ),
                            shape = RoundedCornerShape(dimensionResource(R.dimen.hey_button_default_radius)),
                            modifier = Modifier
                                .width(dimensionResource(R.dimen.watch_action_button_width))
                                .height(dimensionResource(R.dimen.watch_action_button_height))
                        ) {
                            Text(
                                text = "关闭",
                                color = textColor,
                                style = ActionButtonTextStyle,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = "请使用手机版扫码",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
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
