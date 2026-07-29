package com.lightningstudio.watchrss.testutil

import com.lightningstudio.watchrss.data.bili.BiliInteractionState
import com.lightningstudio.watchrss.data.bili.BiliPlaybackCheckpointTrigger
import com.lightningstudio.watchrss.data.bili.BiliPlaybackProgress
import com.lightningstudio.watchrss.data.bili.BiliResolvedPlaybackSource
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceKind
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManagerContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowCacheCoordinatorContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowSnapshot
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinResult
import com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryEntry
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStoreContract
import com.lightningstudio.watchrss.sdk.bili.BiliDurl
import com.lightningstudio.watchrss.sdk.bili.BiliFeedPage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedSource
import com.lightningstudio.watchrss.sdk.bili.BiliHotSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.sdk.bili.BiliOwner
import com.lightningstudio.watchrss.sdk.bili.BiliPage
import com.lightningstudio.watchrss.sdk.bili.BiliPlayUrl
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliStat
import com.lightningstudio.watchrss.sdk.bili.BiliTrendingWord
import com.lightningstudio.watchrss.sdk.bili.BiliVideoInteraction
import com.lightningstudio.watchrss.sdk.bili.BiliVideoDetail
import com.lightningstudio.watchrss.sdk.bili.QrPollResult
import com.lightningstudio.watchrss.sdk.bili.QrPollStatus
import com.lightningstudio.watchrss.sdk.bili.WebQrCode
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

data class TestBiliFavoriteRequest(
    val aid: Long,
    val add: Boolean,
    val bvid: String?
)

data class TestBiliLikeRequest(
    val aid: Long,
    val like: Boolean,
    val bvid: String?
)

data class TestBiliCoinRequest(
    val aid: Long,
    val multiply: Int,
    val selectLike: Boolean,
    val bvid: String?
)

data class TestBiliPlaybackHistoryReportRequest(
    val aid: Long?,
    val bvid: String?,
    val cid: Long,
    val positionMs: Long,
    val durationMs: Long,
    val trigger: BiliPlaybackCheckpointTrigger
)

