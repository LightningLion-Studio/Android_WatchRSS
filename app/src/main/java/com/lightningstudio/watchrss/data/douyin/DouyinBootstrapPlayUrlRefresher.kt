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
    if (resolvedAtMs <= 0L || resolvedAtMs > nowMs) return true
    if (nowMs - resolvedAtMs >= ttlMs) return true
    val deadline = runCatching {
        java.net.URI(item.playUrl).rawQuery.orEmpty().split('&').mapNotNull { parameter ->
            val parts = parameter.split('=', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            when (parts[0].lowercase()) {
                "expire", "expires", "x-expires" -> parts[1].toLongOrNull()?.times(1_000)
                "wstime" -> parts[1].toLongOrNull(16)?.times(1_000)
                else -> null
            }
        }.minOrNull()
    }.getOrNull()
    return deadline != null && nowMs >= deadline
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
    if (fresh.isEmpty()) return DouyinBootstrapPlayUrlRefreshResult(
        items.filterNot { isDouyinPlayUrlExpired(it, nowMs, ttlMs) }, emptyList())
    val retained = items.filterNot { isDouyinPlayUrlExpired(it, nowMs, ttlMs) }
    val merged = mergeDouyinPrioritizedItems(retained, fresh, maxOf(items.size, fresh.size))
    return DouyinBootstrapPlayUrlRefreshResult(merged, fresh.map { it.awemeId })
}
