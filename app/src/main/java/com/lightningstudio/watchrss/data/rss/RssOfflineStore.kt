package com.lightningstudio.watchrss.data.rss

import com.lightningstudio.watchrss.data.cache.CacheTrimReason
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.db.OfflineMediaDao
import com.lightningstudio.watchrss.data.db.OfflineMediaEntity
import com.lightningstudio.watchrss.data.db.RssItemEntity
import com.lightningstudio.watchrss.debug.DebugLogBuffer
import java.io.File

class RssOfflineStore(
    private val offlineRoot: File,
    private val offlineMediaDao: OfflineMediaDao,
    private val downloadClient: RssDownloadClient,
    private val cacheService: ManagedCacheService? = null
) {
    constructor(
        appContext: android.content.Context,
        offlineMediaDao: OfflineMediaDao,
        downloadClient: RssDownloadClient,
        cacheService: ManagedCacheService? = null
    ) : this(
        File(appContext.filesDir, OFFLINE_ROOT_DIR_NAME),
        offlineMediaDao,
        downloadClient,
        cacheService
    )

    init {
        if (!offlineRoot.exists()) {
            offlineRoot.mkdirs()
        }
    }

    suspend fun downloadMediaForItem(item: RssItemEntity) {
        val refs = RssMediaExtractor.extract(item.toModel())
            .filterNot { it.url.startsWith("data:", ignoreCase = true) }
        if (refs.isEmpty()) return
        val itemDir = File(offlineRoot, item.id.toString()).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val entities = mutableListOf<OfflineMediaEntity>()
        refs.forEachIndexed { index, ref ->
            val exists = offlineMediaDao.findByOrigin(item.id, ref.url)
            if (exists != null) {
                if (!exists.localPath.isNullOrBlank()) return@forEachIndexed
                val file = File(itemDir, buildFileName(ref.type, index, ref.url))
                val localPath = downloadClient.downloadToFile(ref.url, file)
                if (localPath == null) {
                    DebugLogBuffer.log(
                        "offline",
                        "retry failed item=${item.id} url=${ref.url}"
                    )
                } else {
                    offlineMediaDao.updateLocalPath(item.id, ref.url, localPath)
                    cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_WRITE)
                    DebugLogBuffer.log(
                        "offline",
                        "retry ok item=${item.id} url=${ref.url}"
                    )
                }
                return@forEachIndexed
            }
            val file = File(itemDir, buildFileName(ref.type, index, ref.url))
            val localPath = downloadClient.downloadToFile(ref.url, file)
            if (localPath == null) {
                runCatching { file.delete() }
                DebugLogBuffer.log(
                    "offline",
                    "download failed item=${item.id} url=${ref.url}"
                )
            } else {
                cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_WRITE)
            }
            entities.add(
                OfflineMediaEntity(
                    itemId = item.id,
                    mediaType = ref.type.name,
                    originUrl = ref.url,
                    localPath = localPath,
                    createdAt = now
                )
            )
        }
        if (entities.isNotEmpty()) {
            offlineMediaDao.insertAll(entities)
        }
    }

    suspend fun deleteMediaForItem(itemId: Long) {
        val entries = offlineMediaDao.getByItemId(itemId)
        entries.forEach { entry ->
            val path = entry.localPath ?: return@forEach
            runCatching { File(path).delete() }
        }
        offlineMediaDao.deleteByItemId(itemId)
        cleanupEmptyDirectories(offlineRoot)
        cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_DELETE)
    }

    suspend fun deleteMediaForChannel(channelId: Long) {
        val entries = offlineMediaDao.getByChannelId(channelId)
        entries.forEach { entry ->
            val path = entry.localPath ?: return@forEach
            runCatching { File(path).delete() }
        }
        cleanupEmptyDirectories(offlineRoot)
        cacheService?.scheduleMaintenance(CacheTrimReason.CACHE_DELETE)
    }

    private fun buildFileName(type: OfflineMediaType, index: Int, url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val extension = clean.substringAfterLast('.', "")
        val suffix = if (extension.isNotEmpty() && extension.length <= 6) {
            ".$extension"
        } else {
            if (type == OfflineMediaType.VIDEO) ".mp4" else ".jpg"
        }
        return "${type.name.lowercase()}_${index}$suffix"
    }

    private fun RssItemEntity.toModel(): RssItem = RssItem(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        content = content,
        link = link,
        pubDate = pubDate,
        imageUrl = imageUrl,
        audioUrl = audioUrl,
        videoUrl = videoUrl,
        summary = summary,
        previewImageUrl = previewImageUrl,
        isRead = isRead,
        isLiked = isLiked,
        readingProgress = readingProgress,
        fetchedAt = fetchedAt
    )

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

    companion object {
        internal const val OFFLINE_ROOT_DIR_NAME = "offline/rss"
    }
}