class TestBiliRepository(
    initialLoggedIn: Boolean = false,
    initialFeedItems: List<BiliItem> = listOf(sampleBiliItem())
) : BiliRepositoryContract {
    var loggedIn = initialLoggedIn
    var feedCache: List<BiliItem> = initialFeedItems
    var feedResult: BiliResult<BiliFeedPage> = BiliResult(
        code = 0,
        data = BiliFeedPage(items = initialFeedItems, source = BiliFeedSource.WEB)
    )
    var videoDetailResult: BiliResult<BiliVideoDetail> = BiliResult(
        code = 0,
        data = sampleBiliVideoDetail(initialFeedItems.first())
    )
    var remoteInteractionStateResult: BiliResult<BiliInteractionState> =
        BiliResult(code = BiliErrorCodes.REQUEST_FAILED, message = "relation_not_stubbed")
    var likeResult: BiliResult<Unit> = BiliResult(code = 0, data = Unit)
    var coinResult: BiliResult<Boolean> = BiliResult(code = 0, data = false)
    var favoriteResult: BiliResult<Boolean> = BiliResult(code = 0, data = true)
    var addToViewResult: BiliResult<Unit> = BiliResult(code = 0, data = Unit)
    var playUrlResult: BiliResult<BiliPlayUrl> = BiliResult(
        code = 0,
        data = BiliPlayUrl(
            durl = listOf(BiliDurl(url = "https://example.com/video.mp4"))
        )
    )
    var playHeaders: Map<String, String> = mapOf(
        "User-Agent" to "TestBiliRepository",
        "Referer" to "https://www.bilibili.com"
    )
    var resolvedPlaybackSourceValue: BiliResolvedPlaybackSource? = null
    var reportPlaybackHistoryResult: BiliResult<Unit> = BiliResult(code = 0, data = Unit)
    var reportPlaybackHistoryDelayMs: Long = 0L
    val resolvedPlaybackSourceResultQueueByCid = mutableMapOf<Long, MutableList<BiliResult<BiliResolvedPlaybackSource>>>()
    val resolvedPlaybackSourceResultsByCid = mutableMapOf<Long, BiliResult<BiliResolvedPlaybackSource>>()
    val resolvePlaybackSourceDelayMsByCid = mutableMapOf<Long, Long>()
    var videoDetailDelayMs: Long = 0L
    var cachedPreviewUriValue: String? = null
    var cachedPreviewUriAnyValue: String? = null
    var warmupDetailResult: Result<Unit> = Result.success(Unit)
    var ensureInteractionReadyResult: Result<Unit> = Result.success(Unit)
    var hotSearchResult: BiliResult<BiliHotSearchResponse> = BiliResult(
        code = 0,
        data = BiliHotSearchResponse(listOf(BiliTrendingWord(keyword = "Compose", showName = "Compose")))
    )
    var searchHistory = mutableListOf("Compose")
    var applyCookieResult: Result<Unit> = Result.success(Unit)
    var webQrCode: WebQrCode? = WebQrCode(qrKey = "web-test-key", url = "https://example.com/qr.png")
    var webQrPollResult: QrPollResult = QrPollResult(status = QrPollStatus.SUCCESS, rawCode = 0)
    var logoutCalls = 0
    val writtenFeedCaches = mutableListOf<List<BiliItem>>()
    val favoriteRequests = mutableListOf<TestBiliFavoriteRequest>()
    val addToViewRequests = mutableListOf<Pair<Long?, String?>>()
    val cachedPreviewRequests = mutableListOf<Triple<Long?, String?, Long?>>()
    val clearedPreviewRequests = mutableListOf<Triple<Long?, String?, Long?>>()
    val warmupDetailRequests = mutableListOf<Triple<Long?, String?, Long?>>()
    val ensureInteractionRequests = mutableListOf<Triple<Long?, String?, Long?>>()
    val likeRequests = mutableListOf<TestBiliLikeRequest>()
    val coinRequests = mutableListOf<TestBiliCoinRequest>()
    val localInteractionReadRequests = mutableListOf<Pair<Long?, String?>>()
    val localInteractionWriteRequests = mutableListOf<Triple<Long?, String?, BiliInteractionState>>()
    val remoteInteractionRequests = mutableListOf<Pair<Long?, String?>>()
    val localInteractionStates = mutableMapOf<String, BiliInteractionState>()
    val playbackProgressRecords = mutableListOf<BiliPlaybackProgress>()
    val latestPlaybackProgressReadRequests = mutableListOf<Pair<Long?, String?>>()
    val exactPlaybackProgressReadRequests = mutableListOf<Triple<Long?, String?, Long>>()
    val playbackProgressWrites = mutableListOf<BiliPlaybackProgress>()
    val clearedPlaybackProgressRequests = mutableListOf<Triple<Long?, String?, Long>>()
    val reportPlaybackHistoryRequests = mutableListOf<TestBiliPlaybackHistoryReportRequest>()
    val callLog = mutableListOf<String>()
    var requestWebQrCodeCalls = 0
    var lastWebPollToken: String? = null

    override suspend fun isLoggedIn(): Boolean = loggedIn

    override suspend fun fetchFeed(): BiliResult<BiliFeedPage> = feedResult

    override suspend fun fetchVideoDetail(aid: Long?, bvid: String?): BiliResult<BiliVideoDetail> {
        if (videoDetailDelayMs > 0L) {
            delay(videoDetailDelayMs)
        }
        return videoDetailResult
    }

    override suspend fun fetchRemoteInteractionState(
        aid: Long?,
        bvid: String?
    ): BiliResult<BiliInteractionState> {
        remoteInteractionRequests += aid to bvid
        callLog += "relation:$aid:$bvid"
        return remoteInteractionStateResult
    }

    override suspend fun fetchPlayUrlMp4(
        cid: Long,
        aid: Long?,
        bvid: String?,
        qn: Int
    ): BiliResult<BiliPlayUrl> {
        callLog += "play:$cid:$aid:$bvid:$qn"
        return playUrlResult
    }

    override suspend fun resolvePlaybackSource(
        aid: Long?,
        bvid: String?,
        cid: Long,
        qn: Int
    ): BiliResult<BiliResolvedPlaybackSource> {
        callLog += "resolve:$cid:$aid:$bvid:$qn"
        resolvePlaybackSourceDelayMsByCid[cid]?.takeIf { it > 0L }?.let { delay(it) }
        resolvedPlaybackSourceResultQueueByCid[cid]
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.removeAt(0) }
        resolvedPlaybackSourceResultsByCid[cid]?.let { return it }
        resolvedPlaybackSourceValue?.let { resolved ->
            return BiliResult(code = 0, data = resolved)
        }
        if (!playUrlResult.isSuccess) {
            return BiliResult(code = playUrlResult.code, message = playUrlResult.message)
        }
        val playUrl = playUrlResult.data
            ?: return BiliResult(code = 0, message = "empty_play_url")
        val url = playUrl.durl.firstOrNull()?.url
            ?: return BiliResult(code = 0, message = "empty_play_url")
        return BiliResult(
            code = 0,
            data = BiliResolvedPlaybackSource(
                cid = cid,
                url = url,
                headers = playHeaders,
                cacheKey = "bili:test:$cid:q${playUrl.quality ?: qn}",
                quality = playUrl.quality ?: qn
            )
        )
    }

    override suspend fun readLocalInteractionState(aid: Long?, bvid: String?): BiliInteractionState {
        localInteractionReadRequests += aid to bvid
        return interactionKeys(aid, bvid)
            .asSequence()
            .mapNotNull(localInteractionStates::get)
            .firstOrNull()
            ?: BiliInteractionState()
    }

    override suspend fun writeLocalInteractionState(aid: Long?, bvid: String?, state: BiliInteractionState) {
        localInteractionWriteRequests += Triple(aid, bvid, state)
        interactionKeys(aid, bvid).forEach { key ->
            if (state.hasAnyInteraction) {
                localInteractionStates[key] = state
            } else {
                localInteractionStates.remove(key)
            }
        }
    }

    override suspend fun readLatestPlaybackProgress(aid: Long?, bvid: String?): BiliPlaybackProgress? {
        latestPlaybackProgressReadRequests += aid to bvid
        return playbackProgressRecords
            .filter { matchesPlaybackVideo(it, aid, bvid) }
            .maxByOrNull { it.updatedAtMillis }
    }

    override suspend fun readPlaybackProgress(aid: Long?, bvid: String?, cid: Long): BiliPlaybackProgress? {
        exactPlaybackProgressReadRequests += Triple(aid, bvid, cid)
        return playbackProgressRecords
            .filter { matchesPlaybackIdentity(it, aid, bvid, cid) }
            .maxByOrNull { it.updatedAtMillis }
    }

    override suspend fun readAllPlaybackProgress(): List<BiliPlaybackProgress> {
        return playbackProgressRecords.sortedByDescending { it.updatedAtMillis }
    }

    override suspend fun writePlaybackProgress(progress: BiliPlaybackProgress) {
        playbackProgressWrites += progress
        playbackProgressRecords.removeAll {
            matchesPlaybackIdentity(it, progress.aid, progress.bvid, progress.cid)
        }
        playbackProgressRecords += progress
    }

    override suspend fun clearPlaybackProgress(aid: Long?, bvid: String?, cid: Long) {
        clearedPlaybackProgressRequests += Triple(aid, bvid, cid)
        playbackProgressRecords.removeAll { matchesPlaybackIdentity(it, aid, bvid, cid) }
    }

    override suspend fun reportPlaybackHistory(
        aid: Long?,
        bvid: String?,
        cid: Long,
        positionMs: Long,
        durationMs: Long,
        trigger: BiliPlaybackCheckpointTrigger
    ): BiliResult<Unit> {
        callLog += "historyReport:$aid:$bvid:$cid:$positionMs:$durationMs:${trigger.name}"
        reportPlaybackHistoryRequests += TestBiliPlaybackHistoryReportRequest(
            aid = aid,
            bvid = bvid,
            cid = cid,
            positionMs = positionMs,
            durationMs = durationMs,
            trigger = trigger
        )
        if (reportPlaybackHistoryDelayMs > 0L) {
            delay(reportPlaybackHistoryDelayMs)
        }
        return reportPlaybackHistoryResult
    }

    override suspend fun readFeedCache(): List<BiliItem> = feedCache

    override suspend fun writeFeedCache(items: List<BiliItem>) {
        feedCache = items
        writtenFeedCaches += items
    }

    override suspend fun favorite(aid: Long, add: Boolean, bvid: String?): BiliResult<Boolean> {
        callLog += "favorite:$aid:$add:$bvid"
        favoriteRequests += TestBiliFavoriteRequest(aid = aid, add = add, bvid = bvid)
        return favoriteResult
    }

    override suspend fun like(aid: Long, like: Boolean, bvid: String?): BiliResult<Unit> {
        callLog += "like:$aid:$like:$bvid"
        likeRequests += TestBiliLikeRequest(aid = aid, like = like, bvid = bvid)
        return likeResult
    }

    override suspend fun coin(
        aid: Long,
        multiply: Int,
        selectLike: Boolean,
        bvid: String?
    ): BiliResult<Boolean> {
        callLog += "coin:$aid:$multiply:$selectLike:$bvid"
        coinRequests += TestBiliCoinRequest(
            aid = aid,
            multiply = multiply,
            selectLike = selectLike,
            bvid = bvid
        )
        return coinResult
    }

    override suspend fun addToView(aid: Long?, bvid: String?): BiliResult<Unit> {
        callLog += "toview:$aid:$bvid"
        addToViewRequests += aid to bvid
        return addToViewResult
    }

    override suspend fun warmupDetailPreview(aid: Long?, bvid: String?, cid: Long?): Result<Unit> {
        callLog += "warmup:$aid:$bvid:$cid"
        warmupDetailRequests += Triple(aid, bvid, cid)
        return warmupDetailResult
    }

    override suspend fun ensureInteractionReady(aid: Long?, bvid: String?, cid: Long?): Result<Unit> {
        callLog += "ensure:$aid:$bvid:$cid"
        ensureInteractionRequests += Triple(aid, bvid, cid)
        return ensureInteractionReadyResult
    }

    override fun savedLink(bvid: String?, aid: Long?, cid: Long?): String? {
        val base = when {
            !bvid.isNullOrBlank() -> "https://www.bilibili.com/video/$bvid"
            aid != null -> "https://www.bilibili.com/video/av$aid"
            else -> null
        } ?: return null
        return if (cid == null) base else "$base?cid=$cid"
    }

    override suspend fun cachePreviewClip(aid: Long?, bvid: String?, cid: Long?): Result<String> {
        callLog += "cache:$aid:$bvid:$cid"
        cachedPreviewRequests += Triple(aid, bvid, cid)
        return Result.success("/tmp/bili-preview.mp4")
    }

    override suspend fun clearCachedPreview(aid: Long?, bvid: String?, cid: Long?) {
        callLog += "clearcache:$aid:$bvid:$cid"
        clearedPreviewRequests += Triple(aid, bvid, cid)
    }

    override suspend fun buildPlayHeaders(): Map<String, String> = playHeaders

    override suspend fun cachedPreviewUri(aid: Long?, bvid: String?, cid: Long?): String? {
        callLog += "cached:$aid:$bvid:$cid"
        return cachedPreviewUriValue
    }

    override suspend fun cachedPreviewUriAny(aid: Long?, bvid: String?): String? {
        callLog += "cachedAny:$aid:$bvid"
        return cachedPreviewUriAnyValue
    }

    override suspend fun requestWebQrCode(): WebQrCode? {
        requestWebQrCodeCalls += 1
        return webQrCode
    }

    override suspend fun pollWebQrCode(qrKey: String): QrPollResult {
        lastWebPollToken = qrKey
        if (webQrPollResult.status == QrPollStatus.SUCCESS) {
            loggedIn = true
        }
        return webQrPollResult
    }

    override suspend fun applyCookieHeader(rawCookie: String): Result<Unit> {
        if (applyCookieResult.isSuccess) {
            loggedIn = true
        }
        return applyCookieResult
    }

    override suspend fun getHotSearch(): BiliResult<BiliHotSearchResponse> = hotSearchResult

    override suspend fun getSearchHistory(): List<String> = searchHistory.toList()

    override suspend fun addSearchHistory(keyword: String) {
        searchHistory.remove(keyword)
        searchHistory.add(0, keyword)
    }

    override suspend fun clearSearchHistory() {
        searchHistory.clear()
    }

    override suspend fun logoutAndClearPreviewCache() {
        logoutCalls += 1
        loggedIn = false
        playbackProgressRecords.clear()
    }

    private fun interactionKeys(aid: Long?, bvid: String?): List<String> {
        return buildList {
            bvid?.trim()?.takeIf { it.isNotEmpty() }?.let { add("bv:$it") }
            aid?.let { add("av:$it") }
        }
    }

    private fun matchesPlaybackIdentity(
        progress: BiliPlaybackProgress,
        aid: Long?,
        bvid: String?,
        cid: Long
    ): Boolean {
        if (progress.cid != cid) return false
        return matchesPlaybackVideo(progress, aid, bvid) ||
            (aid == null && bvid.isNullOrBlank() && !progress.hasVideoIdentity)
    }

    private fun matchesPlaybackVideo(progress: BiliPlaybackProgress, aid: Long?, bvid: String?): Boolean {
        val safeBvid = bvid?.trim()
        val sameBvid = !safeBvid.isNullOrEmpty() && progress.bvid.equals(safeBvid, ignoreCase = true)
        val sameAid = aid != null && progress.aid == aid
        return sameBvid || sameAid
    }
}

