package com.lightningstudio.watchrss.ui.components

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

@Stable
class PlayerVolumeState internal constructor(
    private val audioManager: AudioManager,
    private val scope: CoroutineScope,
    private val guardEnabled: Boolean
) {
    private val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val playbackStartTargetVolume = nearestPositiveVolumeForPercent(
        targetPercent = PLAYBACK_START_TARGET_PERCENT,
        minVolume = minVolume,
        maxVolume = maxVolume
    )
    private val rotarySessionCapVolume = highestSafeVolumeForPercent(
        maxPercent = ROTARY_SESSION_CAP_PERCENT,
        minVolume = minVolume,
        maxVolume = maxVolume
    )
    private var hideJob: Job? = null
    private var rotaryGuardState by mutableStateOf(RotaryVolumeGuardState())

    var currentVolume by mutableIntStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
    )
        private set

    var isVisible by mutableStateOf(false)
        private set

    val progress: Float
        get() = volumeProgress(
            currentVolume = currentVolume,
            minVolume = minVolume,
            maxVolume = maxVolume
        )

    val percentText: String
        get() = "${(progress * 100f).roundToInt()}%"

    fun adjustBySteps(steps: Int, eventUptimeMs: Long = SystemClock.elapsedRealtime()) {
        if (steps == 0) return
        val current = readCurrentVolume()
        val guardedTarget = applyRotaryVolumeGuard(
            currentVolume = current,
            requestedSteps = steps,
            minVolume = minVolume,
            maxVolume = maxVolume,
            guardEnabled = guardEnabled,
            sessionCapVolume = rotarySessionCapVolume,
            previousState = rotaryGuardState,
            eventUptimeMs = eventUptimeMs
        )
        rotaryGuardState = guardedTarget.nextState
        setVolume(guardedTarget.targetVolume, current)
        show()
    }

    fun enforcePlaybackStartGuard() {
        if (!guardEnabled) return
        val current = readCurrentVolume()
        if (volumeProgress(current, minVolume, maxVolume) <= PLAYBACK_START_THRESHOLD_PERCENT) return
        setVolume(playbackStartTargetVolume, current)
        if (currentVolume != current) {
            show()
        }
    }

    private fun readCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
    }

    private fun setVolume(target: Int, current: Int = readCurrentVolume()) {
        val clampedTarget = target.coerceIn(minVolume, maxVolume)
        if (!audioManager.isVolumeFixed && clampedTarget != current) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clampedTarget, 0)
        }
        currentVolume = readCurrentVolume()
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
fun rememberPlayerVolumeState(guardEnabled: Boolean = true): PlayerVolumeState {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val scope = rememberCoroutineScope()
    return remember(audioManager, scope, guardEnabled) {
        PlayerVolumeState(
            audioManager = audioManager,
            scope = scope,
            guardEnabled = guardEnabled
        )
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

    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(innerSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.VolumeUp,
            contentDescription = "音量",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(18.dp)
        )
        Text(
            text = "：${state.percentText}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private const val VOLUME_OVERLAY_HIDE_DELAY_MS = 1_200L
private const val PLAYBACK_START_THRESHOLD_PERCENT = 0.15f
private const val PLAYBACK_START_TARGET_PERCENT = 0.07f
private const val ROTARY_SESSION_CAP_PERCENT = 0.16f
private const val ROTARY_SESSION_IDLE_TIMEOUT_MS = 600L

internal data class RotaryVolumeGuardState(
    val sessionCapVolume: Int? = null,
    val lastEventUptimeMs: Long = Long.MIN_VALUE
)

internal data class RotaryVolumeGuardResult(
    val targetVolume: Int,
    val nextState: RotaryVolumeGuardState
)

internal fun applyRotaryVolumeGuard(
    currentVolume: Int,
    requestedSteps: Int,
    minVolume: Int,
    maxVolume: Int,
    guardEnabled: Boolean,
    sessionCapVolume: Int,
    previousState: RotaryVolumeGuardState,
    eventUptimeMs: Long
): RotaryVolumeGuardResult {
    val clampedCurrent = currentVolume.coerceIn(minVolume, maxVolume)
    if (requestedSteps == 0) {
        return RotaryVolumeGuardResult(
            targetVolume = clampedCurrent,
            nextState = previousState
        )
    }

    val isNewSession = previousState.lastEventUptimeMs == Long.MIN_VALUE ||
        eventUptimeMs - previousState.lastEventUptimeMs > ROTARY_SESSION_IDLE_TIMEOUT_MS
    val direction = requestedSteps.compareTo(0)
    var activeCapVolume = if (isNewSession || direction <= 0 || !guardEnabled) {
        null
    } else {
        previousState.sessionCapVolume
    }
    var targetVolume = (clampedCurrent + requestedSteps).coerceIn(minVolume, maxVolume)

    if (guardEnabled && direction > 0) {
        val effectiveCapVolume = activeCapVolume ?: if (clampedCurrent < sessionCapVolume) {
            sessionCapVolume
        } else {
            null
        }
        if (effectiveCapVolume != null) {
            activeCapVolume = effectiveCapVolume
            targetVolume = targetVolume.coerceAtMost(effectiveCapVolume)
        }
    }

    return RotaryVolumeGuardResult(
        targetVolume = targetVolume,
        nextState = RotaryVolumeGuardState(
            sessionCapVolume = activeCapVolume,
            lastEventUptimeMs = eventUptimeMs
        )
    )
}

internal fun volumeProgress(
    currentVolume: Int,
    minVolume: Int,
    maxVolume: Int
): Float {
    if (maxVolume <= minVolume) return 0f
    val clampedVolume = currentVolume.coerceIn(minVolume, maxVolume)
    val range = (maxVolume - minVolume).coerceAtLeast(1)
    return ((clampedVolume - minVolume).toFloat() / range.toFloat()).coerceIn(0f, 1f)
}

internal fun nearestPositiveVolumeForPercent(
    targetPercent: Float,
    minVolume: Int,
    maxVolume: Int
): Int {
    if (maxVolume <= minVolume) return minVolume
    if (targetPercent <= 0f) return minVolume
    val range = (maxVolume - minVolume).coerceAtLeast(1)
    val target = minVolume + (range * targetPercent).roundToInt()
    return target.coerceIn(minVolume + 1, maxVolume)
}

internal fun highestSafeVolumeForPercent(
    maxPercent: Float,
    minVolume: Int,
    maxVolume: Int
): Int {
    if (maxVolume <= minVolume) return minVolume
    if (maxPercent <= 0f) return minVolume
    val range = (maxVolume - minVolume).coerceAtLeast(1)
    val target = minVolume + floor(range * maxPercent).toInt()
    return target.coerceIn(minVolume + 1, maxVolume)
}
