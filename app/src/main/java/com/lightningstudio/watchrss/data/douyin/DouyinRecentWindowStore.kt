package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DouyinRecentWindowSnapshot(
    val items: List<DouyinStreamItem>,
    val anchorAwemeId: String?,
    val savedAtMs: Long
)

interface DouyinRecentWindowStoreContract {
    fun saveWindow(
        items: List<DouyinStreamItem>,
        anchorAwemeId: String?,
        savedAtMs: Long = System.currentTimeMillis()
    )

    fun readSnapshot(limit: Int = DOUYIN_RECENT_WINDOW_SIZE): DouyinRecentWindowSnapshot

    fun clear()
}

object NoOpDouyinRecentWindowStore : DouyinRecentWindowStoreContract {
    override fun saveWindow(items: List<DouyinStreamItem>, anchorAwemeId: String?, savedAtMs: Long) = Unit

    override fun readSnapshot(limit: Int): DouyinRecentWindowSnapshot {
        return DouyinRecentWindowSnapshot(
            items = emptyList(),
            anchorAwemeId = null,
            savedAtMs = 0L
        )
    }

    override fun clear() = Unit
}

class DouyinRecentWindowStore(context: Context) : DouyinRecentWindowStoreContract {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveWindow(items: List<DouyinStreamItem>, anchorAwemeId: String?, savedAtMs: Long) {
        val normalizedItems = items
            .fold(linkedMapOf<String, DouyinStreamItem>()) { acc, item ->
                val awemeId = item.awemeId.trim()
                val playUrl = item.playUrl.trim()
                if (awemeId.isNotEmpty() && playUrl.isNotEmpty()) {
                    acc.putIfAbsent(awemeId, item.copy(awemeId = awemeId, playUrl = playUrl))
                }
                acc
            }
            .values
            .take(DOUYIN_RECENT_WINDOW_SIZE)
        val normalizedAnchorAwemeId = anchorAwemeId?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit()
            .putString(
                KEY_WINDOW_JSON,
                encodeDouyinRecentWindow(
                    items = normalizedItems,
                    anchorAwemeId = normalizedAnchorAwemeId,
                    savedAtMs = savedAtMs
                )
            )
            .apply()
    }

    override fun readSnapshot(limit: Int): DouyinRecentWindowSnapshot {
        val raw = prefs.getString(KEY_WINDOW_JSON, null)
            ?: return DouyinRecentWindowSnapshot(emptyList(), null, 0L)
        return parseDouyinRecentWindow(raw, limit)
    }

    override fun clear() {
        prefs.edit().remove(KEY_WINDOW_JSON).apply()
    }

    companion object {
        private const val PREFS_NAME = "douyin_recent_window"
        private const val KEY_WINDOW_JSON = "recent_window_json"
    }
}

private fun encodeDouyinRecentWindow(
    items: List<DouyinStreamItem>,
    anchorAwemeId: String?,
    savedAtMs: Long
): String {
    val array = JSONArray()
    items.forEach { item ->
        array.put(
            JSONObject().apply {
                put("awemeId", item.awemeId)
                put("playUrl", item.playUrl)
                put("coverUrl", item.coverUrl.orEmpty())
                put("title", item.title.orEmpty())
                put("author", item.author.orEmpty())
                put("likeCount", item.likeCount)
                put("playUrlResolvedAtMs", item.playUrlResolvedAtMs)
                put("durationMs", item.durationMs)
                put("sourceOrigin", item.sourceOrigin.name)
                put("variants", encodeDouyinVariants(item.variants))
            }
        )
    }
    return JSONObject().apply {
        put("savedAtMs", savedAtMs)
        put("anchorAwemeId", anchorAwemeId.orEmpty())
        put("items", array)
    }.toString()
}

internal fun parseDouyinRecentWindow(
    raw: String,
    limit: Int = DOUYIN_RECENT_WINDOW_SIZE
): DouyinRecentWindowSnapshot {
    return runCatching {
        val root = JSONObject(raw)
        val savedAtMs = root.optLong("savedAtMs", 0L)
        val anchorAwemeId = root.optString("anchorAwemeId").takeIf { it.isNotBlank() }
        val array = root.optJSONArray("items") ?: JSONArray()
        val items = buildList(array.length()) {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val awemeId = json.optString("awemeId").trim()
                val playUrl = json.optString("playUrl").trim()
                if (awemeId.isEmpty() || playUrl.isEmpty()) continue
                add(
                    DouyinStreamItem(
                        awemeId = awemeId,
                        playUrl = playUrl,
                        coverUrl = json.optString("coverUrl").takeIf { it.isNotBlank() },
                        title = json.optString("title").takeIf { it.isNotBlank() },
                        author = json.optString("author").takeIf { it.isNotBlank() },
                        likeCount = json.optLong("likeCount", 0L),
                        playUrlResolvedAtMs = json.optLong("playUrlResolvedAtMs", savedAtMs),
                        durationMs = json.optLong("durationMs", 0L).coerceAtLeast(0L),
                        sourceOrigin = DouyinSourceOrigin.fromPersistedValue(
                            json.optString("sourceOrigin").takeIf { it.isNotBlank() }
                        ),
                        variants = decodeDouyinVariants(json.optJSONArray("variants"))
                    )
                )
            }
        }
        DouyinRecentWindowSnapshot(
            items = if (limit > 0) items.take(limit) else items,
            anchorAwemeId = anchorAwemeId,
            savedAtMs = savedAtMs
        )
    }.getOrElse {
        DouyinRecentWindowSnapshot(emptyList(), null, 0L)
    }
}
