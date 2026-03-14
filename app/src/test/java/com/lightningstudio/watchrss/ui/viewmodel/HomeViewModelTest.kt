package com.lightningstudio.watchrss.ui.viewmodel

import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.collectFlow
import com.lightningstudio.watchrss.testutil.sampleRssChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_ensuresBuiltinChannels_and_exposesChannels() = runTest {
        val repo = TestRssRepository(
            initialChannels = listOf(sampleRssChannel(id = 1L), sampleRssChannel(id = 2L))
        )

        val viewModel = HomeViewModel(repo)
        val collection = collectFlow(viewModel.channels)
        advanceUntilIdle()

        assertEquals(1, repo.ensureBuiltinChannelsCount)
        assertEquals(listOf(1L, 2L), viewModel.channels.value.map { it.id })
        collection.cancel()
    }

    @Test
    fun refreshAll_usesAllChannels_and_surfacesFirstFailure() = runTest {
        val repo = TestRssRepository(
            initialChannels = listOf(sampleRssChannel(id = 11L), sampleRssChannel(id = 22L))
        )
        repo.refreshResults[22L] = Result.failure(IllegalStateException("第二个频道刷新失败"))
        val viewModel = HomeViewModel(repo)
        val collection = collectFlow(viewModel.channels)
        advanceUntilIdle()

        viewModel.refreshAll()
        advanceUntilIdle()

        assertEquals(listOf(11L to false, 22L to false), repo.refreshRequests)
        assertEquals("第二个频道刷新失败", viewModel.message.value)
        assertEquals(false, viewModel.isRefreshing.value)
        collection.cancel()
    }

    @Test
    fun channelActions_delegateToRepository_and_clearMessageResetsState() = runTest {
        val first = sampleRssChannel(id = 1L, title = "第一个")
        val second = sampleRssChannel(id = 2L, title = "第二个")
        val repo = TestRssRepository(initialChannels = listOf(first, second))
        val viewModel = HomeViewModel(repo)
        val collection = collectFlow(viewModel.channels)
        advanceUntilIdle()

        viewModel.moveToTop(second)
        viewModel.togglePinned(first)
        viewModel.markChannelRead(first)
        viewModel.deleteChannel(first)
        advanceUntilIdle()
        viewModel.clearMessage()

        assertEquals(listOf(2L), repo.movedToTopChannelIds)
        assertEquals(listOf(1L to true), repo.setPinnedRequests)
        assertEquals(listOf(1L), repo.markedReadChannelIds)
        assertEquals(listOf(1L), repo.deletedChannelIds)
        assertEquals(listOf(2L), viewModel.channels.value.map { it.id })
        assertNull(viewModel.message.value)
        collection.cancel()
    }
}
