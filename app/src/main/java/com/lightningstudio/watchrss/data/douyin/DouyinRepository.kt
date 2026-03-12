package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.lightningstudio.watchrss.sdk.douyin.ABogus
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.sdk.douyin.DouyinUnifiedParser
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import com.lightningstudio.watchrss.sdk.douyin.DouyinWebCrawler
import com.lightningstudio.watchrss.sdk.douyin.EncryptedDouyinCookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

class DouyinRepository(context: Context) {
    private val cookieStore = EncryptedDouyinCookieStore(context)
    private val parser = DouyinUnifiedParser()
    private val crawler = DouyinWebCrawler(ABogus())

    suspend fun isLoggedIn(): Boolean = !cookieStore.readCookie().isNullOrBlank()

    suspend fun readCookie(): String? = cookieStore.readCookie()

    suspend fun clearCookie() {
        cookieStore.writeCookie(null)
        clearWebViewSession()
    }

    suspend fun applyCookieHeader(rawCookie: String): Result<Unit> {
        val trimmed = rawCookie.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("缺少有效 Cookie"))
        }
        cookieStore.writeCookie(trimmed)
        return Result.success(Unit)
    }

    suspend fun fetchFeed(): DouyinResult<List<DouyinVideo>> {
        val page = fetchFeedPage(cursor = null, count = 10)
        return if (page.isSuccess) {
            DouyinResult(DouyinErrorCodes.OK, data = page.data?.items.orEmpty())
        } else {
            DouyinResult(page.code, page.message)
        }
    }

    suspend fun fetchFeedPage(cursor: String?, count: Int): DouyinResult<DouyinFeedPage> {
        val cookie = cookieStore.readCookie().orEmpty()
        if (cookie.isBlank()) {
            return DouyinResult(DouyinErrorCodes.NOT_LOGGED_IN, "未登录")
        }
        return withContext(Dispatchers.IO) {
            try {
                val raw = crawler.fetchJingxuanFeed(cookie = cookie, cursor = cursor, count = count)
                val page = parser.parseFeedPage(raw)
                DouyinResult(DouyinErrorCodes.OK, data = page)
            } catch (e: IOException) {
                val msg = e.message.orEmpty()
                val code = if (msg.contains("Cookie无效")) {
                    DouyinErrorCodes.NOT_LOGGED_IN
                } else {
                    DouyinErrorCodes.REQUEST_FAILED
                }
                DouyinResult(code, msg.ifBlank { "网络请求失败" })
            } catch (e: JSONException) {
                DouyinResult(DouyinErrorCodes.PARSE_FAILED, e.message ?: "解析失败")
            }
        }
    }

    suspend fun fetchVideo(awemeId: String): DouyinResult<DouyinContent> {
        val cookie = cookieStore.readCookie().orEmpty()
        if (cookie.isBlank()) {
            return DouyinResult(DouyinErrorCodes.NOT_LOGGED_IN, "未登录")
        }
        return withContext(Dispatchers.IO) {
            try {
                val raw = crawler.fetchOneVideo(awemeId, cookie)
                val content = parser.parse(raw)
                DouyinResult(DouyinErrorCodes.OK, data = content)
            } catch (e: IOException) {
                val msg = e.message.orEmpty()
                val code = if (msg.contains("Cookie无效")) {
                    DouyinErrorCodes.NOT_LOGGED_IN
                } else {
                    DouyinErrorCodes.REQUEST_FAILED
                }
                DouyinResult(code, msg.ifBlank { "网络请求失败" })
            } catch (e: JSONException) {
                DouyinResult(DouyinErrorCodes.PARSE_FAILED, e.message ?: "解析失败")
            }
        }
    }

    suspend fun buildPlayHeaders(): Map<String, String> {
        val cookie = cookieStore.readCookie()
        val headers = mutableMapOf(
            "User-Agent" to DouyinWebCrawler.USER_AGENT,
            "Referer" to DouyinWebCrawler.REFERER
        )
        if (!cookie.isNullOrBlank()) {
            headers["Cookie"] = cookie
        }
        return headers
    }

    private fun clearWebViewSession() {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            DOUYIN_COOKIE_URLS.forEach { url ->
                val cookieNames = parseCookieNames(cookieManager.getCookie(url))
                cookieNames.forEach { cookieName ->
                    cookieManager.setCookie(
                        url,
                        "$cookieName=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/"
                    )
                    cookieManager.setCookie(
                        url,
                        "$cookieName=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/; Domain=.douyin.com"
                    )
                }
            }
            cookieManager.flush()
        }
        runCatching {
            val webStorage = WebStorage.getInstance()
            DOUYIN_ORIGINS.forEach(webStorage::deleteOrigin)
        }
    }

    private fun parseCookieNames(rawCookie: String?): Set<String> {
        if (rawCookie.isNullOrBlank()) return emptySet()
        return rawCookie
            .split(";")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val index = token.indexOf('=')
                if (index <= 0) null else token.substring(0, index).trim().takeIf { it.isNotBlank() }
            }
            .toSet()
    }

    companion object {
        private val DOUYIN_COOKIE_URLS = listOf(
            "https://www.douyin.com",
            "https://douyin.com",
            "https://creator.douyin.com",
            "https://www.iesdouyin.com"
        )

        private val DOUYIN_ORIGINS = listOf(
            "https://www.douyin.com",
            "https://douyin.com",
            "https://creator.douyin.com",
            "https://www.iesdouyin.com"
        )
    }
}

object DouyinErrorCodes {
    const val OK = 0
    const val NOT_LOGGED_IN = 401
    const val REQUEST_FAILED = 500
    const val PARSE_FAILED = 501
}

data class DouyinResult<T>(
    val code: Int,
    val message: String? = null,
    val data: T? = null
) {
    val isSuccess: Boolean = code == DouyinErrorCodes.OK
}

fun formatDouyinError(code: Int, message: String? = null): String {
    return when (code) {
        DouyinErrorCodes.NOT_LOGGED_IN -> "需要登录"
        DouyinErrorCodes.PARSE_FAILED -> "解析失败"
        DouyinErrorCodes.REQUEST_FAILED -> "网络请求失败"
        else -> message?.takeIf { it.isNotBlank() } ?: "加载失败"
    }
}
