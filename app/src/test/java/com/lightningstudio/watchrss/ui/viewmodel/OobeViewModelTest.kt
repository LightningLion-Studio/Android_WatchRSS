package com.lightningstudio.watchrss.ui.viewmodel

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lightningstudio.watchrss.data.network.InternetAvailabilityMonitor
import com.lightningstudio.watchrss.data.network.InternetAvailabilityStatus
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.data.settings.PRIVACY_POLICY_VERSION
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.testutil.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OobeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun completeOobe_recordsOobeVersionAndPrivacyPolicyVersion() = runTest {
        val (settingsRepository, scope) = createTestSettingsRepository()
        try {
            val viewModel = OobeViewModel(
                settingsRepository = settingsRepository,
                internetAvailabilityMonitor = fakeMonitor()
            )

            viewModel.completeOobe()
            advanceUntilIdle()

            assertEquals(CURRENT_OOBE_VERSION, settingsRepository.oobeSeenVersion.first())
            assertEquals(PRIVACY_POLICY_VERSION, settingsRepository.privacyPolicyAgreedVersion.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun setIntroPage_clampsToValidRange() = runTest {
        val (settingsRepository, scope) = createTestSettingsRepository()
        try {
            val viewModel = OobeViewModel(
                settingsRepository = settingsRepository,
                internetAvailabilityMonitor = fakeMonitor()
            )
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.introPage)

            viewModel.setIntroPage(10)
            assertEquals(OobeViewModel.INTRO_PAGE_COUNT - 1, viewModel.uiState.value.introPage)

            viewModel.setIntroPage(-1)
            assertEquals(0, viewModel.uiState.value.introPage)
        } finally {
            scope.cancel()
        }
    }

    private fun createTestSettingsRepository(): Pair<SettingsRepository, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val file = File(tempFolder.root, "oobe-viewmodel.preferences_pb").apply {
            parentFile?.mkdirs()
            if (!exists()) {
                createNewFile()
            }
        }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return SettingsRepository(dataStore) to scope
    }

    private fun fakeMonitor(): InternetAvailabilityMonitor {
        return object : InternetAvailabilityMonitor {
            override val internetAvailability = MutableStateFlow(InternetAvailabilityStatus.Available)
        }
    }
}
