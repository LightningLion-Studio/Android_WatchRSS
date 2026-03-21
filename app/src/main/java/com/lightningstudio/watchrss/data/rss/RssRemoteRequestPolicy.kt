package com.lightningstudio.watchrss.data.rss

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

object RssRemoteRequestPolicy {
    const val DEFAULT_USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val defaultHeaders = linkedMapOf(
        "User-Agent" to DEFAULT_USER_AGENT
    )

    fun headerMapFor(url: String): Map<String, String> {
        val headers = LinkedHashMap(defaultHeaders)
        val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
        if (host == "sspai.com" || host.endsWith(".sspai.com")) {
            headers["Referer"] = "https://sspai.com/"
        }
        return headers
    }

    fun headersFor(url: String): Headers {
        val builder = Headers.Builder()
        headerMapFor(url).forEach { (name, value) ->
            builder[name] = value
        }
        return builder.build()
    }

    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        return builder.addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            headerMapFor(original.url.toString()).forEach { (name, value) ->
                if (original.header(name).isNullOrBlank()) {
                    requestBuilder.header(name, value)
                }
            }
            chain.proceed(requestBuilder.build())
        }
    }

    fun newRequestBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .headers(headersFor(url))
    }
}
