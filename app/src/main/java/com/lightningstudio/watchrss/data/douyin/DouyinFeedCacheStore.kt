package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

interface DouyinFeedCacheStoreContract {
    fun save(
        items: List<DouyinStreamItem>,
        nextCursor: String? = null,
        hasMore: Boolean = items.isNotEmpty(),
        savedAtMs: Long = System.currentTimeMillis()
    )
    fun read(limit: Int = 20): List<DouyinStreamItem>
    fun readSnapshot(limit: Int = 20): DouyinFeedCacheSnapshot
}

data class DouyinFeedCacheSnapshot(
    val items: List<DouyinStreamItem>,
    val savedAtMs: Long,
    val nextCursor: String?,
    val hasMore: Boolean
)

class DouyinFeedCacheStore(context: Context) : DouyinFeedCacheStoreContract {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun save(items: List<DouyinStreamItem>, nextCursor: String?, hasMore: Boolean, savedAtMs: Long) {
        prefs.edit().putString(
            KEY_CACHE_JSON,
            serializeDouyinFeedCache(
                items = items,
                nextCursor = nextCursor,
                hasMore = hasMore,
                savedAtMs = savedAtMs
            )
        ).apply()
    }

    override fun read(limit: Int): List<DouyinStreamItem> = readSnapshot(limit).items

    override fun readSnapshot(limit: Int): DouyinFeedCacheSnapshot {
        val raw = prefs.getString(KEY_CACHE_JSON, null)
            ?: return DouyinFeedCacheSnapshot(emptyList(), 0L, null, false)
        return parseDouyinFeedCache(raw, limit)
    }

    companion object {
        private const val PREFS_NAME = "douyin_feed_cache"
        private const val KEY_CACHE_JSON = "feed_cache_json"
    }
}

internal fun serializeDouyinFeedCache(
    items: List<DouyinStreamItem>,
    nextCursor: String? = null,
    hasMore: Boolean = items.isNotEmpty(),
    savedAtMs: Long = System.currentTimeMillis()
): String {
    return buildString {
        append("{\"savedAtMs\":")
        append(savedAtMs)
        append(",\"hasMore\":")
        append(if (hasMore) "true" else "false")
        append(",\"nextCursor\":")
        if (nextCursor.isNullOrBlank()) {
            append("null")
        } else {
            appendJsonStringValue(nextCursor)
        }
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
            append(",\"durationMs\":")
            append(item.durationMs)
            append(",\"sourceOrigin\":")
            appendJsonStringValue(item.sourceOrigin.name)
            append(",\"variants\":")
            append(encodeDouyinVariants(item.variants).toString())
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
        val nextCursor: String?
        val hasMore: Boolean
        when {
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                savedAtMs = root.optLong("savedAtMs", 0L)
                nextCursor = root.optString("nextCursor").takeIf { it.isNotBlank() }
                hasMore = if (root.has("hasMore")) {
                    root.optBoolean("hasMore", false)
                } else {
                    true
                }
                array = root.optJSONArray("items") ?: root.optJSONArray("aweme_list") ?: JSONArray()
            }
            trimmed.startsWith("[") -> {
                savedAtMs = 0L
                nextCursor = null
                hasMore = true
                array = JSONArray(trimmed)
            }
            else -> {
                savedAtMs = 0L
                nextCursor = null
                hasMore = false
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
                sourceOrigin = DouyinSourceOrigin.fromPersistedValue(obj.optString("sourceOrigin").takeIf { it.isNotBlank() }),
                durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
                variants = decodeDouyinVariants(obj.optJSONArray("variants"))
            )
        }
        val limited = if (limit > 0) result.take(limit) else result
        DouyinFeedCacheSnapshot(
            items = limited,
            savedAtMs = savedAtMs,
            nextCursor = nextCursor,
            hasMore = hasMore && limited.isNotEmpty()
        )
    }.getOrElse {
        DouyinFeedCacheSnapshot(emptyList(), 0L, null, false)
    }
}
