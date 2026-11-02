package com.lightningstudio.watchrss.sdk.douyin

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

enum class DouyinLoginProbeResult { AUTHENTICATED, UNAUTHENTICATED, UNKNOWN }
class DouyinChallengeException : IOException("抖音请求受限，请稍后重试")

class DouyinWebCrawler(
    private val abogusInstance: ABogus,
    private val config: DouyinRequestConfig = DouyinRequestConfig(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS).build()
) {
    suspend fun fetchOneVideo(awemeId: String, cookie: String): String = execute(
        request("/aweme/v1/web/aweme/detail/", PostDetail(awemeId).toMap(), cookie)
    )

    suspend fun fetchJingxuanFeed(cookie: String, cursor: String? = null, count: Int = 10): String = execute(
        request("/aweme/v2/web/module/feed/", JingxuanFeed(cursor, count.coerceIn(1, 20)).toMap(), cookie, post = true)
    )

    internal fun request(path: String, base: Map<String, String>, cookie: String, post: Boolean = false): Request {
        val params = config.parameters(base)
        val signature = abogusInstance.getValue(params)
        val url = ("https://www.douyin.com" + path).toHttpUrlOrNull()!!.newBuilder()
        params.forEach { (key, value) -> url.addQueryParameter(key, value) }
        url.addQueryParameter("a_bogus", signature)
        return Request.Builder().url(url.build())
            .header("User-Agent", config.userAgent).header("Referer", REFERER)
            .header("Cookie", cookie).header("Accept", "application/json")
            .apply {
                if (post) post("".toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaTypeOrNull()))
                else get()
            }.build()
    }

    /** Only the independently specified notice/count status 0 or 8 is definitive. */
    suspend fun probeLogin(cookie: String): DouyinLoginProbeResult {
        val body = execute(request("/aweme/v1/web/notice/count/", BaseRequestModel().toMap().apply {
            put("is_new_notice", "1")
            put("need_social_count", "1")
        }, cookie), client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build())
        return when (JSONObject(body).opt("status_code")) {
            0 -> DouyinLoginProbeResult.AUTHENTICATED
            8 -> DouyinLoginProbeResult.UNAUTHENTICATED
            else -> DouyinLoginProbeResult.UNKNOWN
        }
    }

    private suspend fun execute(request: Request, httpClient: OkHttpClient = client): String =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("网络请求失败"))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (it.code == 403) throw DouyinChallengeException()
                            if (it.code != 200) throw IOException("请求失败，状态码：${it.code}")
                            // Bound challenge/error bodies as well as successful payloads.
                            val source = it.body?.source() ?: throw IOException("响应体为空")
                            if (source.request(MAX_BODY_BYTES + 1)) throw IOException("响应体过大")
                            validateDouyinResponse(source.readUtf8())
                        }
                    }
                    result.fold(
                        onSuccess = { if (continuation.isActive) continuation.resume(it) },
                        onFailure = { if (continuation.isActive) continuation.resumeWithException(it) }
                    )
                }
            })
        }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36"
        const val REFERER = "https://www.douyin.com/"
        private const val MAX_BODY_BYTES = 4L * 1024 * 1024
    }
}

fun validateDouyinResponse(body: String): String {
    val trimmed = body.trim()
    if (trimmed.isEmpty() || trimmed.equals("blocked", true) ||
        trimmed.startsWith("<") || trimmed.contains("verify_center", true)) {
        throw DouyinChallengeException()
    }
    return body
}
