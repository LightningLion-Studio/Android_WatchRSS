package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import org.json.JSONObject

class WatchCloudStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "watchrss_cloud_state",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun nextSequence(serverSequence: Long): Long {
        val next = maxOf(preferences.getLong("sequence", 0), serverSequence) + 1
        check(preferences.edit().putLong("sequence", next).commit())
        return next
    }

    fun applied(deviceId: String, full: Boolean): Long =
        appliedJson(full).optLong(deviceId)

    fun markApplied(deviceId: String, sequence: Long, full: Boolean) {
        val json = appliedJson(full).put(deviceId, maxOf(applied(deviceId, full), sequence))
        preferences.edit().putString(appliedKey(full), json.toString()).apply()
    }

    fun lastContentHash(full: Boolean): String? =
        preferences.getString(contentHashKey(full), preferences.getString("content_hash", null))

    fun markUploaded(hash: String, full: Boolean) {
        preferences.edit().putString(contentHashKey(full), hash).apply()
    }

    private fun appliedJson(full: Boolean) = runCatching {
        JSONObject(
            preferences.getString(
                appliedKey(full),
                preferences.getString("applied", "{}")
            ).orEmpty()
        )
    }.getOrDefault(JSONObject())

    private fun appliedKey(full: Boolean): String =
        if (full) "applied_full" else "applied_state"

    private fun contentHashKey(full: Boolean): String =
        if (full) "content_hash_full" else "content_hash_state"
}
