package com.lightningstudio.watchrss.sdk.bili

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EncryptedBiliAccountStore(
    context: Context,
    private val prefsName: String = "bili_account_store"
) : BiliAccountStore {
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

    override suspend fun read(): BiliAccount? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val raw = prefs.getString(KEY_ACCOUNT, null) ?: return@withLock null
            runCatching { biliJson.decodeFromString(BiliAccount.serializer(), raw) }
                .getOrNull()
        }
    }

    override suspend fun write(account: BiliAccount) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val payload = biliJson.encodeToString(BiliAccount.serializer(), account)
            prefs.edit().putString(KEY_ACCOUNT, payload).apply()
        }
    }

    override suspend fun update(transform: (BiliAccount) -> BiliAccount) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
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

    private companion object {
        private const val KEY_ACCOUNT = "account_json"
    }
}
