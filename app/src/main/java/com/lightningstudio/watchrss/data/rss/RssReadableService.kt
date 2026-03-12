package com.lightningstudio.watchrss.data.rss

import com.lightningstudio.watchrss.debug.DebugLogBuffer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URL
import java.util.concurrent.TimeUnit

class RssReadableService(
    private val client: OkHttpClient = buildClient(),
    private val userAgent: String = DEFAULT_ORIGINAL_CONTENT_UA
) {
    fun fetchOriginalContent(link: String?, baseUrl: String?): String? {
        val resolved = resolveItemLink(link, baseUrl)
            ?: run {
                DebugLogBuffer.log("orig", "skip no-link base=$baseUrl")
                return null
            }
        return try {
            val request = Request.Builder()
                .url(resolved)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DebugLogBuffer.log(
                        "orig",
                        "http ${response.code} ${response.message} url=$resolved"
                    )
                    return null
                }
                val body = response.body?.string()?.takeIf { it.isNotBlank() }
                if (body == null) {
                    DebugLogBuffer.log("orig", "empty-body url=$resolved")
                    return null
                }
                val extracted = extractReadableHtml(body, resolved)
                if (extracted == null) {
                    DebugLogBuffer.log("orig", "no-readable-content url=$resolved")
                } else {
                    DebugLogBuffer.log("orig", "ok url=$resolved size=${extracted.length}")
                }
                extracted
            }
        } catch (e: Exception) {
            DebugLogBuffer.log(
                "orig",
                "error ${e.javaClass.simpleName} url=$resolved msg=${e.message}"
            )
            null
        }
    }

    private fun resolveItemLink(link: String?, baseUrl: String?): String? {
        val trimmed = link?.trim()?.ifEmpty { null } ?: return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (baseUrl.isNullOrBlank()) return null
        return try {
            URL(URL(baseUrl), trimmed).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractReadableHtml(html: String, baseUrl: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        doc.outputSettings().prettyPrint(false)
        doc.select("script,style,noscript").remove()
        val candidates = doc.select(
            "article, main, [role=main], div#content, div#article, div#article_content, div.content, div.left_zw"
        )
        val root = candidates.maxByOrNull { it.text().length } ?: doc.body()
        if (root == null) return null
        sanitizeReadableRoot(root)
        val text = root.text().trim()
        val hasMedia = root.select("img,video,iframe").isNotEmpty()
        if (text.isEmpty() && !hasMedia) return null
        val content = root.outerHtml().trim()
        return content.ifEmpty { null }
    }

    private fun sanitizeReadableRoot(root: Element) {
        root.select("script,style,noscript,header,footer,nav,form,aside,button,svg").remove()
        normalizeMediaUrls(root)
    }

    private fun normalizeMediaUrls(root: Element) {
        val imageFallbacks = listOf(
            "data-src",
            "data-original",
            "data-lazy-src",
            "data-actualsrc",
            "data-url"
        )
        root.select("img").forEach { img ->
            val src = pickFirstAttr(img, "src", imageFallbacks)
            updateAbsUrl(img, "src", src)
            if (img.hasAttr("srcset")) {
                img.removeAttr("srcset")
            }
        }
        root.select("video[src], source[src], iframe[src]").forEach { element ->
            updateAbsUrl(element, "src", element.attr("src").trim())
        }
    }

    private fun pickFirstAttr(element: Element, primary: String, fallbacks: List<String>): String? {
        val direct = element.attr(primary).trim()
        if (direct.isNotBlank()) return direct
        for (attr in fallbacks) {
            val value = element.attr(attr).trim()
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun updateAbsUrl(element: Element, attr: String, value: String?) {
        val trimmed = value?.trim()?.ifEmpty { null } ?: return
        element.attr(attr, trimmed)
        val abs = element.absUrl(attr)
        if (abs.isNotBlank()) {
            element.attr(attr, abs)
        }
    }

    companion object {
        private const val DEFAULT_ORIGINAL_CONTENT_UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private fun buildClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
