package com.lightningstudio.watchrss.data.douyin

import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo

private fun unsupportedDouyinRepositoryMethod(): Nothing {
    throw UnsupportedOperationException("DouyinRepositoryContract method is not implemented")
}

interface DouyinRepositoryContract {
    suspend fun isLoggedIn(): Boolean = unsupportedDouyinRepositoryMethod()
    suspend fun readCookie(): String? = unsupportedDouyinRepositoryMethod()
    suspend fun clearCookie(): Unit = unsupportedDouyinRepositoryMethod()
    suspend fun logoutAndClearMediaCache(): Unit = unsupportedDouyinRepositoryMethod()
    suspend fun applyCookieHeader(rawCookie: String): Result<Unit> = unsupportedDouyinRepositoryMethod()
    suspend fun fetchFeed(): DouyinResult<List<DouyinVideo>> = unsupportedDouyinRepositoryMethod()
    suspend fun fetchFeedPage(cursor: String?, count: Int): DouyinResult<DouyinFeedPage> =
        unsupportedDouyinRepositoryMethod()
    suspend fun fetchVideo(awemeId: String): DouyinResult<DouyinContent> =
        unsupportedDouyinRepositoryMethod()
    suspend fun buildPlayHeaders(): Map<String, String> = unsupportedDouyinRepositoryMethod()
}
