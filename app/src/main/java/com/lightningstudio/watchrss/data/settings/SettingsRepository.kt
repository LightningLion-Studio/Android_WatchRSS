package com.lightningstudio.watchrss.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val CACHE_LIMIT_BYTES = longPreferencesKey("cache_limit_bytes")
private val BUILTIN_CHANNELS_INITIALIZED = booleanPreferencesKey("builtin_channels_initialized")
private val OOBE_SEEN_VERSION = intPreferencesKey("oobe_seen_version")
private val READING_THEME_DARK = booleanPreferencesKey("reading_theme_dark")
private val READING_FONT_SIZE_SP = intPreferencesKey("reading_font_size_sp")
private val SHARE_USE_SYSTEM = booleanPreferencesKey("share_use_system")
private val PHONE_CONNECTION_ENABLED = booleanPreferencesKey("phone_connection_enabled")
const val DEFAULT_CACHE_LIMIT_MB: Long = 50
const val MB_BYTES: Long = 1024 * 1024
const val DEFAULT_READING_FONT_SIZE_SP: Int = 14
const val CURRENT_OOBE_VERSION: Int = 1

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val cacheLimitBytes: Flow<Long> = dataStore.data.map { preferences ->
        preferences[CACHE_LIMIT_BYTES] ?: (DEFAULT_CACHE_LIMIT_MB * MB_BYTES)
    }
    val builtinChannelsInitialized: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BUILTIN_CHANNELS_INITIALIZED] ?: false
    }
    val oobeSeenVersion: Flow<Int> = dataStore.data.map { preferences ->
        preferences[OOBE_SEEN_VERSION] ?: 0
    }
    val readingThemeDark: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[READING_THEME_DARK] ?: true
    }
    val readingFontSizeSp: Flow<Int> = dataStore.data.map { preferences ->
        preferences[READING_FONT_SIZE_SP] ?: DEFAULT_READING_FONT_SIZE_SP
    }
    val shareUseSystem: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHARE_USE_SYSTEM] ?: false
    }
    val phoneConnectionEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PHONE_CONNECTION_ENABLED] ?: false
    }

    suspend fun setCacheLimitBytes(bytes: Long) {
        dataStore.edit { preferences ->
            preferences[CACHE_LIMIT_BYTES] = bytes
        }
    }

    suspend fun setBuiltinChannelsInitialized(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[BUILTIN_CHANNELS_INITIALIZED] = value
        }
    }

    suspend fun setOobeSeenVersion(value: Int) {
        dataStore.edit { preferences ->
            preferences[OOBE_SEEN_VERSION] = value
        }
    }

    suspend fun setReadingThemeDark(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[READING_THEME_DARK] = value
        }
    }

    suspend fun setReadingFontSizeSp(value: Int) {
        dataStore.edit { preferences ->
            preferences[READING_FONT_SIZE_SP] = value
        }
    }

    suspend fun setShareUseSystem(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHARE_USE_SYSTEM] = value
        }
    }

    suspend fun setPhoneConnectionEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PHONE_CONNECTION_ENABLED] = value
        }
    }
}
