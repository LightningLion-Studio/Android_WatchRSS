package com.lightningstudio.watchrss.testutil

import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.bili.BiliErrorCodes
import com.lightningstudio.watchrss.sdk.bili.BiliAccount
import com.lightningstudio.watchrss.sdk.bili.BiliCommentContent
import com.lightningstudio.watchrss.sdk.bili.BiliCommentCursor
import com.lightningstudio.watchrss.sdk.bili.BiliCommentData
import com.lightningstudio.watchrss.sdk.bili.BiliCommentMember
import com.lightningstudio.watchrss.sdk.bili.BiliCommentPage
import com.lightningstudio.watchrss.sdk.bili.BiliCommentReplyPage
import com.lightningstudio.watchrss.sdk.bili.BiliFavoriteFolder
import com.lightningstudio.watchrss.sdk.bili.BiliFavoriteMedia
import com.lightningstudio.watchrss.sdk.bili.BiliFavoritePage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedPage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedSource
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryCursor
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryEntry
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryItem
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryPage
import com.lightningstudio.watchrss.sdk.bili.BiliHotSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.sdk.bili.BiliOwner
import com.lightningstudio.watchrss.sdk.bili.BiliPage
import com.lightningstudio.watchrss.sdk.bili.BiliPlayUrl
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliStat
import com.lightningstudio.watchrss.sdk.bili.BiliToViewPage
import com.lightningstudio.watchrss.sdk.bili.BiliTrendingWord
import com.lightningstudio.watchrss.sdk.bili.BiliVideoDetail
import com.lightningstudio.watchrss.sdk.bili.BiliDurl
import com.lightningstudio.watchrss.sdk.bili.QrPollResult
import com.lightningstudio.watchrss.sdk.bili.QrPollStatus
import com.lightningstudio.watchrss.sdk.bili.TvQrCode
import com.lightningstudio.watchrss.sdk.bili.WebQrCode

