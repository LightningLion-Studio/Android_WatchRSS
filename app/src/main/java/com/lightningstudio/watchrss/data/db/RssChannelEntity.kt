package com.lightningstudio.watchrss.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rss_channels",
    indices = [Index(value = ["url"], unique = true)]
)
data class RssChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val url: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val lastFetchedAt: Long?,
    val createdAt: Long,
    val sortOrder: Long,
    val isPinned: Boolean,
    val useOriginalContent: Boolean = false
)
