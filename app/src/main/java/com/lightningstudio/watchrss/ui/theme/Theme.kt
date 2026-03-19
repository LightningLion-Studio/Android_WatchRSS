package com.lightningstudio.watchrss.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import com.lightningstudio.watchrss.ui.input.PointerWheelScrollTracker
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val WatchDarkColorScheme = darkColorScheme(
    primary = BrandOrange,
    secondary = BrandOrangeDark,
    tertiary = WatchPinnedSurface,
    background = WatchBackground,
    surface = WatchSurface,
    surfaceVariant = WatchSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = WatchTextPrimary,
    onBackground = WatchTextPrimary,
    onSurface = WatchTextPrimary,
    onSurfaceVariant = WatchTextSecondary,
    outline = WatchDivider,
    error = WatchDanger,
    onError = Color.Black,
    errorContainer = WatchDangerContainer,
    onErrorContainer = WatchTextPrimary,
    secondaryContainer = WatchPillSurface,
    onSecondaryContainer = WatchTextPrimary,
    tertiaryContainer = WatchPinnedSurface,
    onTertiaryContainer = WatchTextPrimary
)

internal val WatchLightColorScheme = lightColorScheme(
    primary = BrandOrange,
    secondary = BrandOrangeDark,
    tertiary = WatchPinnedSurface,
    background = Color(0xFFF7F4F0),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDE6DE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFFDDD4CB),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    secondaryContainer = Color(0xFFEAE2DA),
    onSecondaryContainer = Color(0xFF1C1B1F),
    tertiaryContainer = Color(0xFFD8D2CC),
    onTertiaryContainer = Color(0xFF1C1B1F)
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun WatchRSSTheme(
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WatchDarkColorScheme else WatchLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null,
                LocalContentColor provides colorScheme.onSurface
            ) {
                ProvideTextStyle(Typography.bodyMedium) {
                    RubberBandOverscrollContainer(content = content)
                }
            }
        }
    )
}

@Composable
private fun RubberBandOverscrollContainer(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var rawOverscrollY by remember { mutableFloatStateOf(0f) }
    var renderedOffsetY by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(1f) }
    var reboundJob by remember { mutableStateOf<Job?>(null) }
    var flingImpactLocked by remember { mutableStateOf(false) }

    fun clampRaw(value: Float): Float {
        val limit = viewportHeightPx * 6f
        return value.coerceIn(-limit, limit)
    }

    fun rubberBand(value: Float): Float {
        if (value == 0f) return 0f
        val c = 0.55f
        val x = abs(value)
        val d = viewportHeightPx
        val banded = (1f - 1f / ((x * c / d) + 1f)) * d
        return banded * sign(value)
    }

    fun updateOverscroll(raw: Float) {
        rawOverscrollY = clampRaw(raw)
        renderedOffsetY = rubberBand(rawOverscrollY)
    }

    fun cancelRebound() {
        reboundJob?.cancel()
        reboundJob = null
    }

    fun startRebound() {
        if (abs(rawOverscrollY) < 0.5f) {
            updateOverscroll(0f)
            return
        }
        cancelRebound()
        reboundJob = scope.launch {
            Animatable(rawOverscrollY).animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) {
                updateOverscroll(value)
            }
            updateOverscroll(0f)
            reboundJob = null
        }
    }

    val nestedConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (PointerWheelScrollTracker.isRecentPointerWheelScroll()) {
                    if (abs(rawOverscrollY) >= 0.5f) {
                        startRebound()
                    }
                    return Offset.Zero
                }
                if (abs(rawOverscrollY) < 0.5f) return Offset.Zero

                val delta = available.y
                if (delta == 0f) return Offset.Zero
                // When already overscrolled, consume opposite drag first to pull back immediately.
                if (delta * rawOverscrollY < 0f) {
                    cancelRebound()
                    updateOverscroll(rawOverscrollY + delta)
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y == 0f) return Offset.Zero

                if (source == NestedScrollSource.UserInput) {
                    if (PointerWheelScrollTracker.isRecentPointerWheelScroll()) {
                        if (abs(rawOverscrollY) >= 0.5f) {
                            startRebound()
                        }
                        return Offset.Zero
                    }
                    flingImpactLocked = false
                    cancelRebound()
                    updateOverscroll(rawOverscrollY + available.y)
                    return Offset(0f, available.y)
                }

                if (source == NestedScrollSource.SideEffect) {
                    if (!flingImpactLocked && abs(rawOverscrollY) < 0.5f) {
                        // One concise impact from fling overflow, then rebound.
                        updateOverscroll(rawOverscrollY + available.y * 1.0f)
                        flingImpactLocked = true
                        startRebound()
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                flingImpactLocked = false
                if (abs(rawOverscrollY) >= 0.5f) {
                    startRebound()
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                flingImpactLocked = false
                startRebound()
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedConnection)
                .onSizeChanged { size ->
                    val nextHeight = size.height.toFloat().coerceAtLeast(1f)
                    if (nextHeight != viewportHeightPx) {
                        viewportHeightPx = nextHeight
                        renderedOffsetY = rubberBand(rawOverscrollY)
                    }
                }
                .graphicsLayer { translationY = renderedOffsetY }
        ) {
            content()
        }
    }
}
