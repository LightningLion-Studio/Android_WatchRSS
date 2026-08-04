package com.lightningstudio.watchrss.data.announcement

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
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
        val forceUpdate: Boolean,
        val downloadUrl: String
    )

    suspend fun checkAnnouncement(): Announcement? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.WATCHRSS_BACKEND_URL}/functions/v1/announcement")
            .get()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val announcement = parseAnnouncement(json) ?: return@withContext null
                if (compareVersions(announcement.version, BuildConfig.VERSION_NAME) <= 0) {
                    return@withContext null
                }
                if (!announcement.forceUpdate && announcement.version == prefs.getString(KEY_DISMISSED_VERSION, null)) {
                    return@withContext null
                }
                announcement
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to check app update", e)
            null
        }
    }

    fun markDismissed(version: String) {
        prefs.edit { putString(KEY_DISMISSED_VERSION, version) }
    }

    companion object {
        private const val TAG = "AnnouncementRepository"

        internal fun parseAnnouncement(json: JSONObject): Announcement? {
            val version = json.optString("version").trim()
            val changelog = json.optString("changelog_md").trim()
            val downloadUrl = json.optString("download_url").trim()
            if (version.isEmpty() || changelog.isEmpty() || downloadUrl.isEmpty()) return null
            return Announcement(
                version = version,
                changelogMarkdown = changelog,
                forceUpdate = json.optBoolean("force_update", false),
                downloadUrl = downloadUrl
            )
        }

        internal fun compareVersions(left: String, right: String): Int {
            val leftParts = Regex("\\d+").findAll(left).map { it.value.toLongOrNull() ?: 0L }.toList()
            val rightParts = Regex("\\d+").findAll(right).map { it.value.toLongOrNull() ?: 0L }.toList()
            val length = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until length) {
                val result = leftParts.getOrElse(index) { 0L }
                    .compareTo(rightParts.getOrElse(index) { 0L })
                if (result != 0) return result
            }
            return 0
        }
    }
}
