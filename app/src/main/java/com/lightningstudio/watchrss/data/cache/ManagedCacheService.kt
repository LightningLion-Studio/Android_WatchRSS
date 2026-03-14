package com.lightningstudio.watchrss.data.cache

import android.content.Context
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManager
import com.lightningstudio.watchrss.data.rss.RssOfflineStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class ManagedCacheBucket {
    DOUYIN_PRELOAD,
    RSS_IMAGES,
    BILI_PREVIEW,
    RSS_OFFLINE
}

enum class CacheTrimReason {
    APP_START,
    CACHE_WRITE,
    CACHE_DELETE,
    SETTINGS_CHANGED,
    MANUAL,
    LOGOUT,
    CHANNEL_DELETE
}

internal data class ManagedCacheRoots(
    val douyinPreloadDir: File,
    val rssImagesDir: File,
    val biliPreviewDir: File,
    val rssOfflineDir: File
)

class ManagedCacheService internal constructor(
    private val roots: ManagedCacheRoots,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope
) {
    private val usageBytes = MutableStateFlow(0L)
    private val maintenanceMutex = Mutex()

    @Volatile
    private var maintenanceJob: Job? = null

    constructor(
        context: Context,
        settingsRepository: SettingsRepository,
        appScope: CoroutineScope
    ) : this(
        roots = ManagedCacheRoots(
            douyinPreloadDir = File(context.cacheDir, DouyinPreloadManager.CACHE_DIR_NAME),
            rssImagesDir = File(context.cacheDir, RssImageLoader.DISK_CACHE_DIR_NAME),
            biliPreviewDir = File(context.filesDir, BiliRepository.PREVIEW_CACHE_DIR_NAME),
            rssOfflineDir = File(context.filesDir, RssOfflineStore.OFFLINE_ROOT_DIR_NAME)
        ),
        settingsRepository = settingsRepository,
        appScope = appScope
    )

    init {
        ensureRoots()
    }

    fun observeUsageBytes(): StateFlow<Long> = usageBytes.asStateFlow()

    fun scheduleMaintenance(reason: CacheTrimReason) {
        val existing = maintenanceJob
        if (existing?.isActive == true) return
        maintenanceJob = appScope.launch(Dispatchers.IO) {
            delay(MAINTENANCE_DEBOUNCE_MS)
            trimToLimit(reason)
        }
    }

    suspend fun trimToLimit(reason: CacheTrimReason) {
        withContext(Dispatchers.IO) {
            maintenanceMutex.withLock {
                maintenanceJob = null
                runMaintenanceLocked(reason)
            }
        }
    }

    suspend fun clearBucket(bucket: ManagedCacheBucket) {
        withContext(Dispatchers.IO) {
            maintenanceMutex.withLock {
                when (bucket) {
                    ManagedCacheBucket.DOUYIN_PRELOAD -> resetDirectory(roots.douyinPreloadDir)
                    ManagedCacheBucket.RSS_IMAGES -> resetDirectory(roots.rssImagesDir)
                    ManagedCacheBucket.BILI_PREVIEW -> resetDirectory(roots.biliPreviewDir)
                    ManagedCacheBucket.RSS_OFFLINE -> resetDirectory(roots.rssOfflineDir)
                }
                usageBytes.value = totalUsageBytes(scanAllBucketsLocked())
            }
        }
    }

    suspend fun clearBiliPreviewsForVideo(aid: Long?, bvid: String?) {
        val key = previewKey(aid, bvid) ?: return
        withContext(Dispatchers.IO) {
            maintenanceMutex.withLock {
                roots.biliPreviewDir.listFiles()
                    ?.asSequence()
                    ?.filter { file ->
                        file.isFile &&
                            file.name.startsWith("${key}_") &&
                            file.name.endsWith(".mp4", ignoreCase = true)
                    }
                    ?.forEach { it.delete() }
                usageBytes.value = totalUsageBytes(scanAllBucketsLocked())
            }
        }
    }

    private suspend fun runMaintenanceLocked(reason: CacheTrimReason) {
        var files = scanAllBucketsLocked()
        var total = totalUsageBytes(files)
        val limit = settingsRepository.cacheLimitBytes.first()
        if (total > limit) {
            total = evictBucket(
                files = files,
                bucket = ManagedCacheBucket.DOUYIN_PRELOAD,
                totalBytes = total,
                limitBytes = limit,
                protectedCount = MIN_DOUYIN_CACHE_COUNT
            )
            if (total > limit) {
                files = scanAllBucketsLocked()
                total = totalUsageBytes(files)
                total = evictBucket(
                    files = files,
                    bucket = ManagedCacheBucket.RSS_IMAGES,
                    totalBytes = total,
                    limitBytes = limit
                )
            }
            if (total > limit) {
                files = scanAllBucketsLocked()
                total = totalUsageBytes(files)
                total = evictBucket(
                    files = files,
                    bucket = ManagedCacheBucket.BILI_PREVIEW,
                    totalBytes = total,
                    limitBytes = limit
                )
            }
        }
        if (reason == CacheTrimReason.CACHE_DELETE || reason == CacheTrimReason.LOGOUT || reason == CacheTrimReason.CHANNEL_DELETE) {
            cleanupEmptyDirectories(roots.rssOfflineDir)
        }
        usageBytes.value = totalUsageBytes(scanAllBucketsLocked())
    }

    private fun evictBucket(
        files: List<CacheFileEntry>,
        bucket: ManagedCacheBucket,
        totalBytes: Long,
        limitBytes: Long,
        protectedCount: Int = 0
    ): Long {
        var total = totalBytes
        val bucketFiles = files
            .filter { it.bucket == bucket }
            .sortedBy { it.lastModified }
        val deletable = if (protectedCount > 0 && bucketFiles.size > protectedCount) {
            bucketFiles.take(bucketFiles.size - protectedCount)
        } else if (protectedCount > 0) {
            emptyList()
        } else {
            bucketFiles
        }
        for (entry in deletable) {
            if (total <= limitBytes) break
            if (entry.file.delete()) {
                total -= entry.size
            }
        }
        if (bucket == ManagedCacheBucket.RSS_OFFLINE) {
            cleanupEmptyDirectories(roots.rssOfflineDir)
        }
        return total
    }

    private fun scanAllBucketsLocked(): List<CacheFileEntry> {
        ensureRoots()
        val result = mutableListOf<CacheFileEntry>()
        result += scanFlatDirectory(roots.douyinPreloadDir, ManagedCacheBucket.DOUYIN_PRELOAD)
        result += scanFlatDirectory(roots.rssImagesDir, ManagedCacheBucket.RSS_IMAGES)
        result += scanFlatDirectory(roots.biliPreviewDir, ManagedCacheBucket.BILI_PREVIEW)
        result += scanRecursiveDirectory(roots.rssOfflineDir, ManagedCacheBucket.RSS_OFFLINE)
        cleanupEmptyDirectories(roots.rssOfflineDir)
        return result
    }

    private fun scanFlatDirectory(dir: File, bucket: ManagedCacheBucket): List<CacheFileEntry> {
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.mapNotNull { sanitizeFile(it, bucket) }
            ?.toList()
            .orEmpty()
    }

    private fun scanRecursiveDirectory(dir: File, bucket: ManagedCacheBucket): List<CacheFileEntry> {
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { sanitizeFile(it, bucket) }
            .toList()
    }

    private fun sanitizeFile(file: File, bucket: ManagedCacheBucket): CacheFileEntry? {
        if (file.name.endsWith(".tmp", ignoreCase = true)) {
            file.delete()
            return null
        }
        val size = file.length()
        val minValidSize = if (bucket == ManagedCacheBucket.DOUYIN_PRELOAD) {
            DouyinPreloadManager.MIN_VALID_FILE_BYTES
        } else {
            1L
        }
        if (size < minValidSize) {
            file.delete()
            return null
        }
        return CacheFileEntry(
            bucket = bucket,
            file = file,
            size = size,
            lastModified = file.lastModified()
        )
    }

    private fun resetDirectory(dir: File) {
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        dir.mkdirs()
    }

    private fun ensureRoots() {
        roots.douyinPreloadDir.mkdirs()
        roots.rssImagesDir.mkdirs()
        roots.biliPreviewDir.mkdirs()
        roots.rssOfflineDir.mkdirs()
    }

    private fun cleanupEmptyDirectories(root: File) {
        if (!root.exists()) return
        root.walkBottomUp()
            .filter { it.isDirectory && it != root }
            .forEach { dir ->
                if (dir.listFiles().isNullOrEmpty()) {
                    dir.delete()
                }
            }
    }

    private fun totalUsageBytes(files: List<CacheFileEntry>): Long = files.sumOf { it.size }

    private fun previewKey(aid: Long?, bvid: String?): String? {
        return when {
            !bvid.isNullOrBlank() -> bvid
            aid != null -> "av$aid"
            else -> null
        }
    }

    private data class CacheFileEntry(
        val bucket: ManagedCacheBucket,
        val file: File,
        val size: Long,
        val lastModified: Long
    )

    companion object {
        private const val MAINTENANCE_DEBOUNCE_MS = 300L
        private const val MIN_DOUYIN_CACHE_COUNT = 2
    }
}
