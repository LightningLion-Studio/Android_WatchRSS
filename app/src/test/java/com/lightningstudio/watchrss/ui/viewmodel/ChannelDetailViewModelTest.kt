package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.sampleRssChannel
import com.lightningstudio.watchrss.testutil.sampleRssItem
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun invalidChannelId_reportsInvalid() = runTest {
        val viewModel = ChannelDetailViewModel(SavedStateHandle(), TestRssRepository())
        advanceUntilIdle()

        assertFalse(viewModel.isValid())
    }

    @Test
    fun actions_delegateToRepository_and_originalContentTriggersBackgroundRefresh() = runTest {
        val repo = TestRssRepository(
            initialChannels = listOf(sampleRssChannel(id = 12L, useOriginalContent = false))
        ).apply {
            setChannelItems(
                12L,
                listOf(sampleRssItem(id = 120L, channelId = 12L, videoUrl = "https://example.com/a.mp4"))
            )
        }
        val viewModel = ChannelDetailViewModel(SavedStateHandle(mapOf("channelId" to 12L)), repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.hasPlayableMedia.collect()
        }
        advanceUntilIdle()

        assertTrue(viewModel.isValid())
        assertTrue(viewModel.hasPlayableMedia.value)

        viewModel.refresh()
        viewModel.markRead()
        viewModel.setOriginalContentEnabled(true)
        viewModel.setContinuePlaybackInBackgroundEnabled(true)
        viewModel.delete()
        advanceUntilIdle()

        assertEquals(listOf(12L to false), repo.refreshRequests)
        assertEquals(listOf(12L), repo.markedReadChannelIds)
        assertEquals(listOf(12L to true), repo.setOriginalContentRequests)
        assertEquals(listOf(12L to true), repo.refreshBackgroundRequests)
        assertEquals(listOf(12L to true), repo.setContinuePlaybackInBackgroundRequests)
        assertEquals(listOf(12L), repo.deletedChannelIds)
    }
}
