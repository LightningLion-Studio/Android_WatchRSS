package com.lightningstudio.watchrss.data.bili

import com.lightningstudio.watchrss.sdk.bili.BiliAccount
import com.lightningstudio.watchrss.sdk.bili.BiliCommentPage
import com.lightningstudio.watchrss.sdk.bili.BiliCommentReplyPage
import com.lightningstudio.watchrss.sdk.bili.BiliFavoriteFolder
import com.lightningstudio.watchrss.sdk.bili.BiliFavoritePage
import com.lightningstudio.watchrss.sdk.bili.BiliFeedPage
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryCursor
import com.lightningstudio.watchrss.sdk.bili.BiliHistoryPage
import com.lightningstudio.watchrss.sdk.bili.BiliHotSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliItem
import com.lightningstudio.watchrss.sdk.bili.BiliPlayUrl
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.sdk.bili.BiliSearchResponse
import com.lightningstudio.watchrss.sdk.bili.BiliToViewPage
import com.lightningstudio.watchrss.sdk.bili.BiliTripleResult
import com.lightningstudio.watchrss.sdk.bili.BiliVideoDetail
import com.lightningstudio.watchrss.sdk.bili.QrPollResult
import com.lightningstudio.watchrss.sdk.bili.TvQrCode
import com.lightningstudio.watchrss.sdk.bili.WebQrCode

private fun unsupportedBiliRepositoryMethod(): Nothing {
    throw UnsupportedOperationException("BiliRepositoryContract method is not implemented")
}

interface BiliRepositoryContract {
    suspend fun isLoggedIn(): Boolean = unsupportedBiliRepositoryMethod()
    suspend fun readAccount(): BiliAccount? = unsupportedBiliRepositoryMethod()
    suspend fun clearAccount(): Unit = unsupportedBiliRepositoryMethod()
    suspend fun logoutAndClearPreviewCache(): Unit = unsupportedBiliRepositoryMethod()
    suspend fun applyCookieHeader(rawCookie: String): Result<Unit> = unsupportedBiliRepositoryMethod()
    suspend fun requestWebQrCode(): WebQrCode? = unsupportedBiliRepositoryMethod()
    suspend fun requestTvQrCode(): TvQrCode? = unsupportedBiliRepositoryMethod()
    suspend fun pollWebQrCode(qrKey: String): QrPollResult = unsupportedBiliRepositoryMethod()
    suspend fun pollTvQrCode(authCode: String): QrPollResult = unsupportedBiliRepositoryMethod()
    suspend fun fetchFeed(): BiliResult<BiliFeedPage> = unsupportedBiliRepositoryMethod()
    suspend fun readFeedCache(): List<BiliItem> = unsupportedBiliRepositoryMethod()
    suspend fun writeFeedCache(items: List<BiliItem>): Unit = unsupportedBiliRepositoryMethod()
    suspend fun fetchVideoDetail(aid: Long? = null, bvid: String? = null): BiliResult<BiliVideoDetail> =
        unsupportedBiliRepositoryMethod()
    suspend fun fetchPlayUrlMp4(
        cid: Long,
        aid: Long? = null,
        bvid: String? = null,
        qn: Int = 32
    ): BiliResult<BiliPlayUrl> = unsupportedBiliRepositoryMethod()
    suspend fun like(aid: Long, like: Boolean): BiliResult<Unit> = unsupportedBiliRepositoryMethod()
    suspend fun coin(
        aid: Long,
        multiply: Int = 1,
        selectLike: Boolean = false
    ): BiliResult<Boolean> = unsupportedBiliRepositoryMethod()
    suspend fun triple(aid: Long): BiliResult<BiliTripleResult> = unsupportedBiliRepositoryMethod()
    suspend fun favorite(aid: Long, add: Boolean): BiliResult<Boolean> = unsupportedBiliRepositoryMethod()
    suspend fun addToView(aid: Long? = null, bvid: String? = null): BiliResult<Unit> =
        unsupportedBiliRepositoryMethod()
    suspend fun fetchToView(): BiliResult<BiliToViewPage> = unsupportedBiliRepositoryMethod()
    suspend fun fetchHistory(cursor: BiliHistoryCursor? = null): BiliResult<BiliHistoryPage> =
        unsupportedBiliRepositoryMethod()
    suspend fun fetchFavoriteFolders(): BiliResult<List<BiliFavoriteFolder>> =
        unsupportedBiliRepositoryMethod()
    suspend fun fetchFavoriteItems(
        mediaId: Long,
        pn: Int = 1,
        ps: Int = 20
    ): BiliResult<BiliFavoritePage> = unsupportedBiliRepositoryMethod()
    suspend fun getHotSearch(): BiliResult<BiliHotSearchResponse> = unsupportedBiliRepositoryMethod()
    suspend fun searchAll(keyword: String, page: Int): BiliResult<BiliSearchResponse> =
        unsupportedBiliRepositoryMethod()
    suspend fun getSearchHistory(): List<String> = unsupportedBiliRepositoryMethod()
    suspend fun addSearchHistory(keyword: String): Unit = unsupportedBiliRepositoryMethod()
    suspend fun clearSearchHistory(): Unit = unsupportedBiliRepositoryMethod()
    suspend fun getComments(oid: Long, next: Long = 0): BiliResult<BiliCommentPage> =
        unsupportedBiliRepositoryMethod()
    suspend fun getReplies(oid: Long, root: Long, pn: Int = 1): BiliResult<BiliCommentReplyPage> =
        unsupportedBiliRepositoryMethod()
    suspend fun buildPlayHeaders(): Map<String, String> = unsupportedBiliRepositoryMethod()
    fun shareLink(bvid: String?, aid: Long?): String? = unsupportedBiliRepositoryMethod()
    fun savedLink(bvid: String?, aid: Long?, cid: Long?): String? = unsupportedBiliRepositoryMethod()
    suspend fun cachedPreviewUri(aid: Long?, bvid: String?, cid: Long?): String? =
        unsupportedBiliRepositoryMethod()
    suspend fun cachedPreviewUriAny(aid: Long?, bvid: String?): String? = unsupportedBiliRepositoryMethod()
    suspend fun cachePreviewClip(aid: Long?, bvid: String?, cid: Long?): Result<String> =
        unsupportedBiliRepositoryMethod()
    suspend fun clearCachedPreview(aid: Long?, bvid: String?, cid: Long?): Unit =
        unsupportedBiliRepositoryMethod()
}
