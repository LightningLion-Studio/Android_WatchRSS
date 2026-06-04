package com.lightningstudio.watchrss.data.rss

object ImportedContentIds {
    const val ROOT_SOURCE_URL = "https://watchrss.local/import-content"
    const val EPUB_SOURCE_ROOT_URL = "https://watchrss.local/import-epub"
    const val PHONE_IMPORT_CHANNEL_URL = "watchrss://phone-imports"
    const val PHONE_IMPORT_CHANNEL_TITLE = "独立文章"

    fun isImportedContentUrl(url: String?): Boolean {
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized.startsWith(ROOT_SOURCE_URL) ||
            normalized.startsWith(EPUB_SOURCE_ROOT_URL) ||
            normalized == PHONE_IMPORT_CHANNEL_URL
    }

    fun isImportedTextSourceUrl(url: String?): Boolean {
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized == ROOT_SOURCE_URL
    }

    fun isImportedTextItemUrl(url: String?): Boolean {
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized.startsWith("$ROOT_SOURCE_URL/txt/")
    }

    fun isDeletableLocalContentChannel(url: String?): Boolean {
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized.startsWith(ROOT_SOURCE_URL) ||
            normalized.startsWith(EPUB_SOURCE_ROOT_URL) ||
            normalized == PHONE_IMPORT_CHANNEL_URL
    }
}
