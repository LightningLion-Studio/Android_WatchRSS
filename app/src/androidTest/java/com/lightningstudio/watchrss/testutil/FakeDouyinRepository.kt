package com.lightningstudio.watchrss.testutil

import com.lightningstudio.watchrss.data.douyin.DouyinErrorCodes
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinResult
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo

class FakeDouyinRepository(
    initialLoggedIn: Boolean = false,
    initialItems: List<DouyinVideo> = listOf(sampleDouyinVideo()),
    initialFeedPage: DouyinFeedPage = DouyinFeedPage(
        items = initialItems,
        nextCursor = null,
        hasMore = false
    ),
    initialHeaders: Map<String, String> = mapOf(
        "User-Agent" to "FakeDouyinRepository",
        "Referer" to "https://www.douyin.com"
    )
) : DouyinRepositoryContract {
    var loggedIn = initialLoggedIn
    var cookie: String? = if (initialLoggedIn) "session=test" else null
    var applyCookieResult: Result<Unit> = Result.success(Unit)
    var feedPages: ArrayDeque<DouyinResult<DouyinFeedPage>> = ArrayDeque(
        listOf(
            DouyinResult(
                code = DouyinErrorCodes.OK,
                data = initialFeedPage
            )
        )
    )
    var feedResult: DouyinResult<List<DouyinVideo>> = DouyinResult(
        code = DouyinErrorCodes.OK,
        data = initialItems
    )
    var videoResult: DouyinResult<DouyinContent> = DouyinResult(
        code = DouyinErrorCodes.OK,
        data = DouyinContent.Video(
            awemeId = "aweme-1",
            desc = "测试抖音视频",
            authorName = "测试作者",
            diggCount = 10L,
            playUrl = "https://example.com/douyin.mp4",
            coverUrl = "https://example.com/douyin.jpg"
        )
    )
    private val feedPageResults = mutableMapOf<String?, DouyinResult<DouyinFeedPage>>()
    private val videoResults = mutableMapOf<String, DouyinResult<DouyinContent>>()
    var headers: Map<String, String> = initialHeaders
    var clearCookieCount = 0
    var logoutCount = 0

    override suspend fun isLoggedIn(): Boolean = loggedIn

    override suspend fun readCookie(): String? = cookie

    override suspend fun clearCookie() {
        clearCookieCount += 1
        loggedIn = false
        cookie = null
    }

    override suspend fun logoutAndClearMediaCache() {
        logoutCount += 1
        clearCookie()
    }

    override suspend fun applyCookieHeader(rawCookie: String): Result<Unit> {
        if (applyCookieResult.isSuccess) {
            loggedIn = true
            cookie = rawCookie
        }
        return applyCookieResult
    }

    override suspend fun fetchFeed(): DouyinResult<List<DouyinVideo>> = feedResult

    override suspend fun fetchFeedPage(cursor: String?, count: Int): DouyinResult<DouyinFeedPage> {
        return feedPageResults[cursor] ?: if (feedPages.isEmpty()) {
            DouyinResult(
                code = DouyinErrorCodes.OK,
                data = DouyinFeedPage(items = emptyList(), nextCursor = null, hasMore = false)
            )
        } else {
            feedPages.removeFirst()
        }
    }

    override suspend fun fetchVideo(awemeId: String): DouyinResult<DouyinContent> {
        return videoResults[awemeId] ?: videoResult
    }

    override suspend fun buildPlayHeaders(): Map<String, String> = headers

    fun setFeedPage(cursor: String?, result: DouyinResult<DouyinFeedPage>) {
        feedPageResults[cursor] = result
    }

    fun setVideo(awemeId: String, result: DouyinResult<DouyinContent>) {
        videoResults[awemeId] = result
    }
}
