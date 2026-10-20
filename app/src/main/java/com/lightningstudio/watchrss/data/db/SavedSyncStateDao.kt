package com.lightningstudio.watchrss.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SavedSyncStateDao {
    @Query("SELECT * FROM saved_sync_states")
    suspend fun getAll(): List<SavedSyncStateEntity>

    @Query("SELECT * FROM saved_sync_states WHERE articleId = :articleId AND saveType = :saveType LIMIT 1")
    suspend fun get(articleId: String, saveType: String): SavedSyncStateEntity?

    @Query("SELECT DISTINCT articleId FROM saved_sync_states WHERE articleId > :afterId ORDER BY articleId LIMIT :limit")
    suspend fun getCloudExportArticleIds(afterId: String, limit: Int): List<String>

    @Query("SELECT * FROM saved_sync_states WHERE articleId = :articleId")
    suspend fun getCloudExportStates(articleId: String): List<SavedSyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SavedSyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<SavedSyncStateEntity>)

    @Query("DELETE FROM saved_sync_states WHERE articleId IN (:articleIds)")
    suspend fun deleteByArticleIds(articleIds: List<String>)

    @Query("DELETE FROM saved_sync_states")
    suspend fun deleteAll()
}
