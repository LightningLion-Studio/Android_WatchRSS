package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.util.QrCodeGenerator
import kotlin.math.roundToInt

@Composable
fun ShareQrScreen(
    link: String,
    onQrError: () -> Unit,
    onBack: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)

    WatchSurface {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                )
                .padding(safePadding)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val maxSize = if (maxWidth < maxHeight) maxWidth else maxHeight
                val sizePx = with(LocalDensity.current) {
                    maxSize.toPx().roundToInt().coerceAtLeast(1)
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
                        modifier = Modifier.size(maxSize)
                    )
                }
            }
        }
    }
}
