package com.lightningstudio.watchrss.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
    fun privacyPolicyAgreedVersion_defaultsToZero() = runBlocking {
        val env = createRepository("privacy-default.preferences_pb")
        try {
            assertEquals(0, env.repository.privacyPolicyAgreedVersion.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun privacyPolicyAgreedVersion_persistsUpdates() = runBlocking {
        val env = createRepository("privacy-updated.preferences_pb")
        try {
            env.repository.setPrivacyPolicyAgreedVersion(PRIVACY_POLICY_VERSION)
            assertEquals(PRIVACY_POLICY_VERSION, env.repository.privacyPolicyAgreedVersion.first())
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

    @Test
    fun mediaVolumeGuardEnabled_defaultsToDisabled() = runBlocking {
        val env = createRepository("media-volume-guard-default.preferences_pb")
        try {
            assertEquals(DEFAULT_MEDIA_VOLUME_GUARD_ENABLED, env.repository.mediaVolumeGuardEnabled.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun mediaVolumeControlEnabled_defaultsToEnabled() = runBlocking {
        val env = createRepository("media-volume-control-default.preferences_pb")
        try {
            assertEquals(DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED, env.repository.mediaVolumeControlEnabled.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun mediaPlaybackStartVolumeLimit_defaultsToUnlimitedForNewUsers() = runBlocking {
        val env = createRepository("media-playback-start-volume-default.preferences_pb")
        try {
            assertEquals(null, env.repository.mediaPlaybackStartVolumeLimitPercent.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun mediaPlaybackStartVolumeLimit_migratesLegacyGuardOnToTen() = runBlocking {
        val env = createRepository("media-playback-start-volume-legacy-on.preferences_pb")
        val legacyGuardKey = booleanPreferencesKey("media_volume_guard_enabled")
        try {
            env.dataStore.edit { preferences ->
                preferences[legacyGuardKey] = true
            }

            assertEquals(
                DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT,
                env.repository.mediaPlaybackStartVolumeLimitPercent.first()
            )
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun mediaPlaybackStartVolumeLimit_migratesLegacyGuardOffToUnlimited() = runBlocking {
        val env = createRepository("media-playback-start-volume-legacy-off.preferences_pb")
        val legacyGuardKey = booleanPreferencesKey("media_volume_guard_enabled")
        try {
            env.dataStore.edit { preferences ->
                preferences[legacyGuardKey] = false
            }

            assertEquals(null, env.repository.mediaPlaybackStartVolumeLimitPercent.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun mediaPlaybackStartVolumeLimit_persistsUpdates() = runBlocking {
        val env = createRepository("media-playback-start-volume-updated.preferences_pb")
        try {
            env.repository.setMediaPlaybackStartVolumeLimitPercent(null)
            assertEquals(null, env.repository.mediaPlaybackStartVolumeLimitPercent.first())

            env.repository.setMediaPlaybackStartVolumeLimitPercent(25)
            assertEquals(25, env.repository.mediaPlaybackStartVolumeLimitPercent.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun mediaPlaybackStartVolumeLimit_invalidStoredValueFallsBackToDefault() = runBlocking {
        val env = createRepository("media-playback-start-volume-invalid.preferences_pb")
        val key = intPreferencesKey("media_playback_start_volume_limit_percent")
        try {
            env.dataStore.edit { preferences ->
                preferences[key] = 7
            }

            assertEquals(
                DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT,
                env.repository.mediaPlaybackStartVolumeLimitPercent.first()
            )
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun mediaVolumeControlEnabled_persistsUpdates() = runBlocking {
        val env = createRepository("media-volume-control-updated.preferences_pb")
        val key = booleanPreferencesKey("media_volume_control_enabled")
        try {
            env.repository.setMediaVolumeControlEnabled(false)
            assertEquals(false, env.repository.mediaVolumeControlEnabled.first())

            env.dataStore.edit { preferences ->
                preferences[key] = true
            }
            assertEquals(true, env.repository.mediaVolumeControlEnabled.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun mediaVolumeGuardEnabled_persistsUpdates() = runBlocking {
        val env = createRepository("media-volume-guard-updated.preferences_pb")
        val key = booleanPreferencesKey("media_volume_guard_enabled")
        try {
            env.repository.setMediaVolumeGuardEnabled(false)
            assertEquals(false, env.repository.mediaVolumeGuardEnabled.first())

            env.dataStore.edit { preferences ->
                preferences[key] = true
            }
            assertEquals(true, env.repository.mediaVolumeGuardEnabled.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun rssInlineImagePrefetchMode_defaultsToFirstFew() = runBlocking {
        val env = createRepository("rss-inline-prefetch-default.preferences_pb")
        try {
            assertEquals(
                DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE,
                env.repository.rssInlineImagePrefetchMode.first()
            )
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun rssInlineImagePrefetchMode_fallsBackForUnknownStoredValue_andPersistsUpdates() = runBlocking {
        val env = createRepository("rss-inline-prefetch-updated.preferences_pb")
        val key = intPreferencesKey("rss_inline_image_prefetch_mode")
        try {
            env.dataStore.edit { preferences ->
                preferences[key] = 99
            }
            assertEquals(
                DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE,
                env.repository.rssInlineImagePrefetchMode.first()
            )

            env.repository.setRssInlineImagePrefetchMode(RssInlineImagePrefetchMode.ALL)
            assertEquals(RssInlineImagePrefetchMode.ALL, env.repository.rssInlineImagePrefetchMode.first())
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun temporaryOriginalContentHint_showsOnThirdEnableWithinWindow_andOnlyOncePerWindow() = runBlocking {
        val env = createRepository("temporary-original-hint.preferences_pb")
        val baseTime = 1_700_000_000_000L
        try {
            assertFalse(env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(7L, baseTime))
            assertFalse(
                env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(
                    7L,
                    baseTime + 1_000L
                )
            )
            assertTrue(
                env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(
                    7L,
                    baseTime + 2_000L
                )
            )
            assertFalse(
                env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(
                    7L,
                    baseTime + 3_000L
                )
            )
        } finally {
            env.scope.cancel()
        }
    }

    @Test
    fun temporaryOriginalContentHint_resetsAfterWindow_and_isolatedPerChannel() = runBlocking {
        val env = createRepository("temporary-original-hint-window.preferences_pb")
        val baseTime = 1_700_000_000_000L
        try {
            repeat(3) { index ->
                env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(
                    7L,
                    baseTime + index * 1_000L
                )
            }

            val afterWindow = baseTime + TEMP_ORIGINAL_MODE_HINT_WINDOW_MS + 1_000L
            assertFalse(env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(7L, afterWindow))
            assertFalse(env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(7L, afterWindow + 1_000L))
            assertTrue(env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(7L, afterWindow + 2_000L))

            assertFalse(env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(9L, baseTime))
            assertFalse(env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(9L, baseTime + 1_000L))
            assertTrue(env.repository.recordTemporaryOriginalContentEnableAndShouldShowHint(9L, baseTime + 2_000L))
        } finally {
            env.scope.cancel()
        }
    }

    private fun createRepository(fileName: String): TestEnvironment {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(tempFolder.root, fileName).apply {
            parentFile?.mkdirs()
            if (!exists()) {
                createNewFile()
            }
        }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
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
