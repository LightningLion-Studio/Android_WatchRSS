package com.lightningstudio.watchrss.port.dydata

import com.lightningstudio.watchrss.data.douyin.*
import com.lightningstudio.watchrss.testutil.*
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DouyinEntryIsolationTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun expiredMetadataAloneDoesNotReplaceRestoredQueueOrPaginationCursor() = runTest {
        val cached = listOf("a", "b", "c").map { sampleDouyinStreamItem(awemeId = it, playUrlResolvedAtMs = 0) }
        val repository = TestDouyinRepository(initialLoggedIn = true)
        val cache = TestDouyinFeedCacheStore(cached).apply {
            cachedNextCursor = "restored-cursor"
            cachedHasMore = true
        }
        val model = main.track(DouyinFeedViewModel(repository, TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(), feedCacheStore = cache,
            storageDispatcher = main.dispatcher))
        advanceUntilIdle()
        assertEquals(cached.map { it.awemeId }, model.uiState.value.items.map { it.awemeId })
        assertTrue(repository.fetchFeedPageCursors.isEmpty())
        assertEquals(listOf("a", "b"), repository.fetchVideoCalls)
        assertEquals("restored-cursor", cache.cachedNextCursor)
        model.loadMoreForList()
        advanceUntilIdle()
        assertEquals(listOf("restored-cursor"), repository.fetchFeedPageCursors)
    }

    @Test fun explicitResumeDoesNotReadOrPrependUnrelatedStock() = runTest {
        var stockReads = 0
        val cached = listOf("a", "b", "c").map { sampleDouyinStreamItem(awemeId = it) }
        val stock = sampleDouyinStreamItem(awemeId = "unrelated-stock", playUrlResolvedAtMs = System.currentTimeMillis())
        val repository = TestDouyinRepository(initialLoggedIn = true)
        val model = main.track(DouyinFeedViewModel(repository, TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(), feedCacheStore = TestDouyinFeedCacheStore(cached),
            storageDispatcher = main.dispatcher, resumeToVideoFlowOnEntry = true,
            stockReader = { stockReads++; listOf(stock) }))
        advanceUntilIdle()
        assertEquals(0, stockReads)
        assertEquals(cached.map { it.awemeId }, model.uiState.value.items.map { it.awemeId })
        assertTrue(repository.fetchFeedPageCursors.isEmpty())
    }

    @Test fun ordinaryEntryRestoresOptedInStockWithoutFetchingUnrelatedFeed() = runTest {
        var stockReads = 0
        val cached = listOf("a", "b", "c").map { sampleDouyinStreamItem(awemeId = it) }
        val stock = sampleDouyinStreamItem(awemeId = "stock", playUrlResolvedAtMs = System.currentTimeMillis())
        val repository = TestDouyinRepository(initialLoggedIn = true)
        val model = main.track(DouyinFeedViewModel(repository, TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(), feedCacheStore = TestDouyinFeedCacheStore(cached),
            storageDispatcher = main.dispatcher, stockReader = { stockReads++; listOf(stock) }))
        advanceUntilIdle()
        assertEquals(1, stockReads)
        assertEquals(listOf("stock", "a", "b", "c"), model.uiState.value.items.map { it.awemeId })
        assertTrue(repository.fetchFeedPageCursors.isEmpty())
    }
}
