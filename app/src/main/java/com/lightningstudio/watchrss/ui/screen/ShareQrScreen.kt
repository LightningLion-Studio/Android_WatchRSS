package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.components.QrCodePanel
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import kotlin.math.roundToInt

@Composable
fun ShareQrScreen(
    title: String?,
    link: String,
    qrWidthRatio: Float = DEFAULT_SHARE_QR_WIDTH_RATIO,
    topHint: String? = null,
    onQrError: () -> Unit,
    onBack: () -> Unit
) {
    val safePadding = WatchDimens.watch_safe_padding
    val constrainedQrWidthRatio = qrWidthRatio.coerceIn(0.1f, 1f)
    val panelTitle = title?.trim().orEmpty().ifBlank { "扫码分享" }
    val panelSubtitle = topHint?.trim().orEmpty().ifBlank { link }

    WatchSurface {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                )
                .semantics { contentDescription = "二维码分享页面，点击屏幕可关闭" },
            contentAlignment = Alignment.Center
        ) {
            val qrSize = (maxWidth * constrainedQrWidthRatio).coerceAtLeast(1.dp)
            val sizePx = with(LocalDensity.current) {
                qrSize.toPx().roundToInt().coerceAtLeast(1)
            }
            val bitmap = remember(link, sizePx) { QrCodeGenerator.create(link, sizePx) }

            if (bitmap == null) {
                LaunchedEffect(link, sizePx) {
                    onQrError()
                }
            } else {
                QrCodePanel(
                    qrBitmap = bitmap,
                    qrSizeDp = qrSize,
                    qrContentDescription = "分享链接的二维码",
                    title = panelTitle,
                    subtitle = panelSubtitle,
                    titleContentDescription = "标题：$panelTitle",
                    subtitleContentDescription = "分享说明：$panelSubtitle",
                    modifier = Modifier.padding(horizontal = safePadding, vertical = safePadding)
                )
            }
        }
    }
}

const val DEFAULT_SHARE_QR_WIDTH_RATIO = 0.88f
