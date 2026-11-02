package com.lightningstudio.watchrss.ui.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/** Opt-in for full-page reader surfaces, not for feed cards or the global theme. */
internal fun Modifier.readerViewportBoundary(): Modifier = nestedScroll(ReaderViewportBoundary)

/**
 * Let the child consume normal scrolling, then stop only unconsumed vertical boundary motion.
 * Otherwise the shared rubber-band container translates the paper itself and exposes black
 * outside it. Horizontal gestures (including back) and other modules remain unchanged.
 */
internal object ReaderViewportBoundary : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(0f, available.y)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(0f, available.y)
}
