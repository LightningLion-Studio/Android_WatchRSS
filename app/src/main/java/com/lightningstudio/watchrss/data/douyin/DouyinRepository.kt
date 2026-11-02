package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.lightningstudio.watchrss.data.cache.ManagedCacheBucket
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.sdk.douyin.ABogus
import com.lightningstudio.watchrss.sdk.douyin.DouyinLoginProbeResult
import com.lightningstudio.watchrss.sdk.douyin.DouyinRequestConfig
import com.lightningstudio.watchrss.sdk.douyin.DouyinChallengeException
import com.lightningstudio.watchrss.sdk.douyin.DouyinContent
import com.lightningstudio.watchrss.sdk.douyin.DouyinFeedPage
import com.lightningstudio.watchrss.sdk.douyin.DouyinUnifiedParser
import com.lightningstudio.watchrss.sdk.douyin.DouyinVideo
import com.lightningstudio.watchrss.sdk.douyin.DouyinWebCrawler
import com.lightningstudio.watchrss.sdk.douyin.EncryptedDouyinCookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.File
import java.io.IOException

class DouyinRepository(
    context: Context,
    private val cacheService: ManagedCacheService? = null,
    private val recentWindowStore: DouyinRecentWindowStoreContract? = null,
    private val requestConfig: DouyinRequestConfig = DouyinRequestConfig()
) : DouyinRepositoryContract {
    private val appContext = context.applicationContext
    private val cookieStore = EncryptedDouyinCookieStore(context)
    private val parser = DouyinUnifiedParser()
    private val crawler = DouyinWebCrawler(ABogus(), config = requestConfig)
    private val cooldownPrefs = appContext.getSharedPreferences("douyin_request_cooldown", Context.MODE_PRIVATE)
    @Volatile private var requestPolicy = newRequestPolicy()

    private fun newRequestPolicy(): DouyinRequestPolicy {
        val saved = listOf("feed", "detail").associateWith { endpoint -> DouyinCooldown(
            cooldownPrefs.getLong("$endpoint.until", 0), cooldownPrefs.getInt("$endpoint.strikes", 0)) }
        lateinit var policy: DouyinRequestPolicy
        policy = DouyinRequestPolicy(initial = saved, persist = { state ->
            synchronized(this) {
                if (requestPolicy === policy) {
                    val editor = cooldownPrefs.edit().clear()
                    state.forEach { (endpoint, value) ->
                        editor.putLong("$endpoint.until", value.until).putInt("$endpoint.strikes", value.strikes)
                    }
                    editor.apply()
                }
            }
        })
        return policy
    }

    @Synchronized private fun resetRequestPolicy() {
        cooldownPrefs.edit().clear().apply()
        requestPolicy = newRequestPolicy()
    }

    init { DouyinStockRuntime.configure(appContext) }

    override suspend fun isLoggedIn(): Boolean = !cookieStore.readCookie().isNullOrBlank()

    override suspend fun readCookie(): String? = cookieStore.readCookie()

    override suspend fun clearCookie() {
        resetRequestPolicy()
        DouyinStockRuntime.clear()
        cookieStore.writeCookie(null)
        recentWindowStore?.clear()
        clearWebViewSession()
    }

    override suspend fun logoutAndClearMediaCache() {
        clearCookie()
        DouyinPlaybackPreviewCache.clearAll()
        if (cacheService != null) {
            cacheService.clearBucket(ManagedCacheBucket.DOUYIN_PRELOAD)
        } else {
            File(appContext.cacheDir, DouyinPreloadManager.CACHE_DIR_NAME).deleteRecursively()
        }
    }

    override suspend fun applyCookieHeader(rawCookie: String): Result<Unit> {
        val trimmed = rawCookie.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("缺少有效 Cookie"))
        }
        resetRequestPolicy()
        DouyinStockRuntime.clear()
        cookieStore.writeCookie(trimmed)
        return Result.success(Unit)
    }

    override suspend fun fetchFeed(): DouyinResult<List<DouyinVideo>> {
        val page = fetchFeedPage(cursor = null, count = 10)
        return if (page.isSuccess) {
            DouyinResult(DouyinErrorCodes.OK, data = page.data?.items.orEmpty())
        } else {
            DouyinResult(page.code, page.message)
        }
    }

    override suspend fun fetchFeedPage(cursor: String?, count: Int): DouyinResult<DouyinFeedPage> {
        val cookie = cookieStore.readCookie().orEmpty()
        if (cookie.isBlank()) {
            return DouyinResult(DouyinErrorCodes.NOT_LOGGED_IN, "未登录")
        }
        val policy = requestPolicy
        if (policy.coolingDown("feed")) return DouyinResult(DouyinErrorCodes.CHALLENGE, "请求冷却中，请稍后重试")
        return withContext(Dispatchers.IO) {
            try {
                val raw = crawler.fetchJingxuanFeed(cookie = cookie, cursor = cursor, count = count)
                val page = parser.parseFeedPage(raw).applyPlaybackPreference()
                policy.successful("feed")
                DouyinResult(DouyinErrorCodes.OK, data = page)
            } catch (e: DouyinChallengeException) {
                val probe = policy.challenged("feed") { crawler.probeLogin(cookie) }
                DouyinResult(DouyinErrorCodes.CHALLENGE, if (probe == DouyinLoginProbeResult.UNAUTHENTICATED) {
                    "登录状态需要确认，请手动检查"
                } else "请求受限，请稍后重试")
            } catch (e: IOException) {
                DouyinResult(DouyinErrorCodes.REQUEST_FAILED, "网络请求失败")
            } catch (e: JSONException) {
                DouyinResult(DouyinErrorCodes.PARSE_FAILED, e.message ?: "解析失败")
            }
        }
    }

    override suspend fun fetchVideo(awemeId: String): DouyinResult<DouyinContent> {
        val cookie = cookieStore.readCookie().orEmpty()
        if (cookie.isBlank()) {
            return DouyinResult(DouyinErrorCodes.NOT_LOGGED_IN, "未登录")
        }
        val policy = requestPolicy
        if (policy.coolingDown("detail")) return DouyinResult(DouyinErrorCodes.CHALLENGE, "请求冷却中，请稍后重试")
        return withContext(Dispatchers.IO) {
            try {
                val raw = crawler.fetchOneVideo(awemeId, cookie)
                val content = parser.parse(raw).applyPlaybackPreference()
                policy.successful("detail")
                DouyinResult(DouyinErrorCodes.OK, data = content)
            } catch (e: DouyinChallengeException) {
                val probe = policy.challenged("detail") { crawler.probeLogin(cookie) }
                DouyinResult(DouyinErrorCodes.CHALLENGE, if (probe == DouyinLoginProbeResult.UNAUTHENTICATED) {
                    "登录状态需要确认，请手动检查"
                } else "请求受限，请稍后重试")
            } catch (e: IOException) {
                DouyinResult(DouyinErrorCodes.REQUEST_FAILED, "网络请求失败")
            } catch (e: JSONException) {
                DouyinResult(DouyinErrorCodes.PARSE_FAILED, e.message ?: "解析失败")
            }
        }
    }

    private fun DouyinFeedPage.applyPlaybackPreference(): DouyinFeedPage {
        items.forEach { video ->
            applyPreferredPlayback(video)
        }
        return this
    }

    private fun DouyinContent.applyPlaybackPreference(): DouyinContent {
        if (this !is DouyinContent.Video) return this
        return applyPreferredPlayback(this)
    }

    override suspend fun buildPlayHeaders(): Map<String, String> {
        val cookie = cookieStore.readCookie()
        val headers = mutableMapOf(
            "User-Agent" to requestConfig.userAgent,
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
    const val CHALLENGE = 429
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
        DouyinErrorCodes.CHALLENGE -> message?.takeIf { it.isNotBlank() } ?: "请求受限，请稍后重试"
        DouyinErrorCodes.PARSE_FAILED -> "解析失败"
        DouyinErrorCodes.REQUEST_FAILED -> "网络请求失败"
        else -> message?.takeIf { it.isNotBlank() } ?: "加载失败"
    }
}
