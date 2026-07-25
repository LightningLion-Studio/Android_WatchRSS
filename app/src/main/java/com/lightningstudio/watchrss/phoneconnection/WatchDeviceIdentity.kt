package com.lightningstudio.watchrss.phoneconnection

import android.content.Context
import java.util.UUID

class WatchDeviceIdentity(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "watch_device_identity",
        Context.MODE_PRIVATE
    )

    val deviceId: String
        get() {
            val existing = preferences.getString(KEY_DEVICE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val created = UUID.randomUUID().toString()
            preferences.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
    }
}
