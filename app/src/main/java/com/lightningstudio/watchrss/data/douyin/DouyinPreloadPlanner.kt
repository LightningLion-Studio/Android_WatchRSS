package com.lightningstudio.watchrss.data.douyin

const val DOUYIN_RECENT_WINDOW_SIZE = 4
const val DOUYIN_PRELOAD_WINDOW_UNWATCHED = 6
const val DOUYIN_ACTIVE_PRELOAD_WINDOW_UNWATCHED = 3
const val DOUYIN_PRELOAD_LOAD_MORE_THRESHOLD = 8
const val DOUYIN_PRELOAD_CACHE_RESERVE_COUNT = 6
internal const val DOUYIN_PRELOAD_CACHE_FLOOR_COUNT = DOUYIN_RECENT_WINDOW_SIZE
internal const val DOUYIN_PRELOAD_MAX_CACHE_ENTRIES = 64

fun resolveDouyinResumeAnchorAwemeId(
    items: List<DouyinStreamItem>,
    latestWatchedAwemeId: String?
): String? {
    if (items.isEmpty()) return null
    val normalizedLatestAwemeId = latestWatchedAwemeId?.trim().orEmpty()
    if (normalizedLatestAwemeId.isEmpty()) {
        return items.firstOrNull()?.awemeId
    }
    val latestIndex = items.indexOfFirst { it.awemeId == normalizedLatestAwemeId }
    val targetIndex = when {
        latestIndex < 0 -> 0
        latestIndex < items.lastIndex -> latestIndex + 1
        else -> 0
    }
    return items.getOrNull(targetIndex)?.awemeId
}

fun resolveDouyinPlaybackAnchorAwemeId(
    items: List<DouyinStreamItem>,
    currentPage: Int
): String? {
    if (items.isEmpty()) return null
    val targetIndex = if (currentPage > 0) {
        (currentPage - 1).coerceIn(0, items.lastIndex)
    } else {
        0
    }
    return items.getOrNull(targetIndex)?.awemeId
}

fun prioritizeDouyinPreloadItems(
    items: List<DouyinStreamItem>,
    anchorAwemeId: String?
): List<DouyinStreamItem> {
    if (items.size <= 1) return items
    val normalizedAnchorAwemeId = anchorAwemeId?.trim().orEmpty()
    if (normalizedAnchorAwemeId.isEmpty()) return items
    val anchorIndex = items.indexOfFirst { it.awemeId == normalizedAnchorAwemeId }
    if (anchorIndex <= 0) return items
    return items.drop(anchorIndex) + items.take(anchorIndex)
}

fun buildDouyinRecentWindow(
    items: List<DouyinStreamItem>,
    anchorIndex: Int
): List<DouyinStreamItem> {
    if (items.isEmpty() || anchorIndex !in items.indices) return emptyList()
    val startIndex = (anchorIndex - 1).coerceAtLeast(0)
    val endExclusive = (anchorIndex + 3).coerceAtMost(items.size)
    return items.subList(startIndex, endExclusive).toList()
}

fun mergeDouyinBootstrapItems(
    feedItems: List<DouyinStreamItem>,
    recentItems: List<DouyinStreamItem>,
    limit: Int = Int.MAX_VALUE
): List<DouyinStreamItem> {
    val normalizedRecentItems = recentItems
        .fold(linkedMapOf<String, DouyinStreamItem>()) { acc, item ->
            val awemeId = item.awemeId.trim()
            if (awemeId.isNotEmpty()) {
                acc.putIfAbsent(awemeId, item)
            }
            acc
        }
        .values
        .toList()
    if (normalizedRecentItems.isEmpty()) {
        return if (limit > 0) feedItems.take(limit) else feedItems
    }
    if (feedItems.isEmpty()) {
        return if (limit > 0) normalizedRecentItems.take(limit) else normalizedRecentItems
    }

    val recentIds = normalizedRecentItems.mapTo(linkedSetOf()) { it.awemeId }
    val overlapIndices = feedItems.indices.filter { index ->
        recentIds.contains(feedItems[index].awemeId)
    }
    val merged = if (overlapIndices.isEmpty()) {
        normalizedRecentItems + feedItems.filterNot { recentIds.contains(it.awemeId) }
    } else {
        val firstOverlap = overlapIndices.first()
        val lastOverlap = overlapIndices.last()
        val prefix = feedItems.take(firstOverlap).filterNot { recentIds.contains(it.awemeId) }
        val suffix = feedItems.drop(lastOverlap + 1).filterNot { recentIds.contains(it.awemeId) }
        prefix + normalizedRecentItems + suffix
    }
    return if (limit > 0) merged.take(limit) else merged
}

fun resolveDouyinLookaheadItemIndices(
    activeItemIndex: Int?,
    itemCount: Int,
    extraCount: Int
): List<Int> {
    val currentIndex = activeItemIndex ?: return emptyList()
    if (extraCount <= 0 || itemCount <= 0) return emptyList()
    val result = ArrayList<Int>(extraCount)
    for (offset in 1..extraCount) {
        val nextIndex = currentIndex + offset
        if (nextIndex !in 0 until itemCount) break
        result += nextIndex
    }
    return result
}
