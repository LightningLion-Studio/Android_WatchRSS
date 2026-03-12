package com.lightningstudio.watchrss.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun oobeSeenVersion_defaultsToZero() = runBlocking {
        val (repository, scope) = createRepository("default.preferences_pb")
        try {
            assertEquals(0, repository.oobeSeenVersion.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun oobeSeenVersion_persistsUpdates() = runBlocking {
        val (repository, scope) = createRepository("updated.preferences_pb")
        try {
            repository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            assertEquals(CURRENT_OOBE_VERSION, repository.oobeSeenVersion.first())
        } finally {
            scope.cancel()
        }
    }

    private fun createRepository(fileName: String): Pair<SettingsRepository, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile(fileName) }
        )
        return SettingsRepository(dataStore) to scope
    }
}
