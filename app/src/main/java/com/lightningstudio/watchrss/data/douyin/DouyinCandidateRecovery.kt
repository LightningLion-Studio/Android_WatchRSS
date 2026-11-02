package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideoCodec

/** At most three distinct CDN sources per signed feed generation, including the failed source. */
internal class DouyinCandidateRecovery {
    private data class Attempt(val generation: Long, val urls: MutableSet<String>)
    private val feedAttempts = linkedMapOf<String, Long>()

    @Synchronized
    fun allowFeedRefresh(item: DouyinStreamItem): Boolean {
        if (feedAttempts[item.awemeId] == item.playUrlResolvedAtMs) return false
        feedAttempts[item.awemeId] = item.playUrlResolvedAtMs
        while (feedAttempts.size > 64) feedAttempts.remove(feedAttempts.keys.first())
        return true
    }

    private val attempts = linkedMapOf<String, Attempt>()

    @Synchronized
    fun next(item: DouyinStreamItem): DouyinStreamItem? {
        val attempt = attempts[item.awemeId]?.takeIf { it.generation == item.playUrlResolvedAtMs }
            ?: Attempt(item.playUrlResolvedAtMs, linkedSetOf()).also { attempts[item.awemeId] = it }
        while (attempts.size > 64) attempts.remove(attempts.keys.first())
        attempt.urls.add(item.playUrl)
        if (attempt.urls.size >= 3) return null
        val variant = item.variants.firstOrNull { item.playUrl == it.playUrl || item.playUrl in it.playUrls }
            ?.takeIf(DouyinDecoderCapabilities::supports) ?: return null
        val next = (listOf(variant.playUrl) + variant.playUrls).distinct().firstOrNull {
            (it.startsWith("https://") || it.startsWith("http://")) && it !in attempt.urls
        } ?: return null
        attempt.urls.add(next)
        return item.copy(playUrl = next)
    }
}

internal fun DouyinVideo.toStreamItem(nowMs: Long = System.currentTimeMillis()): DouyinStreamItem? {
    val id = awemeId?.trim().orEmpty()
    val url = playUrl?.trim().orEmpty()
    if (id.isBlank() || url.isBlank()) return null
    return DouyinStreamItem(id, url, coverUrl, desc, authorName, likeCount, nowMs,
        DouyinSourceOrigin.NETWORK_FEED, duration.toLong().coerceAtLeast(0), variants)
}

internal suspend fun refreshDouyinItemFromFeed(
    repository: DouyinRepositoryContract,
    item: DouyinStreamItem
): DouyinResult<DouyinStreamItem> {
    val result = repository.fetchFeedPage(cursor = null, count = 12)
    val fresh = result.data?.items?.firstOrNull { it.awemeId == item.awemeId }?.toStreamItem()
    return if (result.isSuccess && fresh != null) DouyinResult(DouyinErrorCodes.OK, data = fresh)
    else DouyinResult(if (result.isSuccess) DouyinErrorCodes.REQUEST_FAILED else result.code,
        result.message ?: "当前视频已过期，请刷新推荐")
}
