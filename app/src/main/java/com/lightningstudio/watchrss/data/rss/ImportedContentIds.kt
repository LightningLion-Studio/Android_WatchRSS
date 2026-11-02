package com.lightningstudio.watchrss.data.rss

data class ReaderContentCapabilities(val originalWeb: Boolean, val shareLink: Boolean)

object ImportedContentIds {
    fun readerCapabilities(url: String?): ReaderContentCapabilities {
        val value = url?.trim()?.lowercase().orEmpty()
        val webArticle = (value.startsWith("https://") || value.startsWith("http://")) &&
            !isImportedContentUrl(value)
        return ReaderContentCapabilities(originalWeb = webArticle, shareLink = webArticle)
    }

    const val ROOT_SOURCE_URL = "https://watchrss.local/import-content"
    const val TXT_NOVEL_SOURCE_ROOT_URL = "https://watchrss.local/import-txt-novel"
    const val EPUB_SOURCE_ROOT_URL = "https://watchrss.local/import-epub"
    const val PHONE_IMPORT_CHANNEL_URL = "watchrss://phone-imports"
    const val PHONE_IMPORT_CHANNEL_TITLE = "独立文章"

    fun isImportedContentUrl(url: String?): Boolean {
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized.startsWith(ROOT_SOURCE_URL) ||
            normalized.startsWith(TXT_NOVEL_SOURCE_ROOT_URL) ||
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

    fun isNovelSourceUrl(url: String?): Boolean {
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized == ROOT_SOURCE_URL || isNovelChapterSourceUrl(normalized)
    }

    fun isNovelChapterSourceUrl(url: String?): Boolean {
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized == EPUB_SOURCE_ROOT_URL || normalized.startsWith("$EPUB_SOURCE_ROOT_URL/") ||
            normalized == TXT_NOVEL_SOURCE_ROOT_URL || normalized.startsWith("$TXT_NOVEL_SOURCE_ROOT_URL/")
    }

    /** 小说由 TXT 整书或 EPUB 章节组成；独立网页导入不在此范围。 */
    fun isNovelContentItemUrl(url: String?): Boolean {
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized.startsWith("$ROOT_SOURCE_URL/txt/") ||
            normalized.startsWith(TXT_NOVEL_SOURCE_ROOT_URL) ||
            normalized.startsWith(EPUB_SOURCE_ROOT_URL)
    }

    fun isDeletableLocalContentChannel(url: String?): Boolean {
        val normalized = url?.trim()?.lowercase() ?: return false
        return normalized.startsWith(ROOT_SOURCE_URL) ||
            normalized.startsWith(TXT_NOVEL_SOURCE_ROOT_URL) ||
            normalized.startsWith(EPUB_SOURCE_ROOT_URL) ||
            normalized == PHONE_IMPORT_CHANNEL_URL
    }
}
