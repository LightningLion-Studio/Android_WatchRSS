package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import android.net.Uri
import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class DouyinPreloadManager(
    context: Context,
    private val cacheService: ManagedCacheService? = null
) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun localPathFor(awemeId: String): String? {
        val file = mediaFileFor(awemeId) ?: return null
        if (!file.exists()) return null
        if (file.length() < MIN_VALID_FILE_BYTES) {
            file.delete()
            cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_DELETE)
            return null
        }
        touchFile(file)
        return file.absolutePath
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
            trimCache(maxEntries = MAX_CACHE_ENTRIES)
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
        trimCache(maxEntries = MAX_CACHE_ENTRIES)
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
            touchFile(target)
            cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_WRITE)
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

    private fun trimCache(maxEntries: Int) {
        val files = cacheDir
            .listFiles { file -> file.isFile && file.extension.equals("mp4", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        if (files.size <= maxEntries) return
        files.drop(maxEntries).forEach { file -> file.delete() }
        cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_DELETE)
    }

    private fun touchFile(file: File) {
        runCatching { file.setLastModified(System.currentTimeMillis()) }
    }

    companion object {
        internal const val CACHE_DIR_NAME = "douyin_preload"
        internal const val MIN_VALID_FILE_BYTES = 64 * 1024L
        private const val MAX_CACHE_ENTRIES = 36
    }
}
