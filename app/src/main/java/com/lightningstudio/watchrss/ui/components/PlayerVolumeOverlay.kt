package com.lightningstudio.watchrss.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.media.DigitalCrownVolumeGuardState
import com.lightningstudio.watchrss.data.media.applyDigitalCrownVolumeGuard
import com.lightningstudio.watchrss.data.media.hasOutOfBandVolumeChange
import com.lightningstudio.watchrss.data.media.playbackStartVolumeForPercent
import com.lightningstudio.watchrss.data.media.shouldEnforcePlaybackStartGuard
import com.lightningstudio.watchrss.data.media.volumeForPercent
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.util.showAppToast
import com.lightningstudio.watchrss.util.AppLogger
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class PlayerVolumeState internal constructor(
    private val audioManager: AudioManager,
    private val scope: CoroutineScope,
    private val guardEnabled: Boolean,
    private val playbackStartVolumeLimitPercent: Int?,
    private val onVolumeGuardTriggered: () -> Unit
) {
    private val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    // PlayerVolume: getStreamMaxVolume(STREAM_MUSIC)=16
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        .also { AppLogger.d(VOLUME_TAG, "getStreamMaxVolume(STREAM_MUSIC)=$it") }
    // 每单位 digitalCrown delta 对应的音量步长（5% 音量范围）
    private val volumeSensitivity = (maxVolume - minVolume) * VOLUME_SENSITIVITY_PERCENT
    private val digitalCrownSessionCapVolume = volumeForPercent(
        targetPercent = DIGITAL_CROWN_SESSION_CAP_PERCENT,
        minVolume = minVolume,
        maxVolume = maxVolume
    )
    private var hideJob: Job? = null
    private var digitalCrownGuardState by mutableStateOf(DigitalCrownVolumeGuardState())
    private var playbackStartGuardDismissedByUser by mutableStateOf(false)
    // 浮点虚拟音量，保留 delta 累积精度，仅在提交给 AudioManager 时 roundToInt
    // 用 mutableFloatStateOf 使 UI 直接观察连续值，而非离散的 AudioManager 整数
    private var virtualVolume by mutableFloatStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume).toFloat()
    )

    var currentVolume by mutableIntStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
    )
        private set

    var isMuted by mutableStateOf(audioManager.isStreamMute(AudioManager.STREAM_MUSIC))
        private set

    var isVisible by mutableStateOf(false)
        private set

    val progress: Float
        get() {
            if (isMuted || maxVolume <= minVolume) return 0f
            return ((virtualVolume - minVolume) / (maxVolume - minVolume)).coerceIn(0f, 1f)
        }

    val percentText: String
        get() = "${(progress * 100f).roundToInt()}%"

    fun adjustByDelta(delta: Float, eventUptimeMs: Long = SystemClock.elapsedRealtime()) {
        if (!delta.isFinite() || delta == 0f) return
        playbackStartGuardDismissedByUser = true
        syncOutOfBandVolumeChange()
        val prevVirtual = virtualVolume
        val guardedTarget = applyDigitalCrownVolumeGuard(
            currentVolume = virtualVolume,
            requestedDeltaVolume = delta * volumeSensitivity,
            minVolume = minVolume,
            maxVolume = maxVolume,
            guardEnabled = guardEnabled,
            sessionCapVolume = digitalCrownSessionCapVolume,
            previousState = digitalCrownGuardState,
            eventUptimeMs = eventUptimeMs
        )
        digitalCrownGuardState = guardedTarget.nextState
        virtualVolume = guardedTarget.targetVolume
        if (guardedTarget.shouldNotifyGuardTriggered) {
            onVolumeGuardTriggered()
        }
        val targetInt = virtualVolume.roundToInt()
        AppLogger.d(
            VOLUME_TAG,
            "adjustByDelta delta=$delta sensitivity=$volumeSensitivity virtual $prevVirtual→$virtualVolume targetInt=$targetInt current=$currentVolume"
        )
        setVolume(targetInt)
        show()
    }

    fun setUserProgress(value: Float) {
        if (!value.isFinite()) return
        syncOutOfBandVolumeChange()
        adjustByDelta((value.coerceIn(0f, 1f) - progress) / VOLUME_SENSITIVITY_PERCENT)
    }

    fun enforcePlaybackStartGuard() {
        syncOutOfBandVolumeChange()
        val current = readCurrentVolume()
        val targetVolume = playbackStartVolumeLimitPercent?.let {
            playbackStartVolumeForPercent(
                targetPercent = it,
                minVolume = minVolume,
                maxVolume = maxVolume
            )
        }
        if (!shouldEnforcePlaybackStartGuard(
                playbackStartVolumeLimitPercent = playbackStartVolumeLimitPercent,
                dismissedByUser = playbackStartGuardDismissedByUser,
                currentVolume = current,
                minVolume = minVolume,
                maxVolume = maxVolume
            )
        ) {
            AppLogger.d(
                VOLUME_TAG,
                "skip playback start guard current=$current dismissed=$playbackStartGuardDismissedByUser"
            )
            return
        }
        AppLogger.d(
            VOLUME_TAG,
            "apply playback start guard current=$current target=$targetVolume limitPercent=$playbackStartVolumeLimitPercent"
        )
        val previousVirtualVolume = virtualVolume
        virtualVolume = targetVolume?.toFloat() ?: current.toFloat()
        setVolume(virtualVolume.roundToInt())
        if (currentVolume != current || virtualVolume != previousVirtualVolume) {
            show()
        }
    }

    private fun readCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(minVolume, maxVolume)
    }

    /** Read the system immediately before choosing an explicit mute/unmute command. */
    fun toggleMute() {
        val actuallyMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        if (!audioManager.isVolumeFixed) {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (actuallyMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE,
                0
            )
        }
        // A local mute command must not dismiss the first-play volume limit.
        currentVolume = readCurrentVolume()
        isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        virtualVolume = currentVolume.toFloat()
        digitalCrownGuardState = DigitalCrownVolumeGuardState()
        show()
    }

    fun refreshFromSystem(showOverlayOnChange: Boolean = true) {
        val actual = readCurrentVolume()
        val muted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        if (!hasOutOfBandVolumeChange(currentVolume, actual) && isMuted == muted) return
        playbackStartGuardDismissedByUser = true
        digitalCrownGuardState = DigitalCrownVolumeGuardState()
        currentVolume = actual
        isMuted = muted
        virtualVolume = actual.toFloat()
        if (showOverlayOnChange) show()
    }

    private fun syncOutOfBandVolumeChange() = refreshFromSystem()

    internal fun dispose() {
        hideJob?.cancel()
        hideJob = null
        isVisible = false
    }

    private fun setVolume(target: Int, syncVirtual: Boolean = false) {
        val current = readCurrentVolume()
        val clampedTarget = target.coerceIn(minVolume, maxVolume)
        if (!audioManager.isVolumeFixed && clampedTarget != current) {
            AppLogger.d(VOLUME_TAG, "setStreamVolume $current→$clampedTarget")
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clampedTarget, 0)
        } else {
            AppLogger.d(VOLUME_TAG, "setVolume skipped target=$clampedTarget current=$current fixed=${audioManager.isVolumeFixed}")
        }
        currentVolume = readCurrentVolume()
        isMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
        if (syncVirtual) {
            virtualVolume = currentVolume.toFloat()
        }
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
fun rememberPlayerVolumeState(
    guardEnabled: Boolean = true,
    playbackStartVolumeLimitPercent: Int? = 10
): PlayerVolumeState {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val audioManager = remember(appContext) {
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val scope = rememberCoroutineScope()
    val state = remember(audioManager, scope, guardEnabled, playbackStartVolumeLimitPercent, appContext) {
        PlayerVolumeState(
            audioManager = audioManager,
            scope = scope,
            guardEnabled = guardEnabled,
            playbackStartVolumeLimitPercent = playbackStartVolumeLimitPercent,
            onVolumeGuardTriggered = {
                showAppToast(
                    appContext,
                    VOLUME_GUARD_TRIGGERED_TOAST,
                    Toast.LENGTH_SHORT
                )
            }
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(appContext, state, lifecycleOwner) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = state.refreshFromSystem()
        }
        // Some OEMs notify Settings, while AOSP also broadcasts stream mute changes.
        // Read AudioManager rather than trusting broadcast extras.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                state.refreshFromSystem()
            }
        }
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
        }
        appContext.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refreshFromSystem()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        state.refreshFromSystem()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            appContext.unregisterReceiver(receiver)
            appContext.contentResolver.unregisterContentObserver(observer)
            state.dispose()
        }
    }
    return state
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
            imageVector = if (state.isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
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
private const val DIGITAL_CROWN_SESSION_CAP_PERCENT = 0.21f
private const val VOLUME_GUARD_TRIGGERED_TOAST = "音量调节防干扰触发，先停下以继续调整音量"
// 每单位 scaled delta（已乘以 DIGITAL_CROWN_VOLUME_STEP=0.01）调节的音量百分比
// 按实测一圈表冠总 net scaled ≈ 16.5，校准为 1/16.5 ≈ 0.06，使一圈刚好覆盖 0→100% 音量范围
private const val VOLUME_SENSITIVITY_PERCENT = 0.06f
private const val VOLUME_TAG = "PlayerVolume"
