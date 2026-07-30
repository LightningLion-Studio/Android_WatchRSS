package com.lightningstudio.watchrss.ui.viewmodel

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lightningstudio.watchrss.data.llm.LlmTokenUsageRepository
import com.lightningstudio.watchrss.data.settings.LlmApiKeyProvider
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import com.lightningstudio.watchrss.testutil.TestRssRepository
import com.lightningstudio.watchrss.testutil.sampleRssChannel
import com.lightningstudio.watchrss.testutil.sampleRssItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class LlmSummaryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun prepare_waitsForOriginalContentWhenChannelRequiresIt() = runTest {
        val repo = TestRssRepository(
            initialChannels = listOf(sampleRssChannel(id = 7L, useOriginalContent = true))
        ).apply {
            setChannelItems(
                7L,
                listOf(
                    sampleRssItem(
                        id = 42L,
                        channelId = 7L,
                        description = null,
                        content = null
                    )
                )
            )
        }
        val env = createSettingsRepository("llm-summary-view-model.preferences_pb")

        try {
            val viewModel = LlmSummaryViewModel(
                rssRepository = repo,
                settingsRepository = env.repository,
                llmApiKeyProvider = FakeLlmApiKeyProvider(),
                tokenUsageRepository = mock(LlmTokenUsageRepository::class.java)
            )

            viewModel.prepare(42L)
            advanceUntilIdle()

            assertEquals(SummaryStatus.WaitingForContent, viewModel.state.value.status)
            assertTrue(viewModel.state.value.text.isBlank())
        } finally {
            env.scope.cancel()
        }
    }

    private fun createSettingsRepository(fileName: String): TestEnvironment {
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
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

    private class FakeLlmApiKeyProvider : LlmApiKeyProvider {
        override fun getApiKey(): String = ""
    }
}
