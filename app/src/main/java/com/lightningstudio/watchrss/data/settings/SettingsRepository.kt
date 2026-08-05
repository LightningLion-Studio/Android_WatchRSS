package com.lightningstudio.watchrss.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val CACHE_LIMIT_BYTES = longPreferencesKey("cache_limit_bytes")
private val OOBE_SEEN_VERSION = intPreferencesKey("oobe_seen_version")
private val READING_THEME_DARK = booleanPreferencesKey("reading_theme_dark")
private val READING_FONT_SIZE_SP = intPreferencesKey("reading_font_size_sp")
private val READER_AUTO_SCROLL_ENABLED = booleanPreferencesKey("reader_auto_scroll_enabled")
private val READER_AUTO_SCROLL_LINES_PER_SECOND = floatPreferencesKey("reader_auto_scroll_lines_per_second")
private val SHARE_USE_SYSTEM = booleanPreferencesKey("share_use_system")
private val PHONE_CONNECTION_ENABLED = booleanPreferencesKey("phone_connection_enabled")
private val MEDIA_VOLUME_CONTROL_ENABLED = booleanPreferencesKey("media_volume_control_enabled")
private val MEDIA_VOLUME_GUARD_ENABLED = booleanPreferencesKey("media_volume_guard_enabled")
private val MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT =
    intPreferencesKey("media_playback_start_volume_limit_percent")