class FakeBiliRepository(
    initialLoggedIn: Boolean = false,
    initialFeedItems: List<BiliItem> = listOf(defaultBiliItem()),
    initialFeedCache: List<BiliItem> = initialFeedItems,
    initialFavoriteFolders: List<BiliFavoriteFolder> = listOf(
        BiliFavoriteFolder(id = 1L, fid = 1L, title = "默认收藏夹")
    ),
    initialSearchHistory: List<String> = listOf("Compose"),
    initialPlayHeaders: Map<String, String> = mapOf(
        "User-Agent" to "FakeBiliRepository",
        "Referer" to "https://www.bilibili.com"
    )
) : BiliRepositoryContract {
    var loggedIn: Boolean = initialLoggedIn
    var account: BiliAccount? = null
    var clearAccountCount = 0
    var logoutCount = 0

    var applyCookieResult: Result<Unit> = Result.success(Unit)
    var webQrCode: WebQrCode? = WebQrCode("web_qr_key", "https://example.com/web-qr")
    var tvQrCode: TvQrCode? = TvQrCode("tv_auth", "https://example.com/tv-qr")
    var webQrPollResult: QrPollResult = QrPollResult(
        status = QrPollStatus.SUCCESS,
        rawCode = 0,
        cookies = mapOf("SESSDATA" to "test")
    )
    var tvQrPollResult: QrPollResult = QrPollResult(
        status = QrPollStatus.SUCCESS,
        rawCode = 0,
        cookies = mapOf("SESSDATA" to "tv-test")
    )

    var feedResult: BiliResult<BiliFeedPage> = BiliResult(
        code = 0,
        data = BiliFeedPage(items = initialFeedItems, source = BiliFeedSource.APP)
    )
    var feedCache: List<BiliItem> = initialFeedCache
    var videoDetailResult: BiliResult<BiliVideoDetail> = BiliResult(
        code = 0,
        data = defaultBiliVideoDetail(initialFeedItems.first())
    )
    var playUrlResult: BiliResult<BiliPlayUrl> = BiliResult(
        code = 0,
        data = BiliPlayUrl(
            quality = 32,
            durl = listOf(
                BiliDurl(
                    order = 1,
                    length = 30_000,
                    size = 1024,
                    url = "https://example.com/video.mp4"
                )
            )
        )
    )
    var likeResult: BiliResult<Unit> = BiliResult(0, data = Unit)
    var coinResult: BiliResult<Boolean> = BiliResult(0, data = true)
    var tripleResult: BiliResult<com.lightningstudio.watchrss.sdk.bili.BiliTripleResult> =
        BiliResult(BiliErrorCodes.REQUEST_FAILED, "triple_not_stubbed")
    var favoriteResult: BiliResult<Boolean> = BiliResult(0, data = true)
    var addToViewResult: BiliResult<Unit> = BiliResult(0, data = Unit)
    var toViewResult: BiliResult<BiliToViewPage> = BiliResult(
        code = 0,
        data = BiliToViewPage(count = initialFeedItems.size, items = initialFeedItems)
    )
    var historyResult: BiliResult<BiliHistoryPage> = BiliResult(
        code = 0,
        data = BiliHistoryPage(
            cursor = BiliHistoryCursor(max = 1L, viewAt = 1L),
            items = listOf(
                BiliHistoryItem(
                    title = "历史记录",
                    authorName = "作者",
                    history = BiliHistoryEntry(oid = 1L, bvid = "BV1xx411c7mD", cid = 1L)
                )
            )
        )
    )
    var favoriteFoldersResult: BiliResult<List<BiliFavoriteFolder>> = BiliResult(
        code = 0,
        data = initialFavoriteFolders
    )
    var favoriteItemsResult: BiliResult<BiliFavoritePage> = BiliResult(
        code = 0,
        data = BiliFavoritePage(
            mediaId = 1L,
            title = "默认收藏夹",
            hasMore = false,
            medias = listOf(
                BiliFavoriteMedia(
                    id = 1L,
                    bvid = "BV1xx411c7mD",
                    title = "收藏视频",
                    owner = BiliOwner(name = "UP主")
                )
            )
        )
    )
    var hotSearchResult: BiliResult<BiliHotSearchResponse> = BiliResult(
        code = 0,
        data = BiliHotSearchResponse(
            list = listOf(BiliTrendingWord(keyword = "测试热搜", showName = "测试热搜"))
        )
    )
    var searchHistory: MutableList<String> = initialSearchHistory.toMutableList()
    private val searchResults: MutableMap<Pair<String, Int>, BiliResult<BiliSearchResponse>> = linkedMapOf(
        ("Compose" to 1) to BiliResult(
            code = 0,
            data = BiliSearchResponse(
                numResults = 1,
                numPages = 1,
                page = 1,
                result = emptyList()
            )
        )
    )
    var commentsResult: BiliResult<BiliCommentPage> = BiliResult(
        code = 0,
        data = BiliCommentPage(
            cursor = BiliCommentCursor(next = null, isEnd = true),
            replies = listOf(sampleComment()),
            topReplies = emptyList()
        )
    )
    var repliesResult: BiliResult<BiliCommentReplyPage> = BiliResult(
        code = 0,
        data = BiliCommentReplyPage(
            root = sampleComment(rpid = 10L, message = "根评论"),
            replies = listOf(sampleComment(rpid = 11L, message = "楼中楼"))
        )
    )
    private val commentResults = mutableMapOf<Pair<Long, Long>, BiliResult<BiliCommentPage>>()
    private val replyResults = mutableMapOf<Triple<Long, Long, Int>, BiliResult<BiliCommentReplyPage>>()
    private val videoDetailResults = mutableMapOf<Pair<Long?, String?>, BiliResult<BiliVideoDetail>>()
    private val favoriteItemsResults = mutableMapOf<Pair<Long, Int>, BiliResult<BiliFavoritePage>>()
    var headers: Map<String, String> = initialPlayHeaders
    var cachedPreviewUriValue: String? = null
    var cachedPreviewUriAnyValue: String? = null
    var cachePreviewClipResult: Result<String> = Result.success("/tmp/fake-preview.mp4")
    val clearedPreviewRequests = mutableListOf<Triple<Long?, String?, Long?>>()
    val cachedPreviewRequests = mutableListOf<Triple<Long?, String?, Long?>>()
    val addToViewRequests = mutableListOf<Pair<Long?, String?>>()
    val favoriteRequests = mutableListOf<Pair<Long, Boolean>>()
    val likeRequests = mutableListOf<Pair<Long, Boolean>>()
    val coinRequests = mutableListOf<Triple<Long, Int, Boolean>>()

    override suspend fun isLoggedIn(): Boolean = loggedIn

    override suspend fun readAccount(): BiliAccount? = account

    override suspend fun clearAccount() {
        clearAccountCount += 1
        loggedIn = false
        account = null
    }

    override suspend fun logoutAndClearPreviewCache() {
        logoutCount += 1
        clearAccount()
    }

    override suspend fun applyCookieHeader(rawCookie: String): Result<Unit> {
        if (applyCookieResult.isSuccess) {
            loggedIn = true
        }
        return applyCookieResult
    }

    override suspend fun requestWebQrCode(): WebQrCode? = webQrCode

    override suspend fun requestTvQrCode(): TvQrCode? = tvQrCode

    override suspend fun pollWebQrCode(qrKey: String): QrPollResult {
        if (webQrPollResult.status == QrPollStatus.SUCCESS) {
            loggedIn = true
        }
        return webQrPollResult
    }

    override suspend fun pollTvQrCode(authCode: String): QrPollResult {
        if (tvQrPollResult.status == QrPollStatus.SUCCESS) {
            loggedIn = true
        }
        return tvQrPollResult
    }

    override suspend fun fetchFeed(): BiliResult<BiliFeedPage> = feedResult

    override suspend fun readFeedCache(): List<BiliItem> = feedCache

    override suspend fun writeFeedCache(items: List<BiliItem>) {
        feedCache = items
    }

    override suspend fun fetchVideoDetail(aid: Long?, bvid: String?): BiliResult<BiliVideoDetail> {
        return videoDetailResults[aid to bvid] ?: videoDetailResult
    }

    override suspend fun fetchPlayUrlMp4(
        cid: Long,
        aid: Long?,
        bvid: String?,
        qn: Int
    ): BiliResult<BiliPlayUrl> = playUrlResult

    override suspend fun like(aid: Long, like: Boolean): BiliResult<Unit> {
        likeRequests += aid to like
        return likeResult
    }

    override suspend fun coin(aid: Long, multiply: Int, selectLike: Boolean): BiliResult<Boolean> {
        coinRequests += Triple(aid, multiply, selectLike)
        return coinResult
    }

    override suspend fun triple(aid: Long): BiliResult<com.lightningstudio.watchrss.sdk.bili.BiliTripleResult> {
        return tripleResult
    }

    override suspend fun favorite(aid: Long, add: Boolean): BiliResult<Boolean> {
        favoriteRequests += aid to add
        return favoriteResult
    }

    override suspend fun addToView(aid: Long?, bvid: String?): BiliResult<Unit> {
        addToViewRequests += aid to bvid
        return addToViewResult
    }

    override suspend fun fetchToView(): BiliResult<BiliToViewPage> = toViewResult

    override suspend fun fetchHistory(cursor: com.lightningstudio.watchrss.sdk.bili.BiliHistoryCursor?): BiliResult<com.lightningstudio.watchrss.sdk.bili.BiliHistoryPage> =
        historyResult

    override suspend fun fetchFavoriteFolders(): BiliResult<List<BiliFavoriteFolder>> = favoriteFoldersResult

    override suspend fun fetchFavoriteItems(mediaId: Long, pn: Int, ps: Int): BiliResult<BiliFavoritePage> =
        favoriteItemsResults[mediaId to pn] ?: favoriteItemsResult

    override suspend fun getHotSearch(): BiliResult<BiliHotSearchResponse> = hotSearchResult

    override suspend fun searchAll(keyword: String, page: Int): BiliResult<BiliSearchResponse> {
        return searchResults[keyword to page]
            ?: BiliResult(code = 0, data = BiliSearchResponse(numResults = 0, numPages = 1, page = page, result = emptyList()))
    }

    override suspend fun getSearchHistory(): List<String> = searchHistory.toList()

    override suspend fun addSearchHistory(keyword: String) {
        searchHistory.remove(keyword)
        searchHistory.add(0, keyword)
    }

    override suspend fun clearSearchHistory() {
        searchHistory.clear()
    }

    override suspend fun getComments(oid: Long, next: Long): BiliResult<BiliCommentPage> {
        return commentResults[oid to next] ?: commentsResult
    }

    override suspend fun getReplies(oid: Long, root: Long, pn: Int): BiliResult<BiliCommentReplyPage> =
        replyResults[Triple(oid, root, pn)] ?: repliesResult

    override suspend fun buildPlayHeaders(): Map<String, String> = headers

    override fun shareLink(bvid: String?, aid: Long?): String? {
        return when {
            !bvid.isNullOrBlank() -> "https://www.bilibili.com/video/$bvid"
            aid != null -> "https://www.bilibili.com/video/av$aid"
            else -> null
        }
    }

    override fun savedLink(bvid: String?, aid: Long?, cid: Long?): String? {
        return shareLink(bvid, aid)?.let { base ->
            if (cid == null) base else "$base?cid=$cid"
        }
    }

    override suspend fun cachedPreviewUri(aid: Long?, bvid: String?, cid: Long?): String? {
        return cachedPreviewUriValue
    }

    override suspend fun cachedPreviewUriAny(aid: Long?, bvid: String?): String? {
        return cachedPreviewUriAnyValue
    }

    override suspend fun cachePreviewClip(aid: Long?, bvid: String?, cid: Long?): Result<String> {
        cachedPreviewRequests += Triple(aid, bvid, cid)
        return cachePreviewClipResult
    }

    override suspend fun clearCachedPreview(aid: Long?, bvid: String?, cid: Long?) {
        clearedPreviewRequests += Triple(aid, bvid, cid)
    }

    fun setSearchResult(keyword: String, page: Int, result: BiliResult<BiliSearchResponse>) {
        searchResults[keyword to page] = result
    }

    fun setComments(oid: Long, next: Long, result: BiliResult<BiliCommentPage>) {
        commentResults[oid to next] = result
    }

    fun setReplies(oid: Long, root: Long, pn: Int, result: BiliResult<BiliCommentReplyPage>) {
        replyResults[Triple(oid, root, pn)] = result
    }

    fun setVideoDetail(aid: Long?, bvid: String?, detail: BiliVideoDetail) {
        videoDetailResults[aid to bvid] = BiliResult(code = 0, data = detail)
    }

    fun setFavoriteItems(mediaId: Long, pn: Int, result: BiliResult<BiliFavoritePage>) {
        favoriteItemsResults[mediaId to pn] = result
    }
}

private fun defaultBiliItem(
    aid: Long = 1L,
    bvid: String = "BV1xx411c7mD",
    cid: Long = 11L,
    title: String = "测试视频",
    ownerName: String = "测试UP主"
): BiliItem {
    return BiliItem(
        aid = aid,
        bvid = bvid,
        cid = cid,
        title = title,
        cover = "https://example.com/bili-cover.jpg",
        duration = 120,
        owner = BiliOwner(mid = 9527L, name = ownerName),
        stat = BiliStat(view = 100, like = 20, danmaku = 3, coin = 5, favorite = 8)
    )
}

private fun defaultBiliVideoDetail(item: BiliItem = defaultBiliItem()): BiliVideoDetail {
    return BiliVideoDetail(
        item = item,
        desc = "这是一条测试用的视频简介",
        pages = listOf(BiliPage(cid = item.cid, page = 1, part = "P1", duration = item.duration))
    )
}

private fun sampleComment(rpid: Long = 1L, message: String = "测试评论"): BiliCommentData {
    return BiliCommentData(
        rpid = rpid,
        oid = 1L,
        member = BiliCommentMember(uname = "测试用户"),
        content = BiliCommentContent(message = message)
    )
}