class TestDouyinRepository(
    initialLoggedIn: Boolean = false
) : DouyinRepositoryContract {
    var loggedIn = initialLoggedIn
    var clearCookieCalls = 0
    var logoutCalls = 0
    var feedPageResults: ArrayDeque<DouyinResult<DouyinFeedPage>> = ArrayDeque(
        listOf(
            DouyinResult(
                code = DouyinErrorCodes.OK,
                data = DouyinFeedPage(items = listOf(sampleDouyinVideo()), nextCursor = null, hasMore = false)
            )
        )
    )
    var videoResult: DouyinResult<DouyinContent> = DouyinResult(
        code = DouyinErrorCodes.OK,
        data = DouyinContent.Video(
            awemeId = "7357000000000000001",
            desc = "测试抖音详情",
            authorName = "测试作者",
            diggCount = 9L,
            playUrl = "https://example.com/video.mp4",
            coverUrl = "https://example.com/cover.jpg"
        )
    )
    private val videoResults = mutableMapOf<String, DouyinResult<DouyinContent>>()
    var headers: Map<String, String> = mapOf("User-Agent" to "TestDouyinRepository")
    val fetchVideoCalls = mutableListOf<String>()
    val fetchFeedPageCursors = mutableListOf<String?>()
    var fetchVideoGate: CompletableDeferred<Unit>? = null

    override suspend fun isLoggedIn(): Boolean = loggedIn

    override suspend fun clearCookie() {
        clearCookieCalls += 1
        loggedIn = false
    }

    override suspend fun logoutAndClearMediaCache() {
        logoutCalls += 1
        clearCookie()
    }

    override suspend fun fetchFeed(): DouyinResult<List<DouyinVideo>> {
        return DouyinResult(code = DouyinErrorCodes.OK, data = listOf(sampleDouyinVideo()))
    }

    override suspend fun fetchFeedPage(cursor: String?, count: Int): DouyinResult<DouyinFeedPage> {
        fetchFeedPageCursors += cursor
        return if (feedPageResults.isEmpty()) {
            DouyinResult(
                code = DouyinErrorCodes.OK,
                data = DouyinFeedPage(items = listOf(sampleDouyinVideo()), nextCursor = null, hasMore = false)
            )
        } else {
            feedPageResults.removeFirst()
        }
    }

    override suspend fun fetchVideo(awemeId: String): DouyinResult<DouyinContent> {
        fetchVideoCalls += awemeId
        fetchVideoGate?.await()
        return videoResults[awemeId] ?: videoResult
    }

    override suspend fun buildPlayHeaders(): Map<String, String> {
        return headers
    }

    fun setVideoResult(awemeId: String, result: DouyinResult<DouyinContent>) {
        videoResults[awemeId] = result
    }
}

