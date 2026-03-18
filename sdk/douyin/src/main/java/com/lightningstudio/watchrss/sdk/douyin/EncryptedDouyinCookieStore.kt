package com.lightningstudio.watchrss.sdk.douyin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

class EncryptedDouyinCookieStore(
    context: Context,
    private val prefsName: String = "douyin_account_store"
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var prefsRef: SharedPreferences? = null

    suspend fun readCookie(): String? = withContext(Dispatchers.IO) {
        withPrefs { prefs ->
            prefs.getString(KEY_COOKIE, null)
        }
    }

    suspend fun writeCookie(cookie: String?) = withContext(Dispatchers.IO) {
        withPrefs { prefs ->
            if (cookie.isNullOrBlank()) {
                prefs.edit().remove(KEY_COOKIE).apply()
            } else {
                prefs.edit().putString(KEY_COOKIE, cookie).apply()
            }
        }
    }

    private suspend fun <T> withPrefs(block: (SharedPreferences) -> T): T {
        return mutex.withLock {
            try {
                block(ensurePrefsLocked())
            } catch (error: Exception) {
                if (!isRecoverableCryptoFailure(error)) {
                    throw error
                }
                Log.w(TAG, "Encrypted douyin prefs failed, resetting secure storage", error)
                resetSecureStorageLocked()
                block(ensurePrefsLocked())
            }
        }
    }

    private fun ensurePrefsLocked(): SharedPreferences {
        prefsRef?.let { return it }
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { prefsRef = it }
    }

    private fun resetSecureStorageLocked() {
        prefsRef = null
        ALL_SECURE_PREFS.forEach { name ->
            runCatching { appContext.deleteSharedPreferences(name) }
        }
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }
    }

    private fun isRecoverableCryptoFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is GeneralSecurityException || current is IOException) {
                return true
            }
            val name = current.javaClass.name
            val message = current.message.orEmpty()
            if (
                name == "android.security.KeyStoreException" ||
                message.contains("Signature/MAC verification failed", ignoreCase = true) ||
                message.contains("keystore", ignoreCase = true) ||
                message.contains("aeadbadtagexception", ignoreCase = true) ||
                message.contains("decryption failed", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private companion object {
        private const val TAG = "EncryptedDouyinStore"
        private const val KEY_COOKIE = "cookie"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private val ALL_SECURE_PREFS = listOf(
            "bili_account_store",
            "douyin_account_store"
        )
    }
}
