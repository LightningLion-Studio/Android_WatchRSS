package com.lightningstudio.watchrss.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 通用二维码面板，统一布局与无障碍语义。
 *
 * 布局结构：
 *   [title]        ← 标题，Heading 语义
 *   8 dp
 *   [subtitle]     ← 副标题，超长时跑马灯展示
 *   16 dp
 *   二维码图片      ← Role.Image
 *
 * @param qrBitmap                二维码 Bitmap，为 null 时不渲染图片区域
 * @param qrSizeDp                二维码图片尺寸
 * @param qrContentDescription    二维码图片无障碍描述
 * @param modifier                作用于外层 Column（可传入 testTag 等）
 * @param title                   标题，渲染为 Heading
 * @param titleContentDescription 标题无障碍描述（null 时读取 title 文本本身）
 * @param subtitle                副标题，超长时自动跑马灯
 * @param subtitleContentDescription 副标题无障碍描述（null 时读取 subtitle 文本本身）
 * @param qrTestTag               可选测试标签，应用于二维码图片
 */
@Composable
fun QrCodePanel(
    qrBitmap: Bitmap?,
    qrSizeDp: Dp,
    qrContentDescription: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleContentDescription: String? = null,
    subtitleContentDescription: String? = null,
    qrTestTag: String? = null,
) {
    var qrImageEntered by remember(qrBitmap) { mutableStateOf(false) }
    LaunchedEffect(qrBitmap) {
        if (qrBitmap == null) {
            qrImageEntered = false
            return@LaunchedEffect
        }
        qrImageEntered = false
        withFrameNanos { }
        qrImageEntered = true
    }
    val qrImageTransition = updateTransition(targetState = qrImageEntered, label = "QrCodePanelImageEntry")
    val qrImageAlpha by qrImageTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 980, easing = LinearOutSlowInEasing) },
        label = "QrCodePanelImageAlpha"
    ) { entered -> if (entered) 1f else 0.12f }
    val qrImageScale by qrImageTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 1500, easing = FastOutSlowInEasing) },
        label = "QrCodePanelImageScale"
    ) { entered -> if (entered) 1f else 1.16f }
    val qrImageBlurDp by qrImageTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 1650, easing = FastOutSlowInEasing) },
        label = "QrCodePanelImageBlur"
    ) { entered -> if (entered) 0f else 18f }
    val qrImageBrightnessOverlay by qrImageTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 1350, easing = FastOutSlowInEasing) },
        label = "QrCodePanelImageBrightnessOverlay"
    ) { entered -> if (entered) 0f else 0.24f }
    val qrImageBrightnessBias by qrImageTransition.animateFloat(
        transitionSpec = { tween(durationMillis = 1400, easing = FastOutSlowInEasing) },
        label = "QrCodePanelImageBrightnessBias"
    ) { entered -> if (entered) 0f else 58f }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE)
                    .semantics {
                        heading()
                        val desc = titleContentDescription
                        if (desc != null) contentDescription = desc
                    }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE)
                    .semantics {
                        val desc = subtitleContentDescription
                        if (desc != null) contentDescription = desc
                    }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        qrBitmap?.let { bitmap ->
            val brightnessMatrix = remember(qrImageBrightnessBias) {
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, qrImageBrightnessBias,
                        0f, 1f, 0f, 0f, qrImageBrightnessBias,
                        0f, 0f, 1f, 0f, qrImageBrightnessBias,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            val imageModifier = if (qrTestTag != null) {
                Modifier
                    .size(qrSizeDp)
                    .graphicsLayer {
                        alpha = qrImageAlpha
                        scaleX = qrImageScale
                        scaleY = qrImageScale
                    }
                    .then(if (qrImageBlurDp > 0f) Modifier.blur(qrImageBlurDp.dp) else Modifier)
                    .drawWithContent {
                        drawContent()
                        if (qrImageBrightnessOverlay > 0f) {
                            drawRect(
                                color = Color.White.copy(alpha = qrImageBrightnessOverlay),
                                blendMode = BlendMode.Screen
                            )
                        }
                    }
                    .testTag(qrTestTag)
            } else {
                Modifier
                    .size(qrSizeDp)
                    .graphicsLayer {
                        alpha = qrImageAlpha
                        scaleX = qrImageScale
                        scaleY = qrImageScale
                    }
                    .then(if (qrImageBlurDp > 0f) Modifier.blur(qrImageBlurDp.dp) else Modifier)
                    .drawWithContent {
                        drawContent()
                        if (qrImageBrightnessOverlay > 0f) {
                            drawRect(
                                color = Color.White.copy(alpha = qrImageBrightnessOverlay),
                                blendMode = BlendMode.Screen
                            )
                        }
                    }
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = qrContentDescription,
                colorFilter = ColorFilter.colorMatrix(brightnessMatrix),
                modifier = imageModifier.semantics {
                    role = Role.Image
                    contentDescription = qrContentDescription
                }
            )
        }
    }
}
