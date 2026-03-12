package com.lightningstudio.watchrss.data.rss

import org.jsoup.Jsoup

private val videoExtensions = listOf(".mp4", ".m3u8", ".webm", ".mov")

object RssMediaExtractor {
    fun extract(item: RssItem): List<RssMediaRef> {
        val refs = mutableListOf<RssMediaRef>()
        item.imageUrl?.trim()?.takeIf { it.isNotEmpty() }?.let {
            refs.add(RssMediaRef(OfflineMediaType.IMAGE, it))
        }
        item.videoUrl?.trim()?.takeIf { it.isNotEmpty() }?.let {
            refs.add(RssMediaRef(OfflineMediaType.VIDEO, it))
        }

        val html = item.content?.takeIf { it.isNotBlank() }
            ?: item.description?.takeIf { it.isNotBlank() }
        if (!html.isNullOrBlank()) {
            refs.addAll(extractFromHtml(html))
        }
        return refs.distinctBy { it.url }
    }

    fun extractFromHtml(raw: String): List<RssMediaRef> {
        val refs = mutableListOf<RssMediaRef>()
        val doc = Jsoup.parseBodyFragment(raw)
        doc.outputSettings().prettyPrint(false)
        doc.select("script,style").remove()

        doc.select("img[src]").forEach { element ->
            val url = element.attr("src").trim()
            if (url.isNotEmpty()) {
                refs.add(RssMediaRef(OfflineMediaType.IMAGE, url))
            }
        }

        doc.select("video[src]").forEach { element ->
            val url = element.attr("src").trim()
            if (url.isNotEmpty()) {
                refs.add(RssMediaRef(OfflineMediaType.VIDEO, url))
            }
        }

        doc.select("video source[src]").forEach { element ->
            val url = element.attr("src").trim()
            if (url.isNotEmpty()) {
                refs.add(RssMediaRef(OfflineMediaType.VIDEO, url))
            }
        }

        doc.select("iframe[src]").forEach { element ->
            val url = element.attr("src").trim()
            if (url.isNotEmpty()) {
                refs.add(RssMediaRef(OfflineMediaType.VIDEO, url))
            }
        }

        doc.select("a[href]").forEach { element ->
            val url = element.attr("href").trim()
            if (url.isNotEmpty() && looksLikeVideo(url)) {
                refs.add(RssMediaRef(OfflineMediaType.VIDEO, url))
            }
        }

        return refs
    }

    private fun looksLikeVideo(url: String): Boolean {
        val lower = url.lowercase()
        return videoExtensions.any { lower.contains(it) }
    }
}

data class RssMediaRef(
    val type: OfflineMediaType,
    val url: String
)
