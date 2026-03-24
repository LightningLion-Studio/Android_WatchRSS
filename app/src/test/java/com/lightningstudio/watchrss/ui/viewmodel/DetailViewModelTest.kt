package com.lightningstudio.watchrss.ui.viewmodel

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.sampleRssChannel
import com.lightningstudio.watchrss.testutil.sampleRssItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun retryOfflineMedia_updatesLoadingStateImmediately_and_ignoresDuplicateClicks() = runTest {
        val repo = TestRssRepository(initialChannels = listOf(sampleRssChannel(id = 7L))).apply {
            setChannelItems(7L, listOf(sampleRssItem(id = 42L, channelId = 7L)))
        }
        val retryGate = CompletableDeferred<Unit>()
        repo.retryOfflineMediaBehavior = { retryGate.await() }
        val env = createSettingsRepository("detail-view-model.preferences_pb")

        try {
            val viewModel = DetailViewModel(
                savedStateHandle = SavedStateHandle(mapOf("itemId" to 42L)),
                repository = repo,
                settingsRepository = env.repository
            )
            advanceUntilIdle()

            assertFalse(viewModel.isRetryingOfflineMedia.value)

            viewModel.retryOfflineMedia()
            advanceUntilIdle()

            assertTrue(viewModel.isRetryingOfflineMedia.value)
            assertEquals(listOf(42L), repo.retriedOfflineMediaIds)

            viewModel.retryOfflineMedia()
            advanceUntilIdle()

            assertEquals(listOf(42L), repo.retriedOfflineMediaIds)

            retryGate.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.isRetryingOfflineMedia.value)
        } finally {
            env.scope.cancel()
        }
    }

    private fun createSettingsRepository(fileName: String): TestEnvironment {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile(fileName) }
        )
        return TestEnvironment(
            repository = SettingsRepository(dataStore),
            scope = scope
        )
    }

    private data class TestEnvironment(
        val repository: SettingsRepository,
        val scope: CoroutineScope
    )
}