class TestDouyinPreloadManager : DouyinPreloadManagerContract {
    val localPaths = linkedMapOf<String, String>()
    val callbackPaths = linkedMapOf<String, String>()
    val invalidatedIds = mutableListOf<String>()
    var ensureCalls = 0
    var ensurePlaybackWindowCalls = 0
    val ensuredSnapshots = mutableListOf<List<String>>()
    val playbackWindowSnapshots = mutableListOf<List<String>>()
    val playbackWindowPrefixCounts = mutableListOf<Int>()

    override suspend fun localPathFor(awemeId: String): String? = localPaths[awemeId]

    override suspend fun resolveLocalPaths(awemeIds: List<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        awemeIds.distinct().forEach { awemeId ->
            localPaths[awemeId]?.let { result[awemeId] = it }
        }
        return result
    }

    override suspend fun ensurePlaybackWindowCached(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        requiredPrefixCount: Int,
        onItemCached: ((awemeId: String, localPath: String) -> Unit)?
    ) {
        ensurePlaybackWindowCalls += 1
        playbackWindowSnapshots += items.map { it.awemeId }
        playbackWindowPrefixCounts += requiredPrefixCount
        items.take(requiredPrefixCount).forEach { item ->
            callbackPaths[item.awemeId]?.let { localPath ->
                localPaths[item.awemeId] = localPath
                onItemCached?.invoke(item.awemeId, localPath)
            }
        }
    }

