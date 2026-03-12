package com.lightningstudio.watchrss.data.rss

import java.io.File

interface RssDownloadClient {
    fun downloadToFile(url: String, file: File): String?
}
