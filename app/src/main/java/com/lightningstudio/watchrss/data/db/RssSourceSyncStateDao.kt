package com.lightningstudio.watchrss.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RssSourceSyncStateDao {
    @Query("SELECT * FROM rss_source_sync_states WHERE url = :url LIMIT 1")
    suspend fun get(url: String): RssSourceSyncStateEntity?

    @Query("SELECT * FROM rss_source_sync_states")
    suspend fun getAll(): List<RssSourceSyncStateEntity>

    @Query("SELECT * FROM rss_source_sync_states WHERE url IN (:urls)")
    suspend fun getByUrls(urls: List<String>): List<RssSourceSyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: RssSourceSyncStateEntity)
}
