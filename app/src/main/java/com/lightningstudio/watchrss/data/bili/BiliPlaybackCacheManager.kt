@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lightningstudio.watchrss.data.bili

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.lightningstudio.watchrss.sdk.bili.BiliDurl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import kotlin.math.max
import kotlin.math.min

class BiliPlaybackCacheManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    private val cacheLock = Any()
    private val upstreamClient = OkHttpClient.Builder()
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cache: SimpleCache? = null

    fun buildPlaybackDataSourceFactory(
        headers: Map<String, String>,
        forcedCacheKey: String? = null
    ): DataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(
            appContext,
            OkHttpDataSource.Factory(upstreamClient).apply {
                if (headers.isNotEmpty()) {
                    setDefaultRequestProperties(headers)
                }
            }
        )
        return CacheDataSource.Factory()
            .setCache(obtainCache())
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .apply {
                forcedCacheKey
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { cacheKey ->
                        setCacheKeyFactory { cacheKey }
                    }
            }
    }

    suspend fun prefetch(
        url: String,
        headers: Map<String, String>,
        cacheKey: String,
        lengthBytes: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val safeUrl = url.trim()
        val safeKey = cacheKey.trim()
        if (safeUrl.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("empty_url"))
        }
        if (safeKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("empty_cache_key"))
        }
        if (lengthBytes <= 0) {
            return@withContext Result.failure(IllegalArgumentException("invalid_length"))
        }
        runCatching {
            val dataSource = buildPlaybackDataSourceFactory(
                headers = headers,
                forcedCacheKey = safeKey
            ).createDataSource() as CacheDataSource
            val dataSpec = DataSpec.Builder()
                .setUri(safeUrl)
                .setPosition(0)
                .setLength(lengthBytes)
                .setKey(safeKey)
                .build()
            CacheWriter(dataSource, dataSpec, null, null).cache()
        }.map { Unit }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            synchronized(cacheLock) {
                cache?.release()
                cache = null
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                cacheDir.mkdirs()
            }
        }
    }

    private fun obtainCache(): SimpleCache {
        synchronized(cacheLock) {
            val existing = cache
            if (existing != null) {
                return existing
            }
            return SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            ).also { created ->
                cache = created
            }
        }
    }

    companion object {
        const val CACHE_DIR_NAME = "bili_stream_cache"
        private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L

        fun buildCacheKey(
            aid: Long?,
            bvid: String?,
            cid: Long,
            quality: Int?
        ): String {
            val videoKey = when {
                !bvid.isNullOrBlank() -> "bv:${bvid.trim()}"
                aid != null -> "av:$aid"
                else -> "cid:$cid"
            }
            val qualityKey = quality?.takeIf { it > 0 } ?: 0
            return "bili:$videoKey:$cid:q$qualityKey"
        }

        fun estimatePreviewBytes(
            durl: BiliDurl,
            maxPreviewMs: Long
        ): Long? {
            val size = durl.size ?: return null
            val lengthMs = durl.length ?: return null
            if (size <= 0 || lengthMs <= 0 || maxPreviewMs <= 0) {
                return null
            }
            val targetMs = min(maxPreviewMs, lengthMs)
            return max(1L, size * targetMs / lengthMs)
        }
    }
}
