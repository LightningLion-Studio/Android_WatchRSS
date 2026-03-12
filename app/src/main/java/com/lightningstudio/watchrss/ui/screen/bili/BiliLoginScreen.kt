package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.sdk.bili.QrPollStatus
import com.lightningstudio.watchrss.ui.components.PullRefreshBox
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import com.lightningstudio.watchrss.ui.viewmodel.BiliLoginUiState
import kotlin.math.roundToInt

@Composable
fun BiliLoginScreen(
    uiState: BiliLoginUiState,
    onRefreshQr: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val spacing = dimensionResource(R.dimen.hey_distance_6dp)
    val qrSize = 200.dp
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onLoginSuccess()
        }
    }

    PullRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefreshQr,
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
        indicatorPadding = safePadding,
        isAtTop = { scrollState.value == 0 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "扫码登录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(spacing))
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
            Spacer(modifier = Modifier.height(spacing))
            Text(
                text = statusText(uiState.status),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing))
            Text(
                text = "下拉刷新二维码",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
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
