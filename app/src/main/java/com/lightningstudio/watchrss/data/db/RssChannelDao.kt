package com.lightningstudio.watchrss.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RssChannelDao {
    @Query("SELECT * FROM rss_channels ORDER BY isPinned DESC, sortOrder DESC, createdAt DESC")
    fun observeChannels(): Flow<List<RssChannelEntity>>

    @Query("SELECT * FROM rss_channels WHERE id = :id")
    fun observeChannel(id: Long): Flow<RssChannelEntity?>

    @Query("SELECT * FROM rss_channels WHERE id = :id LIMIT 1")
    suspend fun getChannel(id: Long): RssChannelEntity?

    @Query("SELECT * FROM rss_channels WHERE url = :url LIMIT 1")
    suspend fun getChannelByUrl(url: String): RssChannelEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChannel(channel: RssChannelEntity): Long

    @Update
    suspend fun updateChannel(channel: RssChannelEntity)

    @Query("DELETE FROM rss_channels WHERE id = :id")
    suspend fun deleteChannel(id: Long)
}
