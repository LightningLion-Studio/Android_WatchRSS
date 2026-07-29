package com.lightningstudio.watchrss.ui.screen.rss

import androidx.core.net.toUri
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.TypedValue
import androidx.compose.ui.unit.TextUnit
import com.lightningstudio.watchrss.data.rss.OfflineMedia
import com.lightningstudio.watchrss.data.rss.RssUrlResolver
import com.lightningstudio.watchrss.data.settings.DEFAULT_READING_FONT_SIZE_SP
import com.lightningstudio.watchrss.ui.util.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal const val PREFETCH_MEDIA_COUNT = 2
internal const val PREFETCH_SCAN_LIMIT = 120
private const val VIDEO_FRAME_CACHE_BYTES = 4 * 1024 * 1024
internal const val MEDIA_LOAD_IDLE_DELAY_MS = 600L
internal const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f

internal val decodeSemaphore = Semaphore(permits = 1)

private val videoFrameCache = object : LruCache<String, Bitmap>(VIDEO_FRAME_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}
private val videoRatioCache = object : LruCache<String, Float>(200) {}
private val videoFrameHeaders = mapOf(
    "User-Agent" to "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"
)

internal enum class PrefetchType {
    Image,
    VideoFrame
}

internal data class PrefetchTarget(
    val url: String,
    val type: PrefetchType
) {
    fun cacheKey(maxWidthPx: Int): String {
        return when (type) {
            PrefetchType.Image -> url
            PrefetchType.VideoFrame -> "video:$url@$maxWidthPx"
        }
    }
}

internal fun adjustedTextSizeSp(
    context: Context,
    density: androidx.compose.ui.unit.Density,
    baseDimenRes: Int,
    currentFontSizeSp: Int
): TextUnit {
    val basePx = context.resources.getDimension(baseDimenRes)
    val deltaSp = (currentFontSizeSp - DEFAULT_READING_FONT_SIZE_SP).toFloat()
    val deltaPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        deltaSp,
        context.resources.displayMetrics
    )
    val sizePx = (basePx + deltaPx).coerceAtLeast(10f)
    return with(density) { sizePx.toSp() }
}

internal fun resolveMediaUrl(
    url: String,
    offlineMedia: Map<String, OfflineMedia>,
    baseLink: String?
): String {
    val local = offlineMedia[url]?.localPath
    if (!local.isNullOrBlank()) return local
    return RssUrlResolver.resolveMediaUrl(url, baseLink) ?: url
}

internal fun resolveRemoteUrl(url: String, baseLink: String?): String? {
    return RssUrlResolver.resolveMediaUrl(url, baseLink)
}

internal fun buildPrefetchTargets(block: ContentBlock): List<PrefetchTarget> {
    return when (block) {
        is ContentBlock.Image -> {
            val url = block.url.trim()
            if (url.isBlank()) emptyList() else listOf(PrefetchTarget(url, PrefetchType.Image))
        }
        is ContentBlock.Video -> {
            val poster = block.poster?.trim().orEmpty()
            if (poster.isNotBlank()) {
                listOf(PrefetchTarget(poster, PrefetchType.Image))
            } else {
                val url = block.url.trim()
                if (url.isBlank()) emptyList() else listOf(PrefetchTarget(url, PrefetchType.VideoFrame))
            }
        }
        is ContentBlock.Text -> emptyList()
    }
}

internal fun collectPrefetchTargets(
    blockPrefetchTargets: List<List<PrefetchTarget>>,
    startIndex: Int,
    maxIndex: Int,
    maxTargets: Int,
    scanLimit: Int
): List<PrefetchTarget> {
    if (startIndex > maxIndex || blockPrefetchTargets.isEmpty()) return emptyList()
    val result = ArrayList<PrefetchTarget>(maxTargets)
    var blockIndex = startIndex
    var scanned = 0
    while (blockIndex <= maxIndex && result.size < maxTargets && scanned < scanLimit) {
        val targets = blockPrefetchTargets[blockIndex]
        if (targets.isNotEmpty()) {
            for (target in targets) {
                if (result.size >= maxTargets) break
                result.add(target)
            }
        }
        blockIndex++
        scanned++
    }
    return result
}

internal fun isLocalMedia(url: String): Boolean {
    return url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")
}

internal fun canExtractVideoFrame(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    if (isLocalMedia(trimmed)) return true
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
}

private fun cacheVideoAspectRatio(url: String, width: Int, height: Int, rotation: Int?) {
    if (width <= 0 || height <= 0) return
    val rotated = rotation?.let { it % 180 != 0 } ?: false
    val w = if (rotated) height else width
    val h = if (rotated) width else height
    if (w > 0 && h > 0) {
        videoRatioCache.put(url, w.toFloat() / h.toFloat())
    }
}

internal suspend fun loadCachedVideoFrame(
    context: Context,
    url: String,
    maxWidthPx: Int
): Bitmap? {
    val key = "video:$url@$maxWidthPx"
    videoFrameCache.get(key)?.let { return it }
    val frame = loadVideoFrame(context, url, maxWidthPx)
    if (frame != null) {
        cacheVideoAspectRatio(url, frame.width, frame.height, null)
        videoFrameCache.put(key, frame)
    }
    return frame
}

private suspend fun loadVideoFrame(
    context: Context,
    url: String,
    maxWidthPx: Int
): Bitmap? {
    if (url.isBlank()) return null
    return withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            setRetrieverDataSource(retriever, context, url)
            extractVideoAspectRatio(retriever)?.let { videoRatioCache.put(url, it) }
            val dstWidth = maxWidthPx.coerceAtLeast(1)
            val dstHeight = (maxWidthPx * 2).coerceAtLeast(1)
            val params = MediaMetadataRetriever.BitmapParams().apply {
                setPreferredConfig(Bitmap.Config.RGB_565)
            }
            retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                dstWidth,
                dstHeight,
                params
            )
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

internal suspend fun loadCachedVideoRatio(
    context: Context,
    url: String
): Float? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    videoRatioCache.get(trimmed)?.let { return it }
    val ratio = loadVideoMetadataRatio(context, trimmed)
    if (ratio != null) {
        videoRatioCache.put(trimmed, ratio)
    }
    return ratio
}

private suspend fun loadVideoMetadataRatio(
    context: Context,
    url: String
): Float? {
    return withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            setRetrieverDataSource(retriever, context, url)
            extractVideoAspectRatio(retriever)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

private fun setRetrieverDataSource(
    retriever: MediaMetadataRetriever,
    context: Context,
    url: String
) {
    when {
        url.startsWith("file://") -> retriever.setDataSource(url.removePrefix("file://"))
        url.startsWith("/") -> retriever.setDataSource(url)
        url.startsWith("content://") -> retriever.setDataSource(context, url.toUri())
        else -> retriever.setDataSource(url, videoFrameHeaders)
    }
}

private fun extractVideoAspectRatio(retriever: MediaMetadataRetriever): Float? {
    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        ?.toIntOrNull()
    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        ?.toIntOrNull()
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull()
        ?: 0
    return if (rotation % 180 != 0) {
        height.toFloat() / width.toFloat()
    } else {
        width.toFloat() / height.toFloat()
    }
}
