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
    fun resolveDouyinPlaybackState_switchesToLocalSource_whenNewLocalFileArrivesLater() {
        val resolved = resolveDouyinPlaybackState(
            currentUri = "https://example.com/video.mp4",
            currentRemoteResolvedAtMs = 100L,
            localUri = "file:///tmp/aweme.mp4",
            remoteUri = "https://example.com/video.mp4",
            remoteResolvedAtMs = 100L
        )

        assertEquals("file:///tmp/aweme.mp4", resolved.mediaUri)
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

    @Test
    fun buildDouyinPlaybackPrepareKey_changesWhenRetryingSameLocalSource() {
        val firstKey = buildDouyinPlaybackPrepareKey(
            mediaUri = "file:///tmp/aweme.mp4",
            remoteResolvedAtMs = 0L,
            attemptNonce = 0
        )
        val retriedKey = buildDouyinPlaybackPrepareKey(
            mediaUri = "file:///tmp/aweme.mp4",
            remoteResolvedAtMs = 0L,
            attemptNonce = 1
        )

        assertNotEquals(firstKey, retriedKey)
    }

    @Test
    fun resolveDouyinPlaybackFailureAction_retriesWhileBudgetRemains() {
        val action = resolveDouyinPlaybackFailureAction(
            retryCount = 1,
            maxAutoRetryCount = 2,
            hasValidatedInternetConnection = true,
            hasNextItem = true
        )

        assertEquals(DouyinPlaybackFailureAction.Retry, action)
    }

    @Test
    fun resolveDouyinPlaybackFailureAction_autoSkipsWhenRetryBudgetExhausted_online_andHasNextItem() {
        val action = resolveDouyinPlaybackFailureAction(
            retryCount = 2,
            maxAutoRetryCount = 2,
            hasValidatedInternetConnection = true,
            hasNextItem = true
        )

        assertEquals(DouyinPlaybackFailureAction.AutoSkip, action)
    }

    @Test
    fun resolveDouyinPlaybackFailureAction_autoSkipsWhenOfflineAfterRetryBudgetExhausted() {
        val action = resolveDouyinPlaybackFailureAction(
            retryCount = 2,
            maxAutoRetryCount = 2,
            hasValidatedInternetConnection = false,
            hasNextItem = true
        )

        assertEquals(DouyinPlaybackFailureAction.AutoSkip, action)
    }

    @Test
    fun resolveDouyinPlaybackFailureAction_autoSkipsAtLastItemAfterRetryBudgetExhausted() {
        val action = resolveDouyinPlaybackFailureAction(
            retryCount = 2,
            maxAutoRetryCount = 2,
            hasValidatedInternetConnection = true,
            hasNextItem = false
        )

        assertEquals(DouyinPlaybackFailureAction.AutoSkip, action)
    }

    @Test
    fun resolveDouyinAutoSkipTargetPage_prefersNextVideo() {
        val targetPage = resolveDouyinAutoSkipTargetPage(
            failingPage = 2,
            pageCount = 5
        )

        assertEquals(3, targetPage)
    }

    @Test
    fun resolveDouyinAutoSkipTargetPage_fallsBackToTitlePageWhenLastVideoFails() {
        val targetPage = resolveDouyinAutoSkipTargetPage(
            failingPage = 3,
            pageCount = 4
        )

        assertEquals(0, targetPage)
    }
}
