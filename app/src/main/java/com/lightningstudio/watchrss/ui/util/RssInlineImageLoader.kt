package com.lightningstudio.watchrss.ui.util

import androidx.core.net.toUri
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lightningstudio.watchrss.data.rss.RssRemoteRequestPolicy
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.LinkedHashMap
import okhttp3.OkHttpClient

object RssInlineImageLoader {
    private const val COIL_CACHE_DIR_NAME = "coil"
    private const val DISK_CACHE_MAX_BYTES = 128L * 1024L * 1024L
    private val fetchDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(1)

    private const val MAX_ACCOUNT_IMAGE_LOADERS = 2
    private val imageLoaders = LinkedHashMap<String, ImageLoader>(MAX_ACCOUNT_IMAGE_LOADERS, 0.75f, true)

    fun get(context: Context): ImageLoader {
        val storageContext = com.lightningstudio.watchrss.data.account.workspace.libraryContext(context)
        val cacheScope = storageContext.cacheDir.absolutePath
        synchronized(imageLoaders) {
            imageLoaders[cacheScope]?.let { return it }
            val loader = build(storageContext)
            imageLoaders[cacheScope] = loader
            while (imageLoaders.size > MAX_ACCOUNT_IMAGE_LOADERS) {
                val eldest = imageLoaders.entries.iterator().next()
                imageLoaders.remove(eldest.key)
                eldest.value.shutdown()
            }
            return loader
        }
    }

    fun buildRequest(context: Context, url: String, maxWidthPx: Int): ImageRequest {
        val storageContext = com.lightningstudio.watchrss.data.account.workspace.libraryContext(context)
        val scopedCacheKey = "${storageContext.cacheDir.absolutePath}\u0000$url"
        return ImageRequest.Builder(context)
            .data(resolveRequestData(url))
            .memoryCacheKey(scopedCacheKey)
            .diskCacheKey(scopedCacheKey)
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
        cacheAspectRatio(context, url, result as? SuccessResult ?: return)
    }

    fun cacheAspectRatio(context: Context, url: String, result: SuccessResult) {
        cacheAspectRatio(context, url, result.drawable)
    }

    fun cacheAspectRatio(context: Context, url: String, drawable: Drawable?) {
        val width = drawable?.intrinsicWidth ?: 0
        val height = drawable?.intrinsicHeight ?: 0
        if (width > 0 && height > 0) {
            RssImageLoader.putCachedAspectRatio(context, url, width, height)
        }
    }

    private fun build(context: Context): ImageLoader {
        val cacheDir = File(File(context.cacheDir, RssImageLoader.DISK_CACHE_DIR_NAME), COIL_CACHE_DIR_NAME)
        cacheDir.mkdirs()
        // AccountDataContext intentionally returns itself as applicationContext to
        // preserve account-scoped storage. Coil registers component callbacks on
        // applicationContext, so passing that wrapper causes infinite recursion.
        val applicationContext = applicationContextOf(context)
        return ImageLoader.Builder(applicationContext)
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
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.1)
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

    private fun applicationContextOf(context: Context): Context {
        var current = context
        while (current is ContextWrapper && current.baseContext !== current) {
            current = current.baseContext
        }
        return current.applicationContext ?: current
    }

    private fun resolveRequestData(url: String): Any {
        return when {
            url.startsWith("/") -> File(url)
            url.startsWith("file://") -> url.toUri()
            url.startsWith("content://") -> url.toUri()
            else -> url
        }
    }
}
