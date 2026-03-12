package com.lightningstudio.watchrss.data.rss

data class RssChannelPreview(
    val url: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val siteUrl: String?,
    val items: List<RssPreviewItem>,
    val isBuiltin: Boolean
)

sealed class AddRssPreview {
    data class Existing(val channel: RssChannel) : AddRssPreview()
    data class Ready(val preview: RssChannelPreview) : AddRssPreview()
}

data class RssPreviewItem(
    val title: String?,
    val description: String?,
    val content: String?,
    val link: String?,
    val guid: String?,
    val pubDate: String?,
    val imageUrl: String?,
    val audioUrl: String?,
    val videoUrl: String?
)
