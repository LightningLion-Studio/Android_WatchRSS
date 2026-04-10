package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinContent

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

    val refreshedAwemeIds = mutableListOf<String>()
    val refreshedItems = items.map { item ->
        if (!isDouyinPlayUrlExpired(item = item, nowMs = nowMs, ttlMs = ttlMs)) {
            return@map item
        }
        val result = repository.fetchVideo(item.awemeId)
        val content = result.data as? DouyinContent.Video
        val refreshedPlayUrl = content?.playUrl?.trim().orEmpty()
        if (!result.isSuccess || content == null || refreshedPlayUrl.isEmpty()) {
            return@map item
        }
        refreshedAwemeIds += item.awemeId
        item.copy(
            playUrl = refreshedPlayUrl,
            coverUrl = content.coverUrl.takeIf { it.isNotBlank() } ?: item.coverUrl,
            title = content.desc.takeIf { it.isNotBlank() } ?: item.title,
            author = content.authorName.takeIf { it.isNotBlank() } ?: item.author,
            likeCount = content.diggCount,
            playUrlResolvedAtMs = nowMs,
            sourceOrigin = DouyinSourceOrigin.VIDEO_REFRESH,
            variants = content.variants
        )
    }
    return DouyinBootstrapPlayUrlRefreshResult(
        items = refreshedItems,
        refreshedAwemeIds = refreshedAwemeIds
    )
}
