package com.lightningstudio.watchrss.data.rss

import org.jsoup.Jsoup
import java.security.MessageDigest

data class RssPreview(
    val summary: String?,
    val previewImageUrl: String?
)

object RssPreviewFormatter {
    private const val DEFAULT_MAX_CHARS = 60

    fun buildPreview(
        description: String?,
        content: String?,
        imageUrl: String?,
        link: String?
    ): RssPreview {
        val fallbackImage = imageUrl?.trim()?.ifEmpty { null }
        val raw = content?.takeIf { it.isNotBlank() }
            ?: description?.takeIf { it.isNotBlank() }
            ?: return RssPreview(null, fallbackImage)
        val cacheKey = HtmlPreviewCache.keyFor(raw)
        val cached = HtmlPreviewCache.get(cacheKey) ?: run {
            val entry = parseHtml(raw, DEFAULT_MAX_CHARS)
            HtmlPreviewCache.put(cacheKey, entry)
            entry
        }
        val resolvedImage = fallbackImage ?: RssUrlResolver.resolveMediaUrl(
            cached.previewImageUrl,
            link
        )
        return RssPreview(cached.summary, resolvedImage)
    }

    fun formatSummary(raw: String?, maxChars: Int = DEFAULT_MAX_CHARS): String? {
        if (raw.isNullOrBlank()) return null
        val cacheKey = HtmlPreviewCache.keyFor(raw)
        val cached = HtmlPreviewCache.get(cacheKey)
        if (cached != null && cached.summary?.length ?: 0 <= maxChars) {
            return cached.summary
        }
        val entry = parseHtml(raw, maxChars)
        HtmlPreviewCache.put(cacheKey, entry)
        return entry.summary
    }

    private fun parseHtml(raw: String, maxChars: Int): HtmlPreviewEntry {
        val doc = Jsoup.parseBodyFragment(raw)
        doc.outputSettings().prettyPrint(false)
        doc.select("script,style").remove()

        val cleaned = doc.text().replace(Regex("\\s+"), " ").trim()
        val summary = when {
            cleaned.isEmpty() -> null
            cleaned.length <= maxChars -> cleaned
            else -> cleaned.take(maxChars).trimEnd() + "..."
        }

        val image = doc.selectFirst("img[src]")?.attr("src")?.trim()?.ifEmpty { null }
        return HtmlPreviewEntry(summary, image)
    }
}

private data class HtmlPreviewEntry(
    val summary: String?,
    val previewImageUrl: String?
)

private object HtmlPreviewCache {
    private const val MAX_ENTRIES = 200
    private val lock = Any()
    private val cache = LinkedHashMap<String, HtmlPreviewEntry>(MAX_ENTRIES, 0.75f, true)

    fun get(key: String): HtmlPreviewEntry? = synchronized(lock) { cache[key] }

    fun put(key: String, entry: HtmlPreviewEntry) {
        synchronized(lock) {
            cache[key] = entry
            if (cache.size > MAX_ENTRIES) {
                val iterator = cache.entries.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }

    fun keyFor(raw: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            raw.hashCode().toString()
        }
    }
}
