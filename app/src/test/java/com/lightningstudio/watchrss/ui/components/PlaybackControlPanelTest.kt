package com.lightningstudio.watchrss.ui.components

import org.junit.Assert.*
import org.junit.Test

class PlaybackControlPanelTest {
    @Test
    fun unknownDurationDoesNotOfferASeekTarget() {
        assertNull(playbackSeekTarget(100, 5000, 0))
        assertNull(playbackSeekTarget(100, -5000, Long.MIN_VALUE))
    }

    @Test
    fun seekingClampsBothEndsAndDoesNotOverflow() {
        assertEquals(0L, playbackSeekTarget(1000, -15000, 10000))
        assertEquals(10000L, playbackSeekTarget(9000, 15000, 10000))
        assertEquals(Long.MAX_VALUE, playbackSeekTarget(Long.MAX_VALUE - 1, Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(0L, playbackSeekTarget(1000, Long.MIN_VALUE, Long.MAX_VALUE))
        assertEquals(5000L, playbackSeekTarget(-100, 5000, 10000))
    }

    @Test
    fun timeLabelsRemainReadableAfterOneHourAndHandleUnsetValues() {
        assertEquals("0:00", playbackControlTime(-1))
        assertEquals("1:05", playbackControlTime(65000))
        assertEquals("61:01", playbackControlTime(3661000))
    }
}
