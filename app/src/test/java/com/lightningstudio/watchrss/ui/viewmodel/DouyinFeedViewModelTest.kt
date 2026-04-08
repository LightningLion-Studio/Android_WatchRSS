package com.lightningstudio.watchrss.ui.viewmodel

import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceKind
import com.lightningstudio.watchrss.data.douyin.DouyinResult
import com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestDouyinFeedCacheStore
import com.lightningstudio.watchrss.testutil.TestDouyinPreloadManager
import com.lightningstudio.watchrss.testutil.TestDouyinRepository
import com.lightningstudio.watchrss.testutil.TestDouyinWatchHistoryStore
import com.lightningstudio.watchrss.testutil.sampleDouyinStreamItem
import com.lightningstudio.watchrss.testutil.sampleDouyinVideo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DouyinFeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_whenLatestWatchedExistsInCachedItems_startsFromNextItem() = runTest {
        val first = sampleDouyinStreamItem(awemeId = "aweme-a")
        val second = sampleDouyinStreamItem(awemeId = "aweme-b")
        val third = sampleDouyinStreamItem(awemeId = "aweme-c")
        val historyStore = TestDouyinWatchHistoryStore().apply {
            markWatched(second)
        }

        val viewModel = DouyinFeedViewModel(
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

        val viewModel = DouyinFeedViewModel(
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
    fun init_whenLatestWatchedIsLastCachedItem_usesCachedFirstItemWithoutRefreshing() = runTest {
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

        val viewModel = DouyinFeedViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(cachedFirst, cachedLast))
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTitlePage)
        assertEquals(1, viewModel.uiState.value.currentPage)
        assertEquals(listOf("cached-a", "cached-b"), viewModel.uiState.value.items.map { it.awemeId })
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
        val viewModel = DouyinFeedViewModel(
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
        val viewModel = DouyinFeedViewModel(
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
    fun init_withoutHistory_keepsTitlePage() = runTest {
        val viewModel = DouyinFeedViewModel(
            repository = TestDouyinRepository(initialLoggedIn = true),
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(
                initialItems = listOf(
                    sampleDouyinStreamItem(awemeId = "aweme-a"),
                    sampleDouyinStreamItem(awemeId = "aweme-b")
                )
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTitlePage)
        assertEquals(0, viewModel.uiState.value.currentPage)
        assertEquals(listOf("aweme-a", "aweme-b"), viewModel.uiState.value.items.map { it.awemeId })
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
        val viewModel = DouyinFeedViewModel(
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
        val repo = TestDouyinRepository(initialLoggedIn = true)
        val viewModel = DouyinFeedViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = historyStore,
            feedCacheStore = TestDouyinFeedCacheStore(
                initialItems = listOf(first, second, third, fourth, fifth, sixth)
            )
        )
        advanceUntilIdle()

        repo.feedPageResults = ArrayDeque(
            listOf(
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
        val viewModel = DouyinFeedViewModel(
            repository = repo,
            preloadManager = TestDouyinPreloadManager(),
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(cachedItem))
        )
        advanceUntilIdle()

        assertEquals("https://example.com/old.mp4", viewModel.uiState.value.items.single().playUrl)

        viewModel.enterVideoFlow()
        viewModel.loadInitial()
        advanceUntilIdle()

        val refreshed = viewModel.uiState.value.items.single()
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
        val viewModel = DouyinFeedViewModel(
            repository = repo,
            preloadManager = preloadManager,
            watchHistoryStore = TestDouyinWatchHistoryStore(),
            feedCacheStore = TestDouyinFeedCacheStore(initialItems = listOf(cachedItem))
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.localPlayPaths.containsKey(awemeId))
        val initialResolvedAtMs = viewModel.uiState.value.items.single().playUrlResolvedAtMs

        viewModel.refreshPlaybackSource(awemeId, DouyinPlaybackSourceKind.LOCAL)
        advanceUntilIdle()

        val refreshed = viewModel.uiState.value.items.single()
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
        val viewModel = DouyinFeedViewModel(
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
