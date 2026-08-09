package com.lightningstudio.watchrss.data.note

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watch_notes", indices = [Index(value = ["folderId", "deleted", "pinned", "updatedAt"]), Index(value = ["contentHash"])])
data class WatchNoteEntity(
    @PrimaryKey val noteId: String,
    val folderId: String?,
    val title: String,
    val markdown: String,
    /** Read/search projection only. Editing always uses [markdown] verbatim. */
    val plainText: String,
    val contentHash: String,
    val baseContentHash: String,
    val baseMarkdown: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val modifiedBy: String,
    val deleted: Boolean = false,
    val deletedAt: Long = 0L
)

@Entity(tableName = "watch_note_folders", indices = [Index(value = ["deleted", "sortOrder"])])
data class WatchNoteFolderEntity(
    @PrimaryKey val folderId: String,
    val name: String,
    val sortOrder: Long,
    val updatedAt: Long,
    val modifiedBy: String,
    val deleted: Boolean = false,
    val deletedAt: Long = 0L
)
