package com.lightningstudio.watchrss.data.douyin

import android.content.Context

class DouyinWatchHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markWatched(awemeId: String) {
        val id = awemeId.trim()
        if (id.isEmpty()) return
        val current = prefs.getStringSet(KEY_WATCHED_IDS, emptySet()).orEmpty().toMutableSet()
        if (current.add(id)) {
            // Avoid unbounded growth on watch device.
            val trimmed = if (current.size > MAX_IDS) current.takeLast(MAX_IDS).toSet() else current
            prefs.edit().putStringSet(KEY_WATCHED_IDS, trimmed).apply()
        }
    }

    fun readWatchedIds(): Set<String> {
        return prefs.getStringSet(KEY_WATCHED_IDS, emptySet()).orEmpty()
    }

    fun clear() {
        prefs.edit().remove(KEY_WATCHED_IDS).apply()
    }

    private fun <T> Set<T>.takeLast(n: Int): List<T> {
        if (size <= n) return toList()
        return toList().takeLast(n)
    }

    companion object {
        private const val PREFS_NAME = "douyin_watch_history"
        private const val KEY_WATCHED_IDS = "watched_ids"
        private const val MAX_IDS = 1000
    }
}
