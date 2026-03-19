package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import kotlin.math.roundToInt

@Composable
fun ShareQrScreen(
    link: String,
    onQrError: () -> Unit,
    onBack: () -> Unit
) {
    val safePadding = WatchDimens.watch_safe_padding
    val qrWidthRatio = 0.7f

    WatchSurface {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            val safeWidth = (maxWidth - safePadding * 2).coerceAtLeast(1.dp)
            val safeHeight = (maxHeight - safePadding * 2).coerceAtLeast(1.dp)
            val qrSize = (maxWidth * qrWidthRatio)
                .coerceAtMost(safeWidth)
                .coerceAtMost(safeHeight)
                .coerceAtLeast(1.dp)
            val sizePx = with(LocalDensity.current) {
                qrSize.toPx().roundToInt().coerceAtLeast(1)
            }
            val bitmap = remember(link, sizePx) { QrCodeGenerator.create(link, sizePx) }

            if (bitmap == null) {
                LaunchedEffect(link, sizePx) {
                    onQrError()
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "二维码",
                    modifier = Modifier.size(qrSize)
                )
            }
        }
    }
}
