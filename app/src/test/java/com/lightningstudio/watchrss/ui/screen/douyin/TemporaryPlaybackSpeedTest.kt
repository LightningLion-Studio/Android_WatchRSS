package com.lightningstudio.watchrss.ui.screen.douyin

import org.junit.Assert.*
import org.junit.Test

class TemporaryPlaybackSpeedTest {
    @Test
    fun releaseRestoresTheActualSpeedBeforePress() {
        var actual = 1.5f
        val control = TemporaryPlaybackSpeed({ actual }, { actual = it })
        control.begin()
        assertEquals(2f, actual)
        control.end()
        assertEquals(1.5f, actual)
    }

    @Test
    fun repeatedBeginAndCancellationCannotOverwriteRestorationSpeed() {
        var actual = 0.75f
        val writes = mutableListOf<Float>()
        val control = TemporaryPlaybackSpeed({ actual }, { actual = it; writes += it })
        control.begin()
        control.begin()
        control.end()
        control.end()
        assertEquals(listOf(2f, 0.75f), writes)
        control.begin()
        control.end()
        assertEquals(0.75f, actual)
    }

    @Test
    fun endingWithoutAnActivePressDoesNotChangePlayback() {
        var actual = 1.25f
        val control = TemporaryPlaybackSpeed({ actual }, { actual = it })
        control.end()
        assertEquals(1.25f, actual)
    }
}
