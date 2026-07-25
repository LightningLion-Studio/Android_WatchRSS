package com.lightningstudio.watchrss.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "saved_sync_states",
    primaryKeys = ["articleId", "saveType"],
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["saveType", "saved"])
    ]
)
data class SavedSyncStateEntity(
    val articleId: String,
    val saveType: String,
    val itemId: Long?,
    val url: String,
    val saved: Boolean,
    val changedAt: Long,
    val sortOrder: Long,
    val sourceDeviceId: String
)
