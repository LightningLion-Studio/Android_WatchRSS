package com.lightningstudio.watchrss.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.collectFlow
import com.lightningstudio.watchrss.testutil.sampleRssChannel
import com.lightningstudio.watchrss.testutil.sampleRssItem
import com.lightningstudio.watchrss.testutil.sampleSavedItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedAndSearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun savedItemsViewModel_readsItems_and_togglesMatchingSaveType() = runTest {
        val repo = TestRssRepository()
        repo.setSavedItems(
            SaveType.WATCH_LATER,
            listOf(sampleSavedItem(itemId = 8L, saveType = SaveType.WATCH_LATER))
        )
        val viewModel = SavedItemsViewModel(
            SavedStateHandle(mapOf("saveType" to SaveType.WATCH_LATER.name)),
            repo
        )
        val collection = collectFlow(viewModel.items)
        advanceUntilIdle()

        assertEquals(1, viewModel.items.value.size)

        viewModel.toggleSaved(8L)
        advanceUntilIdle()

        assertEquals(listOf(8L), repo.toggledWatchLaterIds)
        assertEquals(emptyList<Long>(), repo.toggledFavoriteIds)
        collection.cancel()
    }

    @Test
    fun rssSearchViewModel_debouncesKeyword_and_filtersResults() = runTest {
        val repo = TestRssRepository(initialChannels = listOf(sampleRssChannel(id = 15L)))
        repo.setChannelItems(
            15L,
            listOf(
                sampleRssItem(id = 1L, channelId = 15L, title = "Compose Weekly"),
                sampleRssItem(id = 2L, channelId = 15L, title = "Android News"),
                sampleRssItem(id = 3L, channelId = 15L, title = "Compose Digest")
            )
        )
        val viewModel = RssSearchViewModel(SavedStateHandle(mapOf("channelId" to 15L)), repo)
        val collection = collectFlow(viewModel.results)

        viewModel.updateKeyword("Compose")
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(listOf(1L, 3L), viewModel.results.value.map { it.id })
        collection.cancel()
    }
}
