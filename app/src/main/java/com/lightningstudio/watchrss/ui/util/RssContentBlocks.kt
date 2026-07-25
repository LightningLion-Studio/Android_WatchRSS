package com.lightningstudio.watchrss.ui.util

import com.lightningstudio.watchrss.data.douyin.buildDouyinPlaybackWebUrl
import com.lightningstudio.watchrss.data.douyin.parseDouyinAwemeId
import com.lightningstudio.watchrss.data.bili.buildBiliPlaybackWebUrl
import com.lightningstudio.watchrss.data.bili.parseBiliVideoTarget
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.data.rss.effectiveContent

fun buildContentBlocks(item: RssItem, useOriginalContent: Boolean): List<ContentBlock> {
    val raw = item.effectiveContent(useOriginalContent)
    val blocks = if (raw.isNullOrBlank()) {
        mutableListOf()
    } else if (ImportedContentIds.isImportedTextItemUrl(item.link)) {
        RssContentParser.parsePlainText(raw).toMutableList()
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
    } else if (itemVideo == null) {
        val biliTarget = parseBiliVideoTarget(item.link)
        val biliFallbackVideo = buildBiliPlaybackWebUrl(
            aid = biliTarget?.aid,
            bvid = biliTarget?.bvid,
            cid = biliTarget?.cid,
            fallbackUrl = item.link
        )
        if (!biliFallbackVideo.isNullOrBlank() &&
            blocks.none { it is ContentBlock.Video && it.url == biliFallbackVideo }
        ) {
            blocks.add(ContentBlock.Video(biliFallbackVideo, itemImage))
            return blocks
        }

        val douyinAwemeId = parseDouyinAwemeId(item.link)
        val douyinFallbackVideo = buildDouyinPlaybackWebUrl(
            awemeId = douyinAwemeId,
            fallbackUrl = item.link
        )
        if (!douyinFallbackVideo.isNullOrBlank() &&
            blocks.none { it is ContentBlock.Video && it.url == douyinFallbackVideo }
        ) {
            blocks.add(ContentBlock.Video(douyinFallbackVideo, null))
        }
    }
    return blocks
}
