package com.lightningstudio.watchrss.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Stable
class PlayerVolumeState internal constructor(
    private val audioManager: AudioManager,
    private val scope: CoroutineScope
) {
    private val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val volumeRange = (maxVolume - minVolume).coerceAtLeast(1)
    private var hideJob: Job? = null

    var currentVolume by mutableIntStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
    )
        private set

    var isVisible by mutableStateOf(false)
        private set

    val progress: Float
        get() = ((currentVolume - minVolume).toFloat() / volumeRange.toFloat()).coerceIn(0f, 1f)

    val percentText: String
        get() = "${(progress * 100f).roundToInt()}%"

    fun adjustBySteps(steps: Int) {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
        val target = (current + steps).coerceIn(minVolume, maxVolume)
        if (!audioManager.isVolumeFixed && target != current) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
        show()
    }

    private fun show() {
        isVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(VOLUME_OVERLAY_HIDE_DELAY_MS)
            isVisible = false
        }
    }
}

@Composable
fun rememberPlayerVolumeState(): PlayerVolumeState {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val scope = rememberCoroutineScope()
    return remember(audioManager, scope) {
        PlayerVolumeState(audioManager = audioManager, scope = scope)
    }
}

@Composable
fun PlayerVolumeOverlay(
    state: PlayerVolumeState,
    modifier: Modifier = Modifier
) {
    if (!state.isVisible) return

    val radius = watchDimensionResource(R.dimen.hey_button_default_radius)
    val horizontalPadding = watchDimensionResource(R.dimen.hey_distance_8dp)
    val verticalPadding = watchDimensionResource(R.dimen.hey_distance_6dp)
    val innerSpacing = watchDimensionResource(R.dimen.hey_distance_4dp)

    Column(
        modifier = modifier
            .widthIn(min = 72.dp, max = 92.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(innerSpacing)
    ) {
        Text(
            text = "音量 ${state.percentText}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        LinearProgressIndicator(
            progress = { state.progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(100))
        )
    }
}

private const val VOLUME_OVERLAY_HIDE_DELAY_MS = 1_200L
