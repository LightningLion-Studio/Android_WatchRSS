package com.lightningstudio.watchrss.port.dyplayer

import androidx.media3.common.Player
import com.lightningstudio.watchrss.data.douyin.DouyinOfflineReadiness
import com.lightningstudio.watchrss.ui.screen.douyin.commitDouyinForegroundAudio
import com.lightningstudio.watchrss.ui.screen.douyin.douyinCrownVolumeDelta
import com.lightningstudio.watchrss.ui.screen.douyin.douyinFeedLoadNotice
import com.lightningstudio.watchrss.ui.screen.douyin.douyinPlaybackNeedsInternet
import com.lightningstudio.watchrss.ui.screen.douyin.shouldBlockDouyinReverseSwipe
import com.lightningstudio.watchrss.ui.screen.douyin.shouldEndDouyinOfflinePrefix
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class DouyinPlayerPolicyTest {
    @Test
    fun calibratedCrownInputKeepsExactlyOneThirdOfExistingRangeAndSign() {
        // DigitalCrownInput scales normalized raw input by 0.01. Existing full-turn
        // calibration is 16.5 scaled units * 0.06 = 0.99 of the volume range.
        assertEquals(0.33f, douyinCrownVolumeDelta(16.5f) * 0.06f, 0.00001f)
        assertEquals(-0.33f, douyinCrownVolumeDelta(-16.5f) * 0.06f, 0.00001f)
        val deltas = List(100) { 0.165f }
        assertEquals(douyinCrownVolumeDelta(deltas.sum()), deltas.sumOf { douyinCrownVolumeDelta(it).toDouble() }.toFloat(), 0.0001f)
        assertEquals(0f, douyinCrownVolumeDelta(Float.NaN), 0f)
    }

    @Test
    fun firstVideoBlocksReverseDragAndFlingWithoutBlockingNextVideoOrCoverEntry() {
        assertTrue(shouldBlockDouyinReverseSwipe(false, 1, 0f, 40f))
        assertTrue(shouldBlockDouyinReverseSwipe(false, 1, -0.1f, 400f))
        assertFalse(shouldBlockDouyinReverseSwipe(false, 1, 0f, -40f))
        assertFalse(shouldBlockDouyinReverseSwipe(false, 2, 0f, 40f))
        assertFalse(shouldBlockDouyinReverseSwipe(false, 1, 0.2f, 40f))
        assertFalse(shouldBlockDouyinReverseSwipe(true, 0, 0f, 40f))
    }

    @Test
    fun outgoingAudioStopsBeforeIncomingSlotCommit() {
        val outgoing = mock(Player::class.java)
        val incoming = mock(Player::class.java)
        commitDouyinForegroundAudio(outgoing) {
            incoming.volume = 1f
            incoming.play()
        }
        val order = inOrder(outgoing, incoming)
        order.verify(outgoing).volume = 0f
        order.verify(outgoing).playWhenReady = false
        order.verify(outgoing).pause()
        order.verify(incoming).volume = 1f
        order.verify(incoming).play()
    }

    @Test
    fun offlineReadinessOverridesHttpSchemeButAnUnprovenProxyStillRequiresNetwork() {
        val proxy = "http://127.0.0.1:1234/preview"
        assertTrue(douyinPlaybackNeedsInternet(proxy, DouyinOfflineReadiness.NONE))
        assertFalse(douyinPlaybackNeedsInternet(proxy, DouyinOfflineReadiness.PREFIX_READY))
        assertFalse(douyinPlaybackNeedsInternet(proxy, DouyinOfflineReadiness.FULL_READY))
        assertFalse(douyinPlaybackNeedsInternet("file:///prefix.mp4", DouyinOfflineReadiness.NONE))
    }

    @Test
    fun onlyPlayedPrefixesEndOfflineWithoutDiscardOrNetworkRecovery() {
        assertTrue(shouldEndDouyinOfflinePrefix(true, DouyinOfflineReadiness.PREFIX_READY, true))
        assertFalse(shouldEndDouyinOfflinePrefix(false, DouyinOfflineReadiness.PREFIX_READY, true))
        assertFalse(shouldEndDouyinOfflinePrefix(true, DouyinOfflineReadiness.FULL_READY, true))
        assertFalse(shouldEndDouyinOfflinePrefix(true, DouyinOfflineReadiness.NONE, true))
        assertFalse(shouldEndDouyinOfflinePrefix(true, DouyinOfflineReadiness.PREFIX_READY, false))
    }

    @Test
    fun slowAndDeadlineStatesUseTheSamePodjsNoticeAndRetainIndependentContent() {
        assertNull(douyinFeedLoadNotice(false, false))
        assertEquals("内容加载较慢，请稍候", douyinFeedLoadNotice(true, false))
        assertEquals("内容加载较慢，请稍候", douyinFeedLoadNotice(true, true))
        assertEquals("内容加载较慢，请稍候", douyinFeedLoadNotice(false, true))
    }
}
