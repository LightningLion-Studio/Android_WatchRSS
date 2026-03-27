package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

interface DouyinFeedCacheStoreContract {
    fun save(items: List<DouyinStreamItem>, savedAtMs: Long = System.currentTimeMillis())
    fun read(limit: Int = 20): List<DouyinStreamItem>
    fun readSnapshot(limit: Int = 20): DouyinFeedCacheSnapshot
}

data class DouyinFeedCacheSnapshot(
    val items: List<DouyinStreamItem>,
    val savedAtMs: Long
)

class DouyinFeedCacheStore(context: Context) : DouyinFeedCacheStoreContract {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(items: List<DouyinStreamItem>, savedAtMs: Long) {
        prefs.edit().putString(KEY_CACHE_JSON, serializeDouyinFeedCache(items, savedAtMs)).apply()
    }

    override fun read(limit: Int): List<DouyinStreamItem> = readSnapshot(limit).items

    override fun readSnapshot(limit: Int): DouyinFeedCacheSnapshot {
        val raw = prefs.getString(KEY_CACHE_JSON, null) ?: return DouyinFeedCacheSnapshot(emptyList(), 0L)
        return parseDouyinFeedCache(raw, limit)
    }

    companion object {
        private const val PREFS_NAME = "douyin_feed_cache"
        private const val KEY_CACHE_JSON = "feed_cache_json"
    }
}

internal fun serializeDouyinFeedCache(
    items: List<DouyinStreamItem>,
    savedAtMs: Long = System.currentTimeMillis()
): String {
    return buildString {
        append("{\"savedAtMs\":")
        append(savedAtMs)
        append(",\"items\":[")
        items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            append('{')
            appendJsonStringField("awemeId", item.awemeId)
            append(',')
            appendJsonStringField("playUrl", item.playUrl)
            append(',')
            appendJsonNullableStringField("coverUrl", item.coverUrl)
            append(',')
            appendJsonNullableStringField("title", item.title)
            append(',')
            appendJsonNullableStringField("author", item.author)
            append(",\"likeCount\":")
            append(item.likeCount)
            append(",\"playUrlResolvedAtMs\":")
            append(item.playUrlResolvedAtMs)
            append(",\"sourceOrigin\":")
            appendJsonStringValue(item.sourceOrigin.name)
            append('}')
        }
        append("]}")
    }
}

private fun StringBuilder.appendJsonStringField(name: String, value: String) {
    appendJsonFieldPrefix(name)
    appendJsonStringValue(value)
}

private fun StringBuilder.appendJsonNullableStringField(name: String, value: String?) {
    appendJsonFieldPrefix(name)
    if (value == null) {
        append("null")
    } else {
        appendJsonStringValue(value)
    }
}

private fun StringBuilder.appendJsonFieldPrefix(name: String) {
    append('"')
    append(name)
    append("\":")
}

private fun StringBuilder.appendJsonStringValue(value: String) {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}

internal fun parseDouyinFeedCache(raw: String, limit: Int = 20): DouyinFeedCacheSnapshot {
    return runCatching {
        val trimmed = raw.trim()
        val savedAtMs: Long
        val array: JSONArray
        when {
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                savedAtMs = root.optLong("savedAtMs", 0L)
                array = root.optJSONArray("items") ?: root.optJSONArray("aweme_list") ?: JSONArray()
            }
            trimmed.startsWith("[") -> {
                savedAtMs = 0L
                array = JSONArray(trimmed)
            }
            else -> {
                savedAtMs = 0L
                array = JSONArray()
            }
        }
        val result = mutableListOf<DouyinStreamItem>()
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
                likeCount = obj.optLong("likeCount", 0L),
                playUrlResolvedAtMs = obj.optLong("playUrlResolvedAtMs", savedAtMs),
                sourceOrigin = DouyinSourceOrigin.fromPersistedValue(obj.optString("sourceOrigin").takeIf { it.isNotBlank() })
            )
        }
        val limited = if (limit > 0) result.take(limit) else result
        DouyinFeedCacheSnapshot(items = limited, savedAtMs = savedAtMs)
    }.getOrElse {
        DouyinFeedCacheSnapshot(emptyList(), 0L)
    }
}
