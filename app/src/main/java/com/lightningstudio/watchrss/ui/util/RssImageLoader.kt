package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.rss.RssRemoteRequestPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object RssImageLoader {
    internal const val DISK_CACHE_DIR_NAME = "rss_images"
    private const val cacheSizeBytes = 8 * 1024 * 1024
    private const val ratioCacheSize = 300

    @Volatile
    private var cacheService: ManagedCacheService? = null

    private val cache = object : LruCache<String, Bitmap>(cacheSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val ratioCache = object : LruCache<String, Float>(ratioCacheSize) {}

    fun getCachedBitmap(url: String): Bitmap? = cache.get(url)

    fun getCachedAspectRatio(url: String): Float? = ratioCache.get(url)

    fun putCachedAspectRatio(url: String, width: Int, height: Int) {
        storeAspectRatio(url, width, height)
    }

    fun configure(cacheService: ManagedCacheService?) {
        this.cacheService = cacheService
    }

    suspend fun preloadAndCacheRatio(context: Context, url: String, maxWidthPx: Int): Float? {
        loadBitmap(context, url, maxWidthPx)
        return ratioCache.get(url)
    }

    fun load(context: Context, url: String, imageView: ImageView, scope: CoroutineScope, maxWidthPx: Int) {
        imageView.tag = url
        val cached = cache.get(url)
        if (cached != null) {
            storeAspectRatio(url, cached.width, cached.height)
            prepareBitmap(cached)
            imageView.setImageBitmap(cached)
            imageView.visibility = View.VISIBLE
            return
        }
        if (isLocalPath(url)) {
            val bitmap = decodeLocalBitmap(url, maxWidthPx)
            if (bitmap != null) {
                prepareBitmap(bitmap)
                cache.put(url, bitmap)
                storeAspectRatio(url, bitmap.width, bitmap.height)
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.GONE
            }
            return
        }
        val diskBitmap = decodeDiskBitmap(context, url, maxWidthPx)
        if (diskBitmap != null) {
            prepareBitmap(diskBitmap)
            cache.put(url, diskBitmap)
            storeAspectRatio(url, diskBitmap.width, diskBitmap.height)
            imageView.setImageBitmap(diskBitmap)
            imageView.visibility = View.VISIBLE
            return
        }
        imageView.visibility = View.GONE
        scope.launch(Dispatchers.IO) {
            val bitmap = fetchBitmap(context, url, maxWidthPx)
            withContext(Dispatchers.Main) {
                if (imageView.tag != url) {
                    return@withContext
                }
                if (bitmap != null) {
                    prepareBitmap(bitmap)
                    cache.put(url, bitmap)
                    storeAspectRatio(url, bitmap.width, bitmap.height)
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                } else {
                    imageView.visibility = View.GONE
                }
            }
        }
    }

    fun preload(context: Context, url: String, scope: CoroutineScope, maxWidthPx: Int) {
        if (cache.get(url) != null) return
        scope.launch(Dispatchers.IO) {
            if (cache.get(url) != null) return@launch
            val bitmap = if (isLocalPath(url)) {
                decodeLocalBitmap(url, maxWidthPx)
            } else {
                decodeDiskBitmap(context, url, maxWidthPx) ?: fetchBitmap(context, url, maxWidthPx)
            }
            if (bitmap != null) {
                prepareBitmap(bitmap)
                cache.put(url, bitmap)
                storeAspectRatio(url, bitmap.width, bitmap.height)
            }
        }
    }

    suspend fun loadBitmap(context: Context, url: String, maxWidthPx: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            cache.get(url)?.let { return@withContext it }
            if (isLocalPath(url)) {
                val bitmap = decodeLocalBitmap(url, maxWidthPx)
                if (bitmap != null) {
                    prepareBitmap(bitmap)
                    cache.put(url, bitmap)
                    storeAspectRatio(url, bitmap.width, bitmap.height)
                }
                return@withContext bitmap
            }
            val diskBitmap = decodeDiskBitmap(context, url, maxWidthPx)
            if (diskBitmap != null) {
                prepareBitmap(diskBitmap)
                cache.put(url, diskBitmap)
                storeAspectRatio(url, diskBitmap.width, diskBitmap.height)
                return@withContext diskBitmap
            }
            val bitmap = fetchBitmap(context, url, maxWidthPx)
            if (bitmap != null) {
                prepareBitmap(bitmap)
                cache.put(url, bitmap)
                storeAspectRatio(url, bitmap.width, bitmap.height)
            }
            bitmap
        }
    }

    private fun fetchBitmap(context: Context, urlString: String, maxWidthPx: Int): Bitmap? {
        var connection: HttpURLConnection? = null
        val cacheFile = cacheFile(context, urlString)
        val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                doInput = true
                instanceFollowRedirects = true
                requestHeadersFor(url.toString()).forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return null
            }
            connection.inputStream.buffered().use { input ->
                tempFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            val bitmap = decodeBitmapFile(tempFile, urlString, maxWidthPx) ?: return null
            persistTempFile(cacheFile, tempFile)
            bitmap
        } catch (e: Exception) {
            null
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            connection?.disconnect()
        }
    }

    private fun isLocalPath(url: String): Boolean {
        return url.startsWith("/") || url.startsWith("file://")
    }

    private fun decodeLocalBitmap(url: String, maxWidthPx: Int): Bitmap? {
        return try {
            val path = if (url.startsWith("file://")) url.removePrefix("file://") else url
            val file = java.io.File(path)
            if (!file.exists()) return null
            decodeBitmapFile(file, url, maxWidthPx)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeDiskBitmap(context: Context, url: String, maxWidthPx: Int): Bitmap? {
        val file = cacheFile(context, url)
        if (!file.exists()) return null
        return try {
            decodeBitmapFile(file, url, maxWidthPx)?.also { touchFile(file) }
        } catch (e: Exception) {
            null
        }
    }

    private fun persistTempFile(file: File, tempFile: File) {
        runCatching {
            if (file.exists()) {
                file.delete()
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            touchFile(file)
            cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_WRITE)
        }.onFailure {
            tempFile.delete()
        }
    }

    private fun decodeBitmapFile(file: File, cacheKey: String, maxWidthPx: Int): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        storeAspectRatio(cacheKey, bounds.outWidth, bounds.outHeight)
        val options = decodeOptions(bounds, maxWidthPx)
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun cacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, DISK_CACHE_DIR_NAME).apply { mkdirs() }
        return File(dir, "${hashUrl(url)}.img")
    }

    private fun hashUrl(url: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(url.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
    }

    private fun storeAspectRatio(url: String, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        ratioCache.put(url, width.toFloat() / height.toFloat())
    }

    private fun requestHeadersFor(url: String): Map<String, String> {
        return RssRemoteRequestPolicy.headerMapFor(url)
    }

    private fun decodeOptions(bounds: BitmapFactory.Options, maxWidthPx: Int): BitmapFactory.Options {
        val reqWidth = ((maxWidthPx * 3) / 2).coerceAtLeast(1)
        val reqHeight = (maxWidthPx * 3).coerceAtLeast(1)
        return BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
    }

    private fun prepareBitmap(bitmap: Bitmap) {
        runCatching { bitmap.prepareToDraw() }
    }

    private fun touchFile(file: File) {
        runCatching { file.setLastModified(System.currentTimeMillis()) }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0 || reqWidth <= 0 || reqHeight <= 0) {
            return 1
        }
        var inSampleSize = 1
        while (width / inSampleSize > reqWidth || height / inSampleSize > reqHeight) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
