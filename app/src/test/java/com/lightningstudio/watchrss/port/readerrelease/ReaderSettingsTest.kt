package com.lightningstudio.watchrss.port.readerrelease

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.lightningstudio.watchrss.data.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderSettingsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun defaultsAndIndependentAutoSettings() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val file = folder.newFile("default.preferences_pb")
            val repository = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertFalse(repository.readerPageMode.first())
            assertFalse(repository.readerAutoPageEnabled.first())
            assertEquals(15, repository.readerPageSeconds.first())
            repository.setReaderPageMode(true)
            repository.setReaderAutoPageEnabled(true)
            repository.setReaderPageSeconds(25)
            assertTrue(repository.readerPageMode.first())
            assertTrue(repository.readerAutoPageEnabled.first())
            assertFalse(repository.readerAutoScrollEnabled.first())
        } finally { scope.cancel() }
    }

    @Test fun pageSettingsSurviveDataStoreReopenAndClampInvalidValues() = runBlocking {
        val file = folder.newFile("persistent.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var store = PreferenceDataStoreFactory.create(scope = scope) { file }
        var repository = SettingsRepository(store)
        repository.setReaderPageMode(true)
        repository.setReaderAutoPageEnabled(true)
        repository.setReaderPageSeconds(1000)
        assertEquals(120, repository.readerPageSeconds.first())
        scope.coroutineContext[Job]!!.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            store = PreferenceDataStoreFactory.create(scope = scope) { file }
            repository = SettingsRepository(store)
            assertTrue(repository.readerPageMode.first())
            assertTrue(repository.readerAutoPageEnabled.first())
            assertEquals(120, repository.readerPageSeconds.first())
            repository.setReaderPageSeconds(-5)
            assertEquals(5, repository.readerPageSeconds.first())
            store.edit { it[intPreferencesKey("reader_page_seconds")] = Int.MAX_VALUE }
            assertEquals(120, repository.readerPageSeconds.first())
        } finally { scope.cancel() }
    }
}
