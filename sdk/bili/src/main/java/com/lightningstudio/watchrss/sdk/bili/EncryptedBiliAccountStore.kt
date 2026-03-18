package com.lightningstudio.watchrss.sdk.bili

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

class EncryptedBiliAccountStore(
    context: Context,
    private val prefsName: String = "bili_account_store"
) : BiliAccountStore {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var prefsRef: SharedPreferences? = null

    override suspend fun read(): BiliAccount? = withContext(Dispatchers.IO) {
        withPrefs { prefs ->
            val raw = prefs.getString(KEY_ACCOUNT, null) ?: return@withPrefs null
            runCatching { biliJson.decodeFromString(BiliAccount.serializer(), raw) }
                .getOrNull()
        }
    }

    override suspend fun write(account: BiliAccount) = withContext(Dispatchers.IO) {
        withPrefs { prefs ->
            val payload = biliJson.encodeToString(BiliAccount.serializer(), account)
            prefs.edit().putString(KEY_ACCOUNT, payload).apply()
        }
    }

    override suspend fun update(transform: (BiliAccount) -> BiliAccount) {
        withContext(Dispatchers.IO) {
            withPrefs { prefs ->
                val raw = prefs.getString(KEY_ACCOUNT, null)
                val current = raw?.let {
                    runCatching { biliJson.decodeFromString(BiliAccount.serializer(), it) }
                        .getOrNull()
                } ?: BiliAccount()
                val updated = transform(current)
                val payload = biliJson.encodeToString(BiliAccount.serializer(), updated)
                prefs.edit().putString(KEY_ACCOUNT, payload).apply()
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
                Log.w(TAG, "Encrypted bili prefs failed, resetting secure storage", error)
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
        private const val TAG = "EncryptedBiliStore"
        private const val KEY_ACCOUNT = "account_json"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private val ALL_SECURE_PREFS = listOf(
            "bili_account_store",
            "douyin_account_store"
        )
    }
}
