package com.lightningstudio.watchrss.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_entries",
    foreignKeys = [
        ForeignKey(
            entity = RssItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["itemId", "saveType"], unique = true)
    ]
)
data class SavedEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val itemId: Long,
    val saveType: String,
    val createdAt: Long
)
