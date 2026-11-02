package com.lightningstudio.watchrss.data.db

import androidx.room.Embedded
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RssChannelDao {
    @Query("""
        SELECT c.*, COUNT(i.id) AS unreadCount
        FROM rss_channels c LEFT JOIN rss_items i ON i.channelId = c.id AND i.isRead = 0
        GROUP BY c.id ORDER BY c.isPinned DESC, c.sortOrder DESC, c.createdAt DESC
    """)
    fun observeChannelsWithUnreadCounts(): Flow<List<RssChannelWithUnreadCount>>

    @Query("""
        SELECT c.*, COUNT(i.id) AS unreadCount
        FROM rss_channels c LEFT JOIN rss_items i ON i.channelId = c.id AND i.isRead = 0
        WHERE c.id = :id GROUP BY c.id
    """)
    fun observeChannelWithUnreadCount(id: Long): Flow<RssChannelWithUnreadCount?>

    @Query("SELECT * FROM rss_channels ORDER BY isPinned DESC, sortOrder DESC, createdAt DESC")
    fun observeChannels(): Flow<List<RssChannelEntity>>

    @Query("SELECT * FROM rss_channels WHERE id = :id")
    fun observeChannel(id: Long): Flow<RssChannelEntity?>

    @Query("SELECT * FROM rss_channels WHERE id = :id LIMIT 1")
    suspend fun getChannel(id: Long): RssChannelEntity?

    @Query("SELECT * FROM rss_channels WHERE url = :url LIMIT 1")
    suspend fun getChannelByUrl(url: String): RssChannelEntity?

    @Query("SELECT * FROM rss_channels ORDER BY isPinned DESC, sortOrder DESC, createdAt DESC")
    suspend fun getAllChannels(): List<RssChannelEntity>

    @Query("SELECT * FROM rss_channels WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun getCloudExportPage(afterId: Long, limit: Int): List<RssChannelEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChannel(channel: RssChannelEntity): Long

    @Update
    suspend fun updateChannel(channel: RssChannelEntity)

    @Query("DELETE FROM rss_channels WHERE id = :id")
    suspend fun deleteChannel(id: Long)
}

data class RssChannelWithUnreadCount(
    @Embedded val channel: RssChannelEntity,
    val unreadCount: Int
)
