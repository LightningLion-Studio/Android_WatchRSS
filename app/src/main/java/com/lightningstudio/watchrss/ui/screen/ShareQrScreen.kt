package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import kotlin.math.roundToInt

@Composable
fun ShareQrScreen(
    link: String,
    qrWidthRatio: Float = DEFAULT_SHARE_QR_WIDTH_RATIO,
    topHint: String? = null,
    onQrError: () -> Unit,
    onBack: () -> Unit
) {
    val safePadding = WatchDimens.watch_safe_padding
    val constrainedQrWidthRatio = qrWidthRatio.coerceIn(0.1f, 1f)
    val hintStyle = MaterialTheme.typography.bodySmall
    val density = LocalDensity.current
    val hintSpacing = 12.dp
    val hintLineHeight = remember(hintStyle, density) {
        with(density) {
            when {
                hintStyle.lineHeight != TextUnit.Unspecified -> hintStyle.lineHeight.toDp()
                hintStyle.fontSize != TextUnit.Unspecified -> (hintStyle.fontSize * 1.2f).toDp()
                else -> 20.dp
            }
        }
    }
    val hintReservedHeight = if (topHint.isNullOrBlank()) 0.dp else 72.dp + hintLineHeight + hintLineHeight

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
            val safeWidth = (maxWidth).coerceAtLeast(1.dp)
            val safeHeight = (maxHeight - hintReservedHeight - hintSpacing)
                .coerceAtLeast(1.dp)
            val qrSize = (maxWidth * constrainedQrWidthRatio)
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = safePadding, vertical = safePadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "二维码",
                            modifier = Modifier.size(qrSize)
                        )
                    }
                    if (!topHint.isNullOrBlank()) {
                        Box(modifier = Modifier.height(hintSpacing))
                        Text(
                            text = topHint,
                            style = hintStyle,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(hintLineHeight))
                    }
                }
            }
        }
    }
}

const val DEFAULT_SHARE_QR_WIDTH_RATIO = 0.88f
