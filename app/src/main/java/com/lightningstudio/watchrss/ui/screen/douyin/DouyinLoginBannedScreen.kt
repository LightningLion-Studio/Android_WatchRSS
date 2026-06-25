package com.lightningstudio.watchrss.ui.screen.douyin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.components.WatchButton
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator

@Composable
fun DouyinLoginCheckingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DouyinLoginStatusLayout(
        title = "正在检测网络",
        message = "仅在 Wi-Fi 或蜂窝网络可用时打开抖音登录",
        actionLabel = "返回",
        onAction = onBack,
        modifier = modifier
    ) {
        WatchCircularProgressIndicator()
    }
}

@Composable
fun DouyinLoginBannedScreen(
    title: String = "当前网络不可用",
    message: String = "请切换到 Wi-Fi 或蜂窝网络后重试。以太网、蓝牙共享等网络暂不支持抖音登录。",
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DouyinLoginStatusLayout(
        title = title,
        message = message,
        actionLabel = "返回",
        onAction = onBack,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFFF9800)
        )
    }
}

@Composable
private fun DouyinLoginStatusLayout(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 220.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            leadingContent()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFBDBDBD),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            WatchButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text(text = actionLabel)
            }
        }
    }
}
