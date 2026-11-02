package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.util.AppLogger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class DouyinPlaybackRefreshTrigger {
    STARTUP_TTL,
    FOREGROUND_HTTP_403,
    PREFETCH_HTTP_403,
    PLAYBACK_ERROR,
    MANUAL
}

enum class DouyinPlaybackRefreshOutcome {
    SUCCESS,
    FAILURE,
    SKIPPED
}

data class DouyinPlaybackSourceRefreshEvent(
    val eventId: Long,
    val awemeId: String,
    val trigger: DouyinPlaybackRefreshTrigger,
    val outcome: DouyinPlaybackRefreshOutcome,
    val item: DouyinStreamItem? = null,
    val errorCode: Int? = null,
    val message: String? = null
)

interface DouyinPlaybackSourceCoordinatorContract {
    val updates: SharedFlow<DouyinPlaybackSourceRefreshEvent>

    suspend fun replenishFeed(): List<DouyinStreamItem> = emptyList()

    suspend fun refresh(
        item: DouyinStreamItem,
        trigger: DouyinPlaybackRefreshTrigger
    ): DouyinPlaybackSourceRefreshEvent
}

object NoOpDouyinPlaybackSourceCoordinator : DouyinPlaybackSourceCoordinatorContract {
    private val mutableUpdates = MutableSharedFlow<DouyinPlaybackSourceRefreshEvent>()
    override val updates: SharedFlow<DouyinPlaybackSourceRefreshEvent> = mutableUpdates

    override suspend fun refresh(
        item: DouyinStreamItem,
        trigger: DouyinPlaybackRefreshTrigger
    ): DouyinPlaybackSourceRefreshEvent {
        return DouyinPlaybackSourceRefreshEvent(
            eventId = 0L,
            awemeId = item.awemeId,
            trigger = trigger,
            outcome = DouyinPlaybackRefreshOutcome.SKIPPED
        )
    }
}

