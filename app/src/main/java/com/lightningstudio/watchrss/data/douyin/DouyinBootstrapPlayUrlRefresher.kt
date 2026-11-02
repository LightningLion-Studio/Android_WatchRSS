package com.lightningstudio.watchrss.data.douyin

data class DouyinBootstrapPlayUrlRefreshResult(
    val items: List<DouyinStreamItem>,
    val refreshedAwemeIds: List<String>
)

fun isDouyinPlayUrlExpired(
    item: DouyinStreamItem,
    nowMs: Long = System.currentTimeMillis(),
    ttlMs: Long = DOUYIN_PLAY_URL_TTL_MS
): Boolean {
    val resolvedAtMs = item.playUrlResolvedAtMs
    if (resolvedAtMs <= 0L) return true
    return nowMs - resolvedAtMs >= ttlMs
}

suspend fun refreshExpiredDouyinBootstrapPlayUrls(
    items: List<DouyinStreamItem>,
    repository: DouyinRepositoryContract,
    nowMs: Long = System.currentTimeMillis(),
    ttlMs: Long = DOUYIN_PLAY_URL_TTL_MS
): DouyinBootstrapPlayUrlRefreshResult {
    if (items.isEmpty()) {
        return DouyinBootstrapPlayUrlRefreshResult(
            items = emptyList(),
            refreshedAwemeIds = emptyList()
        )
    }

    if (items.none { isDouyinPlayUrlExpired(it, nowMs, ttlMs) }) {
        return DouyinBootstrapPlayUrlRefreshResult(items, emptyList())
    }
    val feed = kotlinx.coroutines.withTimeoutOrNull(10_000) { repository.fetchFeedPage(null, 12) }
    val fresh = feed?.takeIf { it.isSuccess }?.data?.items.orEmpty().mapNotNull { it.toStreamItem(nowMs) }
    return replenishExpiredBootstrap(items, fresh, nowMs, ttlMs)
}

suspend fun refreshExpiredDouyinBootstrapPlayUrls(
    items: List<DouyinStreamItem>,
    coordinator: DouyinPlaybackSourceCoordinatorContract,
    trigger: DouyinPlaybackRefreshTrigger,
    nowMs: Long = System.currentTimeMillis(),
    ttlMs: Long = DOUYIN_PLAY_URL_TTL_MS
): DouyinBootstrapPlayUrlRefreshResult {
    if (items.isEmpty()) {
        return DouyinBootstrapPlayUrlRefreshResult(
            items = emptyList(),
            refreshedAwemeIds = emptyList()
        )
    }
    if (items.none { isDouyinPlayUrlExpired(it, nowMs, ttlMs) }) {
        return DouyinBootstrapPlayUrlRefreshResult(items, emptyList())
    }
    return replenishExpiredBootstrap(items, coordinator.replenishFeed(), nowMs, ttlMs)
}

private fun replenishExpiredBootstrap(
    items: List<DouyinStreamItem>, fresh: List<DouyinStreamItem>, nowMs: Long, ttlMs: Long
): DouyinBootstrapPlayUrlRefreshResult {
    if (fresh.isEmpty()) return DouyinBootstrapPlayUrlRefreshResult(items, emptyList())
    val retained = items.filterNot { isDouyinPlayUrlExpired(it, nowMs, ttlMs) }
    val merged = mergeDouyinPrioritizedItems(retained, fresh, maxOf(items.size, fresh.size))
    return DouyinBootstrapPlayUrlRefreshResult(merged, fresh.map { it.awemeId })
}
