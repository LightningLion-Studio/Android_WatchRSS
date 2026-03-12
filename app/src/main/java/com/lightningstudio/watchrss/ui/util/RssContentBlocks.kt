package com.lightningstudio.watchrss.ui.util

import com.lightningstudio.watchrss.data.rss.RssItem

fun buildContentBlocks(item: RssItem): List<ContentBlock> {
    val raw = item.content ?: item.description
    val blocks = if (raw.isNullOrBlank()) {
        mutableListOf()
    } else {
        RssContentParser.parse(raw).toMutableList()
    }
    val itemImage = item.imageUrl?.takeIf { it.isNotBlank() }
    if (itemImage != null && blocks.none { it is ContentBlock.Image && it.url == itemImage }) {
        blocks.add(ContentBlock.Image(itemImage, null))
    }
    val itemVideo = item.videoUrl?.takeIf { it.isNotBlank() }
    if (itemVideo != null && blocks.none { it is ContentBlock.Video && it.url == itemVideo }) {
        blocks.add(ContentBlock.Video(itemVideo, null))
    }
    return blocks
}
