package com.lightningstudio.watchrss.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BlurFadeVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    blurTargetDp: Float = 10f,
    durationMillis: Int = 240,
    content: @Composable BoxScope.() -> Unit
) {
    val overlayAlpha = remember { Animatable(if (visible) 1f else 0f) }
    val overlayBlur = remember { Animatable(0f) }
    var keepMounted by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            keepMounted = true
            overlayBlur.snapTo(0f)
            overlayAlpha.snapTo(1f)
        } else if (keepMounted) {
            val fadeOut = launch {
                overlayAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = durationMillis)
                )
            }
            val blurOut = launch {
                overlayBlur.animateTo(
                    targetValue = blurTargetDp,
                    animationSpec = tween(durationMillis = durationMillis)
                )
            }
            fadeOut.join()
            blurOut.join()
            keepMounted = false
            overlayBlur.snapTo(0f)
        }
    }

    if (!keepMounted) {
        return
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = overlayAlpha.value }
            .then(if (overlayBlur.value > 0.1f) Modifier.blur(overlayBlur.value.dp) else Modifier),
        contentAlignment = contentAlignment,
        content = content
    )
}
