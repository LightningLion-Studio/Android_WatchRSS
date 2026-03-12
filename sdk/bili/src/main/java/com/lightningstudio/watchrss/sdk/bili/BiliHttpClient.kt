package com.lightningstudio.watchrss.sdk.bili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class BiliHttpClient(
    private val config: BiliSdkConfig,
    private val accountStore: BiliAccountStore?
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun get(
        url: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        includeCookies: Boolean = true
    ): BiliHttpResult = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder()
            .url(httpUrl)
            .headers(buildHeaders(headers, includeCookies))
            .get()
            .build()
        execute(request)
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        includeCookies: Boolean = true
    ): BiliHttpResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers, includeCookies))
            .post(body)
            .build()
        execute(request)
    }

    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        includeCookies: Boolean = true
    ): BiliHttpResult = withContext(Dispatchers.IO) {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers, includeCookies))
            .post(body)
            .build()
        execute(request)
    }

    private suspend fun buildHeaders(
        headers: Map<String, String>,
        includeCookies: Boolean
    ): Headers {
        val builder = Headers.Builder()
        builder.set("User-Agent", config.webUserAgent)
        builder.set("Referer", config.webReferer)
        if (includeCookies) {
            val cookies = accountStore?.read()?.cookies?.takeIf { it.isNotEmpty() }
            if (cookies != null) {
                builder.add("Cookie", cookies.entries.joinToString("; ") { (k, v) -> "$k=$v" })
            }
        }
        headers.forEach { (k, v) -> builder.set(k, v) }
        return builder.build()
    }

    private fun execute(request: Request): BiliHttpResult {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return BiliHttpResult(response.code, body, response.headers)
        }
    }
}

data class BiliHttpResult(
    val code: Int,
    val body: String,
    val headers: Headers
)
