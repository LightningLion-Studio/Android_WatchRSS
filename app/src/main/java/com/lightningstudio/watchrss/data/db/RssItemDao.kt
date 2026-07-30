package com.lightningstudio.watchrss.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RssItemDao {
    @Query(
        """
        SELECT
            id,
            channelId,
            title,
            description,
            NULL AS content,
            NULL AS originalContent,
            link,
            guid,
            pubDate,
            imageUrl,
            audioUrl,
            videoUrl,
            summary,
            previewImageUrl,
            isRead,
            isLiked,
            readingProgress,
            dedupKey,
            fetchedAt,
            contentSizeBytes,
            syncBodyHash,
            syncBodyByteCount,
            syncChunkSize,
            syncChunkHashesJson,
            syncMetadataHash
        FROM rss_items
        WHERE channelId = :channelId
        ORDER BY fetchedAt DESC, id DESC
        LIMIT :limit
        """
    )
    fun observeItemsPaged(channelId: Long, limit: Int): Flow<List<RssItemEntity>>

    @Query("SELECT * FROM rss_items WHERE id = :id")
    fun observeItem(id: Long): Flow<RssItemEntity?>

    @Query("SELECT * FROM rss_items WHERE id = :id LIMIT 1")
    suspend fun getItem(id: Long): RssItemEntity?

    @Query("SELECT * FROM rss_items WHERE channelId = :channelId AND dedupKey = :dedupKey LIMIT 1")
    suspend fun getItemByDedupKey(channelId: Long, dedupKey: String): RssItemEntity?

    @Query(
        """
        SELECT * FROM rss_items
        WHERE channelId = :channelId
        ORDER BY fetchedAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getItemsForChannelSync(channelId: Long, limit: Int): List<RssItemEntity>

    @Query(
        """
        SELECT
            id,
            channelId,
            title,
            description,
            NULL AS content,
            NULL AS originalContent,
            link,
            guid,
            pubDate,
            imageUrl,
            audioUrl,
            videoUrl,
            summary,
            previewImageUrl,
            isRead,
            isLiked,
            readingProgress,
            dedupKey,
            fetchedAt,
            contentSizeBytes,
            syncBodyHash,
            syncBodyByteCount,
            syncChunkSize,
            syncChunkHashesJson,
            syncMetadataHash
        FROM rss_items
        WHERE channelId = :channelId
        ORDER BY fetchedAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getItemsForChannelSyncManifest(channelId: Long, limit: Int): List<RssItemEntity>

    @Query(
        """
        SELECT
            id,
            channelId,
            title,
            description,
            NULL AS content,
            NULL AS originalContent,
            link,
            guid,
            pubDate,
            imageUrl,
            audioUrl,
            videoUrl,
            summary,
            previewImageUrl,
            isRead,
            isLiked,
            readingProgress,
            dedupKey,
            fetchedAt,
            contentSizeBytes,
            syncBodyHash,
            syncBodyByteCount,
            syncChunkSize,
            syncChunkHashesJson,
            syncMetadataHash
        FROM rss_items
        WHERE channelId = :channelId AND (
            title LIKE :keyword ESCAPE '\' OR
            description LIKE :keyword ESCAPE '\' OR
            content LIKE :keyword ESCAPE '\' OR
            originalContent LIKE :keyword ESCAPE '\' OR
            summary LIKE :keyword ESCAPE '\'
        )
        ORDER BY fetchedAt DESC, id DESC
        LIMIT :limit
        """
    )
    fun searchItems(channelId: Long, keyword: String, limit: Int): Flow<List<RssItemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<RssItemEntity>): List<Long>

    @Query("UPDATE rss_items SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE rss_items SET isRead = 1 WHERE channelId = :channelId")
    suspend fun markReadByChannel(channelId: Long)

    @Query("UPDATE rss_items SET isLiked = :liked WHERE id = :id")
    suspend fun updateLiked(id: Long, liked: Boolean)

    @Query(
        """
        UPDATE rss_items
        SET readingProgress = :progress,
            readingPositionBytes = :positionBytes,
            readingPositionContentHash = :positionContentHash,
            readingPositionChangedAt = :positionChangedAt
        WHERE id = :id
        """
    )
    suspend fun updateReadingProgress(
        id: Long,
        progress: Float,
        positionBytes: Long,
        positionContentHash: String,
        positionChangedAt: Long
    )

    @Query("DELETE FROM rss_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query(
        """
        UPDATE rss_items SET
            description = :description,
            content = :content,
            originalContent = NULL,
            imageUrl = :imageUrl,
            audioUrl = :audioUrl,
            videoUrl = :videoUrl,
            summary = :summary,
            previewImageUrl = :previewImageUrl,
            contentSizeBytes = :contentSizeBytes,
            syncBodyHash = '',
            syncBodyByteCount = 0,
            syncChunkSize = 0,
            syncChunkHashesJson = '',
            syncMetadataHash = ''
        WHERE channelId = :channelId AND dedupKey = :dedupKey
        """
    )
    suspend fun updateContentByDedupKey(
        channelId: Long,
        dedupKey: String,
        description: String?,
        content: String?,
        imageUrl: String?,
        audioUrl: String?,
        videoUrl: String?,
        summary: String?,
        previewImageUrl: String?,
        contentSizeBytes: Long
    )

    @Query(
        """
        UPDATE rss_items SET
            summary = CASE
                WHEN summary IS NULL OR summary = '' THEN :summary
                ELSE summary
            END,
            previewImageUrl = CASE
                WHEN previewImageUrl IS NULL OR previewImageUrl = '' THEN :previewImageUrl
                ELSE previewImageUrl
            END
        WHERE id = :id
        """
    )
    suspend fun updatePreview(id: Long, summary: String?, previewImageUrl: String?)

    @Query(
        """
        UPDATE rss_items SET
            originalContent = :content,
            contentSizeBytes = :contentSizeBytes,
            syncBodyHash = '',
            syncBodyByteCount = 0,
            syncChunkSize = 0,
            syncChunkHashesJson = '',
            syncMetadataHash = ''
        WHERE channelId = :channelId AND dedupKey = :dedupKey
        """
    )
    suspend fun updateOriginalContentByDedupKey(
        channelId: Long,
        dedupKey: String,
        content: String?,
        contentSizeBytes: Long
    )

    @Query(
        """
        UPDATE rss_items SET
            title = :title,
            description = :description,
            content = :content,
            originalContent = :originalContent,
            link = :link,
            imageUrl = :imageUrl,
            summary = :summary,
            previewImageUrl = :previewImageUrl,
            fetchedAt = :fetchedAt,
            contentSizeBytes = :contentSizeBytes,
            syncBodyHash = :syncBodyHash,
            syncBodyByteCount = :syncBodyByteCount,
            syncChunkSize = :syncChunkSize,
            syncChunkHashesJson = :syncChunkHashesJson,
            syncMetadataHash = :syncMetadataHash,
            readingProgress = :readingProgress,
            isRead = :isRead,
            readingPositionBytes = :readingPositionBytes,
            readingPositionContentHash = :readingPositionContentHash,
            readingPositionChangedAt = :readingPositionChangedAt
        WHERE id = :id
        """
    )
    suspend fun updateSyncedArticle(
        id: Long,
        title: String,
        description: String?,
        content: String?,
        originalContent: String?,
        link: String?,
        imageUrl: String?,
        summary: String?,
        previewImageUrl: String?,
        fetchedAt: Long,
        contentSizeBytes: Long,
        syncBodyHash: String,
        syncBodyByteCount: Long,
        syncChunkSize: Int,
        syncChunkHashesJson: String,
        syncMetadataHash: String,
        readingProgress: Float,
        isRead: Boolean,
        readingPositionBytes: Long,
        readingPositionContentHash: String,
        readingPositionChangedAt: Long
    )

    @Query(
        """
        UPDATE rss_items SET
            syncBodyHash = :syncBodyHash,
            syncBodyByteCount = :syncBodyByteCount,
            syncChunkSize = :syncChunkSize,
            syncChunkHashesJson = :syncChunkHashesJson,
            syncMetadataHash = :syncMetadataHash
        WHERE id = :id
        """
    )
    suspend fun updateSyncMetadata(
        id: Long,
        syncBodyHash: String,
        syncBodyByteCount: Long,
        syncChunkSize: Int,
        syncChunkHashesJson: String,
        syncMetadataHash: String
    )

    @Query("SELECT COUNT(*) FROM rss_items WHERE channelId = :channelId")
    fun observeItemCount(channelId: Long): Flow<Int>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM rss_items
            WHERE channelId = :channelId AND (
                (audioUrl IS NOT NULL AND audioUrl != '') OR
                (videoUrl IS NOT NULL AND videoUrl != '')
            )
        )
        """
    )
    fun observeChannelHasPlayableMedia(channelId: Long): Flow<Boolean>

    @Query("SELECT channelId, COUNT(*) as unreadCount FROM rss_items WHERE isRead = 0 GROUP BY channelId")
    fun observeUnreadCounts(): Flow<List<RssChannelUnreadCount>>

    @Query("SELECT COUNT(*) FROM rss_items WHERE channelId = :channelId AND isRead = 0")
    fun observeUnreadCount(channelId: Long): Flow<Int>

    @Query("SELECT SUM(contentSizeBytes) FROM rss_items")
    fun observeTotalCacheBytes(): Flow<Long?>

    @Query("SELECT SUM(contentSizeBytes) FROM rss_items")
    suspend fun getTotalCacheBytes(): Long?

    @Query("SELECT id, contentSizeBytes FROM rss_items ORDER BY fetchedAt ASC, id ASC")
    suspend fun loadOldestItems(): List<RssItemSize>

    @Query(
        """
        SELECT id, contentSizeBytes FROM rss_items
        WHERE id NOT IN (SELECT itemId FROM saved_entries)
        ORDER BY fetchedAt ASC, id ASC
        """
    )
    suspend fun loadOldestItemsExcludingSaved(): List<RssItemSize>

    @Query("DELETE FROM rss_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM rss_items WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: Long)
}

data class RssItemSize(
    val id: Long,
    val contentSizeBytes: Long
)

data class RssChannelUnreadCount(
    val channelId: Long,
    val unreadCount: Int
)
