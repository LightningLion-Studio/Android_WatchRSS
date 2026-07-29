package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.testutil.TestDouyinFeedCacheStore
import com.lightningstudio.watchrss.testutil.TestDouyinRecentWindowStore
import com.lightningstudio.watchrss.testutil.TestDouyinRepository
import com.lightningstudio.watchrss.testutil.sampleDouyinStreamItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DouyinPlaybackSourceCoordinatorTest {
    @Test
    fun refresh_singleFlightsConcurrentCallers_andPersistsBothStores() = runTest {
        val staleItem = sampleDouyinStreamItem(
            awemeId = "aweme-a",
            playUrl = "https://example.com/stale.mp4",
            playUrlResolvedAtMs = 100L
        )
        val feedStore = TestDouyinFeedCacheStore(initialItems = listOf(staleItem)).apply {
            cachedNextCursor = "cursor-1"
            cachedHasMore = true
        }
        val recentStore = TestDouyinRecentWindowStore(
            initialSnapshot = DouyinRecentWindowSnapshot(
                items = listOf(staleItem, sampleDouyinStreamItem(awemeId = "aweme-b")),
                anchorAwemeId = staleItem.awemeId,
                savedAtMs = 100L
            )
        )
        val gate = CompletableDeferred<Unit>()
        val repository = TestDouyinRepository(initialLoggedIn = true).apply {
            fetchVideoGate = gate
            setVideoResult(
                staleItem.awemeId,
                DouyinResult(
                    code = DouyinErrorCodes.OK,
                    data = DouyinContent.Video(
                        awemeId = staleItem.awemeId,
                        desc = "fresh",
                        authorName = "author",
                        diggCount = 10L,
                        playUrl = "https://example.com/fresh.mp4",
                        coverUrl = ""
                    )
                )
            )
        }
        val coordinatorScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val coordinator = DouyinPlaybackSourceCoordinator(
            appScope = coordinatorScope,
            repository = repository,
            feedCacheStore = feedStore,
            recentWindowStore = recentStore
        )

        val startup = async {
            coordinator.refresh(staleItem, DouyinPlaybackRefreshTrigger.STARTUP_TTL)
        }
        val foreground = async {
            coordinator.refresh(staleItem, DouyinPlaybackRefreshTrigger.FOREGROUND_HTTP_403)
        }
        advanceUntilIdle()
        assertEquals(listOf(staleItem.awemeId), repository.fetchVideoCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(DouyinPlaybackRefreshOutcome.SUCCESS, startup.await().outcome)
        assertEquals(
            DouyinPlaybackRefreshTrigger.FOREGROUND_HTTP_403,
            foreground.await().trigger
        )
        assertEquals("https://example.com/fresh.mp4", feedStore.cachedItems.single().playUrl)
        assertEquals("cursor-1", feedStore.cachedNextCursor)
        assertEquals(true, feedStore.cachedHasMore)
        assertEquals(
            listOf("aweme-a", "aweme-b"),
            recentStore.snapshot.items.map { it.awemeId }
        )
        assertEquals("aweme-a", recentStore.snapshot.anchorAwemeId)
        assertEquals(
            "https://example.com/fresh.mp4",
            recentStore.snapshot.items.first().playUrl
        )
        coordinatorScope.cancel()
    }
}
