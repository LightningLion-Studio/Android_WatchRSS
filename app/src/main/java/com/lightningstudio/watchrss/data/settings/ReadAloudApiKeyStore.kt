package com.lightningstudio.watchrss.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_FILE = "read_aloud_secure_prefs"
private const val KEY_API_KEY = "read_aloud_api_key"

interface ReadAloudApiKeyProvider {
    fun getApiKey(): String
}

class ReadAloudApiKeyStore(context: Context) : ReadAloudApiKeyProvider {
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

    override fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun hasApiKey(): Boolean = prefs.getString(KEY_API_KEY, "").orEmpty().isNotEmpty()
}
