package com.lightningstudio.watchrss.ui.viewmodel

import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestBiliRepository
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.sampleBiliItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiliFeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refresh_withoutLogin_showsLoginMessage() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = false)
        val viewModel = BiliFeedViewModel(repo)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoggedIn)
        assertEquals("请先登录获取推荐内容", viewModel.uiState.value.message)
    }

    @Test
    fun refresh_withCache_mergesFreshFeed_and_persistsCache() = runTest {
        val cached = listOf(sampleBiliItem(aid = 1L, bvid = "BV-cache", title = "缓存视频"))
        val fresh = listOf(
            sampleBiliItem(aid = 2L, bvid = "BV-fresh", title = "新视频"),
            sampleBiliItem(aid = 1L, bvid = "BV-cache", title = "缓存视频")
        )
        val repo = TestBiliRepository(initialLoggedIn = false, initialFeedItems = cached).apply {
            feedResult = com.lightningstudio.watchrss.sdk.bili.BiliResult(
                code = 0,
                data = com.lightningstudio.watchrss.sdk.bili.BiliFeedPage(
                    items = fresh,
                    source = com.lightningstudio.watchrss.sdk.bili.BiliFeedSource.WEB
                )
            )
        }
        val viewModel = BiliFeedViewModel(repo)
        advanceUntilIdle()
        repo.loggedIn = true
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.items.mapNotNull { it.aid })
        assertEquals(1, repo.writtenFeedCaches.size)
        assertEquals(listOf(2L, 1L), repo.writtenFeedCaches.single().mapNotNull { it.aid })
    }

    @Test
    fun favorite_and_watchLater_syncLocalSavedState() = runTest {
        val platformRepo = TestBiliRepository(initialLoggedIn = true)
        val rssRepo = TestRssRepository()
        val viewModel = BiliFeedViewModel(platformRepo, rssRepo)
        val item = sampleBiliItem(aid = 55L, bvid = "BV55", cid = 77L)
        advanceUntilIdle()

        viewModel.favorite(item)
        viewModel.watchLater(item)
        advanceUntilIdle()

        assertEquals(listOf(55L to true), platformRepo.favoriteRequests)
        assertEquals(listOf(55L to "BV55"), platformRepo.addToViewRequests)
        assertEquals(
            listOf(SaveType.FAVORITE, SaveType.WATCH_LATER),
            rssRepo.syncedExternalSavedItems.map { it.second }
        )
        assertEquals(listOf(Triple(55L, "BV55", 77L), Triple(55L, "BV55", 77L)), platformRepo.cachedPreviewRequests)
    }

    @Test
    fun loadMore_failure_surfacesFormattedError() = runTest {
        val repo = TestBiliRepository(initialLoggedIn = true).apply {
            feedResult = com.lightningstudio.watchrss.sdk.bili.BiliResult(code = BiliErrorCodes.REQUEST_FAILED)
        }
        val viewModel = BiliFeedViewModel(repo)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals("RSS解析失败(-9001)", viewModel.uiState.value.message)
    }
}
