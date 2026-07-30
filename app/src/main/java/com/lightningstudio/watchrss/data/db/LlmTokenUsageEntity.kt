package com.lightningstudio.watchrss.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "llm_token_usage",
    indices = [
        Index(value = ["provider", "createdAt"]),
        Index(value = ["requestId"], unique = true)
    ]
)
data class LlmTokenUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val provider: String,
    val model: String,
    val requestId: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val reasoningTokens: Int?,
    val cachedPromptTokens: Int?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val promptTokenCount: Int?,
    val candidatesTokenCount: Int?,
    val totalTokenCount: Int?,
    val createdAt: Long = System.currentTimeMillis()
)
