package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DouyinWatchHistoryEntry(
    val awemeId: String,
    val title: String?,
    val author: String?,
    val coverUrl: String?,
    val playUrl: String,
    val likeCount: Long,
    val watchedAt: Long
)

interface DouyinWatchHistoryStoreContract {
    fun markWatched(item: DouyinStreamItem)
    fun markWatched(awemeId: String)
    fun readWatchedIds(): Set<String>
    fun readHistory(): List<DouyinWatchHistoryEntry>
    fun clear()
}

class DouyinWatchHistoryStore(context: Context) : DouyinWatchHistoryStoreContract {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun markWatched(item: DouyinStreamItem) {
        val awemeId = item.awemeId.trim()
        val playUrl = item.playUrl.trim()
        if (awemeId.isEmpty() || playUrl.isEmpty()) return

        val entry = DouyinWatchHistoryEntry(
            awemeId = awemeId,
            title = item.title?.takeIf { it.isNotBlank() },
            author = item.author?.takeIf { it.isNotBlank() },
            coverUrl = item.coverUrl?.takeIf { it.isNotBlank() },
            playUrl = playUrl,
            likeCount = item.likeCount,
            watchedAt = System.currentTimeMillis()
        )
        val history = readHistoryInternal()
            .filterNot { it.awemeId == awemeId }
            .toMutableList()
            .apply { add(0, entry) }
            .take(MAX_HISTORY_ITEMS)

        persist(
            watchedIds = updatedWatchedIds(awemeId),
            history = history
        )
    }

    override fun markWatched(awemeId: String) {
        val id = awemeId.trim()
        if (id.isEmpty()) return
        persist(
            watchedIds = updatedWatchedIds(id),
            history = readHistoryInternal()
        )
    }

    override fun readWatchedIds(): Set<String> {
        return prefs.getStringSet(KEY_WATCHED_IDS, emptySet()).orEmpty().toSet()
    }

    override fun readHistory(): List<DouyinWatchHistoryEntry> = readHistoryInternal()

    override fun clear() {
        prefs.edit()
            .remove(KEY_WATCHED_IDS)
            .remove(KEY_HISTORY_JSON)
            .apply()
    }

    private fun updatedWatchedIds(awemeId: String): Set<String> {
        val current = prefs.getStringSet(KEY_WATCHED_IDS, emptySet()).orEmpty().toMutableList()
        current.remove(awemeId)
        current.add(awemeId)
        return if (current.size > MAX_IDS) {
            current.takeLast(MAX_IDS).toSet()
        } else {
            current.toSet()
        }
    }

    private fun persist(
        watchedIds: Set<String>,
        history: List<DouyinWatchHistoryEntry>
    ) {
        prefs.edit()
            .putStringSet(KEY_WATCHED_IDS, watchedIds)
            .putString(KEY_HISTORY_JSON, encodeHistory(history))
            .apply()
    }

    private fun readHistoryInternal(): List<DouyinWatchHistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val awemeId = json.optString(JSON_AWEME_ID).trim()
                    val playUrl = json.optString(JSON_PLAY_URL).trim()
                    if (awemeId.isEmpty() || playUrl.isEmpty()) continue
                    add(
                        DouyinWatchHistoryEntry(
                            awemeId = awemeId,
                            title = json.optString(JSON_TITLE).takeIf { it.isNotBlank() },
                            author = json.optString(JSON_AUTHOR).takeIf { it.isNotBlank() },
                            coverUrl = json.optString(JSON_COVER_URL).takeIf { it.isNotBlank() },
                            playUrl = playUrl,
                            likeCount = json.optLong(JSON_LIKE_COUNT),
                            watchedAt = json.optLong(JSON_WATCHED_AT)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeHistory(history: List<DouyinWatchHistoryEntry>): String {
        val array = JSONArray()
        history.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put(JSON_AWEME_ID, entry.awemeId)
                    put(JSON_PLAY_URL, entry.playUrl)
                    put(JSON_LIKE_COUNT, entry.likeCount)
                    put(JSON_WATCHED_AT, entry.watchedAt)
                    put(JSON_TITLE, entry.title.orEmpty())
                    put(JSON_AUTHOR, entry.author.orEmpty())
                    put(JSON_COVER_URL, entry.coverUrl.orEmpty())
                }
            )
        }
        return array.toString()
    }

    companion object {
        private const val PREFS_NAME = "douyin_watch_history"
        private const val KEY_WATCHED_IDS = "watched_ids"
        private const val KEY_HISTORY_JSON = "history_json"
        private const val MAX_IDS = 1000
        private const val MAX_HISTORY_ITEMS = 200
        private const val JSON_AWEME_ID = "aweme_id"
        private const val JSON_TITLE = "title"
        private const val JSON_AUTHOR = "author"
        private const val JSON_COVER_URL = "cover_url"
        private const val JSON_PLAY_URL = "play_url"
        private const val JSON_LIKE_COUNT = "like_count"
        private const val JSON_WATCHED_AT = "watched_at"
    }
}
