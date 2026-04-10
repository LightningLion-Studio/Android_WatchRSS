package com.lightningstudio.watchrss.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lightningstudio.watchrss.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val CACHE_LIMIT_BYTES = longPreferencesKey("cache_limit_bytes")
private val OOBE_SEEN_VERSION = intPreferencesKey("oobe_seen_version")
private val READING_THEME_DARK = booleanPreferencesKey("reading_theme_dark")
private val READING_FONT_SIZE_SP = intPreferencesKey("reading_font_size_sp")
private val SHARE_USE_SYSTEM = booleanPreferencesKey("share_use_system")
private val PHONE_CONNECTION_ENABLED = booleanPreferencesKey("phone_connection_enabled")
private val RSS_INLINE_IMAGE_PREFETCH_MODE = intPreferencesKey("rss_inline_image_prefetch_mode")
private val DOUYIN_VIDEO_CODEC_PREFERENCE = intPreferencesKey("douyin_video_codec_preference")
private val LLM_PROVIDER = stringPreferencesKey("llm_provider")
private val LLM_MODEL = stringPreferencesKey("llm_model")
private val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
private val LLM_SYSTEM_PROMPT = stringPreferencesKey("llm_system_prompt")
private val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
private val LLM_FEATURE_ENABLED = booleanPreferencesKey("llm_feature_enabled")
private val LLM_AUTO_SUMMARIZE = booleanPreferencesKey("llm_auto_summarize")
private val LLM_SHOW_TOKEN_USAGE = booleanPreferencesKey("llm_show_token_usage")
private val LLM_PROMPT_PRESET = intPreferencesKey("llm_prompt_preset")
private const val TEMP_ORIGINAL_MODE_HINT_ATTEMPTS_PREFIX = "temp_original_mode_hint_attempts_"
private const val TEMP_ORIGINAL_MODE_HINT_LAST_TOAST_PREFIX = "temp_original_mode_hint_last_toast_"
private val READ_ALOUD_PROVIDER = stringPreferencesKey("read_aloud_provider")
private val READ_ALOUD_MODEL = stringPreferencesKey("read_aloud_model")
private val READ_ALOUD_VOICE = stringPreferencesKey("read_aloud_voice")
private val READ_ALOUD_BASE_URL = stringPreferencesKey("read_aloud_base_url")
private val READ_ALOUD_REGION = stringPreferencesKey("read_aloud_region")
private val READ_ALOUD_APP_ID = stringPreferencesKey("read_aloud_app_id")
private val READ_ALOUD_RESOURCE_ID = stringPreferencesKey("read_aloud_resource_id")
private val READ_ALOUD_ENABLED = booleanPreferencesKey("read_aloud_enabled")
const val MIN_CACHE_LIMIT_MB: Long = 512
const val MAX_CACHE_LIMIT_MB: Long = 4 * 1024
const val DEFAULT_CACHE_LIMIT_MB: Long = MIN_CACHE_LIMIT_MB
val CACHE_LIMIT_OPTIONS_MB: List<Long> = listOf(512L, 768L, 1024L, 1536L, 2048L, 2560L, 3072L, 4096L)
const val MB_BYTES: Long = 1024 * 1024
const val DEFAULT_READING_FONT_SIZE_SP: Int = 14
const val CURRENT_OOBE_VERSION: Int = 3
const val TEMP_ORIGINAL_MODE_HINT_WINDOW_MS: Long = 12L * 60L * 60L * 1000L
const val TEMP_ORIGINAL_MODE_HINT_THRESHOLD: Int = 3
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val cacheLimitBytes: Flow<Long> = dataStore.data.map { preferences ->
        clampCacheLimitBytes(preferences[CACHE_LIMIT_BYTES] ?: (DEFAULT_CACHE_LIMIT_MB * MB_BYTES))
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
        preferences[PHONE_CONNECTION_ENABLED] ?: BuildConfig.DEBUG
    }
    val rssInlineImagePrefetchMode: Flow<RssInlineImagePrefetchMode> = dataStore.data.map { preferences ->
        RssInlineImagePrefetchMode.fromPersistedValue(
            preferences[RSS_INLINE_IMAGE_PREFETCH_MODE]
                ?: DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE.persistedValue
        )
    }
    val douyinVideoCodecPreference: Flow<DouyinVideoCodecPreference> = dataStore.data.map { preferences ->
        DouyinVideoCodecPreference.fromPersistedValue(
            preferences[DOUYIN_VIDEO_CODEC_PREFERENCE]
                ?: DEFAULT_DOUYIN_VIDEO_CODEC_PREFERENCE.persistedValue
        )
    }
    val llmProvider: Flow<String> = dataStore.data.map { it[LLM_PROVIDER] ?: "" }
    val llmModel: Flow<String> = dataStore.data.map { it[LLM_MODEL] ?: "" }
    val llmBaseUrl: Flow<String> = dataStore.data.map { it[LLM_BASE_URL] ?: "" }
    val llmSystemPrompt: Flow<String> = dataStore.data.map { it[LLM_SYSTEM_PROMPT] ?: "" }
    val llmEnabled: Flow<Boolean> = dataStore.data.map { it[LLM_ENABLED] ?: false }
    val llmFeatureEnabled: Flow<Boolean> = dataStore.data.map { it[LLM_FEATURE_ENABLED] ?: false }
    val llmAutoSummarize: Flow<Boolean> = dataStore.data.map { it[LLM_AUTO_SUMMARIZE] ?: false }
    val llmShowTokenUsage: Flow<Boolean> = dataStore.data.map { it[LLM_SHOW_TOKEN_USAGE] ?: false }
    val llmPromptPreset: Flow<Int> = dataStore.data.map { it[LLM_PROMPT_PRESET] ?: 0 }
    val readAloudProvider: Flow<String> = dataStore.data.map { it[READ_ALOUD_PROVIDER] ?: "" }
    val readAloudModel: Flow<String> = dataStore.data.map { it[READ_ALOUD_MODEL] ?: "" }
    val readAloudVoice: Flow<String> = dataStore.data.map { it[READ_ALOUD_VOICE] ?: "" }
    val readAloudBaseUrl: Flow<String> = dataStore.data.map { it[READ_ALOUD_BASE_URL] ?: "" }
    val readAloudRegion: Flow<String> = dataStore.data.map { it[READ_ALOUD_REGION] ?: "" }
    val readAloudAppId: Flow<String> = dataStore.data.map { it[READ_ALOUD_APP_ID] ?: "" }
    val readAloudResourceId: Flow<String> = dataStore.data.map { it[READ_ALOUD_RESOURCE_ID] ?: "" }
    val readAloudEnabled: Flow<Boolean> = dataStore.data.map { it[READ_ALOUD_ENABLED] ?: false }

    suspend fun setCacheLimitBytes(bytes: Long) {
        dataStore.edit { preferences ->
            preferences[CACHE_LIMIT_BYTES] = clampCacheLimitBytes(bytes)
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

    suspend fun setRssInlineImagePrefetchMode(value: RssInlineImagePrefetchMode) {
        dataStore.edit { preferences ->
            preferences[RSS_INLINE_IMAGE_PREFETCH_MODE] = value.persistedValue
        }
    }

    suspend fun setDouyinVideoCodecPreference(value: DouyinVideoCodecPreference) {
        dataStore.edit { preferences ->
            preferences[DOUYIN_VIDEO_CODEC_PREFERENCE] = value.persistedValue
        }
    }

    suspend fun setLlmConfig(
        provider: String,
        model: String,
        baseUrl: String,
        enabled: Boolean
    ) {
        dataStore.edit { preferences ->
            preferences[LLM_PROVIDER] = provider
            preferences[LLM_MODEL] = model
            preferences[LLM_BASE_URL] = baseUrl
            preferences[LLM_ENABLED] = enabled
        }
    }

    suspend fun setLlmFeatureEnabled(value: Boolean) {
        dataStore.edit { it[LLM_FEATURE_ENABLED] = value }
    }

    suspend fun setLlmAutoSummarize(value: Boolean) {
        dataStore.edit { it[LLM_AUTO_SUMMARIZE] = value }
    }

    suspend fun setLlmShowTokenUsage(value: Boolean) {
        dataStore.edit { it[LLM_SHOW_TOKEN_USAGE] = value }
    }

    suspend fun setLlmPromptPreset(value: Int) {
        dataStore.edit { it[LLM_PROMPT_PRESET] = value }
    }

    suspend fun recordTemporaryOriginalContentEnableAndShouldShowHint(
        channelId: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (channelId <= 0L) return false
        val attemptsKey = temporaryOriginalModeHintAttemptsKey(channelId)
        val lastToastKey = temporaryOriginalModeHintLastToastKey(channelId)
        val windowStart = now - TEMP_ORIGINAL_MODE_HINT_WINDOW_MS
        var shouldShowHint = false

        dataStore.edit { preferences ->
            val attempts = decodeTimestampList(preferences[attemptsKey])
                .filter { it >= windowStart }
                .toMutableList()
            attempts += now
            while (attempts.size > TEMP_ORIGINAL_MODE_HINT_THRESHOLD) {
                attempts.removeAt(0)
            }
            preferences[attemptsKey] = encodeTimestampList(attempts)

            val lastToastAt = preferences[lastToastKey] ?: 0L
            if (attempts.size >= TEMP_ORIGINAL_MODE_HINT_THRESHOLD && lastToastAt < windowStart) {
                preferences[lastToastKey] = now
                shouldShowHint = true
            }
        }

        return shouldShowHint
    }

    suspend fun setReadAloudConfig(
        provider: String,
        model: String,
        voice: String,
        baseUrl: String,
        region: String,
        appId: String,
        resourceId: String,
        enabled: Boolean
    ) {
        dataStore.edit { preferences ->
            preferences[READ_ALOUD_PROVIDER] = provider
            preferences[READ_ALOUD_MODEL] = model
            preferences[READ_ALOUD_VOICE] = voice
            preferences[READ_ALOUD_BASE_URL] = baseUrl
            preferences[READ_ALOUD_REGION] = region
            preferences[READ_ALOUD_APP_ID] = appId
            preferences[READ_ALOUD_RESOURCE_ID] = resourceId
            preferences[READ_ALOUD_ENABLED] = enabled
        }
    }

    private fun clampCacheLimitBytes(bytes: Long): Long {
        val minBytes = MIN_CACHE_LIMIT_MB * MB_BYTES
        val maxBytes = MAX_CACHE_LIMIT_MB * MB_BYTES
        return bytes.coerceIn(minBytes, maxBytes)
    }

    private fun temporaryOriginalModeHintAttemptsKey(channelId: Long) =
        stringPreferencesKey("$TEMP_ORIGINAL_MODE_HINT_ATTEMPTS_PREFIX$channelId")

    private fun temporaryOriginalModeHintLastToastKey(channelId: Long) =
        longPreferencesKey("$TEMP_ORIGINAL_MODE_HINT_LAST_TOAST_PREFIX$channelId")

    private fun decodeTimestampList(raw: String?): List<Long> {
        return raw.orEmpty()
            .split(',')
            .mapNotNull { value -> value.trim().toLongOrNull() }
            .sorted()
    }

    private fun encodeTimestampList(values: List<Long>): String =
        values.joinToString(",")
}
