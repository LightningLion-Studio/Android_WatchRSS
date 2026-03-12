package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class DouyinPreloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun localPathFor(awemeId: String): String? {
        val file = mediaFileFor(awemeId) ?: return null
        return file.takeIf { it.exists() && it.length() >= MIN_VALID_FILE_BYTES }?.absolutePath
    }

    suspend fun resolveLocalPaths(awemeIds: List<String>): Map<String, String> {
        if (awemeIds.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        awemeIds.distinct().forEach { awemeId ->
            val local = localPathFor(awemeId)
            if (!local.isNullOrBlank()) {
                result[awemeId] = local
            }
        }
        return result
    }

    suspend fun ensureUnwatchedCache(
        items: List<DouyinStreamItem>,
        watchedIds: Set<String>,
        headers: Map<String, String>,
        targetUnwatchedCount: Int = 2
    ) {
        if (targetUnwatchedCount <= 0 || items.isEmpty()) return

        val validItems = linkedMapOf<String, DouyinStreamItem>()
        items.forEach { item ->
            val awemeId = item.awemeId.trim()
            val playUrl = item.playUrl.trim()
            if (awemeId.isNotEmpty() && playUrl.isNotEmpty()) {
                validItems.putIfAbsent(awemeId, item)
            }
        }
        if (validItems.isEmpty()) return

        val cachedNow = resolveLocalPaths(validItems.keys.toList())
        var cachedUnwatchedCount = cachedNow.keys.count { awemeId -> !watchedIds.contains(awemeId) }
        if (cachedUnwatchedCount >= targetUnwatchedCount) {
            trimCache(maxEntries = MAX_CACHE_ENTRIES, keepAwemeIds = validItems.keys)
            return
        }

        val candidates = validItems.values.filter { item ->
            !watchedIds.contains(item.awemeId) &&
                cachedNow[item.awemeId].isNullOrBlank()
        }
        for (item in candidates) {
            if (cachedUnwatchedCount >= targetUnwatchedCount) break
            if (downloadToCache(item, headers)) {
                cachedUnwatchedCount += 1
            }
        }
        trimCache(maxEntries = MAX_CACHE_ENTRIES, keepAwemeIds = validItems.keys)
    }

    fun toLocalUri(path: String): Uri = Uri.fromFile(File(path))

    private fun downloadToCache(item: DouyinStreamItem, headers: Map<String, String>): Boolean {
        val target = mediaFileFor(item.awemeId) ?: return false
        if (target.exists() && target.length() >= MIN_VALID_FILE_BYTES) {
            target.setLastModified(System.currentTimeMillis())
            return true
        }

        val temp = File(cacheDir, "${target.name}.tmp")
        return runCatching {
            val requestBuilder = Request.Builder()
                .url(item.playUrl)
                .get()
            headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    requestBuilder.header(key, value)
                }
            }
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("unexpected code ${response.code}")
                }
                val body = response.body ?: throw IOException("empty body")
                temp.outputStream().use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }
            if (temp.length() < MIN_VALID_FILE_BYTES) {
                throw IOException("file too small")
            }
            if (target.exists()) {
                target.delete()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target.setLastModified(System.currentTimeMillis())
            true
        }.getOrElse {
            temp.delete()
            false
        }
    }

    private fun mediaFileFor(awemeId: String): File? {
        val safeId = awemeId.trim()
            .takeIf { it.isNotEmpty() }
            ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
            ?: return null
        return File(cacheDir, "$safeId.mp4")
    }

    private fun trimCache(maxEntries: Int, keepAwemeIds: Set<String>) {
        val files = cacheDir
            .listFiles { file -> file.isFile && file.extension.equals("mp4", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        if (files.size <= maxEntries) return

        val keepNames = keepAwemeIds.map { awemeId ->
            awemeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        }.toSet()

        var kept = 0
        files.forEach { file ->
            val awemeId = file.nameWithoutExtension
            val shouldKeep = kept < maxEntries || keepNames.contains(awemeId)
            if (shouldKeep) {
                kept += 1
            } else {
                file.delete()
            }
        }
    }

    companion object {
        private const val CACHE_DIR_NAME = "douyin_preload"
        private const val MIN_VALID_FILE_BYTES = 64 * 1024L
        private const val MAX_CACHE_ENTRIES = 36
    }
}
