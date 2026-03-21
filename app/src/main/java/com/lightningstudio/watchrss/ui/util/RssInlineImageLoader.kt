package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import coil.ImageLoader
import coil.disk.DiskCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lightningstudio.watchrss.data.rss.RssRemoteRequestPolicy
import kotlinx.coroutines.Dispatchers
import java.io.File
import okhttp3.OkHttpClient

object RssInlineImageLoader {
    private const val COIL_CACHE_DIR_NAME = "coil"
    private const val DISK_CACHE_MAX_BYTES = 128L * 1024L * 1024L
    private val fetchDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(1)

    @Volatile
    private var imageLoader: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return imageLoader ?: synchronized(this) {
            imageLoader ?: build(context.applicationContext).also { imageLoader = it }
        }
    }

    fun buildRequest(context: Context, url: String, maxWidthPx: Int): ImageRequest {
        return ImageRequest.Builder(context)
            .data(resolveRequestData(url))
            .memoryCacheKey(url)
            .diskCacheKey(url)
            .size(maxWidthPx.coerceAtLeast(1), (maxWidthPx * 3).coerceAtLeast(1))
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(false)
            .allowRgb565(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    suspend fun prefetch(context: Context, url: String, maxWidthPx: Int) {
        if (url.isBlank()) return
        val result = get(context).execute(buildRequest(context, url, maxWidthPx))
        cacheAspectRatio(url, result as? SuccessResult ?: return)
    }

    fun cacheAspectRatio(url: String, result: SuccessResult) {
        cacheAspectRatio(url, result.drawable)
    }

    fun cacheAspectRatio(url: String, drawable: Drawable?) {
        val width = drawable?.intrinsicWidth ?: 0
        val height = drawable?.intrinsicHeight ?: 0
        if (width > 0 && height > 0) {
            RssImageLoader.putCachedAspectRatio(url, width, height)
        }
    }

    private fun build(context: Context): ImageLoader {
        val cacheDir = File(File(context.cacheDir, RssImageLoader.DISK_CACHE_DIR_NAME), COIL_CACHE_DIR_NAME)
        cacheDir.mkdirs()
        return ImageLoader.Builder(context)
            .okHttpClient {
                RssRemoteRequestPolicy.configure(
                    OkHttpClient.Builder()
                        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                ).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .cleanupDispatcher(fetchDispatcher)
                    .build()
            }
            .allowHardware(false)
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .fetcherDispatcher(fetchDispatcher)
            .decoderDispatcher(decodeDispatcher)
            .bitmapFactoryMaxParallelism(1)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    private fun resolveRequestData(url: String): Any {
        return when {
            url.startsWith("/") -> File(url)
            url.startsWith("file://") -> Uri.parse(url)
            url.startsWith("content://") -> Uri.parse(url)
            else -> url
        }
    }
}
