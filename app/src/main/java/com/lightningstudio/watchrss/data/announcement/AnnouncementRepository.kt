package com.lightningstudio.watchrss.data.announcement

import android.content.Context
import android.content.SharedPreferences
import com.lightningstudio.watchrss.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val PREFS_NAME = "watchrss_announcement"
private const val KEY_DISMISSED_VERSION = "dismissed_version"

class AnnouncementRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    data class Announcement(
        val version: String,
        val changelogMarkdown: String,
        val forceUpdate: Boolean
    )

    suspend fun checkAnnouncement(): Announcement? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.WATCHRSS_LOCAL_BACKEND_URL}/api/announcement")
            .get()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val version = json.optString("version", "")
                if (version.isBlank() || isVersionLessThanOrEqual(version, BuildConfig.VERSION_NAME)) {
                    return@withContext null
                }
                if (version == prefs.getString(KEY_DISMISSED_VERSION, null)) {
                    return@withContext null
                }
                Announcement(
                    version = version,
                    changelogMarkdown = json.optString("changelog_md", ""),
                    forceUpdate = json.optBoolean("force_update", false)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    fun markDismissed(version: String) {
        prefs.edit().putString(KEY_DISMISSED_VERSION, version).apply()
    }

    private fun isVersionLessThanOrEqual(v1: String, v2: String): Boolean {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a < b) return true
            if (a > b) return false
        }
        return true
    }
}
