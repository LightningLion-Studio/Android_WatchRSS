package com.lightningstudio.watchrss.data.settings

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_FILE = "tts_secure_prefs"
private const val KEY_PREFIX = "tts_api_key_"

interface TtsApiKeyProvider {
    fun getApiKey(engine: String): String
}

class TtsApiKeyStore(context: Context) : TtsApiKeyProvider {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getApiKey(engine: String): String =
        prefs.getString("$KEY_PREFIX$engine", "") ?: ""

    fun setApiKey(engine: String, apiKey: String) {
        prefs.edit { putString("$KEY_PREFIX$engine", apiKey) }
    }

    fun hasApiKey(engine: String): Boolean =
        prefs.getString("$KEY_PREFIX$engine", "").orEmpty().isNotEmpty()
}
