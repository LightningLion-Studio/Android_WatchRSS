package com.lightningstudio.watchrss.data.rss

import com.lightningstudio.watchrss.data.db.RssItemEntity
import java.security.MessageDigest

internal data class PreviewUpdatePayload(
    val summary: String?,
    val previewImageUrl: String?
)

internal object RssPreviewUpdatePlanner {
    fun needsPreviewUpdate(item: RssItemEntity): Boolean {
        val missingSummary = item.summary.isNullOrBlank()
        val missingPreview = item.previewImageUrl.isNullOrBlank() && item.imageUrl.isNullOrBlank()
        return missingSummary || missingPreview
    }

    fun attemptKeyFor(item: RssItemEntity): String {
        val source = listOf(
            normalize(item.description).orEmpty(),
            normalize(item.content).orEmpty(),
            normalize(item.imageUrl).orEmpty(),
            normalize(item.link).orEmpty(),
            "summaryMissing=${item.summary.isNullOrBlank()}",
            "previewMissing=${item.previewImageUrl.isNullOrBlank() && item.imageUrl.isNullOrBlank()}"
        ).joinToString(separator = "\u0001")
        return sha256(source)
    }

    fun buildWritePayload(item: RssItemEntity, preview: RssPreview): PreviewUpdatePayload? {
        val currentSummary = normalize(item.summary)
        val currentPreview = normalize(item.previewImageUrl)
        val previewSummary = normalize(preview.summary)
        val previewImage = normalize(preview.previewImageUrl)

        val summaryToWrite = if (currentSummary == null) previewSummary else null
        val previewToWrite = if (currentPreview == null) previewImage else null

        val effectiveSummary = summaryToWrite ?: currentSummary
        val effectivePreview = previewToWrite ?: currentPreview
        if (effectiveSummary == currentSummary && effectivePreview == currentPreview) {
            return null
        }

        return PreviewUpdatePayload(
            summary = summaryToWrite,
            previewImageUrl = previewToWrite
        )
    }

    private fun normalize(value: String?): String? = value?.trim()?.ifEmpty { null }

    private fun sha256(raw: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            raw.hashCode().toString()
        }
    }
}
