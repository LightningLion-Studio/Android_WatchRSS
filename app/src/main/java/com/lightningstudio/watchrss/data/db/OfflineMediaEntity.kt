package com.lightningstudio.watchrss.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_media",
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
        Index(value = ["itemId", "originUrl"], unique = true)
    ]
)
data class OfflineMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val itemId: Long,
    val mediaType: String,
    val originUrl: String,
    val localPath: String?,
    val createdAt: Long
)
