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
        SELECT * FROM rss_items
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
        WHERE channelId = :channelId AND (
            title LIKE :keyword ESCAPE '\' OR
            description LIKE :keyword ESCAPE '\' OR
            content LIKE :keyword ESCAPE '\' OR
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

    @Query("UPDATE rss_items SET readingProgress = :progress WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, progress: Float)

    @Query(
        """
        UPDATE rss_items SET
            description = :description,
            content = :content,
            imageUrl = :imageUrl,
            audioUrl = :audioUrl,
            videoUrl = :videoUrl,
            summary = :summary,
            previewImageUrl = :previewImageUrl,
            contentSizeBytes = :contentSizeBytes
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
            content = :content,
            contentSizeBytes = :contentSizeBytes
        WHERE channelId = :channelId AND dedupKey = :dedupKey
        """
    )
    suspend fun updateOriginalContentByDedupKey(
        channelId: Long,
        dedupKey: String,
        content: String?,
        contentSizeBytes: Long
    )

    @Query("SELECT COUNT(*) FROM rss_items WHERE channelId = :channelId")
    fun observeItemCount(channelId: Long): Flow<Int>

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
