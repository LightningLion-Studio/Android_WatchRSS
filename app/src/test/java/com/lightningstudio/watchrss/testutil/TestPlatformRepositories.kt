package com.lightningstudio.watchrss.testutil

import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinResult
import com.lightningstudio.watchrss.sdk.bili.BiliFeedPage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedSource
import com.lightningstudio.watchrss.sdk.bili.BiliHotSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.sdk.bili.BiliOwner
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliStat
import com.lightningstudio.watchrss.sdk.bili.BiliTrendingWord
import com.lightningstudio.watchrss.sdk.bili.QrPollResult
import com.lightningstudio.watchrss.sdk.bili.QrPollStatus
import com.lightningstudio.watchrss.sdk.bili.WebQrCode
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo

class TestBiliRepository(
    initialLoggedIn: Boolean = false,
    initialFeedItems: List<BiliItem> = listOf(sampleBiliItem())
) : BiliRepositoryContract {
    var loggedIn = initialLoggedIn
    var feedCache: List<BiliItem> = initialFeedItems
    var feedResult: BiliResult<BiliFeedPage> = BiliResult(
        code = 0,
        data = BiliFeedPage(items = initialFeedItems, source = BiliFeedSource.APP)
    )
    var favoriteResult: BiliResult<Boolean> = BiliResult(code = 0, data = true)
    var addToViewResult: BiliResult<Unit> = BiliResult(code = 0, data = Unit)
    var hotSearchResult: BiliResult<BiliHotSearchResponse> = BiliResult(
        code = 0,
        data = BiliHotSearchResponse(listOf(BiliTrendingWord(keyword = "Compose", showName = "Compose")))
    )
    var searchHistory = mutableListOf("Compose")
    var applyCookieResult: Result<Unit> = Result.success(Unit)
    var qrCode: WebQrCode? = WebQrCode(qrKey = "test-key", url = "https://example.com/qr.png")
    var qrPollResult: QrPollResult = QrPollResult(status = QrPollStatus.SUCCESS, rawCode = 0)
    var logoutCalls = 0
    val writtenFeedCaches = mutableListOf<List<BiliItem>>()
    val favoriteRequests = mutableListOf<Pair<Long, Boolean>>()
    val addToViewRequests = mutableListOf<Pair<Long?, String?>>()
    val cachedPreviewRequests = mutableListOf<Triple<Long?, String?, Long?>>()

    override suspend fun isLoggedIn(): Boolean = loggedIn

    override suspend fun fetchFeed(): BiliResult<BiliFeedPage> = feedResult

    override suspend fun readFeedCache(): List<BiliItem> = feedCache

    override suspend fun writeFeedCache(items: List<BiliItem>) {
        feedCache = items
        writtenFeedCaches += items
    }

    override suspend fun favorite(aid: Long, add: Boolean): BiliResult<Boolean> {
        favoriteRequests += aid to add
        return favoriteResult
    }

    override suspend fun addToView(aid: Long?, bvid: String?): BiliResult<Unit> {
        addToViewRequests += aid to bvid
        return addToViewResult
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
        cachedPreviewRequests += Triple(aid, bvid, cid)
        return Result.success("/tmp/bili-preview.mp4")
    }

    override suspend fun requestWebQrCode(): WebQrCode? = qrCode

    override suspend fun pollWebQrCode(qrKey: String): QrPollResult {
        if (qrPollResult.status == QrPollStatus.SUCCESS) {
            loggedIn = true
        }
        return qrPollResult
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
    }
}

class TestDouyinRepository(
    initialLoggedIn: Boolean = false
) : DouyinRepositoryContract {
    var loggedIn = initialLoggedIn
    var clearCookieCalls = 0
    var logoutCalls = 0

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
        return DouyinResult(
            code = DouyinErrorCodes.OK,
            data = DouyinFeedPage(items = listOf(sampleDouyinVideo()), nextCursor = null, hasMore = false)
        )
    }

    override suspend fun fetchVideo(awemeId: String): DouyinResult<DouyinContent> {
        return DouyinResult(
            code = DouyinErrorCodes.OK,
            data = DouyinContent.Video(
                awemeId = awemeId,
                desc = "测试抖音详情",
                authorName = "测试作者",
                diggCount = 9L,
                playUrl = "https://example.com/video.mp4",
                coverUrl = "https://example.com/cover.jpg"
            )
        )
    }

    override suspend fun buildPlayHeaders(): Map<String, String> {
        return mapOf("User-Agent" to "TestDouyinRepository")
    }
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
