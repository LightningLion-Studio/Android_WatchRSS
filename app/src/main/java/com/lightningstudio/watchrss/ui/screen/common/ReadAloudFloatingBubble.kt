package com.lightningstudio.watchrss.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightningstudio.watchrss.data.tts.ReadAloudPhase
import com.lightningstudio.watchrss.data.tts.ReadAloudUiState
import kotlin.math.roundToInt

enum class ReadAloudBubbleDock {
    LEFT,
    TOP,
    RIGHT,
    BOTTOM
}

@Composable
fun ReadAloudFloatingBubbleOverlay(
    state: ReadAloudUiState,
    defaultDock: ReadAloudBubbleDock,
    onClick: () -> Unit
) {
    if (!state.visible) return
    val bubbleSize = 52.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val backgroundColor = when (state.phase) {
            ReadAloudPhase.IDLE -> Color(0xFF525252)
            ReadAloudPhase.RESOLVING_CONTENT -> Color(0xFF3F51B5)
            ReadAloudPhase.SYNTHESIZING -> Color(0xFF00897B)
            ReadAloudPhase.READY -> MaterialTheme.colorScheme.primary
            ReadAloudPhase.ERROR -> Color(0xFFC62828)
        }
        val bubblePx = with(androidx.compose.ui.platform.LocalDensity.current) { bubbleSize.toPx() }
        val maxX = (constraints.maxWidth - bubblePx).coerceAtLeast(0f)
        val maxY = (constraints.maxHeight - bubblePx).coerceAtLeast(0f)
        var offsetX by remember(defaultDock, constraints.maxWidth, constraints.maxHeight) {
            mutableFloatStateOf(Float.NaN)
        }
        var offsetY by remember(defaultDock, constraints.maxWidth, constraints.maxHeight) {
            mutableFloatStateOf(Float.NaN)
        }

        if (offsetX.isNaN() || offsetY.isNaN()) {
            val defaultOffset = defaultDock.defaultOffset(maxX, maxY)
            offsetX = defaultOffset.x
            offsetY = defaultOffset.y
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(bubbleSize)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .pointerInput(defaultDock, maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            val snapped = snapToEdge(
                                x = offsetX,
                                y = offsetY,
                                maxX = maxX,
                                maxY = maxY
                            )
                            offsetX = snapped.x
                            offsetY = snapped.y
                        },
                        onDragCancel = {
                            val snapped = snapToEdge(offsetX, offsetY, maxX, maxY)
                            offsetX = snapped.x
                            offsetY = snapped.y
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxY)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = "朗读中",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            if (state.queueSize > 0) {
                Text(
                    text = "${state.queueIndex.coerceAtLeast(1)}",
                    color = Color.White,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-4).dp)
                )
            }
        }
    }
}

private fun ReadAloudBubbleDock.defaultOffset(maxX: Float, maxY: Float): Offset {
    return when (this) {
        ReadAloudBubbleDock.LEFT -> Offset(0f, maxY / 2f)
        ReadAloudBubbleDock.TOP -> Offset(maxX / 2f, 0f)
        ReadAloudBubbleDock.RIGHT -> Offset(maxX, maxY / 2f)
        ReadAloudBubbleDock.BOTTOM -> Offset(maxX / 2f, maxY)
    }
}

private fun snapToEdge(x: Float, y: Float, maxX: Float, maxY: Float): Offset {
    val candidates = listOf(
        Offset(0f, y.coerceIn(0f, maxY)),
        Offset(maxX, y.coerceIn(0f, maxY)),
        Offset(x.coerceIn(0f, maxX), 0f),
        Offset(x.coerceIn(0f, maxX), maxY)
    )
    return candidates.minByOrNull { candidate ->
        val dx = candidate.x - x
        val dy = candidate.y - y
        dx * dx + dy * dy
    } ?: Offset(x, y)
}
