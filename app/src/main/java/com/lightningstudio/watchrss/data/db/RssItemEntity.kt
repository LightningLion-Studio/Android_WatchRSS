package com.lightningstudio.watchrss.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rss_items",
    foreignKeys = [
        ForeignKey(
            entity = RssChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["channelId", "dedupKey"], unique = true),
        Index(value = ["channelId", "fetchedAt"])
    ]
)
data class RssItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val channelId: Long,
    val title: String,
    val description: String?,
    val content: String?,
    val link: String?,
    val guid: String?,
    val pubDate: String?,
    val imageUrl: String?,
    val audioUrl: String?,
    val videoUrl: String?,
    val summary: String?,
    val previewImageUrl: String?,
    val isRead: Boolean,
    val isLiked: Boolean,
    val readingProgress: Float,
    val dedupKey: String,
    val fetchedAt: Long,
    val contentSizeBytes: Long
)
