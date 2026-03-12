package com.lightningstudio.watchrss.data.rss

object RssUrlResolver {
    fun resolveMediaUrl(raw: String?, baseLink: String?): String? {
        val trimmed = raw?.trim()?.ifEmpty { return null } ?: return null
        if (trimmed.startsWith("data:", ignoreCase = true)) return null
        val sanitized = trimmed.replace(" ", "%20")
        if (sanitized.startsWith("//")) {
            val scheme = baseLink?.let { runCatching { java.net.URI(it).scheme }.getOrNull() }
                ?: "https"
            return "$scheme:$sanitized"
        }
        if (sanitized.startsWith("http://") || sanitized.startsWith("https://") ||
            sanitized.startsWith("file://") || sanitized.startsWith("/")
        ) {
            if (sanitized.startsWith("/") && !baseLink.isNullOrBlank()) {
                return resolveRelativeUrl(baseLink, sanitized)
            }
            if (sanitized.startsWith("/") && baseLink.isNullOrBlank()) {
                return if (java.io.File(sanitized).exists()) sanitized else null
            }
            return sanitized
        }
        if (!sanitized.contains("://")) {
            if (sanitized.startsWith("www.", ignoreCase = true)) {
                return "https://$sanitized"
            }
            if (!baseLink.isNullOrBlank()) {
                return resolveRelativeUrl(baseLink, sanitized)
            }
        }
        return null
    }

    private fun resolveRelativeUrl(baseLink: String, relative: String): String? {
        return try {
            java.net.URL(java.net.URL(baseLink), relative).toString()
        } catch (e: Exception) {
            null
        }
    }
}
