package com.lightningstudio.watchrss.data.note

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchNoteDao {
    @Query("SELECT * FROM watch_notes WHERE deleted = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeNotes(): Flow<List<WatchNoteEntity>>

    @Query("SELECT * FROM watch_notes WHERE noteId = :noteId LIMIT 1")
    suspend fun get(noteId: String): WatchNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notes: List<WatchNoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolders(folders: List<WatchNoteFolderEntity>)
}
