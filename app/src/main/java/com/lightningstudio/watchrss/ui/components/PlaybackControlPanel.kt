package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import kotlinx.coroutines.delay

internal fun playbackSeekTarget(current: Long, delta: Long, duration: Long): Long? {
    if (duration <= 0L) return null
    val bounded = current.coerceIn(0L, duration)
    val step = if (delta >= 0L) minOf(delta, duration - bounded) else maxOf(delta, -bounded)
    return bounded + step
}

internal fun playbackControlTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = seconds / 60L
    return "${minutes}:${(seconds % 60).toString().padStart(2, '0')}"
}

private data class PlaybackControlSnapshot(
    val position: Long = 0L,
    val duration: Long = 0L,
    val seekable: Boolean = false,
    val playing: Boolean = false
)

@Composable
private fun playbackControlSnapshot(player: Player?): PlaybackControlSnapshot {
    val snapshot by produceState(PlaybackControlSnapshot(), player) {
        while (true) {
            value = if (player == null) PlaybackControlSnapshot() else PlaybackControlSnapshot(
                position = player.currentPosition.coerceAtLeast(0L),
                duration = player.duration.coerceAtLeast(0L),
                seekable = player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
                playing = player.isPlaying
            )
            delay(250L)
        }
    }
    return snapshot
}

@Composable
fun PlaybackSeekControl(player: Player?, modifier: Modifier = Modifier, onScrubbingChanged: (Boolean) -> Unit = {}) {
    val interaction = remember(player) { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val notifyScrubbing by rememberUpdatedState(onScrubbingChanged)
    LaunchedEffect(dragging) { notifyScrubbing(dragging) }
    DisposableEffect(player) { onDispose { notifyScrubbing(false) } }
    val state = playbackControlSnapshot(player)
    var draft by remember(player) { mutableStateOf<Float?>(null) }
    val enabled = state.seekable && state.duration > 0L
    val fraction = draft ?: if (state.duration > 0L) (state.position.toDouble() / state.duration).toFloat().coerceIn(0f, 1f) else 0f
    val progressPosition = (fraction * state.duration).toLong()
    Column(modifier) {
        Text("${playbackControlTime(state.position)} / ${if (state.duration > 0L) playbackControlTime(state.duration) else "--:--"}", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = fraction,
            interactionSource = interaction,
            onValueChange = { draft = it },
            enabled = enabled,
            onValueChangeFinished = {
                draft?.let { selected ->
                    if (player?.isCurrentMediaItemSeekable == true && player.duration > 0L) {
                        player.seekTo((selected.toDouble() * player.duration).toLong().coerceIn(0L, player.duration))
                    }
                }
                draft = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "播放进度"
                    stateDescription = "${playbackControlTime(progressPosition)} / ${if (state.duration > 0L) playbackControlTime(state.duration) else "--:--"}"
                }
        )
        if (!enabled) Text("当前内容暂不支持进度定位", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PlaybackControlPanel(
    player: Player?,
    volume: PlayerVolumeState,
    onTogglePlayback: () -> Unit,
    onDismiss: () -> Unit,
    scaleLabel: String,
    onSelectScale: (Int) -> Unit,
    muted: Boolean? = null,
    onToggleMuted: () -> Unit = {},
    additionalControls: @Composable ColumnScope.() -> Unit = {}
) {
    val state = playbackControlSnapshot(player)
    var expanded by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val scroll = rememberScrollState()
        InstallDigitalCrownScrollHandler(scroll)
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("播放控制", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            PlaybackSeekControl(player)
            WatchTextButton(enabled = player != null, onClick = onTogglePlayback) { Text(if (state.playing) "暂停" else "继续播放") }
            muted?.let {
                WatchTextButton(onClick = onToggleMuted) { Text(if (it) "取消静音" else "静音") }
            }
            Text("音量 ${volume.percentText}")
            Slider(
                value = volume.progress,
                onValueChange = volume::setUserProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "音量"
                        stateDescription = volume.percentText
                    }
            )
            WatchTextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起更多控制" else "快进与画面设置") }
            if (expanded) {
                listOf(-15_000L, -5_000L, 5_000L, 15_000L).chunked(2).forEach { pair ->
                    Row {
                        pair.forEach { delta ->
                            WatchTextButton(enabled = state.seekable && state.duration > 0L, onClick = {
                                player?.let { target ->
                                    if (target.isCurrentMediaItemSeekable) playbackSeekTarget(target.currentPosition, delta, target.duration)?.let(target::seekTo)
                                }
                            }) { Text("${if (delta > 0) "+" else ""}${delta / 1000} 秒") }
                        }
                    }
                }
                Text("画面：$scaleLabel")
                listOf("适应", "填充", "完整画面").forEachIndexed { index, label ->
                    WatchTextButton(onClick = { onSelectScale(index) }) { Text(label) }
                }
                additionalControls()
            }
            WatchTextButton(onClick = onDismiss) { Text("返回视频") }
        }
    }
}
