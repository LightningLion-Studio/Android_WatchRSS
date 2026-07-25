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
        val source = buildString {
            appendPart(attemptFingerprint(item.description))
            appendPart(attemptFingerprint(item.originalContent))
            appendPart(attemptFingerprint(item.content))
            appendPart(normalize(item.imageUrl))
            appendPart(normalize(item.link))
            appendPart("summaryMissing=${item.summary.isNullOrBlank()}")
            appendPart("previewMissing=${item.previewImageUrl.isNullOrBlank() && item.imageUrl.isNullOrBlank()}")
        }
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

    private fun StringBuilder.appendPart(value: String?) {
        append(value.orEmpty())
        append('\u0001')
    }

    private fun attemptFingerprint(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (value.length <= ATTEMPT_INLINE_CHARS) return normalize(value)
        return "len=${value.length};hash=${value.hashCode()}"
    }

    private fun normalize(value: String?): String? {
        val raw = value ?: return null
        if (raw.isBlank()) return null
        return if (raw.length <= SAFE_TRIM_COPY_LIMIT_CHARS) {
            raw.trim()
        } else {
            raw
        }
    }

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

private const val ATTEMPT_INLINE_CHARS = 4_096
private const val SAFE_TRIM_COPY_LIMIT_CHARS = 16_384
