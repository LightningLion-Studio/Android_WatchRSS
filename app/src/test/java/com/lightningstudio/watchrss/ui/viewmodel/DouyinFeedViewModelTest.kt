package com.lightningstudio.watchrss.ui.viewmodel

import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewCache
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceKind
import com.lightningstudio.watchrss.data.douyin.DouyinResult
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowSnapshot
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowCacheCoordinatorContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestDouyinFeedCacheStore
import com.lightningstudio.watchrss.testutil.TestDouyinPreloadManager
import com.lightningstudio.watchrss.testutil.TestDouyinRecentWindowCacheCoordinator
import com.lightningstudio.watchrss.testutil.TestDouyinRecentWindowStore
import com.lightningstudio.watchrss.testutil.TestDouyinRepository
import com.lightningstudio.watchrss.testutil.TestDouyinWatchHistoryStore
import com.lightningstudio.watchrss.testutil.sampleDouyinStreamItem
import com.lightningstudio.watchrss.testutil.sampleDouyinVideo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DouyinFeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun newViewModel(
        repository: TestDouyinRepository,
        preloadManager: TestDouyinPreloadManager,
        watchHistoryStore: TestDouyinWatchHistoryStore,
        feedCacheStore: TestDouyinFeedCacheStore,
        recentWindowStore: DouyinRecentWindowStoreContract = com.lightningstudio.watchrss.data.douyin.NoOpDouyinRecentWindowStore,
        recentWindowCacheCoordinator: DouyinRecentWindowCacheCoordinatorContract = com.lightningstudio.watchrss.data.douyin.NoOpDouyinRecentWindowCacheCoordinator
    ): DouyinFeedViewModel {
        return DouyinFeedViewModel(
            repository = repository,
            preloadManager = preloadManager,
            watchHistoryStore = watchHistoryStore,
            feedCacheStore = feedCacheStore,
            recentWindowStore = recentWindowStore,
            recentWindowCacheCoordinator = recentWindowCacheCoordinator,
            mainDispatcherRule.dispatcher
        )
    }

    @After
    fun tearDown() {
        DouyinPlaybackPreviewCache.resetForTests()
    }

    @Test
    fun init_whenLatestWatchedExistsInCachedItems_startsFromNextItem() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(second)
        }

        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(first, second, third))
        )
        advanceUntilIdle()

        assertEquals(listOf("aweme-a", "aweme-b", "aweme-c"), viewModel.uiState.value.items.map { it.awemeId })
        assertTrue(viewModel.uiState.value.showTitlePage)
        assertEquals(3, viewModel.uiState.value.currentPage)
    }

    @Test
    fun init_mergesRecentWindowIntoBootstrapAndResumesFromMergedTarget() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val fourth = sampleDouyinStreamItem(awemeId = "aweme-d")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(first)
        }
        val recentWindowStore = TestDouyinRecentWindowStore(
            initialSnapshot = DouyinRecentWindowSnapshot(
                items = listOf(first, second, third, fourth),
                anchorAwemeId = second.awemeId,
                savedAtMs = 5L
            )
        )

        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(first, third, fourth)),
            recentWindowStore = recentWindowStore
        )
        advanceUntilIdle()

        assertEquals(listOf("aweme-a", "aweme-b", "aweme-c", "aweme-d"), viewModel.uiState.value.items.map { it.awemeId })
        assertTrue(viewModel.uiState.value.showTitlePage)
        assertEquals(2, viewModel.uiState.value.currentPage)
    }

    @Test
    fun init_doesNotScheduleFullFilePreloadFromBootstrapRestore() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val preloadManager = TestDouyinPreloadManager()
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(second)
        }

        newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = preloadManager,
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(first, second, third))
        )
        advanceUntilIdle()

        assertEquals(0, preloadManager.ensurePlaybackWindowCalls)
        assertEquals(0, preloadManager.ensureCalls)
    }

    @Test
    fun init_doesNotTriggerBackgroundFullFileCacheFill() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val preloadManager = TestDouyinPreloadManager()

        newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = preloadManager,
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(first, second, third))
        )
        advanceUntilIdle()

        assertEquals(0, preloadManager.ensurePlaybackWindowCalls)
        assertEquals(0, preloadManager.ensureCalls)
    }

    @Test
    fun init_ignoresPreloadCallbackPathsBecauseImmersiveFlowNoLongerWritesMp4Files() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val preloadManager = TestDouyinPreloadManager().apply {
            callbackPaths["aweme-b"] = "/tmp/aweme-b.mp4"
        }

        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = preloadManager,
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(first, second))
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.localPlayPaths["aweme-b"])
    }

    @Test
    fun init_preservesCachedSequenceEvenWhenLocalCacheExistsOnDifferentItem() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val preloadManager = TestDouyinPreloadManager().apply {
            localPaths["aweme-c"] = "/tmp/aweme-c.mp4"
        }
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(second)
        }

        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = preloadManager,
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(first, second, third))
        )
        advanceUntilIdle()

        assertEquals(listOf("aweme-a", "aweme-b", "aweme-c"), viewModel.uiState.value.items.map { it.awemeId })
        assertEquals(3, viewModel.uiState.value.currentPage)
    }

    @Test
    fun init_whenBootstrapWindowIsTooShort_refreshesInitialFeedAfterRestore() = runTest {
        val cachedFirst = sampleDouyinStreamItem(awemeId = "cached-a")
        val cachedLast = sampleDouyinStreamItem(awemeId = "cached-b")
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(cachedLast)
        }
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(
                listOf(
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(
                                sampleDouyinVideo(awemeId = "fresh-a"),
                                sampleDouyinVideo(awemeId = "fresh-b")
                            ),
                            nextCursor = "cursor-next",
                            hasMore = true
                        )
                    )
                )
            )
        }

        val viewModel = newViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(cachedFirst, cachedLast))
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTitlePage)
        assertEquals(1, viewModel.uiState.value.currentPage)
        assertEquals(listOf("cached-a", "fresh-a", "fresh-b"), viewModel.uiState.value.items.map { it.awemeId })
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun init_whenRestoreTargetIsNearBootstrapTail_refreshesToRecoverForwardRunway() = runTest {
        val cachedItems = (0 until 16).map { index ->
            sampleDouyinStreamItem(awemeId = "cached-$index")
        }
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(cachedItems[14])
        }
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(
                listOf(
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(
                                sampleDouyinVideo(awemeId = "fresh-a"),
                                sampleDouyinVideo(awemeId = "fresh-b"),
                                sampleDouyinVideo(awemeId = "fresh-c")
                            ),
                            nextCursor = "cursor-next",
                            hasMore = true
                        )
                    )
                )
            )
        }

        val viewModel = newViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = cachedItems)
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTitlePage)
        assertEquals(16, viewModel.uiState.value.currentPage)
        assertEquals(
            cachedItems.map { it.awemeId } + listOf("fresh-a", "fresh-b", "fresh-c"),
            viewModel.uiState.value.items.map { it.awemeId }
        )
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun refreshTitlePageFeed_replacesOldFlowAndClearsPinnedState() = runTest {
        val snapshotDir = Files.createTempDirectory("douyin-refresh-title").toFile()
        DouyinPlaybackPreviewCache.configureForTests(snapshotDir)
        DouyinPlaybackPreviewCache.writeSnapshotForTests(
            0,
            sampleDouyinStreamItem(
                awemeId = "pinned-old",
                playUrl = "https://example.com/pinned-old.mp4",
                playUrlResolvedAtMs = 10L
            ),
            byteArrayOf(1, 2, 3)
        )
        val recentWindowStore = TestDouyinRecentWindowStore(
            initialSnapshot = DouyinRecentWindowSnapshot(
                items = listOf(sampleDouyinStreamItem(awemeId = "recent-old")),
                anchorAwemeId = "recent-old",
                savedAtMs = 12L
            )
        )
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(
                listOf(
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(
                                sampleDouyinVideo(awemeId = "fresh-a"),
                                sampleDouyinVideo(awemeId = "fresh-b")
                            ),
                            nextCursor = "cursor-fresh",
                            hasMore = true
                        )
                    )
                )
            )
        }
        val feedCacheStore = TestDouyinFeedCacheStore(
            initialItems = listOf(
                sampleDouyinStreamItem(awemeId = "old-a"),
                sampleDouyinStreamItem(awemeId = "old-b")
            )
        ).apply {
            cachedNextCursor = "cursor-old"
            cachedHasMore = true
        }
        val viewModel = newViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = feedCacheStore,
            recentWindowStore = recentWindowStore
        )
        advanceUntilIdle()

        viewModel.refreshTitlePageFeed()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTitlePage)
        assertEquals(0, viewModel.uiState.value.currentPage)
        assertEquals(listOf("fresh-a", "fresh-b"), viewModel.uiState.value.items.map { it.awemeId })
        assertEquals(listOf("fresh-a", "fresh-b"), feedCacheStore.cachedItems.map { it.awemeId })
        assertEquals("cursor-fresh", feedCacheStore.cachedNextCursor)
        assertTrue(recentWindowStore.snapshot.items.isEmpty())
        assertTrue(DouyinPlaybackPreviewCache.restorePinnedItems().isEmpty())
    }

    @Test
    fun loadMore_afterBootstrapRestore_usesPersistedNextCursorInsteadOfRestartingFromFirstPage() = runTest {
        val cachedItems = listOf(
            sampleDouyinStreamItem(awemeId = "cached-a"),
            sampleDouyinStreamItem(awemeId = "cached-b"),
            sampleDouyinStreamItem(awemeId = "cached-c")
        )
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(
                listOf(
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(sampleDouyinVideo(awemeId = "fresh-d")),
                            nextCursor = "cursor-after-2",
                            hasMore = true
                        )
                    )
                )
            )
        }
        val feedCacheStore = TestDouyinFeedCacheStore(initialItems = cachedItems).apply {
            cachedNextCursor = "cursor-after-1"
            cachedHasMore = true
        }
        val viewModel = newViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = feedCacheStore
        )
        advanceUntilIdle()

        viewModel.onPageSettled(3)
        advanceUntilIdle()

        assertEquals(listOf("cursor-after-1"), repo.fetchFeedPageCursors)
        assertEquals(
            listOf("cached-a", "cached-b", "cached-c", "fresh-d"),
            viewModel.uiState.value.items.map { it.awemeId }
        )
    }

    @Test
    fun init_whenBootstrapHasMoreButCursorMissing_primesCursorImmediately() = runTest {
        val cachedItems = listOf(
            sampleDouyinStreamItem(awemeId = "cached-a"),
            sampleDouyinStreamItem(awemeId = "cached-b"),
            sampleDouyinStreamItem(awemeId = "cached-c"),
            sampleDouyinStreamItem(awemeId = "cached-d")
        )
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(
                listOf(
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(
                                sampleDouyinVideo(awemeId = "fresh-a"),
                                sampleDouyinVideo(awemeId = "fresh-b")
                            ),
                            nextCursor = "cursor-primed",
                            hasMore = true
                        )
                    )
                )
            )
        }
        val feedCacheStore = TestDouyinFeedCacheStore(initialItems = cachedItems).apply {
            cachedNextCursor = null
            cachedHasMore = true
        }

        val viewModel = newViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = feedCacheStore
        )
        advanceUntilIdle()

        assertEquals(listOf(null), repo.fetchFeedPageCursors)
        assertEquals("cursor-primed", feedCacheStore.cachedNextCursor)
        assertEquals(listOf("fresh-a", "fresh-b"), viewModel.uiState.value.items.map { it.awemeId })
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun enterVideoFlow_fromDeferredEntryTarget_revealsStoredTargetPage() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(second)
        }
        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(first, second, third))
        )
        advanceUntilIdle()

        viewModel.enterVideoFlow()

        assertFalse(viewModel.uiState.value.showTitlePage)
        assertEquals(3, viewModel.uiState.value.currentPage)
    }

    @Test
    fun onPageSettled_fromTitlePageTransition_preservesDeferredTargetUntilActualTargetSettles() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(second)
        }
        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(first, second, third))
        )
        advanceUntilIdle()

        viewModel.onPageSettled(1)

        assertFalse(viewModel.uiState.value.showTitlePage)
        assertEquals(3, viewModel.uiState.value.currentPage)
        assertEquals("aweme-b", historyStore.readHistory().first().awemeId)
    }

    @Test
    fun enterVideoFlow_withExplicitAwemeId_prefersThatItemOverHistoryAdvance() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(second)
        }
        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(first, second, third))
        )
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.currentPage)

        viewModel.enterVideoFlow(targetAwemeId = second.awemeId)

        assertFalse(viewModel.uiState.value.showTitlePage)
        assertEquals(2, viewModel.uiState.value.currentPage)
    }

    @Test
    fun discardPlaybackItem_removesStandbyItemWithoutShiftingCurrentPage() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val fourth = sampleDouyinStreamItem(awemeId = "aweme-d")
        val preloadManager = TestDouyinPreloadManager().apply {
            localPaths[third.awemeId] = "/tmp/${third.awemeId}.mp4"
        }
        val feedCacheStore = TestDouyinFeedCacheStore(
            initialItems = listOf(first, second, third, fourth)
        ).apply {
            cachedHasMore = false
        }
        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = preloadManager,
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = feedCacheStore
        )
        advanceUntilIdle()

        viewModel.enterVideoFlow(targetAwemeId = second.awemeId)
        advanceUntilIdle()

        viewModel.discardPlaybackItem(third.awemeId)
        advanceUntilIdle()

        assertEquals(listOf("aweme-a", "aweme-b", "aweme-d"), viewModel.uiState.value.items.map { it.awemeId })
        assertEquals(2, viewModel.uiState.value.currentPage)
        assertFalse(viewModel.uiState.value.localPlayPaths.containsKey(third.awemeId))
        assertEquals(listOf("aweme-a", "aweme-b", "aweme-c", "aweme-d"), feedCacheStore.cachedItems.map { it.awemeId })
    }

    @Test
    fun onPageSettled_persistsRecentWindowWithoutEnqueuingDurableFullFileCache() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val fourth = sampleDouyinStreamItem(awemeId = "aweme-d")
        val fifth = sampleDouyinStreamItem(awemeId = "aweme-e")
        val recentWindowStore = TestDouyinRecentWindowStore()
        val recentWindowCoordinator = TestDouyinRecentWindowCacheCoordinator()
        val feedCacheStore = TestDouyinFeedCacheStore(
            initialItems = listOf(first, second, third, fourth, fifth)
        )
        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = feedCacheStore,
            recentWindowStore = recentWindowStore,
            recentWindowCacheCoordinator = recentWindowCoordinator
        )
        advanceUntilIdle()

        viewModel.onPageSettled(3)
        advanceUntilIdle()

        assertEquals(listOf("aweme-b", "aweme-c", "aweme-d", "aweme-e"), recentWindowStore.snapshot.items.map { it.awemeId })
        assertEquals("aweme-c", recentWindowStore.snapshot.anchorAwemeId)
        assertEquals(0, recentWindowCoordinator.enqueueCalls)
        assertEquals(
            listOf("aweme-a", "aweme-b", "aweme-c", "aweme-d", "aweme-e"),
            feedCacheStore.cachedItems.map { it.awemeId }
        )
    }

    @Test
    fun init_prependsPinnedPreviewSnapshotsAheadOfBootstrapFeed() = runTest {
        val snapshotDir = Files.createTempDirectory("douyin-preview-test").toFile()
        DouyinPlaybackPreviewCache.configureForTests(snapshotDir)
        val pinnedFirst = sampleDouyinStreamItem(
            awemeId = "aweme-pinned-1",
            playUrl = "https://example.com/pinned-1.mp4",
            playUrlResolvedAtMs = 11L
        )
        val pinnedSecond = sampleDouyinStreamItem(
            awemeId = "aweme-pinned-2",
            playUrl = "https://example.com/pinned-2.mp4",
            playUrlResolvedAtMs = 22L
        )
        DouyinPlaybackPreviewCache.writeSnapshotForTests(0, pinnedFirst, byteArrayOf(1, 2, 3))
        DouyinPlaybackPreviewCache.writeSnapshotForTests(1, pinnedSecond, byteArrayOf(4, 5, 6))

        val cachedFirst = sampleDouyinStreamItem(awemeId = "aweme-a")
        val cachedPinnedDuplicate = pinnedSecond.copy(sourceOrigin = DouyinSourceOrigin.BOOTSTRAP_CACHE)

        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(cachedFirst, cachedPinnedDuplicate))
        )
        advanceUntilIdle()

        assertEquals(
            listOf("aweme-pinned-1", "aweme-pinned-2", "aweme-a"),
            viewModel.uiState.value.items.map { it.awemeId }
        )
    }

    @Test
    fun init_withoutHistory_keepsTitlePage() = runTest {
        val viewModel = newViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(
                initialItems = listOf(
                    sampleDouyinStreamItem(awemeId = "aweme-a"),
                    sampleDouyinStreamItem(awemeId = "aweme-b"),
                    sampleDouyinStreamItem(awemeId = "aweme-c")
                )
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTitlePage)
        assertEquals(0, viewModel.uiState.value.currentPage)
        assertEquals(listOf("aweme-a", "aweme-b", "aweme-c"), viewModel.uiState.value.items.map { it.awemeId })
    }

    @Test
    fun init_withoutCachedItems_triggersInitialRefresh() = runTest {
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(
                listOf(
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(
                                sampleDouyinVideo(awemeId = "fresh-a"),
                                sampleDouyinVideo(awemeId = "fresh-b")
                            ),
                            nextCursor = "next-cursor",
                            hasMore = true
                        )
                    )
                )
            )
        }
        val feedCacheStore = TestDouyinFeedCacheStore()
        val viewModel = newViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = feedCacheStore
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTitlePage)
        assertEquals(0, viewModel.uiState.value.currentPage)
        assertEquals(listOf("fresh-a", "fresh-b"), viewModel.uiState.value.items.map { it.awemeId })
        assertTrue(feedCacheStore.savedSnapshots.isNotEmpty())
    }

    @Test
    fun loadInitial_manualRefresh_preservesCurrentPageInsteadOfReapplyingEntryResume() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val fourth = sampleDouyinStreamItem(awemeId = "aweme-d")
        val fifth = sampleDouyinStreamItem(awemeId = "aweme-e")
        val sixth = sampleDouyinStreamItem(awemeId = "aweme-f")
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(first)
        }
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(
                listOf(
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(
                                sampleDouyinVideo(awemeId = "aweme-a"),
                                sampleDouyinVideo(awemeId = "aweme-b"),
                                sampleDouyinVideo(awemeId = "aweme-c"),
                                sampleDouyinVideo(awemeId = "aweme-d"),
                                sampleDouyinVideo(awemeId = "aweme-e"),
                                sampleDouyinVideo(awemeId = "aweme-f")
                            ),
                            nextCursor = null,
                            hasMore = true
                        )
                    ),
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = emptyList(),
                            nextCursor = null,
                            hasMore = false
                        )
                    ),
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(
                                sampleDouyinVideo(awemeId = "aweme-a"),
                                sampleDouyinVideo(awemeId = "aweme-b"),
                                sampleDouyinVideo(awemeId = "aweme-c"),
                                sampleDouyinVideo(awemeId = "aweme-d")
                            ),
                            nextCursor = null,
                            hasMore = false
                        )
                    )
                )
            )
        }
        val feedCacheStore = TestDouyinFeedCacheStore(
            initialItems = listOf(first, second, third, fourth, fifth, sixth)
        ).apply {
            cachedHasMore = true
        }
        val viewModel = newViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = feedCacheStore
        )
        advanceUntilIdle()

        viewModel.onPageSettled(3)
        advanceUntilIdle()

        viewModel.loadInitial()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showTitlePage)
        assertEquals(3, viewModel.uiState.value.currentPage)
        assertEquals(listOf("aweme-a", "aweme-b", "aweme-c", "aweme-d"), viewModel.uiState.value.items.map { it.awemeId })
    }

    @Test
    fun loadInitial_replacesPreservedCachedPrefix_withFreshPlaybackMetadata() = runTest {
        val awemeId = "aweme-cache"
        val cachedItem = sampleDouyinStreamItem(
            awemeId = awemeId,
            playUrl = "https://example.com/old.mp4",
            title = "旧标题",
            author = "旧作者",
            likeCount = 3L,
            playUrlResolvedAtMs = 0L,
            sourceOrigin = DouyinSourceOrigin.BOOTSTRAP_CACHE
        )
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(
                listOf(
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(
                                sampleDouyinVideo(awemeId = awemeId, desc = "新标题").apply {
                                    authorName = "新作者"
                                    likeCount = 99L
                                    playUrl = "https://example.com/fresh.mp4"
                                }
                            ),
                            nextCursor = null,
                            hasMore = false
                        )
                    )
                )
            )
        }
        val viewModel = newViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(
                initialItems = listOf(
                    cachedItem,
                    sampleDouyinStreamItem(awemeId = "aweme-extra-1"),
                    sampleDouyinStreamItem(awemeId = "aweme-extra-2")
                )
            )
        )
        advanceUntilIdle()

        val initial = viewModel.uiState.value.items.first { it.awemeId == awemeId }
        assertEquals("https://example.com/old.mp4", initial.playUrl)

        viewModel.enterVideoFlow()
        viewModel.loadInitial()
        advanceUntilIdle()

        val refreshed = viewModel.uiState.value.items.first { it.awemeId == awemeId }
        assertEquals("https://example.com/fresh.mp4", refreshed.playUrl)
        assertEquals("新标题", refreshed.title)
        assertEquals("新作者", refreshed.author)
        assertEquals(99L, refreshed.likeCount)
        assertEquals(DouyinSourceOrigin.NETWORK_FEED, refreshed.sourceOrigin)
        assertTrue(refreshed.playUrlResolvedAtMs > 0L)
    }

    @Test
    fun refreshPlaybackSource_localFailure_invalidatesLocalCache_and_updatesVideoSource() = runTest {
        val awemeId = "aweme-local"
        val cachedItem = sampleDouyinStreamItem(
            awemeId = awemeId,
            playUrl = "https://example.com/original.mp4"
        )
        val preloadManager = TestDouyinPreloadManager().apply {
            localPaths[awemeId] = "/tmp/$awemeId.mp4"
        }
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            setVideoResult(
                awemeId,
                DouyinResult(
                    code = DouyinErrorCodes.OK,
                    data = DouyinContent.Video(
                        awemeId = awemeId,
                        desc = "刷新后标题",
                        authorName = "刷新后作者",
                        diggCount = 18L,
                        playUrl = "https://example.com/refreshed.mp4",
                        coverUrl = "https://example.com/refreshed.jpg"
                    )
                )
            )
        }
        val viewModel = newViewModel(
            repository = repo,
            preloadManager = preloadManager,
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(
                initialItems = listOf(
                    cachedItem,
                    sampleDouyinStreamItem(awemeId = "aweme-extra-1"),
                    sampleDouyinStreamItem(awemeId = "aweme-extra-2")
                )
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.localPlayPaths.containsKey(awemeId))
        val initialResolvedAtMs = viewModel.uiState.value.items.first { it.awemeId == awemeId }.playUrlResolvedAtMs

        viewModel.refreshPlaybackSource(awemeId, DouyinPlaybackSourceKind.LOCAL)
        advanceUntilIdle()

        val refreshed = viewModel.uiState.value.items.first { it.awemeId == awemeId }
        assertEquals(listOf(awemeId), preloadManager.invalidatedIds)
        assertFalse(viewModel.uiState.value.localPlayPaths.containsKey(awemeId))
        assertEquals("https://example.com/refreshed.mp4", refreshed.playUrl)
        assertEquals("刷新后标题", refreshed.title)
        assertEquals("刷新后作者", refreshed.author)
        assertEquals(18L, refreshed.likeCount)
        assertEquals(DouyinSourceOrigin.VIDEO_REFRESH, refreshed.sourceOrigin)
        assertTrue(refreshed.playUrlResolvedAtMs >= initialResolvedAtMs)
        assertEquals(listOf(awemeId), repo.fetchVideoCalls)
    }

    @Test
    fun refreshPlaybackSource_remoteFailure_keepsSameUrl_but_refreshesTimestamp() = runTest {
        val awemeId = "aweme-remote"
        val initialItem = sampleDouyinStreamItem(
            awemeId = awemeId,
            playUrl = "https://example.com/still-valid.mp4",
            playUrlResolvedAtMs = 100L
        )
        val repo = TestDouyinRepository(initialLoggedIn = true).apply {
            feedPageResults = ArrayDeque(
                listOf(
                    DouyinResult(
                        code = DouyinErrorCodes.OK,
                        data = DouyinFeedPage(
                            items = listOf(
                                sampleDouyinVideo(awemeId = awemeId).apply {
                                    playUrl = initialItem.playUrl
                                }
                            ),
                            nextCursor = null,
                            hasMore = false
                        )
                    )
                )
            )
            setVideoResult(
                awemeId,
                DouyinResult(
                    code = DouyinErrorCodes.OK,
                    data = DouyinContent.Video(
                        awemeId = awemeId,
                        desc = initialItem.title.orEmpty(),
                        authorName = initialItem.author.orEmpty(),
                        diggCount = initialItem.likeCount,
                        playUrl = initialItem.playUrl,
                        coverUrl = initialItem.coverUrl.orEmpty()
                    )
                )
            )
        }
        val cacheStore = TestDouyinFeedCacheStore(initialItems = listOf(initialItem))
        val viewModel = newViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = cacheStore
        )
        advanceUntilIdle()

        viewModel.refreshPlaybackSource(awemeId, DouyinPlaybackSourceKind.REMOTE)
        advanceUntilIdle()

        val refreshed = viewModel.uiState.value.items.single()
        assertEquals(initialItem.playUrl, refreshed.playUrl)
        assertTrue(refreshed.playUrlResolvedAtMs > initialItem.playUrlResolvedAtMs)
        assertEquals(DouyinSourceOrigin.VIDEO_REFRESH, refreshed.sourceOrigin)
        assertTrue(cacheStore.savedSnapshots.isNotEmpty())
        assertEquals(refreshed.playUrlResolvedAtMs, cacheStore.savedSnapshots.last().first().playUrlResolvedAtMs)
    }
}
