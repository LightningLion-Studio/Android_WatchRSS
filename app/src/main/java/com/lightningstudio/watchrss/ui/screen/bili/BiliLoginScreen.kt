package com.lightningstudio.watchrss.ui.screen.bili

import com.lightningstudio.watchrss.ui.components.WatchTextButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.sdk.bili.QrPollStatus
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.rememberPullRefreshEnabled
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchQrSizeFor
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import com.lightningstudio.watchrss.ui.viewmodel.BiliLoginUiState
import kotlin.math.roundToInt

@Composable
fun BiliLoginScreen(
    uiState: BiliLoginUiState,
    onRefreshQr: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var showScanHelp by remember { mutableStateOf(false) }
    val view = LocalView.current
    val originalKeepScreenOn = remember(view) { view.keepScreenOn }
    val shouldKeepScreenOn = !uiState.qrUrl.isNullOrBlank() &&
        uiState.status in setOf(QrPollStatus.PENDING, QrPollStatus.SCANNED) &&
        !uiState.isSuccess
    DisposableEffect(view, originalKeepScreenOn) {
        onDispose { view.keepScreenOn = originalKeepScreenOn }
    }
    SideEffect { view.keepScreenOn = originalKeepScreenOn || shouldKeepScreenOn }
    if (showScanHelp) {
        AlertDialog(
            onDismissRequest = { showScanHelp = false },
            title = { Text("如何扫码登录") },
            text = { Text("打开手机上的哔哩哔哩客户端，在首页右上角找到扫一扫，对准手表二维码，再在手机上确认登录。\n\n若找不到扫一扫，可使用客户端内搜索查找扫码入口。二维码过期时返回本页下拉刷新；请勿使用微信扫码。") },
            confirmButton = { WatchTextButton(onClick = { showScanHelp = false }) { Text("知道了") } }
        )
    }
    val safePadding = WatchDimens.watch_safe_padding
    val spacing = watchDimensionResource(R.dimen.hey_distance_6dp)
    val topPadding = 4.dp
    val titleSpacing = spacing / 4
    val scrollState = rememberScrollState()
    InstallDigitalCrownScrollHandler(scrollState)
    val canRefresh = rememberPullRefreshEnabled(scrollState)

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onLoginSuccess()
        }
    }

    WatchSurface {
        PullRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefreshQr,
            modifier = Modifier.fillMaxSize(),
            indicatorPadding = safePadding,
            canRefresh = canRefresh
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val qrSize = watchQrSizeFor(
                    availableWidth = (maxWidth - safePadding * 2).coerceAtLeast(0.dp),
                    availableHeight = maxHeight,
                    preferredSize = 180.dp
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = safePadding, top = topPadding, end = safePadding, bottom = safePadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(titleSpacing, Alignment.Top)
                ) {
                    Text(
                        text = "使用 B站手机客户端扫码",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) {
                        qrSize.toPx().roundToInt().coerceAtLeast(1)
                    }
                    val bitmap = remember(uiState.qrUrl, sizePx) {
                        val url = uiState.qrUrl ?: return@remember null
                        QrCodeGenerator.create(url, sizePx)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "登录二维码",
                            modifier = Modifier.size(qrSize)
                        )
                    } else {
                        Text(
                            text = if (uiState.isLoading) "二维码加载中..." else "暂无二维码",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(titleSpacing))
                    Text(
                        text = statusText(uiState.status),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                    if (
                        uiState.status == QrPollStatus.EXPIRED ||
                        uiState.status == QrPollStatus.ERROR
                    ) {
                        WatchTextButton(
                            onClick = onRefreshQr,
                            enabled = !uiState.isLoading
                        ) {
                            Text("刷新二维码")
                        }
                    }
                    WatchTextButton(onClick = { showScanHelp = true }) { Text("扫码帮助？") }
                    Spacer(modifier = Modifier.height(spacing))
                    Text(
                        text = "下拉刷新二维码",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun statusText(status: QrPollStatus): String {
    return when (status) {
        QrPollStatus.PENDING -> "等待扫码"
        QrPollStatus.SCANNED -> "已扫码，请在手机确认"
        QrPollStatus.EXPIRED -> "二维码已过期"
        QrPollStatus.SUCCESS -> "登录成功"
        QrPollStatus.ERROR -> "登录失败"
    }
}