class DouyinPlaybackSourceCoordinator(
    private val appScope: CoroutineScope,
    private val repository: DouyinRepositoryContract,
    private val feedCacheStore: DouyinFeedCacheStoreContract,
    private val recentWindowStore: DouyinRecentWindowStoreContract
) : DouyinPlaybackSourceCoordinatorContract {
    private val inFlightMutex = Mutex()
    private val persistenceMutex = Mutex()
    private val inFlight = linkedMapOf<String, Deferred<DouyinPlaybackSourceRefreshEvent>>()
    private val mutableUpdates = MutableSharedFlow<DouyinPlaybackSourceRefreshEvent>(
        extraBufferCapacity = 32
    )
    private val eventIds = AtomicLong()
    private val candidateRecovery = DouyinCandidateRecovery()
    private val feedRefreshMutex = Mutex()
    private var replenishedAtMs = 0L
    private var replenishedItems: List<DouyinStreamItem> = emptyList()

    override suspend fun replenishFeed(): List<DouyinStreamItem> = feedRefreshMutex.withLock {
        val nowMs = System.currentTimeMillis()
        if (nowMs - replenishedAtMs < 30_000 && replenishedItems.isNotEmpty()) return@withLock replenishedItems
        val result = withTimeoutOrNull(10_000) { repository.fetchFeedPage(null, 12) }
        val fresh = result?.takeIf { it.isSuccess }?.data?.items.orEmpty().mapNotNull { it.toStreamItem(nowMs) }
        if (fresh.isNotEmpty()) {
            persistenceMutex.withLock {
                feedCacheStore.save(fresh, result?.data?.nextCursor, result?.data?.hasMore ?: false, nowMs)
            }
            replenishedAtMs = nowMs
            replenishedItems = fresh
        }
        fresh
    }

    override val updates: SharedFlow<DouyinPlaybackSourceRefreshEvent> = mutableUpdates

    override suspend fun refresh(
        item: DouyinStreamItem,
        trigger: DouyinPlaybackRefreshTrigger
    ): DouyinPlaybackSourceRefreshEvent {
        val awemeId = item.awemeId.trim()
        if (awemeId.isEmpty()) {
            return failureEvent(
                awemeId = item.awemeId,
                trigger = trigger,
                message = "empty aweme id"
            )
        }
        val request = inFlightMutex.withLock {
            inFlight[awemeId]?.takeIf { it.isActive }
                ?: CoroutineScope(currentCoroutineContext()).async {
                    performRefresh(item.copy(awemeId = awemeId), trigger)
                }.also { inFlight[awemeId] = it }
        }
        return try {
            val sharedEvent = request.await()
            if (sharedEvent.trigger == trigger) {
                sharedEvent
            } else {
                sharedEvent.copy(
                    eventId = eventIds.incrementAndGet(),
                    trigger = trigger
                ).also { mutableUpdates.emit(it) }
            }
        } finally {
            inFlightMutex.withLock {
                if (inFlight[awemeId] === request) {
                    inFlight.remove(awemeId)
                }
            }
        }
    }

    private suspend fun performRefresh(
        item: DouyinStreamItem,
        trigger: DouyinPlaybackRefreshTrigger
    ): DouyinPlaybackSourceRefreshEvent {
        AppLogger.d(
            TAG,
            "refresh start awemeId=${item.awemeId} trigger=${trigger.name} resolvedAt=${item.playUrlResolvedAtMs}"
        )
        val result = try {
            (if (trigger in setOf(DouyinPlaybackRefreshTrigger.PLAYBACK_ERROR,
                    DouyinPlaybackRefreshTrigger.MANUAL)) candidateRecovery.next(item) else null)
                ?.let { DouyinResult(DouyinErrorCodes.OK, data = it) }
                ?: (if (candidateRecovery.allowFeedRefresh(item)) {
                    withTimeoutOrNull(10_000) {
                        if (trigger == DouyinPlaybackRefreshTrigger.STARTUP_TTL) refreshDouyinRestoredItem(repository, item)
                        else refreshDouyinItemFromFeed(repository, item)
                    }
                } else DouyinResult(DouyinErrorCodes.REQUEST_FAILED, "播放源候选已用尽"))
                ?: DouyinResult(DouyinErrorCodes.REQUEST_FAILED, "播放源刷新超时")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val event = failureEvent(
                awemeId = item.awemeId,
                trigger = trigger,
                message = error.message ?: error::class.java.simpleName
            )
            mutableUpdates.emit(event)
            AppLogger.w(
                TAG,
                "refresh exception awemeId=${item.awemeId} trigger=${trigger.name}",
                error
            )
            return event
        }
        val refreshedItem = result.data
        val event = if (result.isSuccess && refreshedItem != null && refreshedItem.playUrl.isNotBlank()) {
            persistRefreshedItem(refreshedItem)
            DouyinPlaybackSourceRefreshEvent(
                eventId = eventIds.incrementAndGet(),
                awemeId = item.awemeId,
                trigger = trigger,
                outcome = DouyinPlaybackRefreshOutcome.SUCCESS,
                item = refreshedItem
            )
        } else {
            failureEvent(
                awemeId = item.awemeId,
                trigger = trigger,
                errorCode = result.code,
                message = result.message
            )
        }
        mutableUpdates.emit(event)
        AppLogger.d(
            TAG,
            "refresh finish awemeId=${item.awemeId} trigger=${trigger.name} outcome=${event.outcome.name} " +
                "eventId=${event.eventId}"
        )
        return event
    }

    private suspend fun persistRefreshedItem(refreshedItem: DouyinStreamItem) {
        persistenceMutex.withLock {
            val nowMs = System.currentTimeMillis()
            val feedSnapshot = feedCacheStore.readSnapshot(limit = Int.MAX_VALUE)
            val refreshedFeedItems = replaceDouyinPlaybackItemIfNewer(
                items = feedSnapshot.items,
                refreshedItem = refreshedItem
            )
            if (refreshedFeedItems !== feedSnapshot.items) {
                feedCacheStore.save(
                    items = refreshedFeedItems,
                    nextCursor = feedSnapshot.nextCursor,
                    hasMore = feedSnapshot.hasMore,
                    savedAtMs = nowMs
                )
            }

            val recentSnapshot = recentWindowStore.readSnapshot(limit = Int.MAX_VALUE)
            val refreshedRecentItems = replaceDouyinPlaybackItemIfNewer(
                items = recentSnapshot.items,
                refreshedItem = refreshedItem
            )
            if (refreshedRecentItems !== recentSnapshot.items) {
                recentWindowStore.saveWindow(
                    items = refreshedRecentItems,
                    anchorAwemeId = recentSnapshot.anchorAwemeId,
                    savedAtMs = nowMs
                )
            }
        }
    }

    private fun failureEvent(
        awemeId: String,
        trigger: DouyinPlaybackRefreshTrigger,
        errorCode: Int? = null,
        message: String? = null
    ): DouyinPlaybackSourceRefreshEvent {
        return DouyinPlaybackSourceRefreshEvent(
            eventId = eventIds.incrementAndGet(),
            awemeId = awemeId,
            trigger = trigger,
            outcome = DouyinPlaybackRefreshOutcome.FAILURE,
            errorCode = errorCode,
            message = message
        )
    }

    companion object {
        private const val TAG = "DouyinSourceCoord"
    }
}

internal fun replaceDouyinPlaybackItemIfNewer(
    items: List<DouyinStreamItem>,
    refreshedItem: DouyinStreamItem
): List<DouyinStreamItem> {
    var replaced = false
    val updated = items.map { item ->
        if (
            item.awemeId == refreshedItem.awemeId &&
            refreshedItem.playUrlResolvedAtMs >= item.playUrlResolvedAtMs
        ) {
            replaced = true
            refreshedItem
        } else {
            item
        }
    }
    return if (replaced) updated else items
}
