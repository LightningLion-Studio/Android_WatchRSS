package com.lightningstudio.watchrss.ui.util

private data class ContentCacheKey(
    val itemId: Long,
    val contentHash: Int
)

object RssContentCache {
    private const val MAX_ENTRIES = 32
    private val lock = Any()
    private val cache = LinkedHashMap<ContentCacheKey, List<ContentBlock>>(MAX_ENTRIES, 0.75f, true)

    fun getOrPut(
        itemId: Long,
        contentHash: Int,
        builder: () -> List<ContentBlock>
    ): List<ContentBlock> {
        val key = ContentCacheKey(itemId, contentHash)
        synchronized(lock) {
            cache[key]?.let { return it }
        }
        val result = builder()
        synchronized(lock) {
            cache[key] = result
            if (cache.size > MAX_ENTRIES) {
                val iterator = cache.entries.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        return result
    }
}
