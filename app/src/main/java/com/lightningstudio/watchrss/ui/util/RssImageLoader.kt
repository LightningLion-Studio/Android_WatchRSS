package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object RssImageLoader {
    private const val cacheSizeBytes = 8 * 1024 * 1024
    private const val ratioCacheSize = 300

    private val cache = object : LruCache<String, Bitmap>(cacheSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val ratioCache = object : LruCache<String, Float>(ratioCacheSize) {}

    fun getCachedBitmap(url: String): Bitmap? = cache.get(url)

    fun getCachedAspectRatio(url: String): Float? = ratioCache.get(url)

    suspend fun preloadAndCacheRatio(context: Context, url: String, maxWidthPx: Int): Float? {
        loadBitmap(context, url, maxWidthPx)
        return ratioCache.get(url)
    }

    fun load(context: Context, url: String, imageView: ImageView, scope: CoroutineScope, maxWidthPx: Int) {
        imageView.tag = url
        val cached = cache.get(url)
        if (cached != null) {
            cacheAspectRatio(url, cached.width, cached.height)
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
                cacheAspectRatio(url, bitmap.width, bitmap.height)
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
            cacheAspectRatio(url, diskBitmap.width, diskBitmap.height)
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
                    cacheAspectRatio(url, bitmap.width, bitmap.height)
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
                cacheAspectRatio(url, bitmap.width, bitmap.height)
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
                    cacheAspectRatio(url, bitmap.width, bitmap.height)
                }
                return@withContext bitmap
            }
            val diskBitmap = decodeDiskBitmap(context, url, maxWidthPx)
            if (diskBitmap != null) {
                prepareBitmap(diskBitmap)
                cache.put(url, diskBitmap)
                cacheAspectRatio(url, diskBitmap.width, diskBitmap.height)
                return@withContext diskBitmap
            }
            val bitmap = fetchBitmap(context, url, maxWidthPx)
            if (bitmap != null) {
                prepareBitmap(bitmap)
                cache.put(url, bitmap)
                cacheAspectRatio(url, bitmap.width, bitmap.height)
            }
            bitmap
        }
    }

    private fun fetchBitmap(context: Context, urlString: String, maxWidthPx: Int): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            persistToDisk(context, urlString, bytes)
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            cacheAspectRatio(urlString, bounds.outWidth, bounds.outHeight)
            val options = decodeOptions(bounds, maxWidthPx)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isLocalPath(url: String): Boolean {
        return url.startsWith("/") || url.startsWith("file://")
    }

    private fun decodeLocalBitmap(url: String, maxWidthPx: Int): Bitmap? {
        return try {
            val path = if (url.startsWith("file://")) url.removePrefix("file://") else url
            val bytes = java.io.File(path).takeIf { it.exists() }?.readBytes() ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            cacheAspectRatio(url, bounds.outWidth, bounds.outHeight)
            val options = decodeOptions(bounds, maxWidthPx)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeDiskBitmap(context: Context, url: String, maxWidthPx: Int): Bitmap? {
        val file = cacheFile(context, url)
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            cacheAspectRatio(url, bounds.outWidth, bounds.outHeight)
            val options = decodeOptions(bounds, maxWidthPx)
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun persistToDisk(context: Context, url: String, bytes: ByteArray) {
        val file = cacheFile(context, url)
        if (file.exists()) return
        runCatching {
            file.outputStream().use { it.write(bytes) }
        }
    }

    private fun cacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, "rss_images").apply { mkdirs() }
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

    private fun cacheAspectRatio(url: String, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        ratioCache.put(url, width.toFloat() / height.toFloat())
    }

    private fun decodeOptions(bounds: BitmapFactory.Options, maxWidthPx: Int): BitmapFactory.Options {
        val reqWidth = maxWidthPx.coerceAtLeast(1)
        val reqHeight = (maxWidthPx * 2).coerceAtLeast(1)
        return BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
    }

    private fun prepareBitmap(bitmap: Bitmap) {
        runCatching { bitmap.prepareToDraw() }
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
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / inSampleSize >= reqWidth && halfHeight / inSampleSize >= reqHeight) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
