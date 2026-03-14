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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun channelActionsViewModel_exposesChannel_and_runsActions() = runTest {
        val repo = TestRssRepository(initialChannels = listOf(sampleRssChannel(id = 31L, isPinned = false)))
        val viewModel = ChannelActionsViewModel(SavedStateHandle(mapOf("channelId" to 31L)), repo)
        val collection = collectFlow(viewModel.channel)
        advanceUntilIdle()

        assertTrue(viewModel.isValid())

        viewModel.moveToTop()
        viewModel.togglePinned()
        viewModel.markRead()
        viewModel.delete()
        advanceUntilIdle()

        assertEquals(listOf(31L), repo.movedToTopChannelIds)
        assertEquals(listOf(31L to true), repo.setPinnedRequests)
        assertEquals(listOf(31L), repo.markedReadChannelIds)
        assertEquals(listOf(31L), repo.deletedChannelIds)
        collection.cancel()
    }

    @Test
    fun itemActionsViewModel_readsSavedState_and_togglesEntries() = runTest {
        val repo = TestRssRepository(initialChannels = listOf(sampleRssChannel(id = 40L)))
        repo.setChannelItems(40L, listOf(sampleRssItem(id = 99L, channelId = 40L)))
        repo.setSavedState(99L, SavedState(isFavorite = true, isWatchLater = false))
        val viewModel = ItemActionsViewModel(SavedStateHandle(mapOf("itemId" to 99L)), repo)
        val itemCollection = collectFlow(viewModel.item)
        val savedCollection = collectFlow(viewModel.savedState)
        advanceUntilIdle()

        assertEquals(99L, viewModel.item.value?.id)
        assertEquals(true, viewModel.savedState.value.isFavorite)

        viewModel.toggleFavorite()
        viewModel.toggleWatchLater()
        advanceUntilIdle()

        assertEquals(listOf(99L), repo.toggledFavoriteIds)
        assertEquals(listOf(99L), repo.toggledWatchLaterIds)
        itemCollection.cancel()
        savedCollection.cancel()
    }
}
