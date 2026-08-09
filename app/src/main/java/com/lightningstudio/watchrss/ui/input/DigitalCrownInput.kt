package com.lightningstudio.watchrss.ui.input

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.BaseWatchActivity
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun InstallDigitalCrownScrollHandler(
    scrollState: ScrollState,
    enabled: Boolean = true,
    reverseDirection: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { DIGITAL_CROWN_SCROLL_STEP.dp.toPx() }
    InstallDigitalCrownHandler(enabled = enabled, supportsDigitalCrown = true) { delta ->
        val amount = delta.toScrollAmount(stepPx, reverseDirection)
        if (amount == 0f) {
            false
        } else {
            scope.launch {
                scrollState.scrollBy(amount)
            }
            true
        }
    }
}

@Composable
fun InstallDigitalCrownLazyListHandler(
    listState: LazyListState,
    enabled: Boolean = true,
    reverseDirection: Boolean = false,
    onScrollInput: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { DIGITAL_CROWN_SCROLL_STEP.dp.toPx() }
    InstallDigitalCrownHandler(enabled = enabled, supportsDigitalCrown = true) { delta ->
        val amount = delta.toScrollAmount(stepPx, reverseDirection)
        if (amount == 0f) {
            false
        } else {
            onScrollInput?.invoke()
            scope.launch {
                listState.scrollBy(amount)
            }
            true
        }
    }
}

@Composable
fun InstallDigitalCrownPagerHandler(
    pagerState: PagerState,
    enabled: Boolean = true,
    reverseDirection: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var accumulatedDelta by remember { mutableFloatStateOf(0f) }
    InstallDigitalCrownHandler(enabled = enabled) { delta ->
        accumulatedDelta += if (reverseDirection) -delta else delta
        if (abs(accumulatedDelta) < DIGITAL_CROWN_PAGER_THRESHOLD) {
            return@InstallDigitalCrownHandler true
        }
        val direction = -sign(accumulatedDelta).toInt()
        accumulatedDelta = 0f
        val targetPage = (pagerState.currentPage + direction)
            .coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
        if (targetPage == pagerState.currentPage) {
            false
        } else {
            scope.launch {
                pagerState.animateScrollToPage(targetPage)
            }
            true
        }
    }
}

@Composable
fun InstallDigitalCrownVolumeHandler(
    enabled: Boolean = true,
    showSystemUi: Boolean = true,
    reverseDirection: Boolean = false,
    supportsDigitalCrown: Boolean = false,
    onVolumeAdjust: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var accumulatedDelta by remember { mutableFloatStateOf(0f) }
    InstallDigitalCrownHandler(
        enabled = enabled,
        supportsDigitalCrown = supportsDigitalCrown
    ) { delta ->
        val normalizedDelta = normalizeDigitalCrownVolumeDelta(delta, reverseDirection)
        if (onVolumeAdjust != null) {
            // 直接传 raw delta，由调用方维护浮点虚拟音量
            val scaledDelta = normalizedDelta * DIGITAL_CROWN_VOLUME_STEP.toFloat()
            AppLogger.d(DIGITAL_CROWN_VOLUME_TAG, "digitalCrown delta=$delta normalized=$normalizedDelta scaled=$scaledDelta → onVolumeAdjust")
            onVolumeAdjust(scaledDelta)
        } else {
            // 无自定义 handler 时回退到系统 adjustStreamVolume（整步）
            accumulatedDelta += normalizedDelta
            if (abs(accumulatedDelta) < DIGITAL_CROWN_VOLUME_THRESHOLD) {
                return@InstallDigitalCrownHandler true
            }
            val steps = abs(accumulatedDelta).toInt().coerceAtLeast(1)
            val direction = digitalCrownVolumeDirection(accumulatedDelta)
            accumulatedDelta = 0f
            repeat(steps) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0
                )
            }
        }
        true
    }
}

internal fun normalizeDigitalCrownVolumeDelta(delta: Float, reverseDirection: Boolean): Float {
    return if (reverseDirection) -delta else delta
}

internal fun digitalCrownVolumeDirection(accumulatedDelta: Float): Int {
    return when {
        accumulatedDelta > 0f -> 1
        accumulatedDelta < 0f -> -1
        else -> 0
    }
}

@Composable
fun InstallDigitalCrownHandler(
    enabled: Boolean = true,
    supportsDigitalCrown: Boolean = false,
    onDigitalCrownInput: (Float) -> Boolean
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findBaseWatchActivity() }
    val latestHandler by rememberUpdatedState(onDigitalCrownInput)

    DisposableEffect(activity, enabled) {
        if (!enabled || activity == null) {
            onDispose { }
        } else {
            val id = activity.registerDigitalCrownHandler(supportsDigitalCrown = supportsDigitalCrown) { delta ->
                latestHandler(delta)
            }
            onDispose {
                activity.unregisterDigitalCrownHandler(id)
            }
        }
    }
}

internal fun normalizeDigitalCrownScrollDelta(delta: Float): Float {
    if (delta == 0f) return 0f
    return delta.coerceIn(-MAX_DIGITAL_CROWN_SCROLL_DELTA, MAX_DIGITAL_CROWN_SCROLL_DELTA)
}

private fun Float.toScrollAmount(stepPx: Float, reverseDirection: Boolean): Float {
    if (this == 0f) return 0f
    val direction = if (reverseDirection) 1f else -1f
    return this * stepPx * direction
}

private fun Context.findBaseWatchActivity(): BaseWatchActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is BaseWatchActivity) return current
        current = current.baseContext
    }
    return null
}

// 基于 OPPO Watch X2 实测，0.2 是自然滚动速度
private const val DIGITAL_CROWN_SCROLL_STEP = 0.2
private const val DIGITAL_CROWN_VOLUME_STEP = 0.01
private const val DIGITAL_CROWN_PAGER_THRESHOLD = 0.8f
private const val DIGITAL_CROWN_VOLUME_THRESHOLD = 0.8f
private const val MAX_DIGITAL_CROWN_SCROLL_DELTA = 1f
private const val DIGITAL_CROWN_VOLUME_TAG = "DigitalCrownVolume"