    override suspend fun ensureUnwatchedCache(
        items: List<DouyinStreamItem>,
        watchedIds: Set<String>,
        headers: Map<String, String>,
        targetUnwatchedCount: Int,
        onItemCached: ((awemeId: String, localPath: String) -> Unit)?
    ) {
        ensureCalls += 1
        ensuredSnapshots += items.map { it.awemeId }
        items.forEach { item ->
            callbackPaths[item.awemeId]?.let { localPath ->
                localPaths[item.awemeId] = localPath
                onItemCached?.invoke(item.awemeId, localPath)
            }
        }
    }

    override suspend fun invalidate(awemeId: String): Boolean {
        invalidatedIds += awemeId
        return localPaths.remove(awemeId) != null
    }
}

class TestDouyinWatchHistoryStore : DouyinWatchHistoryStoreContract {
    val watchedIds = linkedSetOf<String>()
    val historyEntries = mutableListOf<DouyinWatchHistoryEntry>()

    override fun markWatched(item: DouyinStreamItem) {
        val awemeId = item.awemeId.trim()
        if (awemeId.isEmpty()) return
        watchedIds += awemeId
        historyEntries.removeAll { it.awemeId == awemeId }
        historyEntries.add(
            0,
            DouyinWatchHistoryEntry(
                awemeId = awemeId,
                title = item.title,
                author = item.author,
                coverUrl = item.coverUrl,
                playUrl = item.playUrl,
                likeCount = item.likeCount,
                watchedAt = System.currentTimeMillis()
            )
        )
    }

