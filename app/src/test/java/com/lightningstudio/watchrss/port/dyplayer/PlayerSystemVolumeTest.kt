package com.lightningstudio.watchrss.port.dyplayer

import android.media.AudioManager
import com.lightningstudio.watchrss.ui.components.PlayerVolumeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSystemVolumeTest {
    private class SystemVolume(var volume: Int = 8, var muted: Boolean = false, fixed: Boolean = false) {
        val audio = mock(AudioManager::class.java).also { audio ->
            `when`(audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)).thenReturn(0)
            `when`(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(16)
            `when`(audio.getStreamVolume(AudioManager.STREAM_MUSIC)).thenAnswer { volume }
            `when`(audio.isStreamMute(AudioManager.STREAM_MUSIC)).thenAnswer { muted }
            `when`(audio.isVolumeFixed).thenReturn(fixed)
            doAnswer { call ->
                muted = call.getArgument<Int>(1) == AudioManager.ADJUST_MUTE
                null
            }.`when`(audio).adjustStreamVolume(anyInt(), anyInt(), anyInt())
            doAnswer { call -> volume = call.getArgument(1); null }
                .`when`(audio).setStreamVolume(anyInt(), anyInt(), anyInt())
        }
    }

    @Test
    fun muteToggleReadsLiveSystemStateEvenWhenCachedButtonIsStale() = runTest {
        val system = SystemVolume()
        val state = PlayerVolumeState(system.audio, backgroundScope, false, null) { }
        assertFalse(state.isMuted)
        // External mute occurs before the observer has delivered its callback.
        system.muted = true
        state.toggleMute()
        verify(system.audio).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        assertFalse(state.isMuted)
        state.toggleMute()
        verify(system.audio).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        assertTrue(state.isMuted)
        assertTrue(state.isVisible)
        assertEquals("0%", state.percentText)
        verify(system.audio, never()).setStreamVolume(anyInt(), anyInt(), anyInt())
    }

    @Test
    fun localUnmutePreservesTheFirstPlaybackVolumeLimit() = runTest {
        val system = SystemVolume(volume = 8, muted = true)
        val state = PlayerVolumeState(system.audio, backgroundScope, false, 10) { }
        state.toggleMute()
        state.enforcePlaybackStartGuard()
        verify(system.audio).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        verify(system.audio).setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0)
        assertEquals(2, state.currentVolume)
        assertFalse(state.isMuted)
    }

    @Test
    fun externalMuteWithoutVolumeIndexChangeShowsOverlayAndRefreshesIcon() = runTest {
        val system = SystemVolume()
        val state = PlayerVolumeState(system.audio, backgroundScope, false, null) { }
        state.refreshFromSystem()
        assertFalse(state.isVisible)
        system.muted = true
        state.refreshFromSystem()
        assertTrue(state.isVisible)
        assertTrue(state.isMuted)
        assertEquals("0%", state.percentText)
        system.muted = false
        state.refreshFromSystem()
        assertFalse(state.isMuted)
        assertEquals("50%", state.percentText)
        verify(system.audio, never()).adjustStreamVolume(anyInt(), anyInt(), anyInt())
    }

    @Test
    fun externalVolumeResetsFractionalCrownBaselineAndOverlayTimeout() = runTest {
        val system = SystemVolume()
        val state = PlayerVolumeState(system.audio, backgroundScope, false, null) { }
        state.adjustByDelta(0.1f, 0L)
        system.volume = 12
        state.refreshFromSystem()
        assertEquals(12, state.currentVolume)
        assertEquals("75%", state.percentText)
        state.adjustByDelta(1f / 3f, 10L)
        assertEquals(12, state.currentVolume)
        runCurrent()
        advanceTimeBy(1201L)
        runCurrent()
        assertFalse(state.isVisible)
    }

    @Test
    fun disposalCancelsOverlayAndFixedVolumeToggleDoesNotInventMuteState() = runTest {
        val system = SystemVolume(fixed = true)
        val state = PlayerVolumeState(system.audio, backgroundScope, false, null) { }
        state.toggleMute()
        assertFalse(state.isMuted)
        verify(system.audio, never()).adjustStreamVolume(anyInt(), anyInt(), anyInt())
        state.dispose()
        assertFalse(state.isVisible)
        runCurrent()
        advanceTimeBy(2000L)
        assertFalse(state.isVisible)
    }
}
