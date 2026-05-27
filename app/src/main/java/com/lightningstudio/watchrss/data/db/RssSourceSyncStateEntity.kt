package com.lightningstudio.watchrss.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rss_source_sync_states")
data class RssSourceSyncStateEntity(
    @PrimaryKey val url: String,
    val sourceDeviceId: String,
    val title: String,
    val description: String,
    val siteUrl: String?,
    val imageUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Long,
    val isPinned: Boolean,
    val deleted: Boolean,
    val deletedAt: Long
)