    override fun markWatched(awemeId: String) {
        val normalizedAwemeId = awemeId.trim()
        if (normalizedAwemeId.isEmpty()) return
        watchedIds += normalizedAwemeId
    }

    override fun readWatchedIds(): Set<String> = watchedIds.toSet()

    override fun readHistory(): List<DouyinWatchHistoryEntry> = historyEntries.toList()

    override fun clear() {
        watchedIds.clear()
        historyEntries.clear()
    }
}

class TestDouyinFeedCacheStore(
    initialItems: List<DouyinStreamItem> = emptyList()
) : DouyinFeedCacheStoreContract {
    var cachedItems: List<DouyinStreamItem> = initialItems
    val savedSnapshots = mutableListOf<List<DouyinStreamItem>>()
    var cachedNextCursor: String? = null
    var cachedHasMore: Boolean = false

    override fun save(items: List<DouyinStreamItem>, nextCursor: String?, hasMore: Boolean, savedAtMs: Long) {
        cachedItems = items
        cachedNextCursor = nextCursor
        cachedHasMore = hasMore
        savedSnapshots += items
    }

    override fun read(limit: Int): List<DouyinStreamItem> {
        return if (limit > 0) cachedItems.take(limit) else cachedItems
    }

    override fun readSnapshot(limit: Int): com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheSnapshot {
        val items = if (limit > 0) cachedItems.take(limit) else cachedItems
        return com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheSnapshot(
            items = items,
            savedAtMs = items.maxOfOrNull { it.playUrlResolvedAtMs } ?: 0L,
            nextCursor = cachedNextCursor,
            hasMore = cachedHasMore
        )
    }
}

