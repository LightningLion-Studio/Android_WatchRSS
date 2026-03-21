package com.lightningstudio.watchrss.data.settings

enum class RssInlineImagePrefetchMode(
    val persistedValue: Int,
    val label: String,
    val summary: String,
    val prefetchCount: Int?
) {
    OFF(
        persistedValue = 0,
        label = "0张",
        summary = "不提前缓存原文媒体",
        prefetchCount = 0
    ),
    FIRST_FEW(
        persistedValue = 1,
        label = "4张",
        summary = "进入原文后后台缓存前 4 项媒体",
        prefetchCount = 4
    ),
    ALL(
        persistedValue = 2,
        label = "全部",
        summary = "进入原文后后台缓存全部媒体",
        prefetchCount = null
    );

    companion object {
        fun fromPersistedValue(value: Int): RssInlineImagePrefetchMode {
            return values().firstOrNull { it.persistedValue == value }
                ?: DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE
        }
    }
}

val DEFAULT_RSS_INLINE_IMAGE_PREFETCH_MODE: RssInlineImagePrefetchMode =
    RssInlineImagePrefetchMode.FIRST_FEW
