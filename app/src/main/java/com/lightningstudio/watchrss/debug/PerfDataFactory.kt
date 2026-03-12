package com.lightningstudio.watchrss.debug

import com.lightningstudio.watchrss.data.rss.RssItem

object PerfDataFactory {
    private const val LARGE_LIST_COUNT = 10_000
    private const val LARGE_ARTICLE_TARGET_CHARS = 100_000
    private const val IMAGE_INTERVAL = 12
    private val perfImages = listOf(
        PerfImage(
            url = "https://picsum.photos/id/1015/800/600",
            alt = "测试图片：湖边山景"
        ),
        PerfImage(
            url = "https://picsum.photos/id/1025/800/600",
            alt = "测试图片：野外动物"
        ),
        PerfImage(
            url = "https://picsum.photos/id/1035/800/600",
            alt = "测试图片：海岸线"
        ),
        PerfImage(
            url = "https://picsum.photos/id/1043/800/600",
            alt = "测试图片：城市街景"
        ),
        PerfImage(
            url = "https://picsum.photos/id/1050/800/600",
            alt = "测试图片：林间道路"
        ),
        PerfImage(
            url = "https://picsum.photos/id/1062/800/600",
            alt = "测试图片：日落天空"
        )
    )

    fun buildLargeList(): List<RssItem> {
        val now = System.currentTimeMillis()
        return List(LARGE_LIST_COUNT) { index ->
            val image = if (index % IMAGE_INTERVAL == 0) {
                perfImages[(index / IMAGE_INTERVAL) % perfImages.size]
            } else {
                null
            }
            RssItem(
                id = index.toLong(),
                channelId = 0L,
                title = if (image == null) {
                    "性能测试条目 ${index + 1}"
                } else {
                    "性能测试条目 ${index + 1}（图片）"
                },
                description = if (image == null) {
                    "这是用于滚动性能测试的摘要内容。"
                } else {
                    "这是一条带图的性能测试摘要内容。"
                },
                content = null,
                link = null,
                pubDate = null,
                imageUrl = image?.url,
                audioUrl = null,
                videoUrl = null,
                summary = if (image == null) {
                    "这是用于滚动性能测试的摘要内容。"
                } else {
                    "这是一条带图的性能测试摘要内容。"
                },
                previewImageUrl = null,
                isRead = index % 3 == 0,
                isLiked = false,
                readingProgress = 0f,
                fetchedAt = now - index
            )
        }
    }

    fun buildLargeArticle(): RssItem {
        val content = buildLargeArticleContent()
        return RssItem(
            id = 0L,
            channelId = 0L,
            title = "性能测试：超长文章",
            description = null,
            content = content,
            link = null,
            pubDate = null,
            imageUrl = perfImages.firstOrNull()?.url,
            audioUrl = null,
            videoUrl = null,
            summary = "用于性能测试的超长正文。",
            previewImageUrl = null,
            isRead = false,
            isLiked = false,
            readingProgress = 0f,
            fetchedAt = System.currentTimeMillis()
        )
    }

    private fun buildLargeArticleContent(): String {
        val paragraph = "这是一段用于性能测试的文本内容，用来模拟超长正文。" +
            "为了压测排版与滚动性能，会重复生成多个段落。\n"
        val builder = StringBuilder()
        var paragraphIndex = 0
        var imageIndex = 0
        while (builder.length < LARGE_ARTICLE_TARGET_CHARS) {
            builder.append("<p>")
            builder.append(paragraph.repeat(4))
            builder.append("</p>")
            paragraphIndex++
            if (paragraphIndex % 4 == 0) {
                val image = perfImages[imageIndex % perfImages.size]
                builder.append("<p><img src=\"")
                builder.append(image.url)
                builder.append("\" alt=\"")
                builder.append(image.alt)
                builder.append("\" /></p>")
                imageIndex++
            }
        }
        return builder.toString()
    }

    private data class PerfImage(
        val url: String,
        val alt: String
    )
}
