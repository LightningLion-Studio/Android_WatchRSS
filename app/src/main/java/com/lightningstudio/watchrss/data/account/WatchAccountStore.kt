package com.lightningstudio.watchrss.data.account

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

class WatchAccountStore(
    context: Context,
    private val prefsName: String = "watchrss_account_state"
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var prefsRef: SharedPreferences? = null
    private val _state = MutableStateFlow<WatchAccountState?>(read())
    val state: StateFlow<WatchAccountState?> = _state

    fun read(): WatchAccountState? = synchronized(lock) {
        withPrefsLocked { prefs ->
            prefs.getString(KEY_STATE, null)?.let(::decodeState)
        }
    }

    fun save(state: WatchAccountState) = synchronized(lock) {
        withPrefsLocked { prefs ->
            prefs.edit {putString(KEY_STATE, encodeState(state))}
        }
        _state.value = state
    }

    fun clear() = synchronized(lock) {
        withPrefsLocked { prefs ->
            prefs.edit {remove(KEY_STATE)}
        }
        _state.value = null
    }

    private fun <T> withPrefsLocked(block: (SharedPreferences) -> T): T {
        return try {
            block(ensurePrefsLocked())
        } catch (error: Exception) {
            if (!isRecoverableCryptoFailure(error)) throw error
            Log.w(TAG, "Encrypted watch account prefs failed, resetting secure storage", error)
            resetSecureStorageLocked()
            block(ensurePrefsLocked())
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
        runCatching { appContext.deleteSharedPreferences(prefsName) }
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }
    }

    private fun encodeState(state: WatchAccountState): String =
        JSONObject().apply {
            put("userId", state.userId)
            put("phoneMasked", state.phoneMasked)
            put("installId", state.installId)
            put("watchDeviceToken", state.watchDeviceToken)
            put("tokenExpiresAtMillis", state.tokenExpiresAtMillis)
            put("watchAccessToken", state.watchDeviceToken)
            put("accessTokenExpiresAtMillis", state.tokenExpiresAtMillis)
            put("watchRefreshToken", state.watchRefreshToken)
            put("refreshTokenExpiresAtMillis", state.refreshTokenExpiresAtMillis)
            put("backendBaseUrl", state.backendBaseUrl)
            put("posthogHost", state.posthogHost)
            put("posthogProjectApiKey", state.posthogProjectApiKey)
            put("updatedAtMillis", state.updatedAtMillis)
            put("entitlement", JSONObject().apply {
                put("plan", state.entitlement.plan)
                put("active", state.entitlement.active)
                put("expiresAtMillis", state.entitlement.expiresAtMillis)
                put("features", JSONArray(state.entitlement.features))
            })
            put("telemetryConfig", JSONObject().apply {
                put("anonymousEnabled", state.telemetryConfig.anonymousEnabled)
                put("diagnosticsEnabled", state.telemetryConfig.diagnosticsEnabled)
                put("sampleRate", state.telemetryConfig.sampleRate)
            })
        }.toString()

    private fun decodeState(raw: String): WatchAccountState? =
        runCatching {
            val json = JSONObject(raw)
            WatchAccountState(
                userId = json.optString("userId").trim(),
                phoneMasked = json.optString("phoneMasked").trim(),
                installId = json.optString("installId").trim(),
                watchDeviceToken = json.optString("watchAccessToken")
                    .ifBlank { json.optString("watchDeviceToken") }.trim(),
                tokenExpiresAtMillis = json.optLong(
                    "accessTokenExpiresAtMillis", json.optLong("tokenExpiresAtMillis")
                ),
                watchRefreshToken = json.optString("watchRefreshToken").trim(),
                refreshTokenExpiresAtMillis = json.optLong("refreshTokenExpiresAtMillis"),
                backendBaseUrl = json.optString("backendBaseUrl").trim(),
                posthogHost = json.optString("posthogHost").trim(),
                posthogProjectApiKey = json.optString("posthogProjectApiKey").trim(),
                entitlement = decodeEntitlement(json.optJSONObject("entitlement")),
                telemetryConfig = decodeTelemetryConfig(json.optJSONObject("telemetryConfig")),
                updatedAtMillis = json.optLong("updatedAtMillis")
            ).takeIf { it.userId.isNotBlank() && it.watchDeviceToken.isNotBlank() }
        }.getOrNull()

    private fun decodeEntitlement(json: JSONObject?): WatchEntitlementSnapshot {
        if (json == null) return WatchEntitlementSnapshot()
        return WatchEntitlementSnapshot(
            plan = json.optString("plan").ifBlank { "free" },
            active = json.optBoolean("active", true),
            expiresAtMillis = json.optLong("expiresAtMillis"),
            features = json.optJSONArray("features").toStringList()
        )
    }

    private fun decodeTelemetryConfig(json: JSONObject?): WatchTelemetryConfig {
        if (json == null) return WatchTelemetryConfig()
        return WatchTelemetryConfig(
            anonymousEnabled = json.optBoolean("anonymousEnabled", true),
            diagnosticsEnabled = json.optBoolean("diagnosticsEnabled", false),
            sampleRate = json.optDouble("sampleRate", 1.0)
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun isRecoverableCryptoFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is GeneralSecurityException || current is IOException) return true
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
        private const val TAG = "WatchAccountStore"
        private const val KEY_STATE = "state_json"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}
