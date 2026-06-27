package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.tts.ReadAloudPhase
import com.lightningstudio.watchrss.data.tts.ReadAloudUiState
import com.lightningstudio.watchrss.ui.components.PlayerVolumeOverlay
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.rememberPlayerVolumeState
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownVolumeHandler
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReadAloudPlaybackScreen(
    state: ReadAloudUiState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onDecreaseSpeechRate: () -> Unit,
    onIncreaseSpeechRate: () -> Unit,
    digitalCrownVolumeEnabled: Boolean = true,
    volumeGuardEnabled: Boolean = true,
    currentArticleActionText: String = "查看当前文章",
    onOpenCurrentArticle: (() -> Unit)? = null
) {
    WatchSurface(pureBlack = true) {
        val effectiveVolumeGuardEnabled = volumeGuardEnabled && digitalCrownVolumeEnabled
        val volumeState = rememberPlayerVolumeState(
            guardEnabled = effectiveVolumeGuardEnabled,
            playbackStartVolumeLimitPercent = null
        )

        InstallDigitalCrownVolumeHandler(
            enabled = digitalCrownVolumeEnabled,
            showSystemUi = false,
            reverseDirection = true,
            supportsDigitalCrown = true,
            onVolumeAdjust = volumeState::adjustByDelta
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .semantics {
                    contentDescription = "朗读播控页面"
                    stateDescription = buildPlaybackStatusText(state)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "朗读播控",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )

                ReadAloudControlPanel(
                    state = state,
                    onTogglePlayPause = onTogglePlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onStop = onStop,
                    onToggleAutoAdvance = onToggleAutoAdvance,
                    onDecreaseSpeechRate = onDecreaseSpeechRate,
                    onIncreaseSpeechRate = onIncreaseSpeechRate
                )

                if (onOpenCurrentArticle != null) {
                    ReadAloudTextAction(
                        text = currentArticleActionText,
                        onClick = onOpenCurrentArticle
                    )
                }
            }

            PlayerVolumeOverlay(
                state = volumeState,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
internal fun ReadAloudControlPanel(
    state: ReadAloudUiState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onDecreaseSpeechRate: () -> Unit,
    onIncreaseSpeechRate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "朗读控制面板"
                stateDescription = buildPlaybackStatusText(state)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 9.dp)
            ) {
                Text(
                    text = state.currentTitle.ifBlank { "尚未开始朗读" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildPlaybackStatusText(state),
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(9.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReadAloudRoundIconButton(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = "上一篇",
                size = 42.dp,
                onClick = onPrevious
            )
            ReadAloudRoundIconButton(
                icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "暂停朗读" else "继续朗读",
                size = 58.dp,
                selected = true,
                onClick = onTogglePlayPause
            )
            ReadAloudRoundIconButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = "下一篇",
                size = 42.dp,
                onClick = onNext
            )
            ReadAloudRoundIconButton(
                icon = Icons.Filled.Stop,
                contentDescription = "停止朗读",
                size = 42.dp,
                onClick = onStop
            )
        }

        Spacer(modifier = Modifier.height(9.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReadAloudAutoAdvanceRow(
                state = state,
                onToggleAutoAdvance = onToggleAutoAdvance,
                modifier = Modifier.weight(1f)
            )
            ReadAloudSpeedRow(
                speechRate = state.speechRate,
                onDecreaseSpeechRate = onDecreaseSpeechRate,
                onIncreaseSpeechRate = onIncreaseSpeechRate,
                modifier = Modifier.width(READ_ALOUD_SPEED_CONTROL_WIDTH)
            )
        }
    }
}

@Composable
private fun ReadAloudAutoAdvanceRow(
    state: ReadAloudUiState,
    onToggleAutoAdvance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onToggleAutoAdvance)
            .semantics {
                contentDescription = if (state.autoAdvanceEnabled) {
                    "自动下一篇已开启"
                } else {
                    "自动下一篇已关闭"
                }
                role = Role.Button
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            val autoAdvanceContentColor = if (state.autoAdvanceEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                READ_ALOUD_DISABLED_CONTROL_COLOR
            }
            Icon(
                imageVector = ReadAloudAutoNextIcon,
                contentDescription = null,
                tint = autoAdvanceContentColor,
                modifier = Modifier.size(24.dp)
            )
            if (!state.autoAdvanceEnabled) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    drawLine(
                        color = autoAdvanceContentColor,
                        start = Offset(size.width * 0.18f, size.height * 0.86f),
                        end = Offset(size.width * 0.86f, size.height * 0.14f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadAloudSpeedRow(
    speechRate: Float,
    onDecreaseSpeechRate: () -> Unit,
    onIncreaseSpeechRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .semantics {
                contentDescription = "播放速度 ${formatSpeechRate(speechRate)}"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReadAloudRoundIconButton(
            icon = Icons.Filled.Remove,
            contentDescription = "减慢朗读速度",
            size = 32.dp,
            onClick = onDecreaseSpeechRate
        )
        Text(
            text = formatSpeechRate(speechRate),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(READ_ALOUD_SPEED_VALUE_WIDTH)
        )
        ReadAloudRoundIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "加快朗读速度",
            size = 32.dp,
            onClick = onIncreaseSpeechRate
        )
    }
}

@Composable
private fun ReadAloudRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
private fun ReadAloudTextAction(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = text
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun buildPlaybackStatusText(state: ReadAloudUiState): String {
    val phaseText = when (state.phase) {
        ReadAloudPhase.IDLE -> "待机"
        ReadAloudPhase.RESOLVING_CONTENT -> "正在离屏解析原文"
        ReadAloudPhase.SYNTHESIZING -> "正在准备本地朗读"
        ReadAloudPhase.READY -> if (state.isPlaying) "播放中" else "已暂停"
        ReadAloudPhase.ERROR -> "播放异常"
    }
    val queueText = if (state.queueSize > 0) "第 ${state.queueIndex}/${state.queueSize} 篇" else ""
    val segmentText = when {
        state.segmentCount > 0 && state.segmentIndex > 0 -> "第 ${state.segmentIndex}/${state.segmentCount} 段"
        state.segmentIndex > 0 -> "第 ${state.segmentIndex} 段"
        else -> ""
    }
    val progressText = if (state.durationMs > 0L) {
        " ${formatDuration(state.progressMs)} / ${formatDuration(state.durationMs)}"
    } else {
        ""
    }
    return listOf(phaseText, queueText, segmentText)
        .filter { it.isNotBlank() }
        .joinToString(" · ") + progressText
}

private fun formatSpeechRate(rate: Float): String {
    val percent = (rate * 100f).roundToInt()
    return when {
        percent % 100 == 0 -> "${percent / 100}x"
        percent % 10 == 0 -> String.format(Locale.US, "%.1fx", percent / 100f)
        else -> String.format(Locale.US, "%.2fx", percent / 100f)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private val READ_ALOUD_SPEED_CONTROL_WIDTH = 106.dp
private val READ_ALOUD_SPEED_VALUE_WIDTH = 42.dp
private val READ_ALOUD_DISABLED_CONTROL_COLOR = Color(0xFFBDBDBD)
private val ReadAloudAutoNextIcon: ImageVector = ImageVector.Builder(
    name = "ReadAloudAutoNext",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 1024f,
    viewportHeight = 1024f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(768f, 401.066667f)
        lineTo(234.666667f, 68.266667f)
        curveTo(149.333333f, 21.333333f, 42.666667f, 81.066667f, 42.666667f, 179.2f)
        verticalLineTo(844.8f)
        curveTo(42.666667f, 942.933333f, 149.333333f, 1006.933333f, 234.666667f, 955.733333f)
        lineTo(768f, 622.933333f)
        curveTo(853.333333f, 571.733333f, 853.333333f, 452.266667f, 768f, 401.066667f)
        close()
        moveTo(725.333333f, 550.4f)
        lineTo(192f, 883.2f)
        curveTo(162.133333f, 896f, 128f, 878.933333f, 128f, 844.8f)
        verticalLineTo(179.2f)
        curveTo(128f, 145.066667f, 162.133333f, 128f, 192f, 140.8f)
        lineTo(725.333333f, 473.6f)
        curveTo(755.2f, 490.666667f, 755.2f, 533.333333f, 725.333333f, 550.4f)
        close()
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(853.333333f, 128f)
        curveTo(853.333333f, 102.4f, 870.4f, 85.333333f, 896f, 85.333333f)
        curveTo(921.6f, 85.333333f, 938.666667f, 102.4f, 938.666667f, 128f)
        verticalLineTo(896f)
        curveTo(938.666667f, 921.6f, 921.6f, 938.666667f, 896f, 938.666667f)
        curveTo(870.4f, 938.666667f, 853.333333f, 921.6f, 853.333333f, 896f)
        verticalLineTo(128f)
        close()
    }
}.build()
