package com.lightningstudio.watchrss.ui.screen.launch

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.BrandOrange
import com.lightningstudio.watchrss.ui.theme.BrandOrangeLight
import com.lightningstudio.watchrss.ui.theme.WatchBackground
import com.lightningstudio.watchrss.ui.theme.WatchBackgroundDeep
import com.lightningstudio.watchrss.ui.theme.WatchTextPrimary
import com.lightningstudio.watchrss.ui.theme.WatchTextSecondary
import com.lightningstudio.watchrss.ui.theme.rememberIsRoundWatch
import kotlinx.coroutines.delay

@Composable
fun LaunchScreenOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var keepMounted by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            keepMounted = true
        } else {
            delay(LAUNCH_EXIT_DURATION_MS.toLong())
            keepMounted = false
        }
    }

    if (!keepMounted) return

    // ---- 首帧优化：第一帧只渲染纯黑背景，跳过所有 blur / 渐变 / Canvas 开销 ----
    var firstFrameReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(16L) // 让 Compose 先提交一帧纯黑，下一帧再挂载重效果
        firstFrameReady = true
    }

    if (!firstFrameReady) {
        // 首帧：极简纯黑占位，零 GPU overdraw
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
        return
    }

    // ---- 第二帧起：恢复完整视觉 ----
    LaunchScreenContent(visible = visible, modifier = modifier)
}

