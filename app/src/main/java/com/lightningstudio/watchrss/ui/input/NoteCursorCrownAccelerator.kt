package com.lightningstudio.watchrss.ui.input

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/**
 * Converts crown detents into cursor steps using a continuous, nonlinear speed curve.
 *
 * Direction changes discard fractional progress so the cursor never moves briefly in the previous
 * direction. The complete curve is scaled down for the watch crown's high-resolution input.
 */
internal class NoteCursorCrownAccelerator {
    private var fractionalCharacters = 0.0
    private var lastEventUptimeMillis: Long? = null
    private var lastDirection = 0

    fun consume(delta: Float, eventUptimeMillis: Long): Int {
        if (delta == 0f) return 0

        val direction = sign(delta).toInt()
        val magnitude = abs(delta.toDouble())
        if (direction != lastDirection) {
            fractionalCharacters = 0.0
            lastEventUptimeMillis = null
        }

        val intervalPerDetent = lastEventUptimeMillis?.let { previous ->
            ((eventUptimeMillis - previous).coerceAtLeast(0L).toDouble() / magnitude)
        } ?: Double.POSITIVE_INFINITY
        val charactersPerDetent = noteCursorCharactersPerDetent(intervalPerDetent)
        fractionalCharacters += direction * magnitude * charactersPerDetent

        val wholeCharacters = floor(abs(fractionalCharacters) + ACCUMULATION_EPSILON).toInt()
        val steps = direction * wholeCharacters
        fractionalCharacters -= steps
        lastEventUptimeMillis = eventUptimeMillis
        lastDirection = direction
        return steps
    }
}

internal fun noteCursorCharactersPerDetent(intervalMillis: Double): Double {
    val speedProgress = (
        (SLOW_DETENT_INTERVAL_MILLIS - intervalMillis) /
            (SLOW_DETENT_INTERVAL_MILLIS - FAST_DETENT_INTERVAL_MILLIS)
        ).coerceIn(0.0, 1.0)
    val smoothProgress = speedProgress * speedProgress * (3.0 - 2.0 * speedProgress)
    val unscaledCharacters = SLOW_CHARACTERS_PER_DETENT +
        (FAST_CHARACTERS_PER_DETENT - SLOW_CHARACTERS_PER_DETENT) * smoothProgress
    return unscaledCharacters * WATCH_CROWN_SENSITIVITY_SCALE
}

private const val SLOW_CHARACTERS_PER_DETENT = 0.1
private const val FAST_CHARACTERS_PER_DETENT = 1.0
private const val WATCH_CROWN_SENSITIVITY_SCALE = 0.2
private const val SLOW_DETENT_INTERVAL_MILLIS = 250.0
private const val FAST_DETENT_INTERVAL_MILLIS = 50.0
private const val ACCUMULATION_EPSILON = 1e-9
