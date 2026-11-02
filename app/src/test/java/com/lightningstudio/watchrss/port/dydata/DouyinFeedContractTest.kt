package com.lightningstudio.watchrss.port.dydata

import com.lightningstudio.watchrss.data.douyin.*
import com.lightningstudio.watchrss.sdk.douyin.*
import com.lightningstudio.watchrss.testutil.*
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DouyinFeedContractTest {
    @get:Rule val main = MainDispatcherRule()

    private class Repo : DouyinRepositoryContract {
        var details = 0
        var cleared = 0
        var calls = 0
        var delayMs = 0L
        var loginDelayMs = 0L
        var code = DouyinErrorCodes.OK
        var items = listOf(sampleDouyinVideo(awemeId = "fresh"))
        var nonCooperativeFirst = false
        override suspend fun isLoggedIn(): Boolean { delay(loginDelayMs); return true }
        override suspend fun buildPlayHeaders(): Map<String, String> = emptyMap()
        override suspend fun clearCookie() { cleared++ }
        override suspend fun fetchVideo(awemeId: String): DouyinResult<DouyinContent> {
            details++
            error("implicit detail request")
        }
        override suspend fun fetchFeedPage(cursor: String?, count: Int): DouyinResult<DouyinFeedPage> {
            val own = ++calls
            val snapshot = items
            if (nonCooperativeFirst && own == 1) withContext(NonCancellable) { delay(2_000) }
            else delay(delayMs)
            return DouyinResult(code, data = DouyinFeedPage(snapshot, null, false))
        }
    }

    private fun vm(repo: Repo, cached: List<DouyinStreamItem> = emptyList()): DouyinFeedViewModel = main.track(
        DouyinFeedViewModel(repository = repo, preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = cached), storageDispatcher = main.dispatcher)
    )

    @Test fun slowNoticeAndTenSecondDeadlinePreserveCachedItemsAndRetry() = runTest {
        val repo = Repo().apply { delayMs = 30_000 }
        val cached = listOf(sampleDouyinStreamItem(awemeId = "cached").copy(playUrlResolvedAtMs = System.currentTimeMillis()))
        val model = vm(repo, cached)
        runCurrent()
        advanceTimeBy(1_499); runCurrent()
        assertFalse(model.uiState.value.isSlowLoading)
        advanceTimeBy(1); runCurrent()
        assertTrue(model.uiState.value.isSlowLoading)
        advanceTimeBy(8_500); runCurrent()
        assertTrue(model.uiState.value.loadDeadlineExceeded)
        assertFalse(model.uiState.value.isLoading)
        assertEquals("cached", model.uiState.value.items.single().awemeId)
        repo.delayMs = 0
        model.retryFeedLoad()
        advanceUntilIdle()
        assertFalse(model.uiState.value.loadDeadlineExceeded)
        assertEquals("fresh", model.uiState.value.items.single().awemeId)
        assertEquals(0, repo.details)
    }

    @Test fun entryDeadlineIncludesLoginReadAndKeepsSlowNoticeContract() = runTest {
        val repo = Repo().apply { loginDelayMs = 30_000 }
        val model = vm(repo)
        runCurrent()
        advanceTimeBy(1_500); runCurrent()
        assertTrue(model.uiState.value.isSlowLoading)
        advanceTimeBy(8_500); runCurrent()
        assertTrue(model.uiState.value.loadDeadlineExceeded)
        assertFalse(model.uiState.value.isLoading)
        assertEquals(0, repo.calls)
    }

    @Test fun cancelledOlderRequestCannotOverwriteRetry() = runTest {
        val repo = Repo().apply { nonCooperativeFirst = true }
        val model = vm(repo)
        runCurrent()
        repo.items = listOf(sampleDouyinVideo(awemeId = "retry"))
        model.retryFeedLoad()
        advanceUntilIdle()
        assertEquals("retry", model.uiState.value.items.single().awemeId)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun challengeKeepsLoginCacheAndCookieAndNeverCallsDetail() = runTest {
        val repo = Repo().apply { code = DouyinErrorCodes.CHALLENGE }
        val model = vm(repo, listOf(sampleDouyinStreamItem(awemeId = "cached")))
        advanceUntilIdle()
        assertTrue(model.uiState.value.isLoggedIn)
        assertEquals("cached", model.uiState.value.items.single().awemeId)
        assertEquals(0, repo.cleared)
        assertEquals(0, repo.details)
        model.refreshPlaybackSource("cached", DouyinPlaybackSourceKind.REMOTE)
        advanceUntilIdle()
        assertEquals(0, repo.details)
        assertEquals(0, repo.cleared)
        assertTrue(model.uiState.value.isLoggedIn)
    }

    @Test fun cancelResetsPendingNoticeWithoutDeletingContent() = runTest {
        val repo = Repo().apply { delayMs = 30_000 }
        val model = vm(repo)
        runCurrent()
        advanceTimeBy(1_500); runCurrent()
        model.cancelFeedLoad()
        advanceUntilIdle()
        assertFalse(model.uiState.value.isSlowLoading)
        assertFalse(model.uiState.value.isLoading)
        assertFalse(model.uiState.value.loadDeadlineExceeded)
    }

    @Test fun expiredQueueReplenishesFromFeedEvenWhenIdsChangeAndCoalescesWarmups() = runTest {
        val repo = Repo()
        val cache = TestDouyinFeedCacheStore()
        val coordinator = DouyinPlaybackSourceCoordinator(backgroundScope, repo, cache, TestDouyinRecentWindowStore())
        val old = listOf(sampleDouyinStreamItem(awemeId = "expired").copy(playUrlResolvedAtMs = 0))
        repeat(2) {
            val refreshed = refreshExpiredDouyinBootstrapPlayUrls(old, coordinator, DouyinPlaybackRefreshTrigger.STARTUP_TTL)
            assertEquals("fresh", refreshed.items.single().awemeId)
        }
        assertEquals("fresh", cache.readSnapshot().items.single().awemeId)
        assertEquals(1, repo.calls)
        assertEquals(0, repo.details)
    }

    @Test fun sourceCoordinatorUsesCandidatesThenBoundedFeedAndNeverDetail() = runTest {
        val repo = Repo()
        val coordinator = DouyinPlaybackSourceCoordinator(backgroundScope, repo,
            TestDouyinFeedCacheStore(), TestDouyinRecentWindowStore())
        val urls = (1..5).map { "https://cdn$it/video" }
        var item = sampleDouyinStreamItem(awemeId = "missing-in-feed").copy(playUrl = urls[0], variants = listOf(
            DouyinVideoVariant(urls[0], DouyinVideoCodec.H264, 200_000, 480, 854, playUrls = urls)))
        repeat(2) {
            val event = coordinator.refresh(item, DouyinPlaybackRefreshTrigger.PLAYBACK_ERROR)
            assertEquals(DouyinPlaybackRefreshOutcome.SUCCESS, event.outcome)
            item = event.item!!
        }
        repeat(2) {
            assertEquals(DouyinPlaybackRefreshOutcome.FAILURE,
                coordinator.refresh(item, DouyinPlaybackRefreshTrigger.PLAYBACK_ERROR).outcome)
        }
        assertEquals(1, repo.calls)
        assertEquals(0, repo.details)
    }
}
