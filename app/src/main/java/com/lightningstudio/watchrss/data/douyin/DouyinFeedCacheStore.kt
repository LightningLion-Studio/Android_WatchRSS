package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DouyinFeedCacheStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(items: List<DouyinStreamItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("awemeId", item.awemeId)
                    .put("playUrl", item.playUrl)
                    .put("coverUrl", item.coverUrl)
                    .put("title", item.title)
                    .put("author", item.author)
                    .put("likeCount", item.likeCount)
            )
        }
        prefs.edit().putString(KEY_CACHE_JSON, array.toString()).apply()
    }

    fun read(limit: Int = 20): List<DouyinStreamItem> {
        val raw = prefs.getString(KEY_CACHE_JSON, null) ?: return emptyList()
        return runCatching {
            val result = mutableListOf<DouyinStreamItem>()
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val awemeId = obj.optString("awemeId").trim()
                val playUrl = obj.optString("playUrl").trim()
                if (awemeId.isEmpty() || playUrl.isEmpty()) continue
                result += DouyinStreamItem(
                    awemeId = awemeId,
                    playUrl = playUrl,
                    coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                    title = obj.optString("title").takeIf { it.isNotBlank() },
                    author = obj.optString("author").takeIf { it.isNotBlank() },
                    likeCount = obj.optLong("likeCount", 0L)
                )
            }
            if (limit > 0) result.take(limit) else result
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val PREFS_NAME = "douyin_feed_cache"
        private const val KEY_CACHE_JSON = "feed_cache_json"
    }
}
