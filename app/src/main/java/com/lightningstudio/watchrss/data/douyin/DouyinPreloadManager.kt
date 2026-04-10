package com.lightningstudio.watchrss.data.douyin

import android.content.Context
import android.net.Uri
import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

interface DouyinPreloadManagerContract {
    suspend fun localPathFor(awemeId: String): String?
    suspend fun resolveLocalPaths(awemeIds: List<String>): Map<String, String>
    suspend fun ensurePlaybackWindowCached(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        requiredPrefixCount: Int = 1,
        onItemCached: ((awemeId: String, localPath: String) -> Unit)? = null
    )
    suspend fun ensureUnwatchedCache(
        items: List<DouyinStreamItem>,
        watchedIds: Set<String>,
        headers: Map<String, String>,
        targetUnwatchedCount: Int = 2,
        onItemCached: ((awemeId: String, localPath: String) -> Unit)? = null
    )
    suspend fun invalidate(awemeId: String): Boolean
}

class DouyinPreloadManager(
    context: Context,
    private val cacheService: ManagedCacheService? = null
) : DouyinPreloadManagerContract {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    // Fast cache lookups must not queue behind long-running mp4 downloads during entry restore.
    private val lookupDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val downloadDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun localPathFor(awemeId: String): String? {
        return withContext(lookupDispatcher) {
            localPathForInternal(awemeId)
        }
    }

    override suspend fun resolveLocalPaths(awemeIds: List<String>): Map<String, String> {
        return withContext(lookupDispatcher) {
            resolveLocalPathsInternal(awemeIds)
        }
    }

    override suspend fun ensurePlaybackWindowCached(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        requiredPrefixCount: Int,
        onItemCached: ((awemeId: String, localPath: String) -> Unit)?
    ) {
        withContext(downloadDispatcher) {
            ensurePlaybackWindowCachedInternal(
                items = items,
                headers = headers,
                requiredPrefixCount = requiredPrefixCount,
                onItemCached = onItemCached
            )
        }
    }

    override suspend fun ensureUnwatchedCache(
        items: List<DouyinStreamItem>,
        watchedIds: Set<String>,
        headers: Map<String, String>,
        targetUnwatchedCount: Int,
        onItemCached: ((awemeId: String, localPath: String) -> Unit)?
    ) {
        withContext(downloadDispatcher) {
            ensureUnwatchedCacheInternal(
                items = items,
                watchedIds = watchedIds,
                headers = headers,
                targetUnwatchedCount = targetUnwatchedCount,
                onItemCached = onItemCached
            )
        }
    }

    fun toLocalUri(path: String): Uri = Uri.fromFile(File(path))

    override suspend fun invalidate(awemeId: String): Boolean {
        return withContext(downloadDispatcher) {
            invalidateInternal(awemeId)
        }
    }

    private fun localPathForInternal(awemeId: String): String? {
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

    private fun resolveLocalPathsInternal(awemeIds: List<String>): Map<String, String> {
        if (awemeIds.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        awemeIds.distinct().forEach { awemeId ->
            val local = localPathForInternal(awemeId)
            if (!local.isNullOrBlank()) {
                result[awemeId] = local
            }
        }
        return result
    }

    private suspend fun ensurePlaybackWindowCachedInternal(
        items: List<DouyinStreamItem>,
        headers: Map<String, String>,
        requiredPrefixCount: Int,
        onItemCached: ((awemeId: String, localPath: String) -> Unit)?
    ) {
        if (requiredPrefixCount <= 0 || items.isEmpty()) return

        val windowItems = buildValidItems(items)
            .take(requiredPrefixCount)
        if (windowItems.isEmpty()) return

        val initialReadyCount = windowItems.count { item ->
            !localPathForInternal(item.awemeId).isNullOrBlank()
        }
        AppLogger.d(
            TAG,
            "ensure playback window ready=$initialReadyCount/${windowItems.size} ids=${
                windowItems.joinToString(",") { it.awemeId }
            }"
        )
        for (item in windowItems) {
            currentCoroutineContext().ensureActive()
            val localPath = localPathForInternal(item.awemeId)
                ?: downloadToCache(item, headers)
            if (!localPath.isNullOrBlank()) {
                onItemCached?.invoke(item.awemeId, localPath)
            }
        }
        trimCache(maxEntries = MAX_CACHE_ENTRIES)
    }

    private suspend fun ensureUnwatchedCacheInternal(
        items: List<DouyinStreamItem>,
        watchedIds: Set<String>,
        headers: Map<String, String>,
        targetUnwatchedCount: Int,
        onItemCached: ((awemeId: String, localPath: String) -> Unit)?
    ) {
        if (targetUnwatchedCount <= 0 || items.isEmpty()) return

        val validItems = buildValidItems(items)
            .associateByTo(linkedMapOf()) { it.awemeId }
        if (validItems.isEmpty()) return

        currentCoroutineContext().ensureActive()
        val cachedNow = resolveLocalPathsInternal(validItems.keys.toList())
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
            currentCoroutineContext().ensureActive()
            if (cachedUnwatchedCount >= targetUnwatchedCount) break
            val localPath = downloadToCache(item, headers)
            if (!localPath.isNullOrBlank()) {
                cachedUnwatchedCount += 1
                onItemCached?.invoke(item.awemeId, localPath)
            }
        }
        trimCache(maxEntries = MAX_CACHE_ENTRIES)
    }

    private fun buildValidItems(items: List<DouyinStreamItem>): List<DouyinStreamItem> {
        val validItems = linkedMapOf<String, DouyinStreamItem>()
        items.forEach { item ->
            val awemeId = item.awemeId.trim()
            val playUrl = item.playUrl.trim()
            if (awemeId.isNotEmpty() && playUrl.isNotEmpty()) {
                validItems.putIfAbsent(awemeId, item)
            }
        }
        return validItems.values.toList()
    }

    private fun invalidateInternal(awemeId: String): Boolean {
        val target = mediaFileFor(awemeId) ?: return false
        val deleted = target.exists() && target.delete()
        if (deleted) {
            cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_DELETE)
        }
        return deleted
    }

    private suspend fun downloadToCache(
        item: DouyinStreamItem,
        headers: Map<String, String>
    ): String? {
        val target = mediaFileFor(item.awemeId) ?: return null
        if (target.exists() && target.length() >= MIN_VALID_FILE_BYTES) {
            target.setLastModified(System.currentTimeMillis())
            AppLogger.d(TAG, "cache hit awemeId=${item.awemeId} bytes=${target.length()}")
            return target.absolutePath
        }

        val temp = File(cacheDir, "${target.name}.tmp")
        val startedAtMs = System.currentTimeMillis()
        return try {
            val requestBuilder = Request.Builder()
                .url(item.playUrl)
                .get()
            headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    requestBuilder.header(key, value)
                }
            }
            val call = httpClient.newCall(requestBuilder.build())
            val job = currentCoroutineContext().job
            val cancelHandle = job.invokeOnCompletion { throwable ->
                if (throwable != null) {
                    call.cancel()
                }
            }
            try {
                call.execute().use { response ->
                    currentCoroutineContext().ensureActive()
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
            } finally {
                cancelHandle.dispose()
            }
            currentCoroutineContext().ensureActive()
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
            val finishedAtMs = System.currentTimeMillis()
            AppLogger.d(
                TAG,
                "cached awemeId=${item.awemeId} bytes=${target.length()} costMs=${finishedAtMs - startedAtMs}"
            )
            target.absolutePath
        } catch (cancelled: CancellationException) {
            AppLogger.d(TAG, "cache cancelled awemeId=${item.awemeId}")
            temp.delete()
            throw cancelled
        } catch (error: Throwable) {
            AppLogger.w(TAG, "cache failed awemeId=${item.awemeId}", error)
            temp.delete()
            null
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
        private const val TAG = "DouyinPreload"
        internal const val CACHE_DIR_NAME = "douyin_preload"
        internal const val MIN_VALID_FILE_BYTES = 64 * 1024L
        private const val MAX_CACHE_ENTRIES = DOUYIN_PRELOAD_MAX_CACHE_ENTRIES
    }
}
