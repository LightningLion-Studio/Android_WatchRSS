package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lightningstudio.watchrss.ReadAloudPlaybackActivity
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.tts.ReadAloudPhase
import com.lightningstudio.watchrss.ui.theme.rememberIsRoundWatch
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val LocalFloatingControlsHosted = staticCompositionLocalOf { false }

// UI-only docking state survives navigation without holding an Activity or changing playback.
private object ReadAloudDock {
    var right by mutableStateOf(true)
    var verticalFraction by mutableFloatStateOf(0.65f)
}

internal fun readAloudDockPosition(
    width: Float, height: Float, size: Float, fraction: Float, right: Boolean, round: Boolean
): Offset {
    val margin = 4f
    val centerX = width / 2f
    val centerY = height / 2f
    val radius = (min(width, height) / 2f - size / sqrt(2f) - margin).coerceAtLeast(0f)
    val dy = ((fraction.coerceIn(0.2f, 0.8f) - 0.5f) * height).coerceIn(-radius, radius)
    val x = if (round) {
        centerX + (if (right) 1f else -1f) * sqrt((radius * radius - dy * dy).coerceAtLeast(0f)) - size / 2f
    } else if (right) width - size - margin else margin
    return Offset(
        x.coerceIn(0f, (width - size).coerceAtLeast(0f)),
        (centerY + dy - size / 2f).coerceIn(0f, (height - size).coerceAtLeast(0f))
    )
}

@Composable
fun ReadAloudFloatingControlsHost(content: @Composable () -> Unit) {
    if (LocalFloatingControlsHosted.current) {
        content()
        return
    }
    CompositionLocalProvider(LocalFloatingControlsHosted provides true) {
        Box(Modifier.fillMaxSize()) {
            content()
            ReadAloudFloatingControls()
        }
    }
}

@Composable
private fun BoxScope.ReadAloudFloatingControls() {
    val context = LocalContext.current
    val app = context.applicationContext as? WatchRssApplication ?: return
    if (context is ReadAloudPlaybackActivity) return
    val controller = app.container.readAloudController
    val state by controller.uiState.collectAsStateWithLifecycle()
    if (!state.visible || state.phase == ReadAloudPhase.IDLE) return
    var expanded by remember { mutableStateOf(false) }
    val canToggle = state.phase == ReadAloudPhase.READY || state.phase == ReadAloudPhase.ERROR
    if (expanded) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { expanded = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false, decorFitsSystemWindows = false
            )
        ) {
            val scroll = rememberScrollState()
            com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler(scroll)
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "朗读控制",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(state.currentTitle, maxLines = 2)
                Text(
                    when (state.phase) {
                        ReadAloudPhase.RESOLVING_CONTENT -> "正在读取内容"
                        ReadAloudPhase.SYNTHESIZING -> "正在合成语音"
                        ReadAloudPhase.ERROR -> "朗读失败，可重试或停止"
                        else -> if (state.isPlaying) "正在朗读" else "已暂停"
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                WatchTextButton(enabled = canToggle, onClick = controller::togglePlayPause) {
                    Text(if (state.phase == ReadAloudPhase.ERROR) "重试" else if (state.isPlaying) "暂停" else "继续")
                }
                WatchTextButton(onClick = { controller.stop(); expanded = false }) { Text("停止朗读") }
                WatchTextButton(onClick = { expanded = false }) { Text("收起，保留朗读") }
            }
        }
    }
    val round = rememberIsRoundWatch()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val touchTargetSize = 48.dp
        val visibleButtonSize = 40.dp
        val size = with(density) { touchTargetSize.toPx() }
        val docked = readAloudDockPosition(width, height, size, ReadAloudDock.verticalFraction, ReadAloudDock.right, round)
        var dragging by remember(width, height) { mutableStateOf<Offset?>(null) }
        val current = dragging ?: docked
        Box(
            Modifier
                .offset { IntOffset(current.x.roundToInt(), current.y.roundToInt()) }
                .size(touchTargetSize)
                .pointerInput(width, height, size, docked) {
                    detectDragGestures(
                        onDragStart = { dragging = docked },
                        onDragEnd = {
                            dragging?.let {
                                ReadAloudDock.right = it.x + size / 2f >= width / 2f
                                ReadAloudDock.verticalFraction = ((it.y + size / 2f) / height.coerceAtLeast(1f)).coerceIn(0.2f, 0.8f)
                            }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                        onDrag = { change, delta ->
                            change.consume()
                            val next = (dragging ?: docked) + delta
                            dragging = Offset(
                                next.x.coerceIn(0f, (width - size).coerceAtLeast(0f)),
                                next.y.coerceIn(0f, (height - size).coerceAtLeast(0f))
                            )
                        }
                    )
                }
                .clickable { expanded = true }
                .semantics {
                    role = Role.Button
                    contentDescription = "打开朗读控制"
                    stateDescription = when (state.phase) {
                        ReadAloudPhase.RESOLVING_CONTENT -> "正在读取内容"
                        ReadAloudPhase.SYNTHESIZING -> "正在合成语音"
                        ReadAloudPhase.ERROR -> "朗读失败"
                        else -> if (state.isPlaying) "正在朗读" else "已暂停"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(visibleButtonSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
