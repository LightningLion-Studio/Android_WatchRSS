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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SavedSyncStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<SavedSyncStateEntity>)
}
