package com.lightningstudio.watchrss.data.rss

import android.webkit.CookieManager
import android.util.Log
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssChannel as ParsedChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class RssFetchService(
    private val parser: RssParser = buildParser(),
    private val downloadClient: OkHttpClient = buildDownloadClient()
) : RssDownloadClient {
    suspend fun fetchChannel(url: String): ParsedChannel = parser.getRssChannel(url)

    suspend fun parseChannelContent(content: String): ParsedChannel = parser.parse(content)

    override fun downloadToFile(url: String, file: File): String? {
        return try {
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            downloadClient.newCall(RssRemoteRequestPolicy.newRequestBuilder(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                response.body?.byteStream()?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (tempFile.length() <= 0L) {
                tempFile.delete()
                return null
            }
            file.parentFile?.mkdirs()
            if (file.exists()) {
                file.delete()
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            if (file.exists() && file.length() > 0) file.absolutePath else null
        } catch (e: Exception) {
            runCatching { File(file.parentFile, "${file.name}.tmp").delete() }
            runCatching { file.delete() }
            null
        }
    }

    companion object {
        private fun buildParser(): RssParser {
            val client = buildFeedClient()
            return RssParserBuilder(
                callFactory = client,
                charset = Charsets.UTF_8
            ).build()
        }

        internal fun buildFeedClient(): OkHttpClient {
            val builder = RssRemoteRequestPolicy.configure(
                OkHttpClient.Builder()
                    .callTimeout(20, TimeUnit.SECONDS)
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
            )
            builder.addInterceptor { chain ->
                val original = chain.request()
                val browserCookie = runCatching {
                    CookieManager.getInstance().getCookie(original.url.toString())
                }.getOrNull()
                val request = if (
                    !browserCookie.isNullOrBlank() && original.header("Cookie").isNullOrBlank()
                ) {
                    original.newBuilder().header("Cookie", browserCookie).build()
                } else {
                    original
                }

                val response = chain.proceed(request)
                val challengePrefix = if (response.isSuccessful) {
                    runCatching {
                        response.peekBody(WAF_CHALLENGE_PROBE_BYTES).string()
                    }.getOrNull()
                } else {
                    null
                }
                if (challengePrefix != null && isRssBrowserChallengePage(challengePrefix)) {
                    val contentLength = response.body?.contentLength() ?: -1L
                    val challengeHtml = if (contentLength > MAX_WAF_CHALLENGE_HTML_BYTES) {
                        ""
                    } else {
                        runCatching {
                            response.peekBody(MAX_WAF_CHALLENGE_HTML_BYTES).string()
                        }.getOrDefault("")
                    }
                    runCatching {
                        Log.i(
                            "RssBrowserVerification",
                            "Feed fetch requires browser verification; captured ${challengeHtml.length} HTML characters"
                        )
                    }
                    val challengeUrl = response.request.url.toString()
                    response.close()
                    throw RssBrowserChallengeRequiredException(challengeHtml, challengeUrl)
                }
                response
            }
            return builder.build()
        }

        private fun buildDownloadClient(): OkHttpClient {
            return RssRemoteRequestPolicy.configure(
                OkHttpClient.Builder()
                    .callTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
            ).build()
        }
    }
}

internal const val WAF_CHALLENGE_MARKER = "__waf_verification_challenge"
private const val WAF_CHALLENGE_PROBE_BYTES = 16L * 1024L
private const val MAX_WAF_CHALLENGE_HTML_BYTES = 512L * 1024L

internal fun isRssBrowserChallengePage(bodyPrefix: String): Boolean =
    bodyPrefix.contains(WAF_CHALLENGE_MARKER)

class RssBrowserChallengeRequiredException(
    val challengeHtml: String = "",
    val challengeUrl: String? = null
) : IOException(
    "该订阅需要通过网页完成安全验证"
)