private val RSS_INLINE_IMAGE_PREFETCH_MODE = intPreferencesKey("rss_inline_image_prefetch_mode")
private val LLM_PROVIDER = stringPreferencesKey("llm_provider")
private val LLM_MODEL = stringPreferencesKey("llm_model")
private val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
private val LLM_SYSTEM_PROMPT = stringPreferencesKey("llm_system_prompt")
private val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
private val LLM_AUTO_SUMMARIZE = booleanPreferencesKey("llm_auto_summarize")
private val LLM_SHOW_TOKEN_USAGE = booleanPreferencesKey("llm_show_token_usage")
private val LLM_PROMPT_PRESET = intPreferencesKey("llm_prompt_preset")
private val TTS_ENGINE = stringPreferencesKey("tts_engine")
private val TTS_MODEL = stringPreferencesKey("tts_model")
private val TTS_VOICE_ID = stringPreferencesKey("tts_voice_id")
private val TTS_SPEED = floatPreferencesKey("tts_speed")
private val TTS_BASE_URL = stringPreferencesKey("tts_base_url")
private const val TEMP_ORIGINAL_MODE_HINT_ATTEMPTS_PREFIX = "temp_original_mode_hint_attempts_"
private const val TEMP_ORIGINAL_MODE_HINT_LAST_TOAST_PREFIX = "temp_original_mode_hint_last_toast_"
private val PRIVACY_POLICY_AGREED_VERSION = intPreferencesKey("privacy_policy_agreed_version")
const val MIN_CACHE_LIMIT_MB: Long = 512
const val MAX_CACHE_LIMIT_MB: Long = 4 * 1024
const val DEFAULT_CACHE_LIMIT_MB: Long = MIN_CACHE_LIMIT_MB
val CACHE_LIMIT_OPTIONS_MB: List<Long> = listOf(512L, 768L, 1024L, 1536L, 2048L, 2560L, 3072L, 4096L)
const val MB_BYTES: Long = 1024 * 1024
const val DEFAULT_READING_FONT_SIZE_SP: Int = 14
const val DEFAULT_READER_AUTO_SCROLL_ENABLED: Boolean = false
const val DEFAULT_READER_AUTO_SCROLL_LINES_PER_SECOND: Float = 2f
const val MIN_READER_AUTO_SCROLL_LINES_PER_SECOND: Float = 0.5f
const val MAX_READER_AUTO_SCROLL_LINES_PER_SECOND: Float = 10f
const val CURRENT_OOBE_VERSION: Int = 3
const val PRIVACY_POLICY_VERSION: Int = 1
const val DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED: Boolean = true
const val DEFAULT_MEDIA_VOLUME_GUARD_ENABLED: Boolean = false
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
    val readerAutoScrollEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[READER_AUTO_SCROLL_ENABLED] ?: DEFAULT_READER_AUTO_SCROLL_ENABLED
    }
    val readerAutoScrollLinesPerSecond: Flow<Float> = dataStore.data.map { preferences ->
        (preferences[READER_AUTO_SCROLL_LINES_PER_SECOND]
            ?: DEFAULT_READER_AUTO_SCROLL_LINES_PER_SECOND)
            .coerceIn(
                MIN_READER_AUTO_SCROLL_LINES_PER_SECOND,
                MAX_READER_AUTO_SCROLL_LINES_PER_SECOND
            )
    }
    val shareUseSystem: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHARE_USE_SYSTEM] ?: false
    }
    val phoneConnectionEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PHONE_CONNECTION_ENABLED] ?: true
    }
    val mediaVolumeControlEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[MEDIA_VOLUME_CONTROL_ENABLED] ?: DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
    }
    val mediaVolumeGuardEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[MEDIA_VOLUME_GUARD_ENABLED] ?: DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
    }
    val mediaPlaybackStartVolumeLimitPercent: Flow<Int?> = dataStore.data.map { preferences ->
        if (preferences.contains(MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT)) {
            decodeMediaPlaybackStartVolumeLimitPercent(
                preferences[MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT]
                    ?: MEDIA_PLAYBACK_START_VOLUME_UNLIMITED_PERSISTED_VALUE
            )
        } else {
            defaultMediaPlaybackStartVolumeLimitPercentForGuard(
                preferences[MEDIA_VOLUME_GUARD_ENABLED] ?: DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
            )
        }
    }
    val rssInlineImagePrefetchMode: Flow<RssInlineImagePrefetchMode> = dataStore.data.map { preferences ->
        RssInlineImagePrefetchMode.fromPersistedValue(
            preferences[RSS_INLINE_IMAGE_PREFETCH_MODE]
                ?: DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE.persistedValue
        )
    }
    val llmProvider: Flow<String> = dataStore.data.map { it[LLM_PROVIDER] ?: "" }
    val llmModel: Flow<String> = dataStore.data.map { it[LLM_MODEL] ?: "" }
    val llmBaseUrl: Flow<String> = dataStore.data.map { it[LLM_BASE_URL] ?: "" }
    val llmSystemPrompt: Flow<String> = dataStore.data.map { it[LLM_SYSTEM_PROMPT] ?: "" }
    val llmEnabled: Flow<Boolean> = dataStore.data.map { it[LLM_ENABLED] ?: true }
    val llmAutoSummarize: Flow<Boolean> = dataStore.data.map { it[LLM_AUTO_SUMMARIZE] ?: false }
    val llmShowTokenUsage: Flow<Boolean> = dataStore.data.map { it[LLM_SHOW_TOKEN_USAGE] ?: false }
    val llmPromptPreset: Flow<Int> = dataStore.data.map { it[LLM_PROMPT_PRESET] ?: 0 }
    val ttsEngine: Flow<String> = dataStore.data.map { it[TTS_ENGINE] ?: "" }
    val ttsModel: Flow<String> = dataStore.data.map { it[TTS_MODEL] ?: "" }
    val ttsVoiceId: Flow<String> = dataStore.data.map { it[TTS_VOICE_ID] ?: "" }
    val ttsSpeed: Flow<Float> = dataStore.data.map { it[TTS_SPEED] ?: 1.0f }
    val ttsBaseUrl: Flow<String> = dataStore.data.map { it[TTS_BASE_URL] ?: "" }
    val privacyPolicyAgreedVersion: Flow<Int> = dataStore.data.map { it[PRIVACY_POLICY_AGREED_VERSION] ?: 0 }

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

    suspend fun setReaderAutoScrollEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[READER_AUTO_SCROLL_ENABLED] = value
        }
    }

    suspend fun setReaderAutoScrollLinesPerSecond(value: Float) {
        dataStore.edit { preferences ->
            preferences[READER_AUTO_SCROLL_LINES_PER_SECOND] = value.coerceIn(
                MIN_READER_AUTO_SCROLL_LINES_PER_SECOND,
                MAX_READER_AUTO_SCROLL_LINES_PER_SECOND
            )
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

    suspend fun setMediaVolumeControlEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[MEDIA_VOLUME_CONTROL_ENABLED] = value
        }
    }

    suspend fun setMediaVolumeGuardEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            if (!preferences.contains(MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT)) {
                preferences[MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT] =
                    encodeMediaPlaybackStartVolumeLimitPercent(
                        defaultMediaPlaybackStartVolumeLimitPercentForGuard(
                            preferences[MEDIA_VOLUME_GUARD_ENABLED]
                                ?: DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
                        )
                    )
            }
            preferences[MEDIA_VOLUME_GUARD_ENABLED] = value
        }
    }

    suspend fun setMediaPlaybackStartVolumeLimitPercent(value: Int?) {
        dataStore.edit { preferences ->
            preferences[MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT] =
                encodeMediaPlaybackStartVolumeLimitPercent(value)
        }
    }

    suspend fun setRssInlineImagePrefetchMode(value: RssInlineImagePrefetchMode) {
        dataStore.edit { preferences ->
            preferences[RSS_INLINE_IMAGE_PREFETCH_MODE] = value.persistedValue
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

    suspend fun setLlmEnabled(value: Boolean) {
        dataStore.edit { it[LLM_ENABLED] = value }
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

    suspend fun setTtsConfig(
        engine: String,
        model: String,
        voiceId: String,
        speed: Float,
        baseUrl: String
    ) {
        dataStore.edit { preferences ->
            preferences[TTS_ENGINE] = engine
            preferences[TTS_MODEL] = model
            preferences[TTS_VOICE_ID] = voiceId
            preferences[TTS_SPEED] = speed
            preferences[TTS_BASE_URL] = baseUrl
        }
    }

    suspend fun setTtsEngine(value: String) {
        dataStore.edit { it[TTS_ENGINE] = value }
    }

    suspend fun setTtsVoiceId(value: String) {
        dataStore.edit { it[TTS_VOICE_ID] = value }
    }

    suspend fun setTtsSpeed(value: Float) {
        dataStore.edit { it[TTS_SPEED] = value }
    }

    suspend fun setPrivacyPolicyAgreedVersion(value: Int) {
        dataStore.edit { it[PRIVACY_POLICY_AGREED_VERSION] = value }
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
