package com.lightningstudio.watchrss.data.rss

data class ExternalSavedItem(
    val channelUrl: String,
    val item: RssPreviewItem,
    val fetchedAt: Long = System.currentTimeMillis()
)
