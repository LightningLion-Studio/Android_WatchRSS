package com.lightningstudio.watchrss.data.rss

object ImportedContentIds {
    const val ROOT_SOURCE_URL = "https://watchrss.local/import-content"

    fun isImportedContentUrl(url: String?): Boolean {
        return url?.trim()?.lowercase()?.startsWith(ROOT_SOURCE_URL) == true
    }
}
