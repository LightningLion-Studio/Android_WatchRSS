package com.lightningstudio.watchrss.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineMediaDao {
    @Query("SELECT * FROM offline_media WHERE itemId = :itemId")
    fun observeByItemId(itemId: Long): Flow<List<OfflineMediaEntity>>

    @Query("SELECT * FROM offline_media WHERE itemId = :itemId")
    suspend fun getByItemId(itemId: Long): List<OfflineMediaEntity>

    @Query(
        """
        SELECT offline_media.* FROM offline_media
        JOIN rss_items ON rss_items.id = offline_media.itemId
        WHERE rss_items.channelId = :channelId
        """
    )
    suspend fun getByChannelId(channelId: Long): List<OfflineMediaEntity>

    @Query("SELECT * FROM offline_media WHERE itemId = :itemId AND originUrl = :originUrl LIMIT 1")
    suspend fun findByOrigin(itemId: Long, originUrl: String): OfflineMediaEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<OfflineMediaEntity>)

    @Query(
        """
        UPDATE offline_media
        SET localPath = :localPath
        WHERE itemId = :itemId AND originUrl = :originUrl
        """
    )
    suspend fun updateLocalPath(itemId: Long, originUrl: String, localPath: String?)

    @Query("DELETE FROM offline_media WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: Long)
}
