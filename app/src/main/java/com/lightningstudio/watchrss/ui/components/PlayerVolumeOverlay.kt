package com.lightningstudio.watchrss.ui.components

import android.content.Context
import android.media.AudioManager
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.lightningstudio.watchrss.util.AppLogger
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
    // PlayerVolume: getStreamMaxVolume(STREAM_MUSIC)=16
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        .also { AppLogger.d(VOLUME_TAG, "getStreamMaxVolume(STREAM_MUSIC)=$it") }
    // 每单位 digitalCrown delta 对应的音量步长（5% 音量范围）
    private val volumeSensitivity = (maxVolume - minVolume) * VOLUME_SENSITIVITY_PERCENT
    private var hideJob: Job? = null
    // 浮点虚拟音量，保留 delta 累积精度，仅在提交给 AudioManager 时 roundToInt
    // 用 mutableFloatStateOf 使 UI 直接观察连续值，而非离散的 AudioManager 整数
    private var virtualVolume by mutableFloatStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume).toFloat()
    )

    var currentVolume by mutableIntStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
    )
        private set

    var isVisible by mutableStateOf(false)
        private set

    val progress: Float
        get() {
            if (maxVolume <= minVolume) return 0f
            return ((virtualVolume - minVolume) / (maxVolume - minVolume)).coerceIn(0f, 1f)
        }

    val percentText: String
        get() = "${(progress * 100f).roundToInt()}%"

    fun adjustByDelta(delta: Float) {
        val prevVirtual = virtualVolume
        virtualVolume = (virtualVolume + delta * volumeSensitivity)
            .coerceIn(minVolume.toFloat(), maxVolume.toFloat())
        val targetInt = virtualVolume.roundToInt()
        AppLogger.d(
            VOLUME_TAG,
            "adjustByDelta delta=$delta sensitivity=$volumeSensitivity virtual $prevVirtual→$virtualVolume targetInt=$targetInt current=$currentVolume"
        )
        setVolume(targetInt)
        show()
    }

    private fun readCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
    }

    private fun setVolume(target: Int) {
        val current = readCurrentVolume()
        val clampedTarget = target.coerceIn(minVolume, maxVolume)
        if (!audioManager.isVolumeFixed && clampedTarget != current) {
            AppLogger.d(VOLUME_TAG, "setStreamVolume $current→$clampedTarget")
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clampedTarget, 0)
        } else {
            AppLogger.d(VOLUME_TAG, "setVolume skipped target=$clampedTarget current=$current fixed=${audioManager.isVolumeFixed}")
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
fun rememberPlayerVolumeState(): PlayerVolumeState {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val scope = rememberCoroutineScope()
    return remember(audioManager, scope) {
        PlayerVolumeState(
            audioManager = audioManager,
            scope = scope
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
// 每单位 scaled delta（已乘以 ROTARY_VOLUME_STEP=0.01）调节的音量百分比
// 按实测一圈表冠总 net scaled ≈ 16.5，校准为 1/16.5 ≈ 0.06，使一圈刚好覆盖 0→100% 音量范围
private const val VOLUME_SENSITIVITY_PERCENT = 0.06f
private const val VOLUME_TAG = "PlayerVolume"