@Composable
private fun LaunchScreenContent(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val isRoundWatch = rememberIsRoundWatch()
    val infiniteTransition = rememberInfiniteTransition(label = "launch_screen_idle")
    val idleDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_drift"
    )
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )
    val idleBrightness by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_brightness"
    )
    val idleOpacity by infiniteTransition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_opacity"
    )

    val exitTransition = updateTransition(targetState = visible, label = "launch_screen_exit")
    val overlayAlpha by exitTransition.animateFloat(
        transitionSpec = { tween(durationMillis = LAUNCH_EXIT_DURATION_MS, easing = FastOutSlowInEasing) },
        label = "overlay_alpha"
    ) { shown -> if (shown) 1f else 0f }
    val overlayScale by exitTransition.animateFloat(
        transitionSpec = { tween(durationMillis = LAUNCH_EXIT_DURATION_MS, easing = FastOutSlowInEasing) },
        label = "overlay_scale"
    ) { shown -> if (shown) 1f else 1.08f }
    val overlayBlurBoost by exitTransition.animateFloat(
        transitionSpec = { tween(durationMillis = LAUNCH_EXIT_DURATION_MS, easing = FastOutSlowInEasing) },
        label = "overlay_blur_boost"
    ) { shown -> if (shown) 1f else 1.55f }
    val overlayGlow by exitTransition.animateFloat(
        transitionSpec = { tween(durationMillis = LAUNCH_EXIT_DURATION_MS, easing = FastOutSlowInEasing) },
        label = "overlay_glow"
    ) { shown -> if (shown) 0.72f else 0.18f }

    val contentPadding = if (isRoundWatch) 28.dp else 22.dp
    val heroSize = if (isRoundWatch) 156.dp else 148.dp
    val iconSize = if (isRoundWatch) 94.dp else 88.dp
    val ringSize = if (isRoundWatch) 116.dp else 108.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha
                scaleX = overlayScale
                scaleY = overlayScale
            }
            .background(Color.Black)
    ) {
        LaunchScreenBackdrop(
            drift = idleDrift,
            pulse = idlePulse,
            opacity = idleOpacity,
            blurBoost = overlayBlurBoost,
            glow = overlayGlow,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = contentPadding)
                .widthIn(max = 220.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(heroSize),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = 0.45f * idleOpacity * overlayAlpha
                            scaleX = 0.88f + idlePulse * 0.18f
                            scaleY = 0.88f + idlePulse * 0.18f
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    BrandOrangeLight.copy(alpha = 0.34f * overlayGlow),
                                    Color(0x334EE1B7),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(ringSize)
                        .graphicsLayer {
                            alpha = 0.9f * overlayAlpha
                            scaleX = 0.96f + idlePulse * 0.05f
                            scaleY = 0.96f + idlePulse * 0.05f
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.13f * idleOpacity),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                RingAccent(
                    progress = idleDrift,
                    modifier = Modifier.size(ringSize)
                )
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            alpha = idleOpacity * overlayAlpha
                            scaleX = idlePulse
                            scaleY = idlePulse
                        }
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF17262A),
                                    Color(0xFF0A1114),
                                    Color(0xFF05080A)
                                ),
                                start = Offset.Zero,
                                end = Offset(180f, 180f)
                            )
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.launch_screen_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.colorMatrix(brightnessMatrix(idleBrightness)),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.16f * idleOpacity),
                                        Color.Transparent,
                                        Color(0x66000000)
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = WatchTextPrimary.copy(alpha = overlayAlpha)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.launch_screen_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = WatchTextSecondary.copy(alpha = 0.88f * idleOpacity * overlayAlpha)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LaunchScreenBackdrop(
    drift: Float,
    pulse: Float,
    opacity: Float,
    blurBoost: Float,
    glow: Float,
    modifier: Modifier = Modifier
) {
    val blurRadius = (16f * blurBoost).dp
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF13201E),
                    WatchBackground,
                    WatchBackgroundDeep,
                    Color(0xFF030506)
                ),
                start = Offset(40f, 0f),
                end = Offset(320f, 420f)
            )
        )
    ) {
        LaunchGlowBlob(
            size = 220.dp,
            offsetX = (-82f + 30f * drift).dp,
            offsetY = (-70f - 18f * drift).dp,
            blur = blurRadius,
            scale = 0.94f + pulse * 0.16f,
            alpha = 0.26f * opacity,
            colors = listOf(
                Color(0xFF9FF7DD),
                Color(0x5575D7BF),
                Color.Transparent
            )
        )
        LaunchGlowBlob(
            size = 246.dp,
            offsetX = (118f - 26f * drift).dp,
            offsetY = (-34f + 24f * drift).dp,
            blur = (20f * blurBoost).dp,
            scale = 0.92f + pulse * 0.14f,
            alpha = 0.2f * opacity,
            colors = listOf(
                BrandOrangeLight.copy(alpha = 0.95f),
                BrandOrange.copy(alpha = 0.45f),
                Color.Transparent
            )
        )
        LaunchGlowBlob(
            size = 268.dp,
            offsetX = (8f + 20f * drift).dp,
            offsetY = (172f - 20f * drift).dp,
            blur = (24f * blurBoost).dp,
            scale = 0.9f + pulse * 0.18f,
            alpha = 0.17f * opacity,
            colors = listOf(
                Color(0xFF3AB8A0),
                Color(0x66308A7E),
                Color.Transparent
            )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-58).dp)
                .size(286.dp)
                .graphicsLayer { alpha = 0.22f * glow }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BrandOrangeLight.copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
                .blur((18f * blurBoost).dp)
        )
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.15f * opacity),
                        Color.Transparent,
                        BrandOrangeLight.copy(alpha = 0.12f * glow),
                        Color.Transparent
                    )
                ),
                startAngle = -110f + 36f * drift,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = size.minDimension * 0.008f, cap = StrokeCap.Round),
                topLeft = Offset(size.width * 0.11f, size.height * 0.11f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.78f, size.height * 0.78f)
            )
        }
    }
}

@Composable
private fun RingAccent(
    progress: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.1f),
                    Color.Transparent
                )
            ),
            radius = size.minDimension * 0.49f,
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Transparent,
                    BrandOrangeLight.copy(alpha = 0.9f),
                    Color.White.copy(alpha = 0.75f),
                    Color.Transparent
                )
            ),
            startAngle = -98f + 110f * progress,
            sweepAngle = 84f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun LaunchGlowBlob(
    size: Dp,
    offsetX: Dp,
    offsetY: Dp,
    blur: Dp,
    scale: Float,
    alpha: Float,
    colors: List<Color>
) {
    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .background(
                brush = Brush.radialGradient(colors = colors),
                shape = CircleShape
            )
            .blur(blur)
    )
}

private fun brightnessMatrix(brightness: Float): ColorMatrix {
    return ColorMatrix(
        floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private const val LAUNCH_EXIT_DURATION_MS = 780
