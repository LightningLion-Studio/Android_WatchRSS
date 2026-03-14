package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lightningstudio.watchrss.data.rss.SavedState
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.collectFlow
import com.lightningstudio.watchrss.testutil.sampleRssChannel
import com.lightningstudio.watchrss.testutil.sampleRssItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadMore_extendsVisibleItems_and_hasMoreReflectsRemainingCount() = runTest {
        val repo = TestRssRepository(initialChannels = listOf(sampleRssChannel(id = 7L)))
        repo.setChannelItems(7L, (1L..14L).map { sampleRssItem(id = it, channelId = 7L) })
        val viewModel = FeedViewModel(SavedStateHandle(mapOf("channelId" to 7L)), repo)
        val itemsCollection = collectFlow(viewModel.items)
        val hasMoreCollection = collectFlow(viewModel.hasMore)
        advanceUntilIdle()

        assertEquals(12, viewModel.items.value.size)
        assertEquals(true, viewModel.hasMore.value)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(14, viewModel.items.value.size)
        assertEquals(false, viewModel.hasMore.value)
        itemsCollection.cancel()
        hasMoreCollection.cancel()
    }

    @Test
    fun refreshFailure_setsMessage_and_requestOriginalContentsDeduplicatesIds() = runTest {
        val repo = TestRssRepository(initialChannels = listOf(sampleRssChannel(id = 3L)))
        repo.refreshResults[3L] = Result.failure(IllegalStateException("刷新失败"))
        val viewModel = FeedViewModel(SavedStateHandle(mapOf("channelId" to 3L)), repo)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("刷新失败", viewModel.message.value)

        viewModel.requestOriginalContents(listOf(1L, 2L, 2L))
        viewModel.requestOriginalContents(listOf(2L, 3L))
        viewModel.setOriginalContentUpdatesPaused(true)
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L, 2L), listOf(3L)), repo.requestedOriginalContentBatchIds)
        assertEquals(listOf(3L to true), repo.pausedOriginalContentUpdates)
    }

    @Test
    fun toggleSavedActions_and_getSavedState_delegateToRepository() = runTest {
        val repo = TestRssRepository(initialChannels = listOf(sampleRssChannel(id = 5L)))
        repo.setSavedState(42L, SavedState(isFavorite = true, isWatchLater = false))
        val viewModel = FeedViewModel(SavedStateHandle(mapOf("channelId" to 5L)), repo)
        advanceUntilIdle()

        assertEquals(SavedState(isFavorite = true, isWatchLater = false), viewModel.getSavedState(42L))

        viewModel.toggleFavorite(42L)
        viewModel.toggleWatchLater(42L)
        advanceUntilIdle()

        assertEquals(listOf(42L), repo.toggledFavoriteIds)
        assertEquals(listOf(42L), repo.toggledWatchLaterIds)
    }
}
