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
        SELECT rss_items.*, rss_channels.title AS channelTitle,
               saved_entries.createdAt AS savedAt, saved_entries.saveType AS saveType
        FROM saved_entries
        JOIN rss_items ON rss_items.id = saved_entries.itemId
        JOIN rss_channels ON rss_channels.id = rss_items.channelId
        WHERE saved_entries.saveType = :saveType
        ORDER BY saved_entries.sortOrder DESC, saved_entries.createdAt DESC
        """
    )
    fun observeSavedItems(saveType: String): Flow<List<SavedRssItem>>
}

data class SavedRssItem(
    @Embedded val item: RssItemEntity,
    val channelTitle: String,
    val savedAt: Long,
    val saveType: String
)