class TestDouyinRecentWindowStore(
    initialSnapshot: DouyinRecentWindowSnapshot = DouyinRecentWindowSnapshot(
        items = emptyList(),
        anchorAwemeId = null,
        savedAtMs = 0L
    )
) : DouyinRecentWindowStoreContract {
    var snapshot: DouyinRecentWindowSnapshot = initialSnapshot
    val savedSnapshots = mutableListOf<DouyinRecentWindowSnapshot>()

    override fun saveWindow(items: List<DouyinStreamItem>, anchorAwemeId: String?, savedAtMs: Long) {
        snapshot = DouyinRecentWindowSnapshot(
            items = items,
            anchorAwemeId = anchorAwemeId,
            savedAtMs = savedAtMs
        )
        savedSnapshots += snapshot
    }

    override fun readSnapshot(limit: Int): DouyinRecentWindowSnapshot {
        val items = if (limit > 0) snapshot.items.take(limit) else snapshot.items
        return snapshot.copy(items = items)
    }

    override fun clear() {
        snapshot = DouyinRecentWindowSnapshot(
            items = emptyList(),
            anchorAwemeId = null,
            savedAtMs = 0L
        )
    }
}

class TestDouyinRecentWindowCacheCoordinator : DouyinRecentWindowCacheCoordinatorContract {
    var enqueueCalls = 0
    val windowSnapshots = mutableListOf<List<String>>()
    val anchorAwemeIds = mutableListOf<String?>()
    val headerSnapshots = mutableListOf<Map<String, String>>()
    val reasons = mutableListOf<String>()

    override fun enqueueWindow(
        items: List<DouyinStreamItem>,
        anchorAwemeId: String?,
        headers: Map<String, String>,
        reason: String
    ) {
        enqueueCalls += 1
        windowSnapshots += items.map { it.awemeId }
        anchorAwemeIds += anchorAwemeId
        headerSnapshots += headers
        reasons += reason
    }
}

fun sampleDouyinStreamItem(
    awemeId: String = "7357000000000000001",
    playUrl: String = "https://example.com/douyin.mp4",
    coverUrl: String? = "https://example.com/douyin.jpg",
    title: String? = "测试抖音视频",
    author: String? = "测试作者",
    likeCount: Long = 12L,
    playUrlResolvedAtMs: Long = 1_700_000_000_000L,
    sourceOrigin: DouyinSourceOrigin = DouyinSourceOrigin.NETWORK_FEED
): DouyinStreamItem {
    return DouyinStreamItem(
        awemeId = awemeId,
        playUrl = playUrl,
        coverUrl = coverUrl,
        title = title,
        author = author,
        likeCount = likeCount,
        playUrlResolvedAtMs = playUrlResolvedAtMs,
        sourceOrigin = sourceOrigin
    )
}

fun sampleBiliItem(
    aid: Long = 101L,
    bvid: String = "BV1xx411c7mD",
    cid: Long = 202L,
    title: String = "测试 B 站视频"
): BiliItem {
    return BiliItem(
        aid = aid,
        bvid = bvid,
        cid = cid,
        title = title,
        cover = "https://example.com/bili.jpg",
        duration = 120,
        pubdate = 1_700_000_000L,
        owner = BiliOwner(mid = 7L, name = "测试 UP"),
        stat = BiliStat(view = 100, like = 8, favorite = 2)
    )
}

fun sampleBiliVideoDetail(
    item: BiliItem = sampleBiliItem(),
    pages: List<BiliPage> = listOf(
        BiliPage(
            cid = item.cid,
            page = 1,
            part = "P1",
            duration = item.duration
        )
    ),
    interaction: BiliVideoInteraction? = null
): BiliVideoDetail {
    return BiliVideoDetail(
        item = item,
        desc = "测试简介",
        pages = pages,
        interaction = interaction
    )
}

fun sampleDouyinVideo(
    awemeId: String = "7357000000000000001",
    desc: String = "测试抖音视频"
): DouyinVideo {
    return DouyinVideo().apply {
        this.awemeId = awemeId
        this.desc = desc
        authorId = "author-1"
        authorName = "测试作者"
        likeCount = 12
        playUrl = "https://example.com/douyin.mp4"
        coverUrl = "https://example.com/douyin.jpg"
        duration = 15
    }
}
