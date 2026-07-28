package com.lightningstudio.watchrss.data.llm

import android.util.Log
import com.lightningstudio.watchrss.data.db.LlmTokenUsageDao
import com.lightningstudio.watchrss.data.db.LlmTokenUsageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class LlmTokenUsageRepository(
    private val dao: LlmTokenUsageDao
) {
    private companion object {
        const val TAG = "LlmTokenUsageRepository"
    }

    data class NormalizedTokens(
        val promptTokens: Int?,
        val completionTokens: Int?,
        val totalTokens: Int?,
        val reasoningTokens: Int?,
        val cachedPromptTokens: Int?,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val promptTokenCount: Int?,
        val candidatesTokenCount: Int?,
        val totalTokenCount: Int?
    )

    fun observeRecent(limit: Int = 100): Flow<List<LlmTokenUsageEntity>> =
        dao.observeRecent(limit)

    fun observeStatistics(provider: String? = null): Flow<com.lightningstudio.watchrss.data.db.LlmTokenUsageStatisticsPojo> =
        dao.observeStatistics(provider)

    fun observeByProvider(): Flow<List<com.lightningstudio.watchrss.data.db.LlmTokenUsageByProviderPojo>> =
        dao.observeByProvider()

    fun observeDaily(sinceDays: Int = 7): Flow<List<com.lightningstudio.watchrss.data.db.LlmTokenUsageDailyPojo>> {
        val bucketMs = 24 * 60 * 60 * 1000L
        val since = System.currentTimeMillis() - sinceDays * bucketMs
        return dao.observeDaily(since, bucketMs)
    }

    suspend fun getRecent(limit: Int = 200): List<LlmTokenUsageEntity> = withContext(Dispatchers.IO) {
        dao.observeRecent(limit).first()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    suspend fun record(
        provider: String,
        model: String,
        requestId: String? = null,
        rawUsage: JSONObject? = null
    ) = withContext(Dispatchers.IO) {
        val normalized = rawUsage?.let { normalize(it) }
        val entity = LlmTokenUsageEntity(
            provider = provider,
            model = model,
            requestId = requestId ?: UUID.randomUUID().toString(),
            promptTokens = normalized?.promptTokens,
            completionTokens = normalized?.completionTokens,
            totalTokens = normalized?.totalTokens,
            reasoningTokens = normalized?.reasoningTokens,
            cachedPromptTokens = normalized?.cachedPromptTokens,
            inputTokens = normalized?.inputTokens,
            outputTokens = normalized?.outputTokens,
            promptTokenCount = normalized?.promptTokenCount,
            candidatesTokenCount = normalized?.candidatesTokenCount,
            totalTokenCount = normalized?.totalTokenCount
        )
        try {
            dao.insert(entity)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record token usage: ${e.message}")
        }
    }

    fun normalize(usage: JSONObject): NormalizedTokens {
        val promptTokens = usage.optInt("prompt_tokens", -1).takeIf { it >= 0 }
        val completionTokens = usage.optInt("completion_tokens", -1).takeIf { it >= 0 }
        val totalTokens = usage.optInt("total_tokens", -1).takeIf { it >= 0 }
        val reasoningTokens = usage.optJSONObject("completion_tokens_details")
            ?.optInt("reasoning_tokens", -1)?.takeIf { it >= 0 }
            ?: usage.optInt("reasoning_tokens", -1).takeIf { it >= 0 }
        val cachedPromptTokens = usage.optJSONObject("prompt_tokens_details")
            ?.optInt("cached_tokens", -1)?.takeIf { it >= 0 }
        val inputTokens = usage.optInt("input_tokens", -1).takeIf { it >= 0 }
        val outputTokens = usage.optInt("output_tokens", -1).takeIf { it >= 0 }
        val promptTokenCount = usage.optInt("promptTokenCount", -1).takeIf { it >= 0 }
        val candidatesTokenCount = usage.optInt("candidatesTokenCount", -1).takeIf { it >= 0 }
        val totalTokenCount = usage.optInt("totalTokenCount", -1).takeIf { it >= 0 }
        return NormalizedTokens(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            reasoningTokens = reasoningTokens,
            cachedPromptTokens = cachedPromptTokens,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            promptTokenCount = promptTokenCount,
            candidatesTokenCount = candidatesTokenCount,
            totalTokenCount = totalTokenCount
        )
    }
}
