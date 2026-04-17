package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.util.AppLogger
import java.security.MessageDigest

interface DouyinPlaybackTransportContract {
    fun proxyUrisFor(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        reason: String = "register"
    ): Map<String, String>

    fun primeStartupWindow(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        reason: String
    )

    fun stop()
}

object NoOpDouyinPlaybackTransport : DouyinPlaybackTransportContract {
    override fun proxyUrisFor(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        reason: String
    ): Map<String, String> = emptyMap()

    override fun primeStartupWindow(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        reason: String
    ) = Unit

    override fun stop() = Unit
}

class DouyinPlaybackTransport : DouyinPlaybackTransportContract {
    private val lock = Any()

    override fun proxyUrisFor(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        reason: String
    ): Map<String, String> {
        val normalized = items
            .fold(linkedMapOf<String, DouyinStreamItem>()) { acc, item ->
                val awemeId = item.awemeId.trim()
                val playUrl = item.playUrl.trim()
                if (awemeId.isNotEmpty() && playUrl.startsWith("http", ignoreCase = true)) {
                    acc.putIfAbsent(awemeId, item.copy(awemeId = awemeId, playUrl = playUrl))
                }
                acc
            }
            .values
            .toList()
        if (normalized.isEmpty()) return emptyMap()

        val cleanHeaders = headers.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() }
        val result = linkedMapOf<String, String>()
        synchronized(lock) {
            normalized.forEach { item ->
                val token = tokenFor(item)
                val cacheUri = cacheUriFor(token)
                val cacheItem = item.copy(playUrl = cacheUri)
                DouyinPlaybackPreviewCache.aliasPreviewBytes(
                    sourceUri = item.playUrl,
                    targetItem = cacheItem
                )
                DouyinPlaybackPreviewCache.registerRemotePlaybackTarget(
                    targetItem = cacheItem,
                    remoteUri = item.playUrl,
                    headers = cleanHeaders
                )
                result[item.awemeId] = cacheUri
            }
        }
        AppLogger.d(TAG, "registered cache targets reason=$reason count=${result.size}")
        return result
    }

    override fun primeStartupWindow(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        reason: String
    ) {
        val proxyUris = proxyUrisFor(items, headers, reason)
        if (proxyUris.isEmpty()) return
        val proxyItems = items.map { item ->
            proxyUris[item.awemeId]?.let { proxyUri -> item.copy(playUrl = proxyUri) } ?: item
        }
        DouyinPlaybackPreviewCache.primeStartupWindow(
            items = proxyItems,
            headers = emptyMap(),
            reason = reason
        )
    }

    override fun stop() = Unit

    companion object {
        private const val TAG = "DouyinTransport"
        private const val CACHE_URI_SCHEME = "watchrss-douyin-cache"

        private fun cacheUriFor(token: String): String {
            return "$CACHE_URI_SCHEME://play/$token.mp4"
        }

        private fun tokenFor(item: DouyinStreamItem): String {
            val raw = "${item.awemeId}|${item.playUrlResolvedAtMs}|${item.playUrl}"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }.take(32)
        }
    }
}
