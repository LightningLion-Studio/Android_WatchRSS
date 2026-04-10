package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

interface DouyinRecentWindowCacheCoordinatorContract {
    fun enqueueWindow(
        items: List<DouyinStreamItem>,
        anchorAwemeId: String?,
        headers: Map<String, String>,
        reason: String
    )
}

object NoOpDouyinRecentWindowCacheCoordinator : DouyinRecentWindowCacheCoordinatorContract {
    override fun enqueueWindow(
        items: List<DouyinStreamItem>,
        anchorAwemeId: String?,
        headers: Map<String, String>,
        reason: String
    ) = Unit
}

class DouyinRecentWindowCacheCoordinator(
    appScope: CoroutineScope,
    private val preloadManager: DouyinPreloadManagerContract
) : DouyinRecentWindowCacheCoordinatorContract {
    private val requests = Channel<DouyinRecentWindowCacheRequest>(capacity = Channel.CONFLATED)

    init {
        appScope.launch {
            for (request in requests) {
                val prioritizedItems = prioritizeDouyinPreloadItems(
                    items = request.items,
                    anchorAwemeId = request.anchorAwemeId
                )
                AppLogger.d(
                    TAG,
                    "cache recent window reason=${request.reason} anchor=${request.anchorAwemeId} ids=${
                        prioritizedItems.joinToString(",") { it.awemeId }
                    }"
                )
                try {
                    preloadManager.ensurePlaybackWindowCached(
                        items = prioritizedItems,
                        headers = request.headers,
                        requiredPrefixCount = prioritizedItems.size
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    AppLogger.w(TAG, "recent window cache failed reason=${request.reason}", error)
                }
            }
        }
    }

    override fun enqueueWindow(
        items: List<DouyinStreamItem>,
        anchorAwemeId: String?,
        headers: Map<String, String>,
        reason: String
    ) {
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
        if (normalizedItems.isEmpty()) return
        requests.trySend(
            DouyinRecentWindowCacheRequest(
                items = normalizedItems,
                anchorAwemeId = anchorAwemeId?.trim()?.takeIf { it.isNotEmpty() },
                headers = headers.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() },
                reason = reason
            )
        )
    }

    private data class DouyinRecentWindowCacheRequest(
        val items: List<DouyinStreamItem>,
        val anchorAwemeId: String?,
        val headers: Map<String, String>,
        val reason: String
    )

    companion object {
        private const val TAG = "DouyinRecentCache"
    }
}
