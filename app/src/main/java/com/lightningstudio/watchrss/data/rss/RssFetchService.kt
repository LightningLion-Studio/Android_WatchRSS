package com.lightningstudio.watchrss.data.rss

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssChannel as ParsedChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class RssFetchService(
    private val parser: RssParser = buildParser(),
    private val downloadClient: OkHttpClient = buildDownloadClient()
) : RssDownloadClient {
    suspend fun fetchChannel(url: String): ParsedChannel = parser.getRssChannel(url)

    override fun downloadToFile(url: String, file: File): String? {
        return try {
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            downloadClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
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
            val client = OkHttpClient.Builder()
                .callTimeout(20, TimeUnit.SECONDS)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
            return RssParserBuilder(
                callFactory = client,
                charset = Charsets.UTF_8
            ).build()
        }

        private fun buildDownloadClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
