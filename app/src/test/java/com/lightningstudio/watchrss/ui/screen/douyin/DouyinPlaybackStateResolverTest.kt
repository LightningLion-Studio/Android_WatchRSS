package com.lightningstudio.watchrss.ui.screen.douyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DouyinPlaybackStateResolverTest {
    @Test
    fun resolveDouyinPlaybackState_switchesFromRemovedLocalFile_toRemoteSource() {
        val resolved = resolveDouyinPlaybackState(
            currentUri = "file:///tmp/aweme.mp4",
            currentRemoteResolvedAtMs = 100L,
            localUri = null,
            remoteUri = "https://example.com/video.mp4",
            remoteResolvedAtMs = 200L
        )

        assertEquals("https://example.com/video.mp4", resolved.mediaUri)
        assertEquals(200L, resolved.remoteResolvedAtMs)
    }

    @Test
    fun resolveDouyinPlaybackState_keepsRemoteSource_whenNewLocalFileArrivesLater() {
        val resolved = resolveDouyinPlaybackState(
            currentUri = "https://example.com/video.mp4",
            currentRemoteResolvedAtMs = 100L,
            localUri = "file:///tmp/aweme.mp4",
            remoteUri = "https://example.com/video.mp4",
            remoteResolvedAtMs = 100L
        )

        assertEquals("https://example.com/video.mp4", resolved.mediaUri)
        assertEquals(100L, resolved.remoteResolvedAtMs)
    }

    @Test
    fun buildDouyinPlaybackPrepareKey_changesWhenRemoteSourceIsRefreshed() {
        val oldKey = buildDouyinPlaybackPrepareKey(
            mediaUri = "https://example.com/video.mp4",
            remoteResolvedAtMs = 100L
        )
        val refreshedKey = buildDouyinPlaybackPrepareKey(
            mediaUri = "https://example.com/video.mp4",
            remoteResolvedAtMs = 200L
        )

        assertNotEquals(oldKey, refreshedKey)
    }
}
