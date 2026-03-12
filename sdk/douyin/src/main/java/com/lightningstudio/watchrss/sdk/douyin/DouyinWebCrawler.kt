package com.lightningstudio.watchrss.sdk.douyin

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// 参考自Python实现的crawlers/douyin/web/web_crawler.py
class DouyinWebCrawler(private val abogusInstance: ABogus) {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    fun fetchOneVideo(awemeId: String, cookie: String): String {
        val endpoint = "https://www.douyin.com/aweme/v1/web/aweme/detail/"

        val params = PostDetail(awemeId).toMap()
        params["msToken"] = ""

        val aBogus = abogusInstance.getValue(params)

        val urlBuilder = endpoint.toHttpUrlOrNull()!!.newBuilder()
        params.forEach { (k, v) ->
            urlBuilder.addQueryParameter(k, v)
        }
        urlBuilder.addQueryParameter("a_bogus", aBogus)
        val finalUrl = urlBuilder.build()

        val request = Request.Builder()
            .url(finalUrl)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Cookie", cookie)
            .header("Accept", "application/json")
            .build()

        return fetchValidData(client = client, request = request)
    }

    fun fetchJingxuanFeed(
        cookie: String,
        cursor: String? = null,
        count: Int = 10
    ): String {
        val endpoint = "https://www.douyin.com/aweme/v2/web/module/feed/"

        val params = JingxuanFeed(cursor = cursor, count = count).toMap()
        params["msToken"] = ""

        val aBogus = abogusInstance.getValue(params)

        val urlBuilder = endpoint.toHttpUrlOrNull()!!.newBuilder()
        params.forEach { (k, v) ->
            urlBuilder.addQueryParameter(k, v)
        }
        urlBuilder.addQueryParameter("a_bogus", aBogus)
        val finalUrl = urlBuilder.build()

        val formMediaType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaTypeOrNull()
        val request = Request.Builder()
            .url(finalUrl)
            .post("".toRequestBody(formMediaType))
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Cookie", cookie)
            .header("Accept", "application/json")
            .build()

        return fetchValidData(client = client, request = request)
    }

    fun fetchValidData(client: OkHttpClient, request: Request): String {
        return client.newCall(request).execute().use { response ->
            if (response.code != 200) {
                throw IOException("请求失败，状态码：${response.code}（仅支持200）")
            }

            val responseBody = response.body?.string()
                ?: throw IOException("响应体为空，无法获取有效数据")

            if (responseBody.isBlank()) {
                throw IOException("Cookie无效：响应体为空字符串")
            } else if (responseBody == "blocked") {
                throw IOException("Cookie无效：响应体为blocked")
            }

            responseBody
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
        const val REFERER = "https://www.douyin.com/"
    }
}
