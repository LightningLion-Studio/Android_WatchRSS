package com.lightningstudio.watchrss.sdk.douyin

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EncryptedDouyinCookieStore(
    context: Context,
    private val prefsName: String = "douyin_account_store"
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun readCookie(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.getString(KEY_COOKIE, null)
        }
    }

    suspend fun writeCookie(cookie: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (cookie.isNullOrBlank()) {
                prefs.edit().remove(KEY_COOKIE).apply()
            } else {
                prefs.edit().putString(KEY_COOKIE, cookie).apply()
            }
        }
    }

    private companion object {
        private const val KEY_COOKIE = "cookie"
    }
}
