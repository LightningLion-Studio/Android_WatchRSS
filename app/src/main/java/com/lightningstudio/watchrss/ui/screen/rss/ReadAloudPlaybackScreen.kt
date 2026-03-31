package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.tts.ReadAloudPhase
import com.lightningstudio.watchrss.data.tts.ReadAloudUiState
import com.lightningstudio.watchrss.ui.components.WatchButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import java.util.Locale

@Composable
fun ReadAloudPlaybackScreen(
    state: ReadAloudUiState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onOpenCurrentArticle: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    InstallDigitalCrownScrollHandler(scrollState)
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val actionColor = MaterialTheme.colorScheme.surfaceVariant

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
        ) {
            SettingsHeader(title = "朗读播控")

            Spacer(modifier = Modifier.height(WatchDimens.hey_content_horizontal_distance))

            Text(
                text = state.currentTitle.ifBlank { "尚未开始朗读" },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.currentChannelTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(WatchDimens.hey_distance_4dp))
                Text(
                    text = state.currentChannelTitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(WatchDimens.hey_distance_8dp))

            Text(
                text = buildPlaybackStatusText(state),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (!state.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(WatchDimens.hey_distance_8dp))
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(WatchDimens.hey_distance_10dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                WatchButton(
                    onClick = onPrevious,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                ) {
                    Text(text = "上一", color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(10.dp))
                WatchButton(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                ) {
                    Text(
                        text = if (state.isPlaying) "暂停" else "播放",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                WatchButton(
                    onClick = onNext,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                ) {
                    Text(text = "下一", color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(WatchDimens.hey_distance_8dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                WatchButton(
                    onClick = onStop,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                ) {
                    Text(text = "停止", color = MaterialTheme.colorScheme.onSurface)
                }
                if (onOpenCurrentArticle != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    WatchButton(
                        onClick = onOpenCurrentArticle,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                    ) {
                        Text(text = "文章", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

private fun buildPlaybackStatusText(state: ReadAloudUiState): String {
    val phaseText = when (state.phase) {
        ReadAloudPhase.IDLE -> "待机"
        ReadAloudPhase.RESOLVING_CONTENT -> "正在离屏解析原文"
        ReadAloudPhase.SYNTHESIZING -> "正在合成语音"
        ReadAloudPhase.READY -> if (state.isPlaying) "播放中" else "已暂停"
        ReadAloudPhase.ERROR -> "播放异常"
    }
    val queueText = if (state.queueSize > 0) "第 ${state.queueIndex}/${state.queueSize} 篇" else ""
    val progressText = if (state.durationMs > 0L) {
        " ${formatDuration(state.progressMs)} / ${formatDuration(state.durationMs)}"
    } else {
        ""
    }
    return listOf(phaseText, queueText).filter { it.isNotBlank() }.joinToString(" · ") + progressText
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
