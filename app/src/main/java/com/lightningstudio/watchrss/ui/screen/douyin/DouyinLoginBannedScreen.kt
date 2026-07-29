package com.lightningstudio.watchrss.ui.screen.douyin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.network.InternetAvailabilityStatus
import com.lightningstudio.watchrss.ui.components.InternetAvailabilityPage

@Composable
fun DouyinLoginCheckingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    InternetAvailabilityPage(
        status = InternetAvailabilityStatus.Checking,
        guidanceMessage = "仅在 Wi-Fi 或蜂窝网络可用时打开抖音登录",
        statusMessage = "正在检测互联网状态…",
        actionText = "返回",
        actionEnabled = true,
        actionModifier = Modifier.padding(bottom = 8.dp),
        onAction = onBack,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

@Composable
fun DouyinLoginBannedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    status: InternetAvailabilityStatus = InternetAvailabilityStatus.Unavailable
) {
    val guidanceMessage = when (status) {
        InternetAvailabilityStatus.Bluetooth -> "蓝牙网络较慢\n建议换用 WiFi 或移动网络"
        else -> "请切换到 Wi-Fi 或蜂窝网络后重试"
    }
    val statusMessage = when (status) {
        InternetAvailabilityStatus.Checking -> "正在检测互联网状态…"
        InternetAvailabilityStatus.Unavailable -> "未检测到可用互联网"
        InternetAvailabilityStatus.Bluetooth -> "当前为蓝牙网络，加载可能较慢"
        InternetAvailabilityStatus.Available -> "已检测到互联网，可以继续"
    }

    InternetAvailabilityPage(
        status = status,
        guidanceMessage = guidanceMessage,
        statusMessage = statusMessage,
        actionText = "返回",
        actionEnabled = true,
        actionModifier = Modifier.padding(bottom = 8.dp),
        onAction = onBack,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}
