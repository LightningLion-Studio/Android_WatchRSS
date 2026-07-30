package com.lightningstudio.watchrss.data.push

import android.content.Context
import android.content.SharedPreferences
import com.lightningstudio.watchrss.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "watchrss_push"
private const val KEY_LAST_SEEN_ID = "last_seen_notification_id"

class PushNotificationRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    data class PushMessage(
        val id: Int,
        val title: String,
        val body: String
    )

    suspend fun fetchNewMessages(topic: String = "watchrss", limit: Int = 10): List<PushMessage> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.WATCHRSS_LOCAL_BACKEND_URL}/api/notify/$topic?limit=$limit")
            .get()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val array = JSONArray(body)
                val lastSeen = prefs.getInt(KEY_LAST_SEEN_ID, 0)
                val messages = mutableListOf<PushMessage>()
                var maxId = lastSeen
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optInt("id", 0)
                    if (id > lastSeen) {
                        messages.add(
                            PushMessage(
                                id = id,
                                title = obj.optString("title", ""),
                                body = obj.optString("body", "")
                            )
                        )
                    }
                    if (id > maxId) maxId = id
                }
                if (maxId > lastSeen) {
                    prefs.edit().putInt(KEY_LAST_SEEN_ID, maxId).apply()
                }
                messages.reversed()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
