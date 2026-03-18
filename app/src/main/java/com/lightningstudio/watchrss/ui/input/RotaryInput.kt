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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun InstallRotaryScrollHandler(
    scrollState: ScrollState,
    enabled: Boolean = true,
    reverseDirection: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { ROTARY_SCROLL_STEP.dp.toPx() }
    InstallRotaryHandler(enabled = enabled) { delta ->
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
fun InstallRotaryLazyListHandler(
    listState: LazyListState,
    enabled: Boolean = true,
    reverseDirection: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { ROTARY_SCROLL_STEP.dp.toPx() }
    InstallRotaryHandler(enabled = enabled) { delta ->
        val amount = delta.toScrollAmount(stepPx, reverseDirection)
        if (amount == 0f) {
            false
        } else {
            scope.launch {
                listState.scrollBy(amount)
            }
            true
        }
    }
}

@Composable
fun InstallRotaryPagerHandler(
    pagerState: PagerState,
    enabled: Boolean = true,
    reverseDirection: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var accumulatedDelta by remember { mutableFloatStateOf(0f) }
    InstallRotaryHandler(enabled = enabled) { delta ->
        accumulatedDelta += if (reverseDirection) -delta else delta
        if (abs(accumulatedDelta) < ROTARY_PAGER_THRESHOLD) {
            return@InstallRotaryHandler true
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
fun InstallRotaryVolumeHandler(
    enabled: Boolean = true,
    showSystemUi: Boolean = true,
    reverseDirection: Boolean = false,
    onVolumeStep: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var accumulatedDelta by remember { mutableFloatStateOf(0f) }
    InstallRotaryHandler(enabled = enabled) { delta ->
        accumulatedDelta += normalizeRotaryVolumeDelta(delta, reverseDirection)
        if (abs(accumulatedDelta) < ROTARY_VOLUME_THRESHOLD) {
            return@InstallRotaryHandler true
        }
        val steps = abs(accumulatedDelta).toInt().coerceAtLeast(1)
        val direction = rotaryVolumeDirection(accumulatedDelta)
        accumulatedDelta = 0f
        if (onVolumeStep != null) {
            onVolumeStep(direction * steps)
        } else {
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

internal fun normalizeRotaryVolumeDelta(delta: Float, reverseDirection: Boolean): Float {
    return if (reverseDirection) -delta else delta
}

internal fun rotaryVolumeDirection(accumulatedDelta: Float): Int {
    return when {
        accumulatedDelta > 0f -> 1
        accumulatedDelta < 0f -> -1
        else -> 0
    }
}

@Composable
fun InstallRotaryHandler(
    enabled: Boolean = true,
    onRotaryInput: (Float) -> Boolean
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findBaseWatchActivity() }
    val latestHandler by rememberUpdatedState(onRotaryInput)

    DisposableEffect(activity, enabled) {
        if (!enabled || activity == null) {
            onDispose { }
        } else {
            val id = activity.registerRotaryHandler { delta ->
                latestHandler(delta)
            }
            onDispose {
                activity.unregisterRotaryHandler(id)
            }
        }
    }
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

private const val ROTARY_SCROLL_STEP = 56
private const val ROTARY_PAGER_THRESHOLD = 0.8f
private const val ROTARY_VOLUME_THRESHOLD = 0.8f
