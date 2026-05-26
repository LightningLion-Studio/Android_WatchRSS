package com.lightningstudio.watchrss.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedEntryDao {
    @Query("SELECT * FROM saved_entries WHERE itemId = :itemId")
    fun observeByItemId(itemId: Long): Flow<List<SavedEntryEntity>>

    @Query("SELECT * FROM saved_entries WHERE itemId = :itemId")
    suspend fun getByItemId(itemId: Long): List<SavedEntryEntity>

    @Query("SELECT COUNT(*) FROM saved_entries WHERE itemId = :itemId")
    suspend fun countByItemId(itemId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: SavedEntryEntity): Long

    @Query("DELETE FROM saved_entries WHERE itemId = :itemId AND saveType = :saveType")
    suspend fun delete(itemId: Long, saveType: String)

    @Query(
        "UPDATE saved_entries SET sortOrder = :sortOrder WHERE itemId = :itemId AND saveType = :saveType"
    )
    suspend fun updateSortOrder(itemId: Long, saveType: String, sortOrder: Long)

    @Transaction
    suspend fun reorderSavedItems(saveType: String, orderedItemIds: List<Long>) {
        val baseOrder = System.currentTimeMillis() + orderedItemIds.size
        orderedItemIds.forEachIndexed { index, itemId ->
            updateSortOrder(
                itemId = itemId,
                saveType = saveType,
                sortOrder = baseOrder - index
            )
        }
    }

    @Query(
        """
        SELECT
               rss_items.id AS id,
               rss_items.channelId AS channelId,
               rss_items.title AS title,
               rss_items.description AS description,
               NULL AS content,
               NULL AS originalContent,
               rss_items.link AS link,
               rss_items.guid AS guid,
               rss_items.pubDate AS pubDate,
               rss_items.imageUrl AS imageUrl,
               rss_items.audioUrl AS audioUrl,
               rss_items.videoUrl AS videoUrl,
               rss_items.summary AS summary,
               rss_items.previewImageUrl AS previewImageUrl,
               rss_items.isRead AS isRead,
               rss_items.isLiked AS isLiked,
               rss_items.readingProgress AS readingProgress,
               rss_items.dedupKey AS dedupKey,
               rss_items.fetchedAt AS fetchedAt,
               rss_items.contentSizeBytes AS contentSizeBytes,
               rss_channels.title AS channelTitle,
               rss_channels.url AS channelUrl,
               saved_entries.createdAt AS savedAt,
               saved_entries.saveType AS saveType
        FROM saved_entries
        JOIN rss_items ON rss_items.id = saved_entries.itemId
        JOIN rss_channels ON rss_channels.id = rss_items.channelId
        WHERE saved_entries.saveType = :saveType
        ORDER BY saved_entries.sortOrder DESC, saved_entries.createdAt DESC
        """
    )
    fun observeSavedItems(saveType: String): Flow<List<SavedRssItem>>

    @Query(
        """
        SELECT rss_items.*, rss_channels.title AS channelTitle, rss_channels.url AS channelUrl,
               saved_entries.createdAt AS savedAt, saved_entries.saveType AS saveType
        FROM saved_entries
        JOIN rss_items ON rss_items.id = saved_entries.itemId
        JOIN rss_channels ON rss_channels.id = rss_items.channelId
        WHERE saved_entries.saveType = :saveType
        ORDER BY saved_entries.sortOrder DESC, saved_entries.createdAt DESC
        """
    )
    suspend fun getSavedItems(saveType: String): List<SavedRssItem>
}

data class SavedRssItem(
    @Embedded val item: RssItemEntity,
    val channelTitle: String,
    val channelUrl: String,
    val savedAt: Long,
    val saveType: String
)
