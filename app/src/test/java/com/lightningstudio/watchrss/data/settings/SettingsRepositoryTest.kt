package com.lightningstudio.watchrss.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        val env = createRepository("default.preferences_pb")
        try {
            assertEquals(0, env.repository.oobeSeenVersion.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun oobeSeenVersion_persistsUpdates() = runBlocking {
        val env = createRepository("updated.preferences_pb")
        try {
            env.repository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            assertEquals(CURRENT_OOBE_VERSION, env.repository.oobeSeenVersion.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun cacheLimitBytes_defaultsToNewMinimum() = runBlocking {
        val env = createRepository("cache-default.preferences_pb")
        try {
            assertEquals(MIN_CACHE_LIMIT_MB * MB_BYTES, env.repository.cacheLimitBytes.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun cacheLimitBytes_clampsLegacyStoredValues() = runBlocking {
        val env = createRepository("cache-clamp.preferences_pb")
        val cacheKey = longPreferencesKey("cache_limit_bytes")
        try {
            env.dataStore.edit { preferences ->
                preferences[cacheKey] = 50L * MB_BYTES
            }
            assertEquals(MIN_CACHE_LIMIT_MB * MB_BYTES, env.repository.cacheLimitBytes.first())

            env.dataStore.edit { preferences ->
                preferences[cacheKey] = 8L * 1024L * MB_BYTES
            }
            assertEquals(MAX_CACHE_LIMIT_MB * MB_BYTES, env.repository.cacheLimitBytes.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun setCacheLimitBytes_clampsRequestedValues() = runBlocking {
        val env = createRepository("cache-set.preferences_pb")
        try {
            env.repository.setCacheLimitBytes(128L * MB_BYTES)
            assertEquals(MIN_CACHE_LIMIT_MB * MB_BYTES, env.repository.cacheLimitBytes.first())

            env.repository.setCacheLimitBytes(6L * 1024L * MB_BYTES)
            assertEquals(MAX_CACHE_LIMIT_MB * MB_BYTES, env.repository.cacheLimitBytes.first())
        } finally {
            env.scope.cancel()
        }
    }

    private fun createRepository(fileName: String): TestEnvironment {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile(fileName) }
        )
        return TestEnvironment(
            repository = SettingsRepository(dataStore),
            dataStore = dataStore,
            scope = scope
        )
    }

    private data class TestEnvironment(
        val repository: SettingsRepository,
        val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        val scope: CoroutineScope
    )
}
